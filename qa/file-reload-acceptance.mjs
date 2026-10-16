import { readFile, writeFile, rename, unlink, stat, utimes } from 'node:fs/promises'
import path from 'node:path'
import { assertIsolated, runtimeReport, writeAndReload, waitForFileReload, rejectFileReload, rejectFileMutation } from './file-reload.mjs'
import { controls, equipment } from './collections.mjs'

const timeoutMs = 30_000

export default {
  name: 'bytecompanions-file-reload',
  description: 'Verify automatic content reload, stable rejection and recovery, retained connected-player state, and removed reload commands.',
  async run(context) {
    await assertIsolated(context)
    const root = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const config = path.join(root, 'config.toml')
    const companion = path.join(root, 'companions', 'bee.toml')
    const attributes = path.join(root, 'features', 'attributes', 'config.toml')
    const temporaryCompanion = path.join(root, 'companions', 'qa_file_wisp.toml')
    const original = new Map(await Promise.all([config, companion, attributes].map(async filename => [filename, await readFile(filename, 'utf8')])))
    context.expect(/autoReload\s*=\s*true/.test(original.get(config)), 'Start focused QA with automatic reload enabled')
    const ui = controls(context)
    const close = async () => {
      if (context.bot.currentWindow) await context.bot.clickWindow(49, 1, 0)
      await context.waitUntil(() => !context.bot.currentWindow, { timeoutMs, label: 'server closes dashboard' })
    }
    let particleCount = 0
    const packet = (_, metadata) => { if (metadata.name === 'world_particles') particleCount++ }
    context.bot._client.on('packet', packet)
    let acceptedConfig = original.get(config)
    const feature = armor => `[[companions.bee]]
attribute = "armor"
amount = ${armor}
operation = "ADD_NUMBER"
`
    const state = () => ui.state()
    const ownerReady = async () => {
      const snapshot = await state()
      context.expect(snapshot.ready && snapshot.companions.bee, 'Connected player lost saved companion or collection', snapshot)
      context.expect(snapshot.profile.viewerPreferences.particleIntensity === 'REDUCED', 'Reload lost particle preference', snapshot)
      await context.command('/bcqa api inspect', /BCQA API PASS selected=bee count=1/, timeoutMs)
      await context.waitUntil(() => context.bot.entities[snapshot.companions.bee.entityId], { timeoutMs, label: 'restored companion reaches owner' })
      const before = particleCount
      await context.waitUntil(() => particleCount > before, { timeoutMs, label: 'saved particle selection emits after replacement' })
      return snapshot
    }
    const armor = async expected => context.waitUntil(async () => {
      const response = await context.command('/bcqa polish', /BCQA POLISH /, timeoutMs)
      return JSON.parse(response.split('BCQA POLISH ')[1]).armor === expected
    }, { timeoutMs, intervalMs: 500, label: `live armor contribution ${expected}` })
    try {
      await context.step('initial core player, selection and personal settings are ready', async () => {
        await ui.command('/gamemode creative', /game mode|gamemode/i)
        await ui.command('/bcqa move 0 100 0', /BCQA MOVE success=true/)
        await ui.command('/bcqa platform', /BCQA PLATFORM blocks=49/)
        await context.waitUntil(async () => (await state()).ready, { timeoutMs, label: 'initial player profile ready' })
        await ui.command('/companions spawn bee', /equipped bee|already have/)
        await ui.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        await ui.command('/companions settings particles reduced', /Collection saved\./)
        await ownerReady()
      })
      await context.step('root change restores all connected player state under default settings', async () => {
        acceptedConfig = original.get(config).replace(/attributes\s*=\s*false/, 'attributes = true')
        acceptedConfig += '\n# Root file reload acceptance\n'
        await writeAndReload(context, config, acceptedConfig)
        await ownerReady()
        await ui.dashboard('bee')
        await close()
        await ui.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/)
      })
      await context.step('companion file edit updates live equipment while retaining selection', async () => {
        await writeAndReload(context, companion, original.get(companion).replaceAll('BEE_NEST', 'HONEY_BLOCK'))
        const snapshot = await ownerReady()
        await equipment(context, snapshot.companions.bee.entityId, 'honey_block', 'file edited companion equipment')
      })
      await context.step('feature file edit updates live bonuses without a reload command', async () => {
        await writeAndReload(context, attributes, feature(12))
        await ownerReady()
        await armor(12)
      })
      await context.step('malformed root edit is rejected once and corrected file recovers', async () => {
        const log = path.join(context.server.directory, 'logs', 'latest.log')
        const offset = (await readFile(log, 'utf8')).length
        await rejectFileReload(context, { filename: config, content: 'invalid = [unterminated\n' })
        await ownerReady()
        await context.sleep(3500)
        const failures = ((await readFile(log, 'utf8')).slice(offset).match(/Configuration hotload rejected; previous settings remain active/g) ?? []).length
        context.expect(failures === 1, 'Unchanged rejected file was repeatedly processed', { failures })
        acceptedConfig += '\n# Corrected root after rejected edit\n'
        await writeAndReload(context, config, acceptedConfig)
        await ownerReady()
      })
      await context.step('required feature deletion preserves active content and restoration recovers', async () => {
        await rejectFileMutation(context, () => unlink(attributes))
        await ownerReady()
        await armor(12)
        await writeAndReload(context, attributes, `${feature(12)}# Restored required feature\n`)
        await ownerReady()
      })
      await context.step('new companion creation and deletion update the definition registry', async () => {
        const before = await runtimeReport(context)
        const count = Number(/Companion definitions: (\d+)/.exec(before.text)?.[1])
        context.expect(Number.isSafeInteger(count), 'Missing definition count')
        const created = await writeAndReload(context, temporaryCompanion, original.get(companion))
        context.expect(created.text.includes(`Companion definitions: ${count + 1}\n`), 'Created companion did not enter registry')
        await unlink(temporaryCompanion)
        const removed = await waitForFileReload(context, created.generation)
        context.expect(removed.text.includes(`Companion definitions: ${count}\n`), 'Deleted companion remained registered')
        await ownerReady()
      })
      await context.step('atomic save and equal-metadata edit both apply companion content', async () => {
        const before = await runtimeReport(context)
        await writeFile(`${companion}.tmp`, original.get(companion), 'utf8')
        await rename(`${companion}.tmp`, companion)
        await waitForFileReload(context, before.generation)
        await equipment(context, (await ownerReady()).companions.bee.entityId, 'bee_nest', 'atomic companion replacement')
        const metadata = await stat(companion)
        const source = await readFile(companion, 'utf8')
        const changed = source.replaceAll('BEE_NEST', 'RED_SAND')
        context.expect(Buffer.byteLength(source) === Buffer.byteLength(changed), 'Equal-metadata fixture changed file size')
        const previous = await runtimeReport(context)
        await writeFile(companion, changed, 'utf8')
        await utimes(companion, metadata.atime, metadata.mtime)
        await waitForFileReload(context, previous.generation)
        await equipment(context, (await ownerReady()).companions.bee.entityId, 'red_sand', 'same-size same-mtime companion edit')
      })
      await context.step('burst edits create one replacement using the complete latest bundle', async () => {
        const before = await runtimeReport(context)
        const log = path.join(context.server.directory, 'logs', 'latest.log')
        const offset = (await readFile(log, 'utf8')).length
        acceptedConfig += '\n# Coalesced multi-file edit\n'
        await Promise.all([writeFile(config, acceptedConfig), writeFile(companion, original.get(companion).replaceAll('BEE_NEST', 'HONEY_BLOCK')), writeFile(attributes, feature(14))])
        const accepted = await waitForFileReload(context, before.generation)
        await equipment(context, (await ownerReady()).companions.bee.entityId, 'honey_block', 'latest complete bundle equipment')
        await armor(14)
        await context.sleep(3500)
        context.expect((await runtimeReport(context)).generation === accepted.generation, 'One file burst caused another runtime replacement')
        const stops = ((await readFile(log, 'utf8')).slice(offset).match(/Plugin runtime stopped/g) ?? []).length
        context.expect(stops === 1, 'File burst was not coalesced into one replacement', { stops })
      })
      await context.step('disabled automatic reload defers edits and root re-enable applies them', async () => {
        const paused = acceptedConfig.replace(/autoReload\s*=\s*true/, 'autoReload = false')
        const disabled = await writeAndReload(context, config, paused)
        await writeFile(companion, original.get(companion), 'utf8')
        await context.sleep(3500)
        context.expect((await runtimeReport(context)).generation === disabled.generation, 'Disabled watcher applied a companion edit')
        await equipment(context, (await ownerReady()).companions.bee.entityId, 'honey_block', 'disabled watcher preserves active content')
        await writeAndReload(context, config, acceptedConfig)
        await equipment(context, (await ownerReady()).companions.bee.entityId, 'bee_nest', 'root re-enable applies pending content')
      })
      await context.step('reload routes are absent and normal player menus remain usable', async () => {
        const roots = await context.bot.tabComplete('/companions ', false, false, 15_000)
        const tokens = roots.map(value => typeof value === 'string' ? value : value.match)
        context.expect(!tokens.some(value => /^(?:reload|attributes|buffs|decorations|effects)$/.test(value)), 'Retired reload-only route remains exposed', { tokens })
        const particleRoots = await context.bot.tabComplete('/companions particles ', false, false, 15_000)
        context.expect(!particleRoots.some(value => (typeof value === 'string' ? value : value.match) === 'reload'), 'Particle reload route remains exposed')
        await ui.dashboard('bee')
        await close()
        await ownerReady()
      })
      await context.step('final persisted selection and settings are ready for process restart', async () => {
        const snapshot = await ownerReady()
        await ui.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/)
        await writeFile(path.join(context.server.directory, '.qa-file-reload.json'), JSON.stringify({ username: context.bot.username, uuid: context.bot.player.uuid, preferences: snapshot.profile.viewerPreferences, selected: Object.keys(snapshot.companions) }), 'utf8')
      })
    } finally {
      context.bot._client.removeListener('packet', packet)
      if (context.bot.currentWindow) context.bot.closeWindow(context.bot.currentWindow)
      await unlink(temporaryCompanion).catch(error => { if (error.code !== 'ENOENT') throw error })
      await unlink(`${companion}.tmp`).catch(error => { if (error.code !== 'ENOENT') throw error })
      const before = await runtimeReport(context)
      await Promise.all([...original].map(([filename, contents]) => writeFile(filename, contents, 'utf8')))
      await waitForFileReload(context, before.generation)
    }
  }
}
