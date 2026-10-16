import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'

const timeoutMs = 15_000
const reloadMs = 45_000
const arena = { x: 2000, y: 181, z: 2000 }

function watch(context) {
  const { bot } = context
  const appearances = new Map()
  let sequence = 0
  let target
  let particles = 0
  let dust = 0
  let entityPackets = 0
  const spawned = entity => {
    if (entity.name === 'armor_stand') appearances.set(entity.id, ++sequence)
  }
  const packet = (value, metadata) => {
    if (target !== undefined && value?.entityId === target) entityPackets++
    if (metadata.name !== 'world_particles' || value.particle?.type !== 'dust'
      || Math.abs((value.particle.data?.scale ?? 0) - 0.3) > 0.00001) return
    dust++
    const position = bot.entities[target]?.position
    if (position && Math.hypot(value.x - position.x, value.y - position.y, value.z - position.z) <= 3) particles++
  }
  bot.on('entitySpawn', spawned)
  bot._client.on('packet', packet)
  return {
    get sequence() { return sequence },
    get dust() { return dust },
    get entityPackets() { return entityPackets },
    appearance(id) { return appearances.get(id) ?? 0 },
    nearby() {
      return Object.values(bot.entities).filter(entity => entity.name === 'armor_stand'
        && entity.position.distanceTo(bot.entity.position) < 24)
    },
    async fresh(after, label) {
      const entity = await context.waitUntil(() => this.nearby().find(candidate => this.appearance(candidate.id) > after),
        { timeoutMs, label })
      target = entity.id
      return target
    },
    async visible(id, label, after = -1) {
      await context.waitUntil(() => bot.entities[id] && this.appearance(id) > after, { timeoutMs, label })
      target = id
      context.expect(this.nearby().length === 1, `${label}: duplicate nearby companion entities`, this.nearby().map(entity => entity.id))
    },
    async particles(label) {
      const before = particles
      await context.waitUntil(() => particles >= before + 3, { timeoutMs, label: `${label}: particle packets` })
      context.report.observations.push({ kind: 'viewer-particles', actor: bot.username, entityId: target, label, received: particles - before })
    },
    close() {
      bot.removeListener('entitySpawn', spawned)
      bot._client.removeListener('packet', packet)
    }
  }
}

