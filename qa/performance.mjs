import { readFile, writeFile, mkdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { performance } from 'node:perf_hooks'
import { runtimeReport } from './file-reload.mjs'

const execute = promisify(execFile)
const timeoutMs = 15000

export default {
  name: 'bytecompanions-performance-workload',
  description: 'Eight fixed clients measure bounded stationary, moving, disabled and distant particle traffic with JFR samples.',
  async run(context) {
    const directory = process.env.BYTE_PROFILE_DIRECTORY
    const jcmd = process.env.BYTE_PROFILE_JCMD
    const pid = process.env.BYTE_PROFILE_PID
    const gloss = process.env.BYTE_PROFILE_GLOSS === 'true'
    context.expect(directory && jcmd && /^\d+$/.test(pid), 'Explicit profiling output, JDK and server PID required')
    context.expect(/^isolated=true$/m.test(await readFile(path.join(context.server.directory, '.server-source'), 'utf8')), 'Isolated instance required')
    const actors = [context]
    const counters = new Map()
    const detach = []
    const positions = () => actors.map(actor => ({ name: actor.bot.username, position: { ...actor.bot.entity.position } }))
    const observe = actor => {
      actor.bot.physicsEnabled = false
      const values = { packets: 0, particles: 0, dust: 0, endRod: 0, bytes: 0, bundles: 0, byteFrames: 0, invalidByteFrames: 0 }
      counters.set(actor.bot.username, values)
      let frame
      const packet = (data, meta) => {
        values.packets++
        if (meta.name === 'world_particles') {
          values.particles++
          if (data.particle?.type === 'dust') { values.dust++; if (frame !== undefined) frame++ }
          if (data.particle?.type === 'end_rod') values.endRod++
        }
        if (meta.name === 'bundle_delimiter') {
          values.bundles++
          if (frame === undefined) frame = 0
          else { if (frame) { values.byteFrames++; if (frame !== 180) values.invalidByteFrames++ } frame = undefined }
        }
      }
      const bytes = chunk => { values.bytes += chunk.length }
      actor.bot._client.on('packet', packet)
      actor.bot._client.socket.on('data', bytes)
      detach.push(() => { actor.bot._client.removeListener('packet', packet); actor.bot._client.socket.removeListener('data', bytes) })
    }
    const snapshot = () => new Map([...counters].map(([name, value]) => [name, { ...value }]))
    const command = (text, expected) => context.command(text, expected, timeoutMs)
    const locate = async (spread = false) => {
      for (let i = 0; i < actors.length; i++) {
        const x = spread ? i * 96 + 0.5 : i % 4 + 0.5
        await command(`/tp ${actors[i].bot.username} ${x} 101 ${Math.floor(i / 4) + 0.5}`, /Teleported/)
      }
      await context.sleep(1000)
    }
    let moving = false
    let moveTask
    const motion = async () => {
      let direction = 1
      while (moving) {
        for (let tick = 0; tick < 80 && moving; tick++) {
          for (const actor of actors) {
            actor.bot.entity.position.x += 0.05 * direction
            actor.bot._client.write('position', { x: actor.bot.entity.position.x, y: 101,
              z: actor.bot.entity.position.z, flags: { onGround: true, hasHorizontalCollision: false } })
          }
          await context.sleep(50)
        }
        direction *= -1
      }
    }
    observe(context)
    context.report.performanceWorkload = { mode: gloss ? 'gloss' : 'alone', population: 8, effect: 'dragon_wing_effect', windows: [],
      profiler: 'JDK Flight Recorder profile.jfc; CPU and allocation sampling, not JProfiler', clientPhysics: false,
      stationaryScope: 'Stationary owners; bundled bee retains its authored orbit and hover movement' }
    try {
      await context.step('prepare eight nearby actors and loaded measurement chunks', async () => {
        await command('/gamemode creative', /game mode|gamemode/i)
        await command('/gamerule minecraft:spawn_mobs false', /false/)
        await command('/gamerule minecraft:advance_time false', /false/)
        await command('/gamerule minecraft:advance_weather false', /false/)
        await command('/time set day', /time/i)
        await command('/weather clear', /weather/i)
        for (let i = 1; i < 8; i++) {
          await context.sleep(1100)
          const actor = await context.connectActor(`ByteLoad${i}`)
          actors.push(actor); observe(actor)
          await command(`/op ${actor.bot.username}`, /operator|already/i)
          await command(`/gamemode creative ${actor.bot.username}`, /game mode|gamemode/i)
        }
        await locate(true)
        await context.sleep(10000)
        await locate()
        if (gloss) {
          const root = path.join(context.server.directory, 'plugins/Gloss')
          for (const sub of ['holograms', 'animations', 'boards']) await mkdir(path.join(root, sub), { recursive: true })
          await writeFile(path.join(root, 'animations/byte-workload.json'), JSON.stringify({ schemaVersion: 1, revision: 1,
            mode: 'ascend', frameIntervalMs: 100, frames: ['Frame A', 'Frame B', 'Frame C', 'Frame D'] }))
          await writeFile(path.join(root, 'boards/byte-workload.json'), JSON.stringify({ schemaVersion: 2, revision: 1,
            select: { priority: 10000, when: 'true' }, presentation: { title: 'BYTE WORKLOAD', hideNumbers: true,
              lines: ['Viewer {{ player.name }}', '|animation.byte-workload|'] }, variants: [] }))
          for (let i = 0; i < 8; i++) await writeFile(path.join(root, `holograms/byte-workload-${i}.json`), JSON.stringify({ schemaVersion: 3, revision: 1,
            anchor: { world: 'world', position: [i % 4 + 2, 103, Math.floor(i / 4) + 2] },
            lines: [`BYTE_WORKLOAD ${i}`, '|animation.byte-workload|'], show: true,
            particleLayers: [{ id: 'qa-endrod-frame', target: { scope: 'projection' }, geometry: { type: 'outline', padding: 0.2, spacing: 0.15 },
              placement: { layer: 'behind', depth: 0.04 }, particle: { key: 'minecraft:end_rod', count: 1, spread: [0, 0, 0], speed: 0 },
              emission: { pattern: 'steady', intervalTicks: 5 }, viewDistance: 48, show: true }] }))
          await context.waitUntil(() => actors.every(actor => Object.values(actor.bot.entities).filter(entity => entity.name === 'text_display').length >= 8),
            { timeoutMs: 45000, label: 'eight Gloss holograms visible to every nearby client' })
          await context.waitUntil(() => [...counters.values()].every(value => value.endRod > 0), { timeoutMs: 15000, label: 'Gloss particle layers emit to every viewer' })
        }
      })
      for (const stage of ['baseline', 'stationary', 'moving', 'off', 'distant']) await context.step(`profile ${stage}`, async () => {
        if (stage === 'stationary') for (const actor of actors) {
          await actor.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
          await actor.command('/companions particles equip bee dragon_wing_effect', /Equipped particle effect/, timeoutMs)
        }
        if (stage === 'stationary') await context.waitUntil(() => [...counters.values()].every(value => value.byteFrames > 0),
          { timeoutMs, label: 'every actor receives complete Byte particle bundles' })
        if (stage === 'moving') { moving = true; moveTask = motion() }
        if (stage === 'off') {
          moving = false; await moveTask; await locate()
          for (const actor of actors) await actor.command('/companions settings particles off', /Collection saved\./, timeoutMs)
        }
        if (stage === 'distant') {
          for (const actor of actors) await actor.command('/companions settings particles full', /Collection saved\./, timeoutMs)
          await locate(true)
        }
        await context.sleep(20000)
        const beforeDiagnostic = await runtimeReport(context)
        const filename = path.join(directory, `${stage}.jfr`)
        const jfrRequestedUtc = new Date().toISOString()
        const start = await execute(jcmd, [pid, 'JFR.start', `name=byte-${stage}`, 'settings=profile', `filename=${filename}`], { timeout: 15000 })
        context.expect(start.stdout.includes('Started recording'), 'JFR did not start', start)
        const tickSamples = []
        const ticks = async () => {
          const line = await command('/bcqa ticktimes', /BCQA TICKS /)
          tickSamples.push({ utc: new Date().toISOString(), ...JSON.parse(line.slice(line.indexOf('{'))) })
        }
        await ticks()
        const before = snapshot()
        const utc = new Date().toISOString()
        const began = performance.now()
        const initialPositions = positions()
        for (let i = 0; i < 30; i++) {
          await context.sleep(1000)
          context.expect(actors.every(actor => actor.bot._client.state === 'play'), 'Workload client disconnected')
          if (i % 2 === 1) await ticks()
        }
        const elapsedMs = performance.now() - began
        const endedUtc = new Date().toISOString()
        const after = snapshot()
        const stopped = await execute(jcmd, [pid, 'JFR.stop', `name=byte-${stage}`], { timeout: 15000 })
        context.expect((await stat(filename)).size > 0, 'JFR capture missing')
        const afterDiagnostic = await runtimeReport(context)
        const traffic = [...after].map(([name, values]) => ({ name, ...Object.fromEntries(Object.keys(values).map(key => [key, values[key] - before.get(name)[key]])) }))
        const result = { stage, warmupMs: 20000, startedUtc: utc, endedUtc, elapsedMs, initialPositions, finalPositions: positions(),
          traffic, tickSamples, jfrRequestedUtc, jfrStoppedUtc: new Date().toISOString(),
          tickScope: 'Periodic public Paper tick-time ring snapshots; overlapping buffers require deduplication by server tick',
          beforeDiagnostic: beforeDiagnostic.text, afterDiagnostic: afterDiagnostic.text, jfr: filename, jfrStart: start.stdout, jfrStop: stopped.stdout }
        context.report.performanceWorkload.windows.push(result)
        await writeFile(path.join(directory, `${stage}.json`), JSON.stringify(result, null, 2))
        context.expect(traffic.every(value => value.packets > 0 && value.bytes > 0), 'Every actor must receive traffic')
        if (stage === 'off' || stage === 'baseline') context.expect(traffic.every(value => value.dust === 0), 'Unexpected Byte particles in control window', traffic)
        else context.expect(traffic.every(value => value.byteFrames > 0 && value.invalidByteFrames === 0), 'An equipped actor lacked complete 180-point Byte frames', traffic)
        if (gloss && stage !== 'distant') context.expect(traffic.every(value => value.endRod > 0), 'Gloss particle workload inactive', traffic)
      })
    } finally {
      moving = false
      if (moveTask) await moveTask
      for (const close of detach) close()
    }
  }
}
