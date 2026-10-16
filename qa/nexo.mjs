import { reloadFiles } from './file-reload.mjs'
import { inspectNexo, requireNexoInstance } from './nexo-common.mjs'

export default {
  name: 'bytecompanions-nexo',
  description: 'Compare companion equipment with genuine Nexo output, including invalid IDs and runtime reload.',
  async run(context) {
    await requireNexoInstance(context)
    await context.step('configured provider preserves genuine Nexo metadata', async () => {
      await inspectNexo(context, 'provider')
    })
    await context.step('unknown and empty Nexo IDs resolve to AIR', async () => {
      await inspectNexo(context, 'invalid')
    })
    await context.step('equipped companion carries the genuine Nexo item', async () => {
      await context.command('/companions spawn qa_nexo', /^You have equipped qa_nexo\.$/, 20_000)
      await inspectNexo(context, 'equipped')
    })
    await context.step('runtime reload restores the custom item automatically', async () => {
      await reloadFiles(context)
      await inspectNexo(context, 'equipped')
    })
  }
}
