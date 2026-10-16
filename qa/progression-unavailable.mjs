import { readFile, writeFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { enableProgression, progressionState, progressionProfile } from './progression.mjs'
import { componentText, itemText } from './merged-features.mjs'
import { mutateAndReload } from './file-reload.mjs'

export default {
  name: 'bytecompanions-progression-unavailable',
  description: 'Enable item/XP progression without any Vault API JAR, then reject a money purchase without a charge, journal lock or benefit.',
  async run(context) {
    const directory = context.server.directory
    context.expect(typeof directory === 'string' && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Missing-Vault QA requires an isolated managed instance')
    const jars = (await readdir(path.join(directory, 'plugins'))).filter(name => name.endsWith('.jar'))
    context.expect(!jars.some(name => /vault|economy/i.test(name)), 'Missing-Vault scenario found an economy JAR', { jars })
    const configPath = path.join(directory, 'plugins/ByteCompanions/config.toml')
    const definitionsPath = path.join(directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionsPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original), 'Missing-Vault QA requires autoReload=true')
    try {
      await context.step('optional progression loads with no Vault classes installed', async () => {
        await mutateAndReload(context, async () => {
          await writeFile(definitionsPath, progressionProfile, 'utf8')
          await writeFile(configPath, enableProgression(original), 'utf8')
        })
        let state
        const deadline = Date.now() + 12_000
        do {
          state = await progressionState(context)
          if (state?.ready) break
          await context.sleep(150)
        } while (Date.now() < deadline)
        context.expect(state?.ready && state.totalXp === 0, 'Fresh player did not load without Vault', { state })
        await context.command(`/companions progression give ${context.bot.username} bee 100`, /Saved 100 companion XP/, 12_000)
      })
      await context.step('missing Vault rejects money purchase without spending or granting', async () => {
        await context.command('/bcqa paymentitems 2 0', /BCQA PAYMENT_ITEMS/, 12_000)
        await context.waitUntil(() => context.bot.inventory.items().filter(item => item.name === 'emerald').reduce((sum, item) => sum + item.count, 0) === 2,
          { timeoutMs: 12_000, label: 'required plain items present without Vault' })
        await context.command('/companions upgrades bee')
        await context.waitUntil(() => componentText(context.bot.currentWindow?.title).includes('Companion Upgrades'),
          { timeoutMs: 12_000, label: 'upgrade menu without Vault' })
        const economy = await context.waitUntil(() => {
          const slot = context.bot.currentWindow?.slots.slice(0, 54).findIndex(item => itemText(item).includes('QA Economy'))
          return slot >= 0 ? { slot } : false
        }, { timeoutMs: 12_000, label: 'money upgrade entry' })
        await context.bot.clickWindow(economy.slot, 0, 0)
        await context.waitUntil(() => componentText(context.bot.currentWindow?.title).includes('Confirm Purchase'),
          { timeoutMs: 12_000, label: 'money confirmation without Vault' })
        const feedback = context.waitForMessage(/economy is unavailable/i, 12_000)
        await context.bot.clickWindow(22, 0, 0)
        await feedback
        const state = await progressionState(context)
        context.expect(state.ready && !state.busy && state.totalXp === 100 && !state.tracks.economy
          && context.bot.inventory.items().filter(item => item.name === 'emerald').reduce((sum, item) => sum + item.count, 0) === 2,
          'Missing economy changed benefits or left purchase locked', { state })
        context.report.observations ??= []
        context.report.observations.push({ kind: 'missing-vault', state, jars })
      })
    } finally {
      if (context.bot.currentWindow) context.bot.closeWindow(context.bot.currentWindow)
      await mutateAndReload(context, async () => {
        await writeFile(definitionsPath, definitions, 'utf8')
        await writeFile(configPath, original, 'utf8')
      })
    }
  }
}
