import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { plainRoots, commandTree, fallbackDispatch } from './command-roots.mjs'

export default {
  name: 'bytecompanions-command-roots-restart',
  description: 'Verify plain command roots and client suggestions after a full server restart.',
  async run(context) {
    await context.step('both plain command roots work after restart', async () => {
      const checkpoint = JSON.parse(await readFile(path.join(context.server.directory, '.qa-command-roots.json'), 'utf8'))
      context.expect(context.bot.player.uuid === checkpoint.uuid, 'Restart player identity differs')
      await plainRoots(context)
      await fallbackDispatch(context)
    })
    await context.step('client command tree and suggestions remain correct after restart', async () => commandTree(context, 'restart'))
  }
}
