import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { enableProgression, progressionState, sameMap } from './progression.mjs'
import { equipAfterCoreLoad } from './database-common.mjs'
import { mutateAndReload } from './file-reload.mjs'

export default {
  name: 'bytecompanions-progression-restart',
  description: 'After a full server restart, verify persisted paid tiers, XP, booster credits, absolute server expiry, paused personal duration and restored potion gameplay.',
  async run(context) {
    const directory = context.server.directory
    context.expect(typeof directory === 'string' && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Restart QA requires an isolated managed instance')
    const checkpoint = JSON.parse(await readFile(path.join(directory, 'bytecompanions-progression-checkpoint.json'), 'utf8'))
    const restart = JSON.parse(await readFile(path.join(directory, 'bytecompanions-progression-restart.json'), 'utf8'))
    context.expect(Date.parse(restart.startedAt) > Date.parse(checkpoint.recordedAt) && restart.previousProcessExited === true,
      'Restart driver must prove the previous server process exited', { restart, recordedAt: checkpoint.recordedAt })
    context.expect(restart.previousProcessIds.length > 0 && Date.parse(restart.startedAt) - Date.parse(restart.stoppedAt) >= 25_000,
      'Personal pause proof requires an observed server PID and at least 25 seconds offline', { restart })
    const configPath = path.join(directory, 'plugins/ByteCompanions/config.toml')
    const definitionsPath = path.join(directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionsPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original), 'Restart QA requires autoReload=true')
    let state
    try {
      await context.step('full-restart player reload restores the saved paid progression', async () => {
        await mutateAndReload(context, async () => {
          await writeFile(definitionsPath, checkpoint.profile, 'utf8')
          await writeFile(configPath, enableProgression(original), 'utf8')
        })
        const deadline = Date.now() + 12_000
        do {
          state = await progressionState(context)
          if (state?.ready) break
          await context.sleep(150)
        } while (Date.now() < deadline)
        context.expect(state?.ready && state.totalXp === checkpoint.state.totalXp
          && sameMap(state.tracks, checkpoint.state.tracks)
          && sameMap(state.credits, checkpoint.state.credits) && !state.busy,
          'Saved XP, tiers, credits or reconciliation state changed after restart', { state, expected: checkpoint.state })
      })
      await context.step('server booster keeps its original absolute expiry after restart', async () => {
        const originalBoost = checkpoint.state.serverBoosters[0]
        const restored = state.serverBoosters.find(value => value.activationId === originalBoost.activationId)
        context.expect(restored?.expiresAt === originalBoost.expiresAt && restored.startsAt === originalBoost.startsAt,
          'Server booster reset or disappeared after bounded restart', { originalBoost, restored })
      })
      await context.step('personal boost resumes without duplicating credits or charging offline time', async () => {
        const originalBoost = checkpoint.state.personalBoosters[0]
        const restored = state.personalBoosters.find(value => value.activationId === originalBoost.activationId)
        context.expect(state.personalBoosters.length === 1 && restored && !restored.queued
          && restored.remainingMillis <= originalBoost.remainingMillis + 2000
          && restored.remainingMillis >= originalBoost.remainingMillis - 20_000,
          'Personal booster elapsed while offline, reset, duplicated or restored cancelled queue', { originalBoost, restored })
        context.expect(state.rates.companion_xp === 60, 'Restored booster multiplier differs', { rates: state.rates })
      })
      await context.step('restored purchased bonuses still affect a real equipped companion', async () => {
        await equipAfterCoreLoad(context, '/companions spawn bee', /You have equipped bee/, 12_000)
        const speed = context.bot.registry.effectsByName.Speed.id
        await context.waitUntil(() => context.bot.entity.effects?.[speed]?.amplifier === 8,
          { timeoutMs: 12_000, label: 'restored paid Speed IX' })
        context.report.observations ??= []
        context.report.observations.push({ kind: 'full-restart-progression', state, restart,
          amplifier: context.bot.entity.effects[speed].amplifier })
      })
    } finally {
      if (context.bot.currentWindow) context.bot.closeWindow(context.bot.currentWindow)
      await context.command('/companions despawn bee')
      await mutateAndReload(context, async () => {
        await writeFile(definitionsPath, definitions, 'utf8')
        await writeFile(configPath, original, 'utf8')
      })
    }
  }
}
