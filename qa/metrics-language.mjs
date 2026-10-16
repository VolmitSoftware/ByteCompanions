import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { assertIsolated, reloadFiles, writeAndReload } from './file-reload.mjs'

const timeoutMs = 15_000

export async function metrics(context) {
  const response = await context.command('/bcqa metrics', /BCQA METRICS /, timeoutMs)
  const state = JSON.parse(response.split('BCQA METRICS ')[1])
  context.report.metricsObservations ??= []
  context.report.metricsObservations.push(state)
  context.expect(state.instances.every(instance => instance.serviceId === 34587 && !instance.globalEnabled
    && instance.scheduledTasks === 0), 'Global bStats opt-out or registered service ID differs', state)
  return state
}

function enabled(source, value) {
  const block = /^\[metrics\]\r?\n[^[]*/m.exec(source)
  if (!block || !/^enabled\s*=\s*(?:true|false)/m.test(block[0])) throw new Error('Missing metrics.enabled setting')
  return source.replace(block[0], block[0].replace(/^enabled\s*=\s*(?:true|false)/m, `enabled = ${value}`))
}

export function olderGermanCatalog(original) {
  const reasons = /^\[collection\.reason\]\r?\n[^[]*/m.exec(original)?.[0]
  const particles = /^\[particles\]\r?\n[^[]*/m.exec(original)?.[0]
  if (!reasons || !/^outfit-name-invalid\s*=/m.test(reasons)
    || !particles || !/^title\s*=/m.test(particles)) throw new Error('Generated locale sections differ from the fixture')
  const edited = original.replace(reasons, reasons.replace(/^outfit-name-invalid\s*=.*(?:\r?\n|$)/m, ''))
    .replace(particles, particles.replace(/^title\s*=.*$/m, 'title = "<gold>QA Betreiberpartikel</gold>"'))
  if (/^outfit-name-invalid\s*=/m.test(edited) || !edited.includes('QA Betreiberpartikel')) {
    throw new Error('Older-catalog fixture did not remove the new key and retain an operator override')
  }
  return edited
}

async function rejectOutfit(context, locale) {
  const german = locale === 'de_DE'
  await context.command(`/companions language self ${locale}`, new RegExp(`${locale}\\.`), timeoutMs)
  const collection = async () => JSON.parse((await context.command('/bcqa collection', /BCQA COLLECTION /, timeoutMs)).split('BCQA COLLECTION ')[1])
  const before = await collection()
  context.expect(before.ready, 'Collection is not loaded before localized rejection')
  const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
  await context.command('/companions outfits')
  await opened
  await context.waitUntil(() => context.bot.currentWindow?.slots[48], { timeoutMs, label: 'save outfit button' })
  const prompt = context.waitForMessage(german ? /Gib einen Outfit-Namen im Chat ein/ : /Enter an outfit name in chat/, timeoutMs)
  await context.bot.clickWindow(48, 0, 0)
  await prompt
  await context.waitUntil(() => !context.bot.currentWindow, { timeoutMs, label: 'outfit name prompt closes inventory' })
  const expected = german
    ? /Outfit-Namen benötigen 1–32 Buchstaben, Zahlen, Unterstriche oder Bindestriche\./
    : /Outfit names use 1–32 letters, numbers, underscores or hyphens\./
  const response = await context.command('!', expected, timeoutMs)
  context.expect(!german || !response.includes('Outfit names use'), 'Selected locale leaked the English rejection')
  const after = await collection()
  context.expect(JSON.stringify(after.profile) === JSON.stringify(before.profile), 'Rejected outfit name modified the profile')
  context.report.localizedRejections ??= []
  context.report.localizedRejections.push({ locale, response, profileUnchanged: true })
}

export default {
  name: 'bytecompanions-metrics-language',
  description: 'Verify bStats opt-out, hot-reload lifecycle, and selected-locale collection validation.',
  async run(context) {
    await assertIsolated(context)
    const root = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const config = path.join(root, 'config.toml')
    const globalConfig = await readFile(path.join(root, '..', 'bStats', 'config.yml'), 'utf8')
    context.expect(/^enabled:\s*false$/m.test(globalConfig), 'QA must disable global bStats submissions before startup')
    let initial
    await context.step('registered metrics instance respects global bStats opt-out', async () => {
      initial = await metrics(context)
      context.expect(initial.initialized && !initial.closed && initial.instances.length === 1,
        'Default plugin metrics did not initialize exactly once', initial)
      context.expect(!initial.instances[0].schedulerShutdown, 'New metrics scheduler is already shut down')
    })
    await context.step('unrelated file reload retains the existing metrics instance', async () => {
      await reloadFiles(context)
      const state = await metrics(context)
      context.expect(state.serviceIdentity === initial.serviceIdentity && state.activeIdentity === initial.activeIdentity
        && state.instances.length === 1, 'Reload duplicated the metrics service or scheduler', state)
    })
    await context.step('plugin opt-out shuts down the active scheduler', async () => {
      await writeAndReload(context, config, enabled(await readFile(config, 'utf8'), false))
      const state = await metrics(context)
      context.expect(!state.initialized && state.instances.length === 1 && state.instances[0].schedulerShutdown,
        'Plugin opt-out left the metrics scheduler active', state)
    })
    await context.step('disabled metrics stay disabled through another file reload', async () => {
      await reloadFiles(context)
      const state = await metrics(context)
      context.expect(!state.initialized && state.instances.length === 1 && state.instances[0].schedulerShutdown,
        'Unrelated reload restarted disabled metrics', state)
    })
    await context.step('plugin re-enable creates one replacement while global opt-out remains effective', async () => {
      await writeAndReload(context, config, enabled(await readFile(config, 'utf8'), true))
      const state = await metrics(context)
      context.expect(state.initialized && state.serviceIdentity === initial.serviceIdentity
        && state.activeIdentity !== initial.activeIdentity && state.instances.length === 2
        && state.instances.filter(instance => instance.schedulerShutdown).length === 1,
      'Re-enable did not replace the stopped metrics instance exactly once', state)
    })
    await context.step('German outfit validation uses the selected locale without changing the profile', async () => {
      await rejectOutfit(context, 'de_DE')
    })
    await context.step('older German catalog uses bundled German rejection and preserves operator overrides', async () => {
      const localeFile = path.join(root, 'languages', 'de_DE.toml')
      const original = await readFile(localeFile, 'utf8')
      const edited = olderGermanCatalog(original)
      await writeFile(path.join(context.server.directory, '.qa-metrics-locale-before.toml'), original, 'utf8')
      await writeFile(path.join(context.server.directory, '.qa-metrics-locale-edited.toml'), edited, 'utf8')
      context.report.localeUpgradeFixture = { omittedKey: 'collection.reason.outfit-name-invalid',
        overrideKey: 'particles.title', overrideValue: 'QA Betreiberpartikel' }
      try {
        await writeFile(localeFile, edited, 'utf8')
        await reloadFiles(context)
        await context.command('/companions particles help', /QA Betreiberpartikel/, timeoutMs)
        await rejectOutfit(context, 'de_DE')
        context.expect((await readFile(localeFile, 'utf8')).includes('QA Betreiberpartikel'),
          'Bundled fallback overwrote the operator translation')
      } finally {
        await writeFile(localeFile, original, 'utf8')
        await reloadFiles(context)
      }
    })
    await context.step('English outfit validation remains available after switching language', async () => {
      await rejectOutfit(context, 'en_US')
    })
    await context.step('personal locale remains saved for restart', async () => {
      await context.command('/companions language self de_DE', /de_DE\./, timeoutMs)
      await context.command('/companions particles help', /Begleiterpartikel/, timeoutMs)
      await writeFile(path.join(context.server.directory, '.qa-metrics-language.json'), JSON.stringify({
        username: context.bot.username, uuid: context.bot.player.uuid, locale: 'de_DE'
      }), 'utf8')
    })
  }
}
