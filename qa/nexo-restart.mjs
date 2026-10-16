import { inspectNexo, requireNexoInstance } from './nexo-common.mjs'

export default {
  name: 'bytecompanions-nexo-restart',
  description: 'Require a previously selected Nexo companion and its item after a full server restart.',
  async run(context) {
    await requireNexoInstance(context)
    await context.step('full server restart restores the selected Nexo companion', async () => {
      await inspectNexo(context, 'equipped')
    })
    await context.step('restored provider still preserves genuine Nexo metadata', async () => {
      await inspectNexo(context, 'provider')
    })
  }
}
