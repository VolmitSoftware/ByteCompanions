import { reloadFiles } from './file-reload.mjs'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { enableProgression, sameMap } from './progression.mjs'
import { expectFoundation, readyProgression, vaultState } from './vault-foundation.mjs'
import { componentText, itemText } from './merged-features.mjs'
import { equipAfterCoreLoad } from './database-common.mjs'

export default {
  name: 'bytecompanions-vault-foundation-restart',
  description: 'After a verified process restart, require real Foundation balance, mixed-cost tier, spent credit, active booster, inventory and potion benefit to persist.',
  async run(context) {
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Restart QA requires isolation')
    const checkpoint = JSON.parse(await readFile(path.join(directory, 'bytecompanions-vault-checkpoint.json'), 'utf8'))
    const restart = JSON.parse(await readFile(path.join(directory, 'bytecompanions-vault-restart.json'), 'utf8'))
    context.expect(restart.previousProcessExited && restart.previousProcessIds.length > 0
      && Date.parse(restart.startedAt) > Date.parse(checkpoint.recordedAt), 'Driver did not establish full restart', { restart })
    const configPath = path.join(directory, 'plugins/ByteCompanions/config.toml')
    const definitionsPath = path.join(directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionsPath, 'utf8')
    const reload = () => reloadFiles(context)
    let state
    try {
      await context.step('real Foundation ledger balance and provider identity survive full restart', async () => {
        const economy = await vaultState(context)
        expectFoundation(context, economy, checkpoint.expected)
        context.expect(economy.balance === checkpoint.economy.balance && economy.balance === 93,
          'Real Foundation balance did not persist', { economy, expected: checkpoint.economy })
        context.report.observations ??= []
        context.report.observations.push({ kind: 'actual-provider-restart', economy, restart })
      })
      await context.step('paid tier, consumed credit and activation identity persist without another payment', async () => {
        await writeFile(definitionsPath, checkpoint.profile, 'utf8')
        await writeFile(configPath, enableProgression(original), 'utf8')
        await reload()
        state = await readyProgression(context)
        context.expect(state.totalXp === checkpoint.state.totalXp && sameMap(state.tracks, checkpoint.state.tracks)
          && sameMap(state.credits, checkpoint.state.credits) && state.personalBoosters.length === 1
          && state.personalBoosters[0].activationId === checkpoint.state.personalBoosters[0].activationId
          && state.rates.companion_xp === 50 && (await vaultState(context)).balance === 93,
        'Restart changed paid benefits or charged again', { state, checkpoint: checkpoint.state })
      })
      await context.step('the restored paid tier still grants its actual potion bonus', async () => {
        await equipAfterCoreLoad(context, '/companions spawn bee', /^You have equipped bee\.$/i)
        const speed = context.bot.registry.effectsByName.Speed.id
        await context.waitUntil(() => context.bot.entity.effects?.[speed]?.amplifier === 6, { timeoutMs: 12_000, label: 'paid Speed VII after restart' })
        const count = named => context.bot.inventory.items().filter(item => item.name === 'emerald'
          && /QA Named Emerald/.test(componentText(itemText(item)).replace(/§[0-9a-fk-orx]/gi, '')) === named)
          .reduce((sum, item) => sum + item.count, 0)
        const inventory = { plain: count(false), named: count(true) }
        context.expect(inventory.plain === checkpoint.inventory.plain && inventory.named === checkpoint.inventory.named
          && inventory.plain === 13 && inventory.named === 4,
        'Plain or named payment inventory changed across restart', { inventory, expected: checkpoint.inventory })
        context.report.observations.push({ kind: 'actual-paid-gameplay-restart', amplifier: 6, inventory, state })
      })
    } finally {
      if (context.bot.currentWindow) context.bot.closeWindow(context.bot.currentWindow)
      await context.command('/companions despawn bee')
      await writeFile(definitionsPath, definitions, 'utf8')
      await writeFile(configPath, original, 'utf8')
      await reload()
    }
  }
}
