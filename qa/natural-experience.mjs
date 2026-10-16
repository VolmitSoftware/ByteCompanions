import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { mutateAndReload, rejectFileReload } from './file-reload.mjs'

const timeoutMs = 15000
const buffs = (specific, general) => `[companions.bee]
default-priority = 0
[companions.bee.vanilla.experience]
enabled = ${general}
operation = "MULTIPLY"
value = 3
cooldown = 0
${['fishing', 'furnace', 'mob'].map(source => `[companions.bee.vanilla.experience-${source}]
enabled = ${specific}
operation = "MULTIPLY"
value = 2
cooldown = 0
`).join('')}`

export default {
  name: 'bytecompanions-natural-experience',
  description: 'Natural fishing, smelting/output extraction and player melee kills verify source XP and actual pickup amounts.',
  async run(context) {
    const { bot } = context
    const spigot = process.env.BYTE_XP_SPIGOT === 'true'
    const root = path.join(context.server.directory, 'plugins/ByteCompanions')
    const configPath = path.join(root, 'config.toml')
    const buffPath = path.join(root, 'features/buffs/config.toml')
    const original = await readFile(configPath, 'utf8')
    context.expect(/^buffs = false$/m.test(original), 'Fresh fixture must start with buffs disabled')
    context.expect(/^progression = false$/m.test(original) && /^boosters = false$/m.test(original), 'Progression and boosters must remain disabled, leaving no progression reward cap')
    const command = (text, expected) => context.command(text, expected, timeoutMs)
    const attackTarget = async entityId => {
      await command('/give @s minecraft:diamond_sword', /Gave/)
      await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'diamond_sword'), { timeoutMs, label: 'melee weapon received' })
      await bot.equip(bot.inventory.items().find(item => item.name === 'diamond_sword'), 'hand')
      await context.waitUntil(() => bot.entities[entityId], { timeoutMs, label: 'live zombie target' })
      await bot.lookAt(bot.entities[entityId].position.offset(0, 1, 0), true)
      await context.sleep(1000)
      bot.attack(bot.entities[entityId])
    }
    const observe = async (operation = 'status') => {
      const text = await command(`/bcqa naturalxp ${operation}`, /BCQA NATURAL_XP /)
      return JSON.parse(text.slice(text.indexOf('{')))
    }
    const location = () => bot.entity.position.clone().set(0.5, 101, 8.5)
    const home = async () => {
      await command('/tp @s 0.5 101 8.5', /Teleported/)
      await context.waitUntil(() => bot.entity.position.distanceTo(location()) < 0.2, { timeoutMs, label: 'XP activity position' })
    }
    const modes = spigot ? ['baseline', 'specific'] : ['baseline', 'specific', 'general', 'both']
    context.report.naturalExperience = { server: spigot ? 'spigot' : 'paper', stableEquippedState: true, activities: [] }
    await context.step('prepare real activity arena and enable isolated source buffs', async () => {
      await mutateAndReload(context, async () => {
        await writeFile(buffPath, buffs(true, false), 'utf8')
        await writeFile(configPath, original.replace(/^buffs = false$/m, 'buffs = true'), 'utf8')
      })
      await command('/gamemode creative', /game mode|gamemode/i)
      await command('/gamerule minecraft:spawn_mobs false', /false/)
      await command('/gamerule minecraft:advance_time false', /false/)
      await command('/time set night', /time/i)
      await home()
      await context.waitUntil(() => bot.blockAt(bot.entity.position.clone().set(-8, 100, -8)) && bot.blockAt(bot.entity.position.clone().set(8, 100, 10)),
        { timeoutMs: 30000, label: 'activity arena chunks loaded' })
      await command('/fill -8 98 -8 8 100 10 minecraft:stone', /filled/)
      await command('/fill -5 99 -5 5 100 5 minecraft:water', /filled/)
      await home()
      await command('/gamemode survival', /game mode|gamemode/i)
      await command('/clear @s', /Removed|No items/)
    })
    for (const mode of modes) {
      if (mode !== 'baseline') await context.step(`configure ${mode} XP bonuses`, async () => {
        await mutateAndReload(context, () => writeFile(buffPath, buffs(mode !== 'general', mode === 'general' || mode === 'both') + `\n# ${mode}\n`, 'utf8'))
        if (mode === 'specific') await command('/companions spawn bee', /You have equipped bee\./)
        await command('/bcqa api inspect', /BCQA API PASS/)
      })
      for (const source of ['fishing', 'furnace', 'mob']) await context.step(`${mode}: natural ${source} drop and pickup`, async () => {
        await home()
        await command('/kill @e[type=minecraft:experience_orb]', /Killed|No entity|No entities|found/i)
        await command('/clear @s', /Removed|No items/)
        await observe(`begin ${source}`)
        if (source === 'fishing') {
          await command('/give @s minecraft:fishing_rod', /Gave/)
          await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'fishing_rod'), { timeoutMs, label: 'fishing rod' })
          await bot.equip(bot.inventory.items().find(item => item.name === 'fishing_rod'), 'hand')
          await bot.lookAt(bot.entity.position.clone().set(0.5, 99.5, 1.5), true)
          let timer
          try { await Promise.race([bot.fish(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('Natural fishing did not complete within45seconds')), 45000) })]) }
          finally { clearTimeout(timer) }
        } else if (source === 'furnace') {
          const prepared = await observe('furnace')
          await command('/give @s minecraft:raw_gold', /Gave/)
          await command('/give @s minecraft:coal', /Gave/)
          const blockPosition = bot.entity.position.clone().set(...prepared.furnace)
          await context.waitUntil(() => bot.blockAt(blockPosition)?.name === 'furnace', { timeoutMs, label: 'placed empty furnace' })
          const furnace = await bot.openFurnace(bot.blockAt(blockPosition))
          try {
            await furnace.putInput(bot.registry.itemsByName.raw_gold.id, null, 1)
            await furnace.putFuel(bot.registry.itemsByName.coal.id, null, 1)
            await context.waitUntil(() => furnace.outputItem()?.name === 'gold_ingot', { timeoutMs: 20000, label: 'vanilla gold smelting finished' })
            await furnace.takeOutput()
          } finally {
            furnace.close()
            await context.waitUntil(() => !bot.currentWindow, { timeoutMs, label: 'furnace inventory closed' })
          }
        } else {
          const prepared = await observe('mob')
          await attackTarget(prepared.entityId)
        }
        let result
        await context.waitUntil(async () => {
          result = await observe()
          return result.events.length > 0 && result.pickupTotal > 0 && result.totalExperience === result.pickupTotal
        }, { timeoutMs: 20000, intervalMs: 500, label: 'natural XP event and actual orb pickup' })
        await context.sleep(1500)
        result = await observe()
        context.expect(result.events.length === 1, 'Expected exactly one natural source event', result)
        const base = result.events[0].base
        const sourceMultiplier = mode === 'specific' || mode === 'both' ? 2 : 1
        const pickupMultiplier = mode === 'general' ? 3 : sourceMultiplier
        context.expect(base > 0 && result.events[0].result === base * sourceMultiplier, 'Natural source XP bonus differs', result)
        context.expect(result.pickupTotal === base * pickupMultiplier && result.totalExperience === base * pickupMultiplier, 'Actual XP pickup differs or bonus stacked twice', result)
        if (source === 'fishing') context.expect(result.fishStates.includes('BITE') && result.fishStates.includes('CAUGHT_FISH'), 'Missing natural bite/catch sequence', result)
        if (source === 'furnace') context.expect(result.smelted === 1 && base === 1, 'Furnace did not naturally smelt one gold item', result)
        if (source === 'furnace') context.expect(bot.inventory.items().some(item => item.name === 'gold_ingot'), 'Smelted gold output was not extracted into player inventory')
        if (source === 'mob') context.expect(result.attacks.length === 1 && result.attacks[0].damage > 0 && !result.attacks[0].cancelled && result.mobHealth === 0,
          'Mob XP must follow a real uncancelled lethal player attack', result)
        context.report.naturalExperience.activities.push({ mode, source, sourceMultiplier, pickupMultiplier, ...result })
      })
    }
    if (spigot) await context.step('Spigot rejects general XP while retaining source configuration', async () => {
      await rejectFileReload(context, { filename: buffPath, content: buffs(true, true), expectedLog: /Configuration hotload rejected/ })
      await writeFile(buffPath, buffs(true, false) + '\n# specific\n', 'utf8')
    })
    if (spigot) await context.step('natural source XP still doubles after rejected general configuration', async () => {
      await home()
      await command('/kill @e[type=minecraft:experience_orb]', /Killed|No entity|No entities|found/i)
      await command('/clear @s', /Removed|No items/)
      await observe('begin mob')
      const prepared = await observe('mob')
      await attackTarget(prepared.entityId)
      let result
      await context.waitUntil(async () => {
        result = await observe()
        return result.events.length === 1 && result.pickupTotal === result.events[0].base * 2 && result.totalExperience === result.pickupTotal
      }, { timeoutMs: 20000, intervalMs: 500, label: 'post-rejection natural XP pickup' })
      context.expect(result.events[0].base > 0 && result.events[0].result === result.events[0].base * 2, 'Rejected config changed active source buff', result)
      context.expect(result.attacks.length === 1 && result.attacks[0].damage > 0 && !result.attacks[0].cancelled && result.mobHealth === 0, 'Post-rejection kill was not a real lethal player attack', result)
      context.report.naturalExperience.activities.push({ mode: 'post-rejection', source: 'mob', sourceMultiplier: 2, pickupMultiplier: 2, ...result })
    })
  }
}
