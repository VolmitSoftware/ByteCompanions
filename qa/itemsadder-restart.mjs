import { inspectItemsAdder, requireItemsAdderInstance } from './itemsadder-common.mjs'

export default {
  name: 'bytecompanions-itemsadder-restart',
  description: 'Verify custom item restoration on a fresh server process before any equip command, then clean up.',
  async run(context) {
    await requireItemsAdderInstance(context)
    await context.step('full restart restores genuine ItemsAdder companion metadata automatically', async () => {
      await inspectItemsAdder(context, 'equipped')
    })
    await context.step('custom item selection can be removed after restart', async () => {
      await context.command('/companions despawn qa_itemsadder', /^Your companion has been unequipped\.$/, 20_000)
      await context.command('/bcqa api inspect', /BCQA API PASS selected= count=0 spawned=0$/, 20_000)
    })
  }
}
