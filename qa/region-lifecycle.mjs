import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { componentText, itemText } from './merged-features.mjs'

const timeoutMs = 20_000
const reloadMs = 60_000
const arenas = [{ x: 2008, y: 300, z: 2008 }, { x: 10200, y: 300, z: 2008 }]

function watch(actor) {
  const { bot } = actor
  let target = -1
  let particles = 0
  let dust = 0
  let worldParticles = 0
  let lastDust
  const packet = (value, metadata) => {
    if (metadata.name !== 'world_particles') return
    worldParticles++
    if (value?.particle?.type === 'dust') {
      dust++
      lastDust = { x: value.x, y: value.y, z: value.z, data: value.particle.data }
    }
    if (value?.particle?.type !== 'dust'
      || Math.abs((value.particle.data?.scale ?? 0) - 0.3) > 0.00001) return
    const position = bot.entities[target]?.position
    if (position && Math.hypot(value.x - position.x, value.y - position.y, value.z - position.z) <= 3) particles++
  }
  bot._client.on('packet', packet)
  return {
    target(id) { target = id },
    nearby() {
      return Object.values(bot.entities).filter(entity => entity.name === 'armor_stand'
        && entity.position.distanceTo(bot.entity.position) < 24)
    },
    async particles(label) {
      const before = particles
      try {
        await actor.waitUntil(() => particles >= before + 3, { timeoutMs, label: `${label}: particles` })
      } catch (failure) {
        actor.report.observations.push({ kind: 'region-particle-failure', actor: bot.username, label,
          version: bot.version, protocolVersion: bot._client.protocolVersion, target,
          targetPosition: bot.entities[target]?.position, playerPosition: bot.entity.position,
          matched: particles, before, dust, worldParticles, lastDust })
        throw failure
      }
      actor.report.observations.push({ kind: 'region-particles', actor: bot.username, label, entityId: target, received: particles - before })
    },
    close() { bot._client.removeListener('packet', packet) }
  }
}

async function open(actor, command) {
  if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
  const opened = actor.waitForEvent('windowOpen', () => true, timeoutMs)
  actor.bot.chat(command)
  let [window] = await opened
  if (command === '/companions config') {
    const loading = window
    window = await actor.waitUntil(() => {
      const current = actor.bot.currentWindow
      return current && current.id !== loading.id && current.slots.some((item, index) => index < current.inventoryStart
        && item && !/^\s*$|^Loading/i.test(itemText(item))) ? current : false
    }, { timeoutMs, label: 'populated configuration editor replaces the loading inventory' })
    actor.report.observations.push({ kind: 'config-editor-loaded', actor: actor.bot.username,
      loadingId: loading.id, populatedId: window.id, title: componentText(window.title) })
  }
  actor.expect(window.inventoryStart > 0, `No inventory contents for ${command}`)
  return window
}

function interact(bot, entityId) {
  if (bot.supportFeature('attackUsesOwnPacket')) {
    bot._client.write('use_entity', { target: entityId, hand: 'main_hand', location: { x: 0, y: 0, z: 0 }, sneaking: false })
  } else {
    bot._client.write('use_entity', { target: entityId, mouse: 0, hand: 0, sneaking: false })
  }
}