export default {
  name: 'bytecompanions-viewer-lifecycle',
  description: 'Two managed clients verify companion and particle visibility across range, dimensions, rapid removal, reload, and peer reconnect.',
  async run(context) {
    const { bot } = context
    const configPath = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'config.toml')
    const original = await readFile(configPath, 'utf8')
    const ownerWatch = watch(context)
    let peer
    let peerWatch
    let companionId
    context.report.observations ??= []
    const reload = () => reloadFiles(context)
    const command = (text, expected) => context.command(text, expected, timeoutMs)
    async function placePeer(x = arena.x + 2, world = 'overworld') {
      await command(`/execute in minecraft:${world} run tp ${peer.bot.username} ${x} ${arena.y} ${arena.z}`, /Teleported/)
      await context.waitUntil(() => Math.abs(peer.bot.entity.position.x - x) < 1
        && Math.abs(peer.bot.entity.position.y - arena.y) < 2,
      { timeoutMs, label: `peer position in ${world}` })
    }
    async function showBoth(label, ownerAfter) {
      companionId = await ownerWatch.fresh(ownerAfter, `${label}: owner spawn`)
      await ownerWatch.visible(companionId, `${label}: owner`)
      await peerWatch.visible(companionId, `${label}: peer`)
      await Promise.all([ownerWatch.particles(`${label}: owner`), peerWatch.particles(`${label}: peer`)])
      context.report.observations.push({ kind: 'shared-companion-id', label, entityId: companionId,
        owner: bot.username, peer: peer.bot.username })
    }
    async function absentFromPeer(label) {
      await context.waitUntil(() => peer.bot.entities[companionId] === undefined, { timeoutMs, label })
      await context.sleep(300)
      const before = peerWatch.dust
      await context.sleep(750)
      context.expect(peer.bot.entities[companionId] === undefined, `${label}: stale companion reappeared`)
      context.expect(peerWatch.dust === before, `${label}: particle packets continued after removal`,
        { before, after: peerWatch.dust })
    }
    async function equip() {
      const before = ownerWatch.sequence
      await command('/companions spawn bee', /You have equipped bee\.|already have (?:that|an exclusive) companion/)
      await command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
      await showBoth('equipped', before)
    }
    try {
      context.expect(/autoReload\s*=\s*true/.test(original), 'Viewer scenario requires autoReload=true')
      for (const key of ['viewDistance', 'viewerRefreshDelay']) {
        context.expect(new RegExp(`${key}\\s*=`).test(original), `Missing canonical setting ${key}`)
      }
      await context.step('prepare owner and ordinary managed peer with deterministic viewer range', async () => {
        await writeFile(configPath, original
          .replace(/viewDistance\s*=\s*\d+/, 'viewDistance = 8')
          .replace(/viewerRefreshDelay\s*=\s*\d+/, 'viewerRefreshDelay = 1'), 'utf8')
        await reload()
        if (bot.game.gameMode !== 'creative') await command('/gamemode creative', /game mode|gamemode/i)
        await command('/execute in minecraft:overworld run tp @s 2000 181 2000', /Teleported/)
        await context.waitUntil(() => bot.blockAt(bot.entity.position.floored().set(1995, 180, 1995))
          && bot.blockAt(bot.entity.position.floored().set(2005, 180, 2005)),
        { timeoutMs: 30_000, label: 'viewer arena chunks loaded before fill' })
        await command('/fill 1995 180 1995 2005 180 2005 minecraft:stone', /Successfully filled|No blocks were filled/)
        await command('/tp @s 2000 181 2000', /Teleported/)
        await context.waitUntil(() => Math.abs(bot.entity.position.y - arena.y) < 0.1
          && bot.blockAt(bot.entity.position.offset(0, -1, 0))?.name === 'stone',
        { timeoutMs, label: 'owner standing above the completed viewer platform' })
        await command('/companions despawn', /unequipped|do not have a companion equipped/i)
        const peerName = bot.username === 'ByteViewPeer' ? 'ByteViewGuest' : 'ByteViewPeer'
        peer = await context.connectActor(peerName)
        peerWatch = watch(peer)
        if (peer.bot.game.gameMode !== 'creative') await command(`/gamemode creative ${peerName}`, /game mode|gamemode/i)
        await placePeer()
      })
      await context.step('owner companion and selected particles reach the nearby peer', equip)
      await context.step('peer beyond viewer distance receives removal and no further particles', async () => {
        await placePeer(arena.x + 64)
        await absentFromPeer('outside view distance')
        context.expect(bot.entities[companionId] !== undefined, 'Peer departure removed owner visual')
      })
      await context.step('returning peer receives a fresh spawn and particles for the same companion', async () => {
        const before = peerWatch.appearance(companionId)
        await placePeer()
        await peerWatch.visible(companionId, 'range return', before)
        await peerWatch.particles('range return')
      })
      await context.step('cross-world peer departure invalidates the old viewer session', async () => {
        const transitioned = peer.waitForEvent('respawn', () => true, timeoutMs)
        await placePeer(arena.x + 2, 'the_nether')
        await transitioned
        await context.sleep(300)
        const before = { entities: peerWatch.entityPackets, particles: peerWatch.dust,
          appearance: peerWatch.appearance(companionId) }
        await context.sleep(750)
        context.expect(peerWatch.entityPackets === before.entities
          && peerWatch.appearance(companionId) === before.appearance,
        'Old-world companion spawn or update packets reached the peer after its dimension change')
        context.expect(peerWatch.dust === before.particles,
          'Old-world particle packets reached the peer after its dimension change')
        context.report.observations.push({ kind: 'dimension-viewer-invalidation', entityId: companionId,
          dimension: peer.bot.game.dimension, entityPacketsAfterSettle: peerWatch.entityPackets - before.entities,
          particlePacketsAfterSettle: peerWatch.dust - before.particles,
          note: 'Respawn observed; Mineflayer retains old entities across respawn, so this assertion uses subsequent protocol traffic. Return requires a new spawn for the same ID.' })
        context.expect(bot.entities[companionId] !== undefined, 'Peer world change removed owner visual')
      })
      await context.step('return from another world restores companion and particles', async () => {
        const before = peerWatch.appearance(companionId)
        const transitioned = peer.waitForEvent('respawn', () => true, timeoutMs)
        await placePeer()
        await transitioned
        await peerWatch.visible(companionId, 'world return', before)
        await peerWatch.particles('world return')
      })
      await context.step('rapid equip and removal leave no stale peer companion', async () => {
        await command('/companions despawn bee', /Your companion has been unequipped/)
        await absentFromPeer('initial removal')
        for (let cycle = 0; cycle < 3; cycle++) {
          const equipped = context.waitForMessage(/You have equipped bee\./, timeoutMs)
          const removed = context.waitForMessage(/Your companion has been unequipped\./, timeoutMs)
          bot.chat('/companions spawn bee')
          bot.chat('/companions despawn bee')
          await Promise.all([equipped, removed])
          await context.waitUntil(() => ownerWatch.nearby().length === 0 && peerWatch.nearby().length === 0,
            { timeoutMs, label: `rapid cycle ${cycle}: all fake entities removed` })
        }
        await context.sleep(750)
        context.expect(ownerWatch.nearby().length === 0 && peerWatch.nearby().length === 0, 'A delayed spawn resurrected a removed companion')
        context.report.observations.push('Rapid removal proves no protocol ghost on this server; it does not force cross-region queue contention. Queue invalidation has separate source tests.')
      })
      await context.step('re-equip restores delivery to both clients', equip)
      await context.step('reload closes owner menu and restores both viewers with particles', async () => {
        const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
        bot.chat('/companions menu')
        await opened
        const before = ownerWatch.sequence
        await reload()
        await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'menu closed by reload' })
        await showBoth('online reload', before)
      })
      await context.step('peer disconnect during reload and reconnect uses a fresh delivery session', async () => {
        const before = ownerWatch.sequence
        peerWatch.close()
        peer = await peer.reconnectAfter(async () => {
          const reloaded = reload()
          peer.bot.quit('Viewer reload reconnect check')
          await reloaded
        }, { timeoutMs: reloadMs })
        peerWatch = watch(peer)
        await placePeer()
        await showBoth('peer reconnected after reload', before)
      })
      context.report.observations.push('This scenario tests two client connections on the selected server. It does not establish Folia/Canvas region ownership, rendering, or resource-pack appearance. Managed actor cleanup closes both connections.')
    } finally {
      context.report.observations.push({ kind: 'viewer-final-positions',
        owner: { ...bot.entity.position }, peer: peer?.bot.entity && { ...peer.bot.entity.position }, companionId })
      ownerWatch.close()
      peerWatch?.close()
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      await writeFile(configPath, original, 'utf8')
      if (bot._client.state === 'play') await reload()
    }
  }
}
