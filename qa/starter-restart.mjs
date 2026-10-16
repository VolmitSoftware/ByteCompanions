import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'

export default {
  name: 'bytecompanions-starter-restart',
  description: 'Verify starter configuration, preserved customization and collection after a full restart.',
  async run(context) {
    const { bot } = context
    const root = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    await context.step('cosmetic profile and customized starter survive restart', async () => {
      const source = await readFile(path.join(root, 'config.toml'), 'utf8')
      for (const name of ['attributes', 'buffs', 'effects', 'progression', 'boosters']) {
        context.expect(new RegExp(`^${name} = false$`, 'm').test(source), `Restart changed ${name}`)
      }
      context.expect((await readdir(path.join(root, 'companions'))).filter(file => file.endsWith('.toml')).length === 8, 'Starter count changed')
      context.expect((await readFile(path.join(root, 'companions', 'bee.toml'), 'utf8')).includes('# Server custom companion retained by setup.'), 'Custom companion changed')
    })
    await context.step('restarted runtime loads the player and opens the starter collection', async () => {
      await context.waitUntil(async () => (await context.command('/bcqa api inspect', /BCQA API/, 10_000)).includes('BCQA API PASS'),
        { timeoutMs: 30_000, label: 'restored companion player' })
      await context.command('/companions setup')
      await context.waitUntil(() => bot.currentWindow && componentText(bot.currentWindow.title).includes('Companion Setup'),
        { timeoutMs: 10_000, label: 'setup after restart' })
      await context.waitUntil(() => bot.currentWindow?.slots[40], { timeoutMs: 10_000, label: 'browse button' })
      await bot.clickWindow(40, 0, 0)
      await context.waitUntil(() => bot.currentWindow && componentText(bot.currentWindow.title).includes('Companion Collection'),
        { timeoutMs: 10_000, label: 'collection after restart' })
      await context.waitUntil(() => bot.currentWindow?.slots[19], { timeoutMs: 10_000, label: 'eight starter icons' })
      const names = [10, 11, 12, 13, 14, 15, 16, 19].map(slot => componentText(itemText(bot.currentWindow.slots[slot])))
      context.expect(names.every(name => name.trim().length > 0), 'Missing starter collection icon', { names })
      await bot.clickWindow(49, 0, 0)
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs: 10_000, label: 'inventory closed' })
    })
  }
}
