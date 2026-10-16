import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { controls } from './collections.mjs'

export default {
  name: 'bytecompanions-server-polish-restart',
  description: 'Verify viewer preferences and selected companion survive a complete server process restart.',
  async run(context) {
    const ui = controls(context)
    const checkpoint = JSON.parse(await readFile(path.join(context.server.directory, '.qa-server-polish.json'), 'utf8'))
    await context.step('full restart restores saved visibility and particle preferences', async () => {
      await context.waitUntil(async () => (await ui.state()).ready, { timeoutMs: 30_000, label: 'profile loaded after restart' })
      const state = await ui.state()
      context.expect(state.profile.viewerPreferences.visibility === 'NONE'
        && state.profile.viewerPreferences.particleIntensity === 'OFF', 'Restart lost settings', { state, checkpoint })
      context.expect(state.companions.bee, 'Hidden selection was lost on restart', state)
      await context.sleep(1500)
      context.expect(!context.bot.entities[state.companions.bee.entityId], 'Hidden companion appeared after restart')
      context.report.observations ??= []
      context.report.observations.push({ kind: 'restart-preferences', state })
    })
    await context.step('restored settings can reveal the retained companion', async () => {
      await ui.command('/companions settings visibility all', /Collection saved\./)
      await ui.command('/companions settings particles full', /Collection saved\./)
      const state = await ui.state()
      await context.waitUntil(() => context.bot.entities[state.companions.bee.entityId],
        { timeoutMs: 15_000, label: 'retained companion revealed' })
    })
  }
}
