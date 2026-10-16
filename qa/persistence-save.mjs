import { commandTimeoutMs, companionAlreadyEquipped, effectId, observeCompanion, requirePersistencePlayer } from './persistence-common.mjs'
import { equipAfterCoreLoad } from './database-common.mjs'

export default {
  name: 'bytecompanions-persistence-save',
  description: 'Equip bee and angel ring as BytePersist, observe protocol state, and retain choices for harness disconnect and server shutdown.',
  async run(context) {
    requirePersistencePlayer(context)
    const companion = observeCompanion(context)
    try {
      await context.step('fresh persistence player equips bee', async () => {
        const previousIds = companion.entityIds()
        await equipAfterCoreLoad(context, '/companions spawn bee', /^You have equipped bee\.$/i)
        await companion.find('bee equipped before server shutdown', previousIds)
      })

      await context.step('equip angel ring and observe its particle packets', async () => {
        await context.command(`/companions particles equip bee ${effectId}`,
          /Equipped particle effect: angel_ring_effect\./i, commandTimeoutMs)
        await companion.requireParticles('before-server-shutdown')
      })

      await context.step('leave both choices equipped for disconnect and stopped-server persistence', async () => {
        await context.command('/companions spawn bee', companionAlreadyEquipped, commandTimeoutMs)
        await context.command(`/companions particles equip bee ${effectId}`,
          /angel_ring_effect is already equipped\./i, commandTimeoutMs)
        context.expect(context.bot.health > 0, 'Persistence player did not remain alive')
        context.report.observations.push({
          kind: 'persistence-handoff', companionType: 'bee', effectId,
          state: 'left-equipped', nextScenario: 'bytecompanions-persistence-restore',
          requiredTransition: 'harness disconnect and graceful server stop, then a new server process'
        })
      })
    } finally {
      companion.close()
    }
  }
}
