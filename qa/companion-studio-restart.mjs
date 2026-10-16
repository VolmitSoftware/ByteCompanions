import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000
const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()

export default {
  name: 'bytecompanions-companion-studio-restart',
  description: 'Verify saved studio content, real equipment, bonuses and editor access after a full server restart.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Requires isolated instance')
    const checkpoint = JSON.parse(await readFile(path.join(directory, '.qa-companion-studio.json'), 'utf8'))
    const root = path.join(directory, 'plugins', 'ByteCompanions')
    const observer = observeCompanion(context)
    context.report.observations ??= []
    try {
      await context.step('saved companion content survives restart byte for byte', async () => {
        const source = await readFile(path.join(root, 'companions', 'bee.toml'), 'utf8')
        context.expect(source === checkpoint.source, 'Restart changed saved companion definition')
        context.expect(source.includes(checkpoint.name) && source.includes(checkpoint.head), 'Saved appearance missing')
        context.expect(await readFile(path.join(root, 'companions', 'studio_copy.toml'), 'utf8') === checkpoint.copySource,
          'Restart changed saved copy definition')
        await context.waitUntil(async () => (await context.command('/bcqa api inspect', /BCQA API/, timeoutMs)).includes('BCQA API PASS'),
          { timeoutMs: 30_000, label: 'companion state loaded after restart' })
      })
      await context.step('saved companion equipment and bonuses operate after restart', async () => {
        await context.command('/companions spawn bee', /You have equipped/, timeoutMs)
        const id = await observer.find('studio companion after restart')
        await context.waitUntil(() => bot.entities[id]?.equipment?.some(stack => stack?.name === 'gold_block'),
          { timeoutMs, label: 'persisted gold block equipment' })
        await context.waitUntil(() => bot.entity.effects[bot.registry.effectsByName.Haste.id], { timeoutMs, label: 'persisted haste bonus' })
        await context.command('/attribute @s minecraft:armor get', /value of attribute .* is 10(?:\.0)?(?:\s|$)/i, timeoutMs)
        await observer.requireParticles('saved built-in particles after restart')
        await context.command('/companions despawn', /unequipped/, timeoutMs)
        await observer.requireRemoved()
      })
      await context.step('companion collection and editor display the saved definition', async () => {
        await context.command('/companions menu', undefined, timeoutMs)
        await context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title).includes('Companion Collection'),
          { timeoutMs, label: 'collection after restart' })
        await context.waitUntil(() => bot.currentWindow.slots.slice(0, 54).some(item => plain(itemText(item)).includes(checkpoint.name)),
          { timeoutMs, label: 'saved display name in collection' })
        await bot.clickWindow(49, 0, 0)
        await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'collection closed' })
        await context.command('/companions edit bee')
        await context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title).includes('Companion Studio')
          && /Appearance/.test(plain(itemText(bot.currentWindow.slots[19]))), { timeoutMs, label: 'studio after restart' })
        const discarded = context.waitForMessage(/Draft discarded/, timeoutMs)
        await bot.clickWindow(49, 0, 0)
        await discarded
        context.expect(await readFile(path.join(root, 'companions', 'bee.toml'), 'utf8') === checkpoint.source, 'Unchanged editor wrote on discard')
      })
    } finally {
      observer.close()
    }
  }
}
