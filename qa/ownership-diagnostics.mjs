import { readFile, readdir } from 'node:fs/promises'
import { setTimeout } from 'node:timers/promises'
import path from 'node:path'

export default {
  name: 'bytecompanions-ownership-diagnostics',
  description: 'Capture raw particles and runtime counters five seconds after equipping a companion effect.',
  async run(context) {
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'debug')
    const files = async () => {
      try { return (await readdir(directory)).filter(name => name.endsWith('.txt')) }
      catch (error) { if (error.code === 'ENOENT') return []; throw error }
    }
    let particles = 0
    const samples = []
    const listener = (packet, metadata) => {
      if (metadata.name !== 'world_particles') return
      particles++
      if (samples.length < 3) samples.push(packet)
    }
    context.bot._client.on('packet', listener)
    try {
      await context.step('equip bee and its angel ring', async () => {
        await context.command('/companions spawn bee', /equipped|already/i, 10_000)
        await context.command('/companions particles equip bee angel_ring_effect', /equipped|already/i, 10_000)
      })
      await context.step('capture delivery and tick counters', async () => {
        await setTimeout(5_000)
        const previous = new Set(await files())
        await context.command('/companions debug', /Saved ByteCompanions debug dump to /, 15_000)
        const file = (await files()).find(name => !previous.has(name))
        context.expect(Boolean(file), 'Diagnostic report was not created')
        const report = await readFile(path.join(directory, file), 'utf8')
        const counters = report.split(/\r?\n/).filter(line =>
          /Active:|Feature particles:|Player tick tasks:|Active companions:|Completed player ticks:|Tick failures:|Particle /.test(line))
        const entities = Object.values(context.bot.entities).filter(entity => entity.name === 'armor_stand')
          .map(entity => ({ id: entity.id, position: { ...entity.position } }))
        context.report.observations ??= []
        context.report.observations.push({ kind: 'owner-particle-diagnostics', file, counters, particles, samples,
          playerPosition: { ...context.bot.entity.position }, entities })
      })
    } finally {
      context.bot._client.removeListener('packet', listener)
    }
  }
}
