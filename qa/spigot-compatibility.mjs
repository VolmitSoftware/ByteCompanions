import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles, rejectFileReload } from './file-reload.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000

function sourceBuffs(general = false) {
  const entries = ['mob', 'block', 'furnace', 'fishing'].map(source => `[companions.bee.vanilla.experience-${source}]
enabled = true
operation = "SUM"
value = 100
cooldown = 0
`).join('')
  return `[companions.bee]
default-priority = 0

[companions.bee.vanilla.experience]
enabled = ${general}
operation = "SUM"
value = 100
cooldown = 0
${entries}`
}

function interact(bot, entityId, hand, attack) {
  if (bot.supportFeature('attackUsesOwnPacket')) {
    if (attack) bot._client.write('attack', { entityId })
    else bot._client.write('use_entity', {
      target: entityId, hand: hand === 'OFF_HAND' ? 'off_hand' : 'main_hand',
      location: { x: 0, y: 0, z: 0 }, sneaking: false
    })
  } else {
    bot._client.write('use_entity', {
      target: entityId, mouse: attack ? 1 : 0,
      hand: hand === 'OFF_HAND' ? 1 : 0, sneaking: false
    })
  }
}

export default {
  name: 'bytecompanions-spigot-compatibility',
  description: 'Protocol interactions, shared public Component identity, source XP event transforms, real combat/mining, and rejected general-XP reload on Spigot.',
  async run(context) {
    const { bot } = context
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const configPath = path.join(directory, 'config.toml')
    const buffsPath = path.join(directory, 'features', 'buffs', 'config.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    const originalBuffs = await readFile(buffsPath, 'utf8')
    const observer = observeCompanion(context)
    context.report.observations ??= []
    let entityId
    const reload = () => reloadFiles(context)
    async function state() {
      const message = await context.command('/bcqa state', /BCQA STATE count=\d+ base=-?\d+ overlays=\d+/, timeoutMs)
      const [, count, base, overlays] = message.match(/count=(\d+) base=(-?\d+) overlays=(\d+)/)
      return { count: Number(count), base: Number(base), overlays: Number(overlays) }
    }
    async function restoreEntity(label) {
      entityId = await observer.find(label)
      const current = await state()
      context.expect(bot.entities[current.base] !== undefined, 'Public API companion ID was not received by the client', current)
      entityId = current.base
      context.report.observations.push({ kind: 'base-interaction-scope', ...current,
        note: 'Interaction assertions target the base ID. Overlay interactions are outside the existing cache contract.' })
    }
    async function oneInteraction(hand, attack, label) {
      await context.command('/bcqa reset', /BCQA RESET count=0/, timeoutMs)
      const event = context.waitForMessage(new RegExp(`BCQA INTERACT count=1 hand=${hand} attack=${attack} base=${entityId}`), timeoutMs)
      interact(bot, entityId, hand, attack)
      await event
      await bot.waitForTicks(8)
      context.expect((await state()).count === 1, `${label}: one packet produced duplicate interaction events`)
    }
    try {
      context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Scenario requires autoReload=true before startup')
      context.expect(/dispatchInteractEvent\s*=/.test(originalConfig), 'Missing canonical interaction setting')
      await context.step('public Component identity and API dependency', async () => {
        await context.command('/bcqa status', /BCQA COMPONENT_IDENTITY PASS/, timeoutMs)
      })
      await context.step('install deterministic interaction and source XP settings', async () => {
        await writeFile(configPath, originalConfig
          .replace(/dispatchInteractEvent\s*=\s*(?:true|false)/, 'dispatchInteractEvent = true'), 'utf8')
        await writeFile(buffsPath, sourceBuffs(), 'utf8')
        await reload()
        if (bot.game.gameMode !== 'creative') await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await context.command('/companions spawn bee', /You have equipped bee\.|already have (?:that|an exclusive) companion/, timeoutMs)
        await restoreEntity('companion with source XP buffs')
        await context.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/, timeoutMs)
        await observer.requireParticles('Spigot particles')
      })
      for (const phase of ['before reload', 'after reload']) {
        if (phase === 'after reload') {
          await context.step('reload restores companion and particles', async () => {
            await reload()
            await restoreEntity('companion after reload')
            await observer.requireParticles('after reload')
          })
        }
        for (const [hand, attack] of [['HAND', false], ['OFF_HAND', false], ['HAND', true]]) {
          await context.step(`one ${hand} attack=${attack} interaction ${phase}`,
            () => oneInteraction(hand, attack, phase))
        }
      }
      await context.step('real entity attack still reaches the server', async () => {
        await context.command('/gamemode survival', /game mode|gamemode/i, timeoutMs)
        const target = await context.command('/bcqa target', /BCQA TARGET id=\d+ health=[\d.]+/, timeoutMs)
        const [, idText, healthText] = target.match(/id=(\d+) health=([\d.]+)/)
        const targetId = Number(idText)
        await context.waitUntil(() => bot.entities[targetId], { timeoutMs, label: 'real cow received' })
        await bot.lookAt(bot.entities[targetId].position.offset(0, 1, 0), true)
        const damage = context.waitForMessage(/BCQA REAL_DAMAGE amount=[\d.]+/, timeoutMs)
        interact(bot, targetId, 'HAND', true)
        const damageMessage = await damage
        context.expect(Number(damageMessage.match(/amount=([\d.]+)/)[1]) > 0, 'Real attack had no damage')
        await bot.waitForTicks(3)
        const health = await context.command('/bcqa health', /BCQA HEALTH value=[\d.]+/, timeoutMs)
        context.expect(Number(health.match(/value=([\d.]+)/)[1]) < Number(healthText), 'Cow health did not decrease')
      })
      for (const source of ['block', 'furnace', 'fishing', 'mob']) {
        await context.step(`synthetic ${source} XP event applies SUM 100`, async () => {
          if (source === 'fishing') {
            await context.command('/give @s minecraft:fishing_rod', /Gave/, timeoutMs)
            await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'fishing_rod'),
              { timeoutMs, label: 'fishing rod received' })
            await bot.equip(bot.inventory.items().find(item => item.name === 'fishing_rod'), 'hand')
            await bot.look(bot.entity.yaw + Math.PI, 0, true)
            const hook = context.waitForMessage(/BCQA FISHING_HOOK ready=true/, timeoutMs)
            bot.activateItem()
            await hook
          }
          await context.command(`/bcqa xp ${source}`, new RegExp(`BCQA SYNTHETIC_XP source=${source} base=10 result=110`), timeoutMs)
          context.report.observations.push({ kind: 'synthetic-event', source, base: 10, result: 110,
            note: 'Fixture-fired Bukkit event; does not prove actual XP orb pickup or the full activity.' })
        })
      }
      await context.step('real ore mining produces source-specific bonus XP', async () => {
        await context.command('/give @s minecraft:diamond_pickaxe', /Gave/, timeoutMs)
        await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'diamond_pickaxe'),
          { timeoutMs, label: 'pickaxe received' })
        await bot.equip(bot.inventory.items().find(item => item.name === 'diamond_pickaxe'), 'hand')
        const position = bot.entity.position.floored().offset(2, 0, 0)
        await context.command(`/setblock ${position.x} ${position.y} ${position.z} minecraft:diamond_ore`, /Changed the block/, timeoutMs)
        const block = await context.waitUntil(() => {
          const candidate = bot.blockAt(position)
          return candidate?.name === 'diamond_ore' && candidate
        }, { timeoutMs, label: 'placed ore received' })
        const experience = context.waitForMessage(/BCQA BLOCK_XP amount=\d+/, timeoutMs)
        await bot.dig(block)
        const message = await experience
        context.expect(Number(message.match(/amount=(\d+)/)[1]) >= 103, 'Real ore break omitted the SUM 100 source buff')
        context.report.observations.push({ kind: 'real-mining-event', message,
          note: 'Client mining packet caused a real block break and modified XP drop; orb collection amount is not asserted.' })
      })
      await context.step('general XP restriction rejects reload without replacing active state', async () => {
        const before = entityId
        await rejectFileReload(context, { filename: buffsPath, content: sourceBuffs(true) })
        context.expect(bot.entities[before] !== undefined, 'Rejected general XP config replaced the working companion')
        context.expect((await state()).base === before, 'Rejected reload changed the public companion identity')
        await observer.requireParticles('rejected general XP configuration')
        await oneInteraction('HAND', false, 'after rejected reload')
      })
    } finally {
      observer.close()
      await writeFile(configPath, originalConfig, 'utf8')
      await writeFile(buffsPath, originalBuffs, 'utf8')
      if (bot._client.state === 'play') await reload()
    }
  }
}