export default {
  name: 'bytecompanions-region-lifecycle',
  description: 'Verify distinct region owners, reload, menus, decorations, companion effects, and reconnect with two managed actors.',
  async run(context) {
    context.report.observations ??= []
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const originals = new Map()
    for (const relative of ['config.toml', 'features/decorations/config.toml', 'features/attributes/config.toml', 'features/effects/config.toml']) {
      originals.set(relative, await readFile(path.join(directory, relative), 'utf8'))
    }
    const properties = await readFile(path.join(context.server.directory, 'server.properties'), 'utf8')
    const worldName = properties.match(/^level-name=(.+)$/m)?.[1].trim()
    context.expect(worldName && /^[\w-]+$/.test(worldName), 'Use a simple overworld level-name in the isolated QA server')
    context.expect(/^(?:minecraft:)?overworld$/.test(context.bot.game.dimension), 'Start the primary actor in the overworld')
    const actors = [context]
    const watchers = [watch(context)]
    let current = []
    let decorations = false
    let peerOperator = false
    let particleAccessId
    let modified = false
    const peerName = context.bot.username === 'ByteRegionPeer' ? 'ByteRegionGuest' : 'ByteRegionPeer'
    const reload = () => reloadFiles(context)
    const command = (actor, text, expected) => actor.command(text, expected, timeoutMs)
    async function edit(relative, contents) {
      modified = true
      await writeFile(path.join(directory, relative), contents, 'utf8')
    }
    async function peerPermission(enabled) {
      if (enabled) peerOperator = true
      await command(context, `/${enabled ? 'op' : 'deop'} ${peerName}`, /operator/i)
      peerOperator = enabled
    }
    async function owned() {
      for (let index = 0; index < 2; index++) {
        const response = await command(actors[index], `/bcqa owner ${actors[1 - index].bot.username}`,
          /BCQA OWNER self=true target=false/)
        context.report.observations.push({ kind: 'region-ownership', actor: actors[index].bot.username, response })
      }
    }
    async function place(actor, arena) {
      if (actor.bot.game.gameMode !== 'creative') await command(actor, '/gamemode creative', /game mode|gamemode/i)
      actor.bot.creative.startFlying()
      const response = await actor.command(`/bcqa move ${arena.x} ${arena.y} ${arena.z}`, /BCQA MOVE success=true world=/, 90_000)
      await actor.waitUntil(() => actor.bot.entity.position.distanceTo(actor.bot.entity.position.clone().set(arena.x, arena.y, arena.z)) < 1,
        { timeoutMs, label: `${actor.bot.username}: destination reached` })
      await command(actor, '/bcqa platform', /BCQA PLATFORM blocks=49/)
      return response.match(/world=([\w-]+)/)[1]
    }
    async function features(actor, enabled, label) {
      const speedId = actor.bot.registry.effectsByName.Speed.id
      await actor.waitUntil(() => enabled ? actor.bot.entity.effects[speedId]?.amplifier === 5 : !actor.bot.entity.effects[speedId],
        { timeoutMs, label: `${label}: speed ${enabled ? 'active' : 'removed'}` })
      await command(actor, '/attribute @s minecraft:armor get',
        new RegExp(`value of attribute .* is ${enabled ? 10 : 0}(?:\\.0)?(?:\\s|$)`, 'i'))
    }
    async function restored(label, previous = []) {
      const next = []
      for (let index = 0; index < 2; index++) {
        const actor = actors[index]
        const observer = watchers[index]
        const base = await actor.waitUntil(async () => {
          const response = await command(actor, '/bcqa state', /BCQA STATE/)
          const id = Number(response.match(/base=(-?\d+)/)[1])
          return id > 0 && !previous[index]?.includes(id) ? id : false
        }, { timeoutMs, label: `${label}: ${actor.bot.username} restored base` })
        await actor.waitUntil(() => actor.bot.entities[base] && observer.nearby().length === (decorations ? 2 : 1)
          && (previous[index] ?? []).every(id => !actor.bot.entities[id]),
        { timeoutMs, label: `${label}: ${actor.bot.username} replacement without duplicates` })
        observer.target(base)
        const ids = observer.nearby().map(entity => entity.id)
        if (decorations) {
          const decoration = observer.nearby().find(entity => entity.id !== base)
          const arena = arenas[index]
          context.expect(Math.hypot(decoration.position.x - arena.x - 6, decoration.position.y - arena.y,
            decoration.position.z - arena.z) < 4, `${label}: decoration is not at its configured location`)
        }
        await observer.particles(`${label}: ${actor.bot.username}`)
        await features(actor, true, label)
        next.push(ids)
        context.report.observations.push({ kind: 'region-restored', actor: actor.bot.username, label, base, ids, previous: previous[index] ?? [] })
      }
      context.expect(next[0].every(id => !actors[1].bot.entities[id]) && next[1].every(id => !actors[0].bot.entities[id]),
        `${label}: distant actors received each other's displays`)
      current = next
    }
    async function editorReload(commands, label) {
      const windows = await Promise.all(actors.map((actor, index) => open(actor, commands[index])))
      const previous = current
      await reload()
      await restored(label, previous)
      for (let index = 0; index < 2; index++) {
        const actor = actors[index]
        const windowState = { actor: actor.bot.username, command: commands[index],
          previous: { id: windows[index].id, title: componentText(windows[index].title) },
          current: actor.bot.currentWindow ? { id: actor.bot.currentWindow.id, title: componentText(actor.bot.currentWindow.title) } : null }
        context.report.observations.push({ kind: 'region-editor-window', label, ...windowState })
        context.expect(actor.bot.currentWindow?.id === windows[index].id,
          `${label}: shared editor inventory changed during runtime replacement`, windowState)
        const slot = await actor.waitUntil(() => {
          const found = actor.bot.currentWindow?.slots.findIndex((item, itemIndex) => itemIndex < windows[index].inventoryStart
            && /^Close$/i.test(itemText(item).replace(/§[0-9a-fk-orx]/gi, '').trim())) ?? -1
          return found < 0 ? false : { index: found }
        }, { timeoutMs, label: `${label}: editor Close button` })
        await actor.bot.clickWindow(slot.index, 0, 0)
        await actor.waitUntil(() => !actor.bot.currentWindow, { timeoutMs, label: `${label}: editor callback remains usable` })
        context.report.observations.push({ kind: 'region-editor', actor: actor.bot.username, command: commands[index], title: componentText(windows[index].title) })
      }
    }
    try {
      await context.step('prepare deterministic features and an operator peer', async () => {
        let config = originals.get('config.toml')
        context.expect(/autoReload\s*=\s*true/.test(config), 'Use autoReload=true for this scenario')
        for (const [key, value] of Object.entries({ viewDistance: 8, viewerRefreshDelay: 1, dispatchInteractEvent: true })) {
          const setting = new RegExp(`^${key}\\s*=.*$`, 'm')
          context.expect(setting.test(config), `Missing canonical setting ${key}`)
          config = config.replace(setting, `${key} = ${value}`)
        }
        const featureBlock = config.match(/^\[features\]\r?\n[^[]*/m)?.[0] ?? ''
        for (const feature of ['attributes', 'effects', 'particles', 'decorations']) {
          context.expect(new RegExp(`^${feature}\\s*=\\s*true(?:\\s|$)`, 'm').test(featureBlock), `Enable ${feature} before this scenario`)
        }
        await edit('config.toml', config)
        await edit('features/decorations/config.toml', `[settings]
update-interval = 3
view-distance = 8
view-refresh-delay = 1

[decorations]
`)
        await edit('features/attributes/config.toml', `[[companions.bee]]
attribute = "armor"
amount = 10
operation = "ADD_NUMBER"
`)
        await edit('features/effects/config.toml', `[[companions.bee]]
effect = "SPEED"
amplifier = 5
priority = 5
stack-mode = "HIGHEST"
`)
        await reload()
        actors.push(await context.connectActor(peerName))
        watchers.push(watch(actors[1]))
        await peerPermission(true)
        for (const actor of actors) await command(actor, '/bcqa status', /BCQA COMPONENT_IDENTITY PASS/)
      })
      await context.step('place actors 8192 blocks apart and prove distinct ownership in both directions', async () => {
        const worlds = []
        for (let index = 0; index < 2; index++) worlds.push(await place(actors[index], arenas[index]))
        context.expect(worlds[0] === worlds[1], 'Both actors must occupy the same world')
        await owned()
      })
      await context.step('equip separate companions with particles, speed, and armor', async () => {
        for (const actor of actors) {
          await command(actor, '/companions despawn', /unequipped|do not have a companion equipped/i)
          await command(actor, '/companions spawn bee', /You have equipped bee\./)
          await command(actor, '/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        }
        await restored('initial')
      })
      await context.step('route synthetic unknown-entity interaction across owners and reply once to the visitor', async () => {
        for (let index = 0; index < 2; index++) {
          const visitor = actors[index]
          const response = await command(actors[1 - index], '/bcqa state', /BCQA STATE/)
          const target = Number(response.match(/base=(\d+)/)[1])
          await command(visitor, '/bcqa reset', /BCQA RESET count=0/)
          const reply = visitor.waitForMessage(new RegExp(`BCQA INTERACT count=1 hand=HAND attack=false base=${target}(?:\\s|$)`), timeoutMs)
          interact(visitor.bot, target)
          await reply
          await visitor.bot.waitForTicks(8)
          await command(visitor, '/bcqa state', /BCQA STATE count=1 /)
        }
        context.report.observations.push('Interaction packets deliberately target a known companion ID in the distant region. This proves event routing and owner-scheduled replies, not a physical in-range click.')
      })
      await context.step('start decorations in both occupied regions and restore companions during reload', async () => {
        const entries = arenas.map((arena, index) => `[decorations.region-${index}]\ncompanion = "bee"\nworld = ${JSON.stringify(worldName)}\nx = ${arena.x + 6}\ny = ${arena.y}\nz = ${arena.z}\n`).join('')
        await edit('features/decorations/config.toml', `[settings]\nupdate-interval = 3\nview-distance = 8\nview-refresh-delay = 1\n${entries}`)
        decorations = true
        const previous = current
        await reload()
        await restored('decorations enabled', previous)
      })
      await context.step('close Layout menus on both owners while replacing displays', async () => {
        await Promise.all(actors.map(actor => open(actor, '/companions menu')))
        const previous = current
        await reload()
        for (const actor of actors) await actor.waitUntil(() => !actor.bot.currentWindow, { timeoutMs, label: 'Layout menu closed by reload' })
        await restored('Layout reload', previous)
      })
      await context.step('keep config and language editors usable through reload on different owners', async () => {
        await editorReload(['/companions config', '/companions language server edit en_US'], 'first editor reload')
      })
      await context.step('swap editors between owners and repeat reload', async () => {
        await editorReload(['/companions language server edit en_US', '/companions config'], 'swapped editor reload')
      })
      await context.step('disconnect a peer during reload and restore its saved companion on reconnect', async () => {
        particleAccessId = actors[1].bot.player.uuid
        context.expect(typeof particleAccessId === 'string', 'Peer must expose its authenticated offline UUID')
        await command(context, `/bcqa particleaccess ${particleAccessId} grant`, /BCQA PARTICLE_ACCESS .* enabled=true/)
        await peerPermission(false)
        const previous = current
        watchers[1].close()
        actors[1] = await actors[1].reconnectAfter(async () => {
          const reloaded = reload()
          actors[1].bot.quit('Region lifecycle reconnect')
          await reloaded
        }, { timeoutMs: reloadMs })
        watchers[1] = watch(actors[1])
        await peerPermission(true)
        actors[1].bot.creative.startFlying()
        await owned()
        await restored('peer reconnect', previous)
      })
      await context.step('remove companions and their effects without removing regional decorations', async () => {
        for (let index = 0; index < 2; index++) {
          const actor = actors[index]
          const base = Number((await command(actor, '/bcqa state', /BCQA STATE/)).match(/base=(\d+)/)[1])
          await command(actor, '/companions despawn bee', /Your companion has been unequipped/)
          await actor.waitUntil(() => !actor.bot.entities[base] && watchers[index].nearby().length === 1,
            { timeoutMs, label: 'only static decoration remains' })
          await features(actor, false, 'unequipped')
        }
      })
      context.report.observations.push('Ten scenario steps cover protocol state and actual region ownership. Rendering, resource packs, normal process restart, and third-party plugin unload are separate checks.')
    } finally {
      const cleanup = []
      for (const observer of watchers) observer.close()
      for (const actor of actors) {
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
        actor.bot.creative.stopFlying()
      }
      if (modified) {
        const results = await Promise.allSettled([...originals].map(([relative, contents]) => writeFile(path.join(directory, relative), contents, 'utf8')))
        cleanup.push(...results.filter(result => result.status === 'rejected').map(result => result.reason))
        if (context.bot._client.state === 'play') {
          try { await reload() } catch (error) { cleanup.push(error) }
        }
      }
      if (peerOperator && context.bot._client.state === 'play') {
        try { await peerPermission(false) } catch (error) { cleanup.push(error) }
      }
      if (particleAccessId && context.bot._client.state === 'play') {
        try { await command(context, `/bcqa particleaccess ${particleAccessId} revoke`, /BCQA PARTICLE_ACCESS .* enabled=false/) }
        catch (error) { cleanup.push(error) }
      }
      if (cleanup.length) throw new AggregateError(cleanup, 'Region scenario cleanup failed')
    }
  }
}
