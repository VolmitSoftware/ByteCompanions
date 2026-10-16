import { readFile, writeFile, unlink } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles, rejectFileReload, runtimeReport, waitForFileReload } from './file-reload.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000

export function reliabilityScenario({ generalExperienceSupported = true } = {}) {
  return {
    name: generalExperienceSupported ? 'bytecompanions-reliability' : 'bytecompanions-spigot-reliability',
    description: 'Verify content rejection, model limits, integer attributes, potion coexistence, and companion particles across teleport and respawn.',
    async run(context) {
      const { bot } = context
      context.report.observations ??= []
      const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
      const originals = new Map()
      const created = []
      const observer = observeCompanion(context)
      const speedId = bot.registry.effectsByName.Speed.id
      const appearances = new Map()
      let spawnSequence = 0
      const appeared = entity => {
        if (entity.name === 'armor_stand') appearances.set(entity.id, ++spawnSequence)
      }
      bot.on('entitySpawn', appeared)
      let entityId
      async function restored(label, before) {
        await context.waitUntil(() => Object.values(bot.entities).some(entity =>
          entity.name === 'armor_stand' && appearances.get(entity.id) > before &&
          entity.position.distanceTo(bot.entity.position) < 24), { timeoutMs, label })
        entityId = await observer.find(label)
      }
      async function remember(relative) {
        if (!originals.has(relative)) originals.set(relative, await readFile(path.join(directory, relative), 'utf8'))
        return originals.get(relative)
      }
      async function edit(relative, contents) {
        await remember(relative)
        await writeFile(path.join(directory, relative), contents, 'utf8')
      }
      async function reload() {
        await reloadFiles(context)
      }
      async function spawn() {
        const ids = observer.entityIds()
        await context.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
        entityId = await observer.find('bee equipped', ids)
      }
      async function despawn() {
        await context.command('/companions despawn bee', /Your companion has been unequipped\./, timeoutMs)
        await observer.requireRemoved()
      }
      async function speed(amplifier, label) {
        await context.waitUntil(() => bot.entity.effects[speedId]?.amplifier === amplifier,
          { timeoutMs, label })
      }
      try {
        const config = await remember('config.toml')
        context.expect(/autoReload\s*=\s*true/.test(config), 'Use autoReload=true for file reload checks')
        await context.step('equip bee and selected particles', async () => {
          if (bot.game.gameMode !== 'creative') await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
          await spawn()
          await context.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/, timeoutMs)
          await observer.requireParticles('initial')
        })
        for (const relative of ['companions/bee.toml', 'features/attributes/config.toml',
          'features/effects/config.toml', 'features/buffs/config.toml', 'features/decorations/config.toml',
          'features/particles/config.toml', 'features/particles/icons.toml']) {
          await context.step(`reject malformed ${relative} without replacing runtime`, async () => {
            const original = await remember(relative)
            try {
              await rejectFileReload(context, { filename: path.join(directory, relative), content: 'invalid = [unterminated\n' })
              context.expect(bot.entities[entityId] !== undefined, 'Rejected content replaced the working entity', { relative, entityId })
              await observer.requireParticles(`rejected ${relative}`)
            } finally {
              await writeFile(path.join(directory, relative), original, 'utf8')
            }
          })
        }
        await context.step('reject an unknown model without replacing runtime', async () => {
          const original = await remember('companions/bee.toml')
          try {
            await rejectFileReload(context, { filename: path.join(directory, 'companions/bee.toml'),
              content: original.replace(/^model\s*=.*$/m, 'model = "INVALID_MODEL"') })
            context.expect(bot.entities[entityId] !== undefined, 'Invalid model replaced the working entity')
          } finally {
            await writeFile(path.join(directory, 'companions/bee.toml'), original, 'utf8')
          }
        })
        await context.step('reattach selected particles after same-world teleport', async () => {
          const before = spawnSequence
          const position = bot.entity.position
          const x = Math.floor(position.x) + 40
          const z = Math.floor(position.z) + 40
          await context.command(`/tp @s ${x} 100 ${z}`, /Teleported/, timeoutMs)
          await context.waitUntil(() => Math.abs(bot.entity.position.x - x) < 2,
            { timeoutMs, label: 'player teleported' })
          await restored('companion replaced after same-world teleport', before)
          await observer.requireParticles('same-world teleport')
        })
        await context.step('reattach selected particles after world change', async () => {
          let before = spawnSequence
          await context.command('/execute in minecraft:the_nether run tp @s 0 110 0', /Teleported/, timeoutMs)
          await restored('companion restored in nether', before)
          await observer.requireParticles('nether')
          before = spawnSequence
          await context.command('/execute in minecraft:overworld run tp @s 0 100 0', /Teleported/, timeoutMs)
          await restored('companion restored in overworld', before)
          await observer.requireParticles('overworld return')
        })
        await context.step('reattach selected particles after death and respawn', async () => {
          const before = spawnSequence
          const death = context.waitForEvent('death', () => true, timeoutMs)
          await context.command('/kill @s')
          await death
          await context.waitUntil(() => bot.health > 0, { timeoutMs, label: 'player respawned' })
          await restored('companion restored after respawn', before)
          await observer.requireParticles('respawn')
        })
        await context.step('equal and stronger external potion effects survive companion removal', async () => {
          await context.command('/effect give @s minecraft:speed 60 8 true', /Applied effect/, timeoutMs)
          await speed(8, 'stronger external speed visible')
          await despawn()
          await speed(8, 'external speed retained after companion removal')
          await spawn()
          await speed(8, 'stronger external speed retained on equip')
          await despawn()
          await context.command('/effect clear @s', /Removed|Cleared/, timeoutMs)
          await spawn()
          await context.command('/effect give @s minecraft:speed 2 8 true', /Applied effect/, timeoutMs)
          await speed(8, 'stronger external effect applied over companion')
          await despawn()
          await context.sleep(2_500)
          await context.waitUntil(() => !bot.entity.effects[speedId],
            { timeoutMs, label: 'external expiry does not resurrect removed companion effect' })
          await context.sleep(300)
          context.expect(!bot.entity.effects[speedId], 'Companion effect resurfaced after external expiry')
          await context.command('/effect give @s minecraft:speed 60 5 true', /Applied effect/, timeoutMs)
          await spawn()
          await despawn()
          await speed(5, 'equal external speed retained after removal')
        })
        await context.step('weaker external potion resumes with remaining duration', async () => {
          await context.command('/effect clear @s', /Removed|Cleared/, timeoutMs)
          await context.command('/effect give @s minecraft:speed 60 1 true', /Applied effect/, timeoutMs)
          await speed(1, 'weak external speed installed')
          const initialDuration = bot.entity.effects[speedId].duration
          await spawn()
          await speed(5, 'companion speed overrides weak external effect')
          await bot.waitForTicks(25)
          await reload()
          entityId = await observer.find('companion restored while weaker potion is displaced')
          await speed(5, 'companion speed restored after reload')
          await despawn()
          await speed(1, 'weak external speed restored after removal')
          context.expect(bot.entity.effects[speedId].duration > 0 && bot.entity.effects[speedId].duration < initialDuration - 10,
            'Restored potion duration was extended', bot.entity.effects[speedId])
        })
        await context.step('companion speed resumes after a stronger external effect expires', async () => {
          await context.command('/effect clear @s', /Removed|Cleared/, timeoutMs)
          await spawn()
          await speed(5, 'companion speed active')
          await context.command('/effect give @s minecraft:speed 2 8 true', /Applied effect/, timeoutMs)
          await speed(8, 'temporary stronger external effect active')
          await speed(5, 'companion speed resumed after external expiry')
          await despawn()
        })
        await context.step('weaker potion received while companion is active survives removal', async () => {
          await spawn()
          await speed(5, 'companion speed active before weaker potion')
          await context.command('/effect give @s minecraft:speed 60 1 true', /Applied effect|Unable to apply/, timeoutMs)
          await despawn()
          await speed(1, 'weaker potion received during companion effect is preserved')
        })
        await context.step('integer attribute amounts apply and are removed on unequip', async () => {
          await edit('features/attributes/config.toml', `[[companions.bee]]
attribute = "armor"
amount = 10
operation = "ADD_NUMBER"
`)
          await reload()
          await spawn()
          await context.command('/attribute @s minecraft:armor get', /value of attribute .* is 10(?:\.0)?(?:\s|$)/i, timeoutMs)
          await despawn()
          await context.command('/attribute @s minecraft:armor get', /value of attribute .* is 0(?:\.0)?(?:\s|$)/i, timeoutMs)
        })
        if (generalExperienceSupported) {
          await context.step('experience buffs restore after reload and stop after unequip', async () => {
            await edit('features/buffs/config.toml', `[companions.bee.vanilla.experience]
enabled = true
operation = "MULTIPLY"
value = 2.0
cooldown = 0
`)
            await reload()
            await spawn()
            async function pickup(expected) {
              const before = bot.experience.points
              await context.command('/summon minecraft:experience_orb ~ ~ ~ {Value:1}', /Summoned/, timeoutMs)
              await context.waitUntil(() => bot.experience.points >= before + expected,
                { timeoutMs, label: `experience pickup awards ${expected} points` })
              context.expect(bot.experience.points === before + expected, 'Unexpected experience award',
                { before, after: bot.experience.points, expected })
            }
            await pickup(2)
            await reload()
            entityId = await observer.find('companion restored for buff reload')
            await pickup(2)
            await despawn()
            await pickup(1)
          })
        } else {
          context.report.observations.push('General experience pickup buffs require Paper. The positive general XP check is excluded on Spigot; spigot-compatibility.mjs verifies rejection of that setting and supported source-specific XP behavior.')
        }
        await context.step('failed storage initialization restores the previous working runtime', async () => {
          await spawn()
          await context.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/, timeoutMs)
          try {
            const invalidStorage = config.replace(/^(directory\s*=\s*).*$/m, '$1"config.toml"')
            context.expect(invalidStorage !== config, 'H2 directory setting missing')
            const before = await runtimeReport(context)
            const log = path.join(context.server.directory, 'logs', 'latest.log')
            const offset = (await readFile(log, 'utf8')).length
            await edit('config.toml', invalidStorage)
            await context.waitUntil(async () => /Configuration hotload rejected|Could not reload ByteCompanions/i.test((await readFile(log, 'utf8')).slice(offset)),
              { timeoutMs: 45_000, intervalMs: 500, label: 'storage installation rejected' })
            await waitForFileReload(context, before.generation)
            entityId = await observer.find('working companion restored after storage initialization failure')
            await observer.requireParticles('storage initialization rollback')
            await despawn()
          } finally {
            await writeFile(path.join(directory, 'config.toml'), config, 'utf8')
          }
        })
        await context.step('per-model limit blocks a second nonexclusive head companion', async () => {
          const bee = (await remember('companions/bee.toml')).replace(/^exclusive\s*=.*$/m, 'exclusive = false')
          for (const id of ['qa_head_one', 'qa_head_two']) {
            const relative = `companions/${id}.toml`
            created.push(relative)
            await writeFile(path.join(directory, relative), bee, 'utf8')
          }
          await reload()
          await context.command('/companions spawn qa_head_one', /You have equipped qa_head_one\./, timeoutMs)
          await context.command('/companions spawn qa_head_two', /reached the limit of companions for the model/, timeoutMs)
          await context.command('/companions despawn qa_head_one', /Your companion has been unequipped\./, timeoutMs)
        })
        await context.step('decoration visuals disappear when their definitions are removed', async () => {
          const position = bot.entity.position
          const ids = observer.entityIds()
          await edit('features/decorations/config.toml', `[settings]
update-interval = 3
view-distance = 32
view-refresh-delay = 3

[decorations.qa_bee]
companion = "bee"
world = "world"
x = ${position.x + 2}
y = ${position.y + 1}
z = ${position.z}
`)
          await reload()
          entityId = await observer.find('configured decoration spawned', ids)
          await edit('features/decorations/config.toml', `[decorations]
`)
          await reload()
          await context.waitUntil(() => bot.entities[entityId] === undefined,
            { timeoutMs, label: 'removed decoration no longer visible to the client' })
        })
      } finally {
        bot.removeListener('entitySpawn', appeared)
        observer.close()
        for (const [relative, original] of originals) await writeFile(path.join(directory, relative), original, 'utf8')
        for (const relative of created) await unlink(path.join(directory, relative)).catch(error => {
          if (error.code !== 'ENOENT') throw error
        })
      }
    }
  }
}

export default reliabilityScenario()
