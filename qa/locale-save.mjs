import { readFile } from 'node:fs/promises'
import path from 'node:path'

const timeoutMs = 20_000

export default {
  name: 'bytecompanions-locale-save',
  description: 'Persist German for this player and French as the server default, then stop the server and run locale-restore with the same username.',
  async run(context) {
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    await context.step('persist personal German and server French', async () => {
      await context.command('/companions language self de_DE', /de_DE\./, timeoutMs)
      await context.command('/companions language server fr_FR', /fr_FR\./, timeoutMs)
      const configuration = await readFile(path.join(directory, 'config.toml'), 'utf8')
      const preferences = await readFile(path.join(directory, 'languages', 'language-preferences.properties'), 'utf8')
      context.expect(/language\s*=\s*"fr_FR"/.test(configuration), 'Server default was not persisted')
      context.expect(typeof context.bot.player?.uuid === 'string'
        && preferences.includes(`${context.bot.player.uuid}=de_DE`), 'Personal German selection was not persisted')
    })
    await context.step('personal language takes precedence over server default', async () => {
      await context.command('/companions particles help', /Begleiterpartikel/, timeoutMs)
    })
  }
}
