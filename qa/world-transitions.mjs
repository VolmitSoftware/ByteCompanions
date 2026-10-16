import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000
const travelTimeoutMs = 45_000

export default {
  name: 'bytecompanions-world-transitions',
  description: 'Verify fresh companion spawns and selected particles across twenty distant dimension changes, retaining packet and runtime diagnostics on failure.',
  async run(context) {
    const { bot } = context
    const observer = observeCompanion(context)
    const rawSpawns = new Map()
    const trace = []
    let sequence = 0
    let respawnSequence = 0
    let currentStep = 'preparation'
    context.report.observations ??= []
    const packet = (value, metadata) => {
      sequence++
      if (metadata.name === 'respawn') respawnSequence = sequence
      if (metadata.name === 'spawn_entity') rawSpawns.set(value.entityId, sequence)
      if (!['respawn', 'spawn_entity', 'entity_destroy', 'position', 'game_state_change'].includes(metadata.name)) return
      trace.push({ sequence, time: Date.now(), name: metadata.name, value })
      if (trace.length > 512) trace.shift()
    }
    bot._client.on('packet', packet)
    const command = (text, expected, timeout = timeoutMs) => context.command(text, expected, timeout)
    const nearby = () => Object.values(bot.entities).filter(entity => entity.name === 'armor_stand'
      && entity.position.distanceTo(bot.entity.position) < 24)
    async function debug() {
      const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'debug')
      const files = async () => {
        try { return (await readdir(directory)).filter(name => name.endsWith('.txt')) }
        catch (error) { if (error.code === 'ENOENT') return []; throw error }
      }
      const before = new Set(await files())
      await command('/companions debug', /Saved ByteCompanions debug dump to /)
      const file = await context.waitUntil(async () => (await files()).find(name => !before.has(name)),
        { timeoutMs, label: 'transition diagnostic file' })
      const report = await readFile(path.join(directory, file), 'utf8')
      context.report.observations.push({ kind: 'transition-runtime-diagnostics', step: currentStep, file,
        counters: report.split(/\r?\n/).filter(line =>
          /Active:|Feature particles:|Player tick tasks:|Active companions:|Completed player ticks:|Tick failures:|Particle /.test(line)) })
    }
    async function transition(dimension, x, z) {
      const before = sequence
      const startedAt = Date.now()
      await command(`/execute in minecraft:${dimension} run tp @s ${x} 181 ${z}`, /Teleported/, travelTimeoutMs)
      await context.waitUntil(() => respawnSequence > before && bot.game.dimension === dimension
        && Math.abs(bot.entity.position.x - x) < 2 && Math.abs(bot.entity.position.z - z) < 2
        && Math.abs(bot.entity.position.y - 181) < 2,
      { timeoutMs: travelTimeoutMs, label: `${currentStep}: destination dimension and position` })
      const arrivedAt = Date.now()
      const entity = await context.waitUntil(() => nearby().find(candidate =>
        (rawSpawns.get(candidate.id) ?? 0) > respawnSequence),
      { timeoutMs, label: `${currentStep}: fresh companion spawn after dimension reset` })
      await observer.find(currentStep)
      await observer.requireParticles(currentStep)
      context.expect(nearby().length === 1, 'Dimension transition duplicated the nearby companion', nearby().map(value => value.id))
      context.report.observations.push({ kind: 'dimension-transition', dimension, destination: { x, y: 181, z },
        entityId: entity.id, before, respawnSequence, spawnSequence: rawSpawns.get(entity.id),
        arrivalMs: arrivedAt - startedAt, restorationMs: Date.now() - arrivedAt,
        position: { ...bot.entity.position }, packets: trace.filter(value => value.sequence > before) })
    }
    try {
      await context.step('equip companion and selected particles before distant travel', async () => {
        if (bot.game.gameMode !== 'creative') await command('/gamemode creative', /game mode|gamemode/i)
        bot.creative.startFlying()
        await command('/companions spawn bee', /You have equipped bee\.|already.*companion equipped/i)
        await observer.find('initial companion')
        await command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        await observer.requireParticles('initial')
        await debug()
      })
      for (let cycle = 0; cycle < 10; cycle++) {
        for (const dimension of ['the_nether', 'overworld']) {
          currentStep = `${dimension} transition ${cycle + 1}`
          await context.step(currentStep, () => transition(dimension, 100_000 + cycle * 4096, 100_000 + cycle * 4096))
        }
      }
      await context.step('remove companion after all dimension transitions', async () => {
        await command('/companions despawn bee', /Your companion has been unequipped\./)
        await observer.requireRemoved()
        await debug()
      })
    } catch (error) {
      context.report.observations.push({ kind: 'transition-failure', step: currentStep,
        dimension: bot.game.dimension, position: { ...bot.entity.position }, respawnSequence,
        entities: Object.values(bot.entities).filter(entity => entity.name === 'armor_stand')
          .map(entity => ({ id: entity.id, position: { ...entity.position }, spawnSequence: rawSpawns.get(entity.id) })),
        trace: [...trace] })
      try { await debug() }
      catch (diagnosticError) {
        context.report.observations.push({ kind: 'transition-diagnostic-failure', message: diagnosticError.message })
      }
      throw error
    } finally {
      observer.close()
      bot._client.removeListener('packet', packet)
      bot.creative.stopFlying()
    }
  }
}
