import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { controls } from './collections.mjs'

const timeoutMs = 15_000
const region = 'bytecompanions_qa'

export default {
  name: 'bytecompanions-worldguard',
  description: 'Use genuine WorldEdit and WorldGuard commands to verify independent custom flags and live region transitions.',
  async run(context) {
    const ui = controls(context)
    const root = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const originals = new Map()
    let peer, peerUi, companionId, defined = false, particles = 0
    const observe = async () => JSON.parse((await ui.command('/bcqa polish', /BCQA POLISH /)).split('BCQA POLISH ')[1])
    const current = async () => {
      const state = await ui.state()
      context.expect(state.companions.bee, 'Region transition lost selected bee', state)
      companionId = state.companions.bee.entityId
      return companionId
    }
    const packet = (value, meta) => {
      if (meta.name === 'world_particles' && value.particle?.type === 'dust'
        && Math.abs((value.particle.data?.scale ?? 0) - 0.3) < 0.00001) particles++
    }
    const edit = async (relative, contents) => {
      const filename = path.join(root, relative)
      originals.set(filename, await readFile(filename, 'utf8'))
      await writeFile(filename, contents, 'utf8')
    }
    const flag = (name, value) => ui.command(`/rg flag ${region} bytecompanions-${name} ${value}`, /flag.*set|set.*flag/i)
    const bonuses = async allowed => {
      await context.waitUntil(async () => {
        const state = await observe()
        return state.armor === (allowed ? 10 : 0)
          && state.effects.includes('minecraft:speed:0') === allowed
      }, { timeoutMs, label: `region bonuses ${allowed}` })
      await ui.command('/bcqa xp block', new RegExp(`base=10 result=${allowed ? 15 : 10}`))
    }
    const visible = async allowed => {
      await current()
      await peer.waitUntil(() => Boolean(peer.bot.entities[companionId]) === allowed, { timeoutMs, label: `region visual ${allowed}` })
      await context.sleep(400)
      const before = particles
      if (allowed) await peer.waitUntil(() => particles >= before + 25, { timeoutMs, label: 'region particles restored' })
      else {
        await context.sleep(1000)
        context.expect(particles === before, 'Denied region still emitted particles')
      }
    }
    const move = async x => {
      await ui.command(`/bcqa move ${x} 100 0`, /BCQA MOVE success=true/)
      await ui.command('/bcqa platform', /BCQA PLATFORM blocks=49/)
      await context.waitUntil(() => Math.abs(context.bot.entity.position.x - x) < 0.2, { timeoutMs, label: 'owner region position' })
      await current()
    }
    try {
      await context.step('genuine WorldGuard and WorldEdit initialize custom flags and region', async () => {
        await ui.command('/version WorldGuard', /WorldGuard version/i)
        await ui.command('/version WorldEdit', /WorldEdit version/i)
        if (context.bot.game.gameMode !== 'creative') await ui.command('/gamemode creative', /game mode|gamemode/i)
        await ui.command('/companions settings visibility all', /Collection saved\./)
        await ui.command('/companions settings particles full', /Collection saved\./)
        await edit('features/attributes/config.toml', `[[companions.bee]]
attribute = "armor"
amount = 10
operation = "ADD_NUMBER"
`)
        await edit('features/effects/config.toml', `[[companions.bee]]
effect = "SPEED"
amplifier = 0
priority = 10
stack-mode = "HIGHEST"
`)
        await edit('features/buffs/config.toml', `[companions.bee]
default-priority = 10

[companions.bee.vanilla.experience-block]
enabled = true
operation = "ADD_PERCENT"
value = 50
cooldown = 0
`)
        await reloadFiles(context)
        const restored = await ui.state()
        if (!restored.companions.bee) await ui.command('/companions spawn bee', /equipped bee/)
        if (restored.profile.styles?.bee?.particleId !== 'angel_ring_effect') {
          await ui.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect/)
        }
        await move(1)
        peer = await context.connectActor('ByteRegionPeer')
        peerUi = controls(peer)
        peer.bot._client.on('packet', packet)
        await ui.command(`/op ${peer.bot.username}`, /operator|already/i)
        await ui.command(`/gamemode creative ${peer.bot.username}`, /game mode|gamemode/i)
        await peerUi.command('/bcqa move 8 100 0', /BCQA MOVE success=true/)
        await peerUi.command('/bcqa platform', /BCQA PLATFORM blocks=49/)
        await ui.command('//pos1 0,95,-3', /position.*set/i)
        await ui.command('//pos2 4,110,3', /position.*set/i)
        await ui.command(`/rg define ${region}`, /new region has been made|defined|created/i)
        defined = true
        await ui.command(`/rg flag ${region} build allow`, /flag.*set|set.*flag/i)
        await flag('visuals', 'allow')
        await flag('bonuses', 'allow')
        await visible(true)
        await bonuses(true)
      })
      await context.step('visual deny hides operator companions while bonuses stay active', async () => {
        await flag('visuals', 'deny')
        await visible(false)
        await bonuses(true)
      })
      await context.step('independent bonus deny removes modifiers and effects while visuals return', async () => {
        await flag('visuals', 'allow')
        await flag('bonuses', 'deny')
        await visible(true)
        await bonuses(false)
      })
      await context.step('leaving region restores bonuses without plugin reload', async () => {
        await move(10)
        await visible(true)
        await bonuses(true)
      })
      await context.step('entering region suspends bonuses without plugin reload or deselection', async () => {
        await move(1)
        await visible(true)
        await bonuses(false)
      })
      await context.step('combined deny blocks visual and bonus channels together', async () => {
        await flag('visuals', 'deny')
        await visible(false)
        await bonuses(false)
      })
      await context.step('leaving combined restriction restores visuals and bonuses', async () => {
        await move(10)
        await visible(true)
        await bonuses(true)
      })
      await context.step('live flag removal restores an operator inside the region', async () => {
        await move(1)
        await visible(false)
        await ui.command(`/rg remove ${region}`, /removed/i)
        defined = false
        await visible(true)
        await bonuses(true)
      })
    } finally {
      peer?.bot._client.removeListener('packet', packet)
      if (defined && context.bot._client.state === 'play') await ui.command(`/rg remove ${region}`, /removed/i)
      for (const [filename, content] of originals) await writeFile(filename, content, 'utf8')
      if (context.bot._client.state === 'play') await reloadFiles(context)
    }
  }
}
