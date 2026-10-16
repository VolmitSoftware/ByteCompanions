import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000
const reloadMs = 45_000

function dustCounter(bot) {
  let count = 0
  const packet = (value, metadata) => {
    if (metadata.name === 'world_particles' && value.particle?.type === 'dust'
      && Math.abs((value.particle.data?.scale ?? 0) - 0.3) < 0.00001) count++
  }
  bot._client.on('packet', packet)
  return { get count() { return count }, close() { bot._client.removeListener('packet', packet) } }
}

export default {
  name: 'bytecompanions-disable-lifecycle',
  description: 'Disable the plugin with two managed clients online, verify visual/effect/command cleanup, and leave file restoration for the next server restart.',
  async run(context) {
    const { bot } = context
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const originals = new Map()
    const ownerObserver = observeCompanion(context)
    const ownerDust = dustCounter(bot)
    let peer
    let peerObserver
    let peerDust
    let entityId
    const speedId = bot.registry.effectsByName.Speed.id
    context.report.observations ??= []
    const command = (text, expected, timeout = timeoutMs) => context.command(text, expected, timeout)
    async function edit(relative, text) {
      const filename = path.join(directory, relative)
      originals.set(filename, await readFile(filename, 'utf8'))
      await writeFile(filename, text, 'utf8')
    }
    try {
      await context.step('prepare deterministic owned attributes and potion with a live peer', async () => {
        await command('/bcqa status', /BCQA COMPONENT_IDENTITY PASS/)
        const config = await readFile(path.join(directory, 'config.toml'), 'utf8')
        context.expect(/autoReload\s*=\s*true/.test(config), 'Disable scenario requires autoReload=true')
        context.expect(/viewDistance\s*=/.test(config), 'Missing canonical view distance')
        await edit('config.toml', config.replace(/viewDistance\s*=\s*\d+/, 'viewDistance = 8'))
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
        await reloadFiles(context)
        if (bot.game.gameMode !== 'creative') await command('/gamemode creative', /game mode|gamemode/i)
        await command('/execute in minecraft:overworld run tp @s 3000 181 3000', /Teleported/)
        await context.waitUntil(() => bot.blockAt(bot.entity.position.floored().set(2995, 180, 2995))
          && bot.blockAt(bot.entity.position.floored().set(3005, 180, 3005)),
        { timeoutMs: 30_000, label: 'disable arena chunks loaded before fill' })
        await command('/fill 2995 180 2995 3005 180 3005 minecraft:stone', /Successfully filled|No blocks were filled/)
        await command('/tp @s 3000 181 3000', /Teleported/)
        await context.waitUntil(() => Math.abs(bot.entity.position.y - 181) < 0.1
          && bot.blockAt(bot.entity.position.offset(0, -1, 0))?.name === 'stone',
        { timeoutMs, label: 'owner standing above the completed disable platform' })
        await command('/companions despawn', /unequipped|do not have a companion equipped/i)
        await command('/effect give @s minecraft:speed 120 1 true', /Applied effect/)
        const peerName = bot.username === 'ByteDisablePeer' ? 'ByteDisableGuest' : 'ByteDisablePeer'
        peer = await context.connectActor(peerName)
        peerObserver = observeCompanion(peer)
        peerDust = dustCounter(peer.bot)
        if (peer.bot.game.gameMode !== 'creative') await command(`/gamemode creative ${peerName}`, /game mode|gamemode/i)
        await command(`/tp ${peerName} 3002 181 3000`, /Teleported/)
        await context.waitUntil(() => peer.bot.entity.position.distanceTo(bot.entity.position) < 4,
          { timeoutMs, label: 'peer alongside owner' })
      })
      await context.step('both clients receive the companion and particles while owned effects are active', async () => {
        await command('/companions spawn bee', /You have equipped bee\./)
        entityId = await ownerObserver.find('owner companion before live disable')
        await context.waitUntil(() => peer.bot.entities[entityId] !== undefined,
          { timeoutMs, label: 'peer sees owner companion' })
        context.expect(await peerObserver.find('peer companion before live disable') === entityId, 'Peer observed another companion ID')
        await command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        await Promise.all([ownerObserver.requireParticles('before disable owner'), peerObserver.requireParticles('before disable peer')])
        await command('/attribute @s minecraft:armor get', /value of attribute .* is 10(?:\.0)?(?:\s|$)/i)
        await context.waitUntil(() => bot.entity.effects[speedId]?.amplifier === 5,
          { timeoutMs, label: 'companion speed effect active before disable' })
      })
      await context.step('live plugin disable removes both client visuals without disconnecting players', async () => {
        await command('/bcqa disable', /BCQA DISABLED enabled=false/, reloadMs)
        await context.waitUntil(() => bot.entities[entityId] === undefined && peer.bot.entities[entityId] === undefined,
          { timeoutMs, label: 'both clients receive final companion removal' })
        context.expect(bot._client.state === 'play' && peer.bot._client.state === 'play', 'Plugin disable disconnected a client')
      })
      await context.step('disable removes owned attributes and restores the external potion', async () => {
        await command('/attribute @s minecraft:armor get', /value of attribute .* is 0(?:\.0)?(?:\s|$)/i)
        await context.waitUntil(() => bot.entity.effects[speedId]?.amplifier === 1,
          { timeoutMs, label: 'preexisting external speed restored after disable' })
        context.expect(bot.entity.effects[speedId].duration > 0 && bot.entity.effects[speedId].duration < 2400,
          'Restored external potion duration is missing or extended', bot.entity.effects[speedId])
      })
      await context.step('no queued entity or particle delivery resumes after disable', async () => {
        await context.sleep(300)
        const before = { owner: ownerDust.count, peer: peerDust.count }
        await context.sleep(1250)
        context.expect(ownerDust.count === before.owner && peerDust.count === before.peer,
          'Particle delivery continued after plugin disable', { before, after: { owner: ownerDust.count, peer: peerDust.count } })
        context.expect(bot.entities[entityId] === undefined && peer.bot.entities[entityId] === undefined, 'A queued spawn resurrected the disabled companion')
      })
      await context.step('plugin commands are removed and both clients remain responsive', async () => {
        await command('/companions help', /Unknown command|Unknown or incomplete command/i)
        const peerAlive = peer.waitForMessage(/BCQA_DISABLE_CONNECTIONS_ALIVE/, timeoutMs)
        await command('/say BCQA_DISABLE_CONNECTIONS_ALIVE', /BCQA_DISABLE_CONNECTIONS_ALIVE/)
        await peerAlive
        context.expect(bot._client.state === 'play' && peer.bot._client.state === 'play', 'Clients did not remain connected after disable')
      })
      context.report.observations.push({ kind: 'live-plugin-disable', entityId, owner: bot.username, peer: peer.bot.username,
        note: 'PluginManager.disablePlugin executed while both clients remained connected. Final removals, stopped particles, owned attribute cleanup, external potion restoration, and removed command dispatch were observed. File restoration does not re-enable the plugin; restart the server before another scenario.' })
    } finally {
      ownerObserver.close()
      peerObserver?.close()
      ownerDust.close()
      peerDust?.close()
      for (const [filename, original] of originals) await writeFile(filename, original, 'utf8')
    }
  }
}
