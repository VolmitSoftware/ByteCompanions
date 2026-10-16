import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { controls } from './collections.mjs'

export default {
  name: 'bytecompanions-file-reload-restart',
  description: 'Verify selection and viewer settings persist through a fresh server process after automatic file reloads.',
  async run(context) {
    const ui = controls(context)
    const checkpoint = JSON.parse(await readFile(path.join(context.server.directory, '.qa-file-reload.json'), 'utf8'))
    await context.step('new server process restores saved selection and viewer preferences', async () => {
      context.expect(context.bot.player.uuid === checkpoint.uuid, 'Restart used a different player identity')
      await context.waitUntil(async () => {
        const state = await ui.state()
        return state.ready && state.companions.bee
      }, { timeoutMs: 30_000, label: 'player restored after full restart' })
      const state = await ui.state()
      context.expect(JSON.stringify(state.profile.viewerPreferences) === JSON.stringify(checkpoint.preferences), 'Restart lost viewer preferences', { state, checkpoint })
      context.expect(Object.keys(state.companions).join(',') === checkpoint.selected.join(','), 'Restart changed selection')
      await context.waitUntil(() => context.bot.entities[state.companions.bee.entityId], { timeoutMs: 15_000, label: 'restarted companion visible' })
    })
    await context.step('restored player can open dashboard and mutate saved settings', async () => {
      await ui.dashboard('bee')
      await context.bot.clickWindow(49, 1, 0)
      await context.waitUntil(() => !context.bot.currentWindow, { timeoutMs: 15_000, label: 'server closes dashboard' })
      await ui.command('/companions settings particles full', /Collection saved\./)
      context.expect((await ui.state()).profile.viewerPreferences.particleIntensity === 'FULL', 'Restarted settings command failed')
      await ui.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/)
    })
  }
}
