import { commandTimeoutMs, companionAlreadyEquipped, effectId, observeCompanion, requirePersistencePlayer } from './persistence-common.mjs'

export default {
  name: 'bytecompanions-persistence-restore',
  description: 'After a full server restart, require automatic bee and angel ring restoration for BytePersist before issuing equip commands, then remove the saved choices.',
  async run(context) {
    requirePersistencePlayer(context)
    const companion = observeCompanion(context)
    try {
      await context.step('saved companion restores automatically on login after server restart', async () => {
        await companion.find('bee restored on a new server process')
      })

      await context.step('saved angel ring restores without an equip command', async () => {
        await companion.requireParticles('automatic-restoration-after-server-restart')
      })

      await context.step('both restored choices report already equipped', async () => {
        await context.command('/companions spawn bee', companionAlreadyEquipped, commandTimeoutMs)
        await context.command(`/companions particles equip bee ${effectId}`,
          /angel_ring_effect is already equipped\./i, commandTimeoutMs)
      })

      await context.step('normal despawn removes the restored companion and saved choices', async () => {
        await context.command('/companions despawn bee', /Your companion has been unequipped\./i, commandTimeoutMs)
        await companion.requireRemoved()
        context.expect(context.bot.health > 0, 'Persistence player did not remain alive')
        context.report.observations.push({
          kind: 'persistence-cleanup', companionType: 'bee', effectId,
          requestedThrough: '/companions despawn bee', clientEntityRemoved: true,
          databaseDeletionVerified: false
        })
      })
    } finally {
      companion.close()
    }
  }
}
