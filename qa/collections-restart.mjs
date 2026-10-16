import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { controls, equipment } from './collections.mjs'

export default {
  name: 'bytecompanions-collections-restart',
  description: 'Verify collection preferences and equipped outfit restore after a full server restart.',
  async run(context) {
    const ui = controls(context)
    context.report.observations ??= []
    const checkpoint = JSON.parse(await readFile(path.join(context.server.directory, '.qa-collections.json'), 'utf8'))
    await context.step('durable collection profile survives restart', async () => {
      await context.waitUntil(async () => {
        const state = await ui.state()
        return state.ready && state.companions.bee && state.companions.warrior
      }, { timeoutMs: 30_000, label: 'collection and equipped companions loaded' })
      const state = await ui.state()
      context.expect(state.profile.favorites.includes('bee') && state.profile.outfits.adventure
        && state.profile.styles.bee.nickname === 'Honey Buddy' && state.profile.styles.bee.variantId === 'moss'
        && state.profile.styles.bee.particleId === 'angel_ring_effect', 'Saved collection choices missing', { state, checkpoint })
      context.expect(Object.keys(state.companions).sort().join(',') === 'bee,warrior', 'Equipped outfit missing after restart', state)
      context.report.observations.push({ kind: 'restart-profile', state })
    })
    await context.step('restored companion uses saved equipment and normal follow mode', async () => {
      const state = await ui.state()
      await equipment(context, state.companions.bee.entityId, 'jack_o_lantern', 'saved variant equipment')
      context.expect(state.companions.bee.mode === 'FOLLOW' && state.companions.warrior.mode === 'FOLLOW', 'Temporary mode persisted')
      await ui.outfit()
    })
  }
}
