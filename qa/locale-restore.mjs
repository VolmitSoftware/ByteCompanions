import { readFile } from 'node:fs/promises'
import path from 'node:path'

const timeoutMs = 20_000

export default {
  name: 'bytecompanions-locale-restore',
  description: 'After a server restart, verify saved player/server locales using the same username as locale-save, then return both to the English default.',
  async run(context) {
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    await context.step('load persisted personal German after server restart', async () => {
      const configuration = await readFile(path.join(directory, 'config.toml'), 'utf8')
      context.expect(/language\s*=\s*"fr_FR"/.test(configuration), 'Saved French default is missing')
      await context.waitUntil(async () => {
        const response = await context.command('/companions particles help', /Begleiterpartikel|Particules|Companion particles/, timeoutMs)
        return response.includes('Begleiterpartikel')
      }, { timeoutMs, intervalMs: 1_200, label: 'persisted German messages loaded' })
    })
    await context.step('clearing the personal choice reveals the persisted French default', async () => {
      await context.command('/companions language self reset', /par défaut/, timeoutMs)
      await context.command('/companions particles help', /Particules/, timeoutMs)
      const preferences = await readFile(path.join(directory, 'languages', 'language-preferences.properties'), 'utf8')
      context.expect(!preferences.includes(context.bot.player.uuid), 'Personal locale was not cleared')
    })
    await context.step('restore English as the server default', async () => {
      await context.command('/companions language server en_US', /server language is now en_US\./, timeoutMs)
      await context.command('/companions particles help', /Companion particles/, timeoutMs)
      context.expect(/language\s*=\s*"en_US"/.test(await readFile(path.join(directory, 'config.toml'), 'utf8')), 'English default was not persisted')
    })
  }
}
