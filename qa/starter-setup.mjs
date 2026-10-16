import { readFile, writeFile, unlink, readdir } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'

const ids = ['bee', 'copper', 'lantern', 'mushroom', 'myself', 'rainbow', 'scout', 'warrior']
const timeoutMs = 15_000
const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()

export default {
  name: 'bytecompanions-starter-setup',
  description: 'Exercise vanilla starters, cosmetic defaults, setup permissions, confirmation, preservation, profiles and reload.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Requires isolated instance')
    const root = path.join(directory, 'plugins', 'ByteCompanions')
    const configPath = path.join(root, 'config.toml')
    const original = await readFile(configPath, 'utf8')
    const beePath = path.join(root, 'companions', 'bee.toml')
    const originalBee = await readFile(beePath, 'utf8')
    const customBee = originalBee + '\n# Server custom companion retained by setup.\n'
    const scoutPath = path.join(root, 'companions', 'scout.toml')
    const source = () => readFile(configPath, 'utf8')
    const flags = async survival => {
      const current = await source()
      for (const name of ['attributes', 'buffs', 'effects']) {
        context.expect(new RegExp(`^${name} = ${survival}$`, 'm').test(current), `${name} differs from profile`)
      }
      for (const name of ['progression', 'boosters']) context.expect(new RegExp(`^${name} = false$`, 'm').test(current), `${name} must be opt-in`)
      context.expect(/^particles = true$/m.test(current), 'Particles disabled')
    }
    async function close() {
      if (bot.currentWindow) await bot.clickWindow(49, 0, 0)
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'server closed inventory' })
    }
    async function screen(title) {
      return context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title).includes(title) ? bot.currentWindow : false,
        { timeoutMs, label: title })
    }
    async function item(slot, expected) {
      return context.waitUntil(() => {
        const value = bot.currentWindow?.slots[slot]
        return value && expected.test(plain(itemText(value))) ? value : false
      }, { timeoutMs, label: `slot ${slot} item` })
    }
    async function open() {
      await close()
      await context.command('/companions setup')
      await screen('Companion Setup')
      await item(20, /Cosmetic/)
      return item(24, /Survival/)
    }
    async function choose(slot) {
      await bot.clickWindow(slot, 0, 0)
      await screen('Confirm Setup')
      return item(22, /Apply setup/)
    }
    async function apply(slot) {
      await open()
      await choose(slot)
      const reply = context.waitForMessage(/Companion setup saved/, 45_000)
      await bot.clickWindow(22, 0, 0)
      await reply
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'setup closed for reload' })
    }
    async function ready() {
      await context.waitUntil(async () => (await context.command('/bcqa api inspect', /BCQA API/, timeoutMs)).includes('BCQA API PASS'),
        { timeoutMs: 30_000, label: 'companion player loaded' })
    }
    const permission = value => context.command(`/bcqa permission ${bot.player.uuid} bytecompanions.command.setup ${value}`,
      new RegExp(`node=bytecompanions.command.setup value=${value}`), timeoutMs)
    try {
      await context.step('fresh install contains eight vanilla companions and cosmetic defaults', async () => {
        context.expect(JSON.stringify((await readdir(path.join(root, 'companions'))).filter(file => file.endsWith('.toml')).sort())
          === JSON.stringify(ids.map(id => id + '.toml')), 'Starter collection differs')
        await flags(false)
        for (const id of ids) {
          const toml = await readFile(path.join(root, 'companions', id + '.toml'), 'utf8')
          context.expect(!/custom-model-data|itemsadder:|nexo:|https?:\/\//i.test(toml), `Starter ${id} requires external assets`)
        }
        await ready()
        await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await context.command('/bcqa move 0 100 0', /BCQA MOVE success=true/, timeoutMs)
        await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, timeoutMs)
      })
      await context.step('ordinary player cannot open administration setup', async () => {
        await permission('false')
        await context.command('/companions setup', /do not have permission/, timeoutMs)
        context.expect(bot.currentWindow === null, 'Denied setup opened inventory')
        await permission('clear')
      })
      await context.step('setup explains profiles and cancel keeps current settings', async () => {
        await open()
        context.expect(plain(itemText(bot.currentWindow.slots[20])).includes('Cosmetic'), 'Cosmetic choice missing')
        context.expect(plain(itemText(bot.currentWindow.slots[24])).includes('Survival'), 'Survival choice missing')
        await choose(24)
        await bot.clickWindow(46, 0, 0)
        await screen('Companion Setup')
        await close()
        context.expect(await source() === original, 'Cancel wrote settings')
      })
      await context.step('confirmation rechecks administrator permission', async () => {
        await open()
        await choose(24)
        await permission('false')
        const denied = context.waitForMessage(/do not have permission/, timeoutMs)
        await bot.clickWindow(22, 0, 0)
        await denied
        context.expect(await source() === original, 'Revoked permission applied profile')
        await close()
        await permission('clear')
      })
      await context.step('starter install restores missing files and preserves server customization', async () => {
        await writeFile(beePath, customBee, 'utf8')
        await unlink(scoutPath)
        await apply(20)
        context.expect(await readFile(beePath, 'utf8') === customBee, 'Setup overwrote custom bee')
        context.expect((await readFile(scoutPath, 'utf8')).length > 0, 'Missing starter not restored')
        await flags(false)
        await ready()
      })
      for (const id of ids) {
        await context.step(`equip vanilla starter ${id} and open its dashboard`, async () => {
          await context.command('/companions despawn', /unequipped|do not have a companion equipped/, timeoutMs)
          await context.command(`/companions spawn ${id}`, /You have equipped/, timeoutMs)
          const state = await context.command('/bcqa api inspect', /BCQA API PASS/, timeoutMs)
          context.expect(state.includes(`selected=${id} count=1 spawned=1`), `Starter ${id} not spawned`, { state })
          await context.command(`/companions menu ${id}`)
          await screen('Companion Dashboard')
          await item(13, /\S/)
          context.expect(bot.currentWindow.slots[13] !== null, 'Companion overview missing')
          await close()
          context.expect(!bot.entity.effects[bot.registry.effectsByName.Speed.id], 'Cosmetic companion granted speed')
        })
      }
      await context.step('browse button returns to the collection', async () => {
        await open()
        await bot.clickWindow(40, 0, 0)
        await screen('Companion Collection')
        await item(51, /Starter setup/)
        context.expect(plain(itemText(bot.currentWindow.slots[51])).includes('Starter setup'), 'Collection setup shortcut missing')
        await close()
      })
      await context.step('survival profile enables configured bonuses without paid upgrades', async () => {
        await apply(24)
        await flags(true)
        await ready()
        await context.command('/companions despawn', /unequipped|do not have a companion equipped/, timeoutMs)
        await context.command('/companions spawn mushroom', /You have equipped/, timeoutMs)
        await context.waitUntil(() => bot.entity.effects[bot.registry.effectsByName.NightVision.id], { timeoutMs, label: 'survival night vision' })
      })
      await context.step('cosmetic profile removes active bonuses and repeated setup preserves files', async () => {
        await apply(20)
        await flags(false)
        await context.waitUntil(() => !bot.entity.effects[bot.registry.effectsByName.NightVision.id], { timeoutMs, label: 'night vision removed' })
        await apply(20)
        context.expect(await readFile(beePath, 'utf8') === customBee, 'Repeated setup overwrote customization')
        await flags(false)
      })
    } finally {
      await close()
      await permission('clear')
    }
  }
}
