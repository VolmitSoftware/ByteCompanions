import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { metrics } from './metrics-language.mjs'

export default {
  name: 'bytecompanions-metrics-language-restart',
  description: 'Verify metrics opt-out and saved language after a full process restart.',
  async run(context) {
    await context.step('restart creates one metrics instance with no scheduled submissions', async () => {
      const state = await metrics(context)
      context.expect(state.initialized && !state.closed && state.instances.length === 1
        && !state.instances[0].schedulerShutdown, 'Restart metrics state differs', state)
    })
    await context.step('saved personal language survives the full process restart', async () => {
      const checkpoint = JSON.parse(await readFile(path.join(context.server.directory, '.qa-metrics-language.json'), 'utf8'))
      context.expect(context.bot.player.uuid === checkpoint.uuid && checkpoint.locale === 'de_DE', 'Restart player identity differs')
      await context.command('/companions particles help', /Begleiterpartikel/, 15_000)
    })
  }
}
