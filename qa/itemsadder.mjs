import { reloadFiles } from './file-reload.mjs'
import { inspectItemsAdder, requireItemsAdderInstance } from './itemsadder-common.mjs'

export default {
  name: 'bytecompanions-itemsadder',
  description: 'Compare the configured companion item against genuine ItemsAdder, including invalid IDs and reload.',
  async run(context) {
    context.report.observations ??= []
    await requireItemsAdderInstance(context)
    await context.step('configured namespaced provider preserves genuine ItemsAdder metadata', async () => {
      await inspectItemsAdder(context, 'provider')
    })
    await context.step('unknown and empty ItemsAdder IDs resolve safely to AIR', async () => {
      await inspectItemsAdder(context, 'invalid')
    })
    await context.step('equipped companion carries the genuine ItemsAdder item', async () => {
      await context.command('/companions spawn qa_itemsadder', /^You have equipped qa_itemsadder\.$/, 20_000)
      await inspectItemsAdder(context, 'equipped')
    })
    await context.step('ByteCompanions reload restores the custom item without manual re-equip', async () => {
      await reloadFiles(context)
      await inspectItemsAdder(context, 'equipped')
    })
    await context.step('custom item selection remains equipped for full server restart', async () => {
      await inspectItemsAdder(context, 'equipped')
      context.report.observations.push({kind:'itemsadder-restart-handoff',companion:'qa_itemsadder',item:'_iainternal:icon_left_blue',requiredTransition:'disconnect and graceful stop, then a new Java process'})
    })
  }
}
