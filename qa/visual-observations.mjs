import { createHash } from 'node:crypto'
import { readFile, writeFile, mkdir, access } from 'node:fs/promises'
import path from 'node:path'

const effectIds = ['angel_ring', 'aura', 'batman', 'cloud', 'double_helix', 'dragon_wing', 'love_particle', 'magic_aura', 'magic', 'music', 'orb', 'pendulum', 'pulse', 'rainbow', 'rainbow_umbrella', 'rotating_ring', 'scanner', 'umbrella'].map(id => `${id}_effect`)
const companionIds = ['bee', 'copper', 'lantern', 'mushroom', 'myself', 'rainbow', 'scout', 'warrior']
const timeoutMs = 15_000
const outputDirectory = path.resolve(import.meta.dirname, '../.qa/visual-observations')

function pose(entity) {
  return { x: entity.position.x, y: entity.position.y, z: entity.position.z, yaw: entity.yaw, pitch: entity.pitch }
}

export default {
  name: 'bytecompanions-visual-observations',
  description: 'Capture actual particle packet centers and companion movement for diagnostic projections, with finite-value, delivery, removal, and restoration assertions. Does not reproduce Minecraft rendering.',
  async run(context) {
    const { bot } = context
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Visual observations require an isolated managed instance')
    const config = await readFile(path.join(context.server.directory, 'plugins/ByteCompanions/config.toml'), 'utf8')
    context.expect(/particles\s*=\s*true/.test(config) && /autoReload\s*=\s*true/.test(config),
      'Particles and automatic file reload must be enabled')
    const artifactSha256 = createHash('sha256').update(await readFile(path.join(context.server.directory, 'plugins/ByteCompanions.jar'))).digest('hex')
    context.expect(artifactSha256 === '0030bb709e24fc4bf79a307967008ab73e95ad94bb946582471c3f2c065fa405', 'Unexpected product artifact')
    const capture = { schemaVersion: 1, artifactSha256, server: { instance: context.server.instance, brand: bot.game.serverBrand, version: bot.version, protocol: bot.version },
      startedAt: new Date().toISOString(), limitations: ['Packet centers are not rendered particle sprites or trajectories.', 'Particle spread, lifetime, textures, equipment rendering, resource packs, and menu appearance require a real client.', 'Entity poses are decoded protocol state, not rendered interpolation.'], effects: [], companions: [], lifecycle: [] }
    await mkdir(outputDirectory, { recursive: true })
    async function save() { await writeFile(path.join(outputDirectory, 'capture.json'), JSON.stringify(capture)) }
    const bodies = () => Object.values(bot.entities).filter(entity => entity.name === 'armor_stand' && entity.position.distanceTo(bot.entity.position) < 24)
    let active
    let bundle
    let effectStart = 0
    let totalPackets = 0
    let invalidPackets = 0
    let liveOverlay = false
    const overlayIds = new Set()
    function display(frame) {
      if (!bot.viewer) return
      for (const id of overlayIds) bot.viewer.erase(id)
      overlayIds.clear()
      const groups = new Map()
      for (const packet of frame.packets) {
        const color = packet.color ?? 0x00ccff
        if (!groups.has(color)) groups.set(color, [])
        groups.get(color).push({ x: packet.x, y: packet.y, z: packet.z })
      }
      for (const [color, points] of groups) {
        const id = `observed-particle-centers-${color}`
        bot.viewer.drawPoints(id, points, color, 6)
        overlayIds.add(id)
      }
    }
    function finishFrame(packets) {
      if (!packets.length) return
      const frame = { tMs: Date.now() - effectStart, anchor: bodies()[0] ? pose(bodies()[0]) : null, packets }
      if (active && active.frames.length < 160 && (!active.frames.length || frame.tMs - active.frames.at(-1).tMs >= 100)) active.frames.push(frame)
      if (liveOverlay) display(frame)
    }
    function observe(packet, metadata) {
      if (metadata.name === 'bundle_delimiter') {
        if (bundle === undefined) bundle = []
        else { finishFrame(bundle); bundle = undefined }
      } else if (metadata.name === 'world_particles') {
        totalPackets++
        const point = { x: packet.x, y: packet.y, z: packet.z, type: packet.particle?.type,
          amount: packet.amount, offsetX: packet.offsetX, offsetY: packet.offsetY, offsetZ: packet.offsetZ,
          speed: packet.velocityOffset, longDistance: packet.longDistance, alwaysShow: packet.alwaysShow }
        if (packet.particle?.data?.color !== undefined) point.color = packet.particle.data.color & 0xffffff
        if (packet.particle?.data?.scale !== undefined) point.scale = packet.particle.data.scale
        const invalidFields = Object.fromEntries(Object.entries(point).filter(([key, value]) => ['x', 'y', 'z', 'amount', 'offsetX', 'offsetY', 'offsetZ', 'speed', 'scale'].includes(key) && !Number.isFinite(value)).map(([key, value]) => [key, String(value)]))
        if (Object.keys(invalidFields).length) { invalidPackets++; point.invalidFields = invalidFields }
        if (bundle) bundle.push(point)
        else finishFrame([point])
      }
    }
    bot._client.on('packet', observe)
    let selectedCompanion
    let selectedParticle = false
    async function moveTo(x, z) {
      await context.command(`/bcqa move ${x} 100 ${z}`, /BCQA MOVE success=true/, timeoutMs)
      await context.waitUntil(() => Math.abs(bot.entity.position.x - x) < 0.1 && Math.abs(bot.entity.position.z - z) < 0.1,
        { timeoutMs, label: 'teleport acknowledged' })
      await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, timeoutMs)
    }
    async function spawn(id) {
      await context.command(`/companions spawn ${id}`, new RegExp(`You have equipped ${id}\\.`), timeoutMs)
      selectedCompanion = id
      await context.waitUntil(() => bodies().length > 0, { timeoutMs, label: `${id} body arrived` })
    }
    async function despawn() {
      const ids = bodies().map(entity => entity.id)
      await context.command(`/companions despawn ${selectedCompanion}`, /Your companion has been unequipped\./, timeoutMs)
      await context.waitUntil(() => ids.every(id => !bot.entities[id]), { timeoutMs, label: 'all companion bodies removed' })
      selectedCompanion = undefined
    }
    async function trace(record, name, durationMs, action) {
      const stage = { name, samples: [] }
      record.stages.push(stage)
      const start = Date.now()
      if (action) await action()
      while (Date.now() - start < durationMs) {
        const sample = { tMs: Date.now() - start, player: pose(bot.entity), bodies: bodies().map(entity => ({ id: entity.id, ...pose(entity) })) }
        context.expect([sample.player, ...sample.bodies].every(value => [value.x, value.y, value.z, value.yaw, value.pitch].every(Number.isFinite)), 'Nonfinite entity pose', { id: record.id, stage: name })
        stage.samples.push(sample)
        await context.sleep(100)
      }
      context.expect(stage.samples.some(sample => sample.bodies.length), 'No companion body observed during stage', { id: record.id, stage: name })
    }
    try {
      await context.step('prepare controlled platform and particle observer', async () => {
        await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await moveTo(0, 0)
        await spawn('bee')
      })
      for (const id of effectIds) {
        await context.step(`${id}: capture finite packet centers and styles`, async () => {
          await context.command(`/companions particles equip bee ${id}`, new RegExp(`Equipped particle effect: ${id}\\.`), timeoutMs)
          selectedParticle = true
          await context.sleep(500)
          const before = totalPackets
          const invalidBefore = invalidPackets
          active = { id, durationMs: id === 'rainbow_effect' ? 16_000 : 5_000, totalPackets: 0, frames: [] }
          effectStart = Date.now()
          capture.effects.push(active)
          await context.sleep(active.durationMs)
          active.totalPackets = totalPackets - before
          context.expect(active.totalPackets > 0 && active.frames.length >= 2, 'Particle effect delivered insufficient samples', { id, packets: active.totalPackets, frames: active.frames.length })
          active.invalidPackets = invalidPackets - invalidBefore
          active.valid = active.invalidPackets === 0
          context.expect(active.frames.every(frame => frame.anchor && frame.packets.every(packet => typeof packet.type === 'string')), 'Missing companion anchor or particle type', { id })
          active = undefined
          await save()
        })
      }
      await context.step('particle removal stops packets and re-equip resumes', async () => {
        await context.command('/companions particles unequip bee', /Particle effect unequipped\./, timeoutMs)
        selectedParticle = false
        await context.sleep(400)
        const stopped = totalPackets
        await context.sleep(1000)
        context.expect(totalPackets === stopped, 'Particle delivery continued after removal')
        await context.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect: angel_ring_effect\./, timeoutMs)
        selectedParticle = true
        await context.waitUntil(() => totalPackets > stopped, { timeoutMs, label: 'particle re-equip resumes delivery' })
        capture.lifecycle.push({ name: 'remove-and-resume', stoppedPackets: totalPackets - stopped, passed: true })
        await context.command('/companions particles unequip bee', /Particle effect unequipped\./, timeoutMs)
        selectedParticle = false
        await despawn()
      })
      for (const [index, id] of companionIds.entries()) {
        await context.step(`${id}: movement, rotation, teleport, and removal trace`, async () => {
          await moveTo(index * 32, 0)
          await spawn(id)
          const record = { id, stages: [] }
          capture.companions.push(record)
          await trace(record, 'stationary', 1500)
          const before = bot.entity.position.clone()
          bot.setControlState('forward', true)
          try { await trace(record, 'moving', 700) } finally { bot.clearControlStates() }
          context.expect(bot.entity.position.distanceTo(before) > 0.2, 'Player movement did not occur')
          await trace(record, 'settled', 1500)
          const stationaryBody = record.stages[0].samples.at(-1).bodies[0]
          const settledBody = record.stages[2].samples.at(-1).bodies.find(body => body.id === stationaryBody.id)
          context.expect(settledBody && Math.hypot(settledBody.x - stationaryBody.x, settledBody.y - stationaryBody.y, settledBody.z - stationaryBody.z) > 0.05,
            'Companion body did not move during player movement and settling', { id, stationaryBody, settledBody })
          await trace(record, 'turned', 1500, () => bot.look(Math.PI / 2, 0, true))
          const oldPosition = bodies()[0].position.clone()
          await moveTo(index * 32, 32)
          await context.waitUntil(() => bodies().some(entity => entity.position.distanceTo(bot.entity.position) < 8 && entity.position.distanceTo(oldPosition) > 20),
            { timeoutMs, label: 'companion arrives near the new teleport anchor' })
          await trace(record, 'teleported', 1500)
          await despawn()
          await save()
        })
      }
      await context.step('publish live packet-center overlay for browser inspection', async () => {
        await moveTo(0, 0)
        await spawn('bee')
        await context.command('/companions particles equip bee dragon_wing_effect', /Equipped particle effect: dragon_wing_effect\./, timeoutMs)
        selectedParticle = true
        await context.sleep(1000)
        const target = bodies()[0].position
        await bot.lookAt(target.offset(0, 1, 0), true)
        liveOverlay = true
        context.expect(typeof bot.viewer?.drawPoints === 'function', 'Viewer overlay API unavailable')
        await writeFile(path.join(outputDirectory, 'viewer-ready.json'), JSON.stringify({ readyAt: new Date().toISOString(), url: context.report.viewer.url, effect: 'dragon_wing_effect', meaning: 'Observed packet centers, not Minecraft particle rendering' }))
        const deadline = Date.now() + 180_000
        while (Date.now() < deadline) {
          try { await access(path.join(outputDirectory, 'viewer-inspected')); break } catch { await context.sleep(1000) }
        }
        context.expect(overlayIds.size > 0, 'No particle-center overlay was drawn')
      })
      capture.completedAt = new Date().toISOString()
      capture.invalidPackets = invalidPackets
      capture.valid = invalidPackets === 0 && capture.effects.every(effect => effect.valid)
      context.report.visualCapture = { path: path.join(outputDirectory, 'capture.json'), effects: capture.effects.length, companions: capture.companions.length, invalidPackets }
      await save()
      await context.step('all observed particle fields are finite', async () => {
        context.expect(capture.valid, 'One or more particle effects delivered nonfinite fields', {
          findings: capture.effects.filter(effect => !effect.valid).map(effect => ({ id: effect.id, invalidPackets: effect.invalidPackets })) })
      })
    } finally {
      bot.clearControlStates()
      liveOverlay = false
      for (const id of overlayIds) bot.viewer?.erase(id)
      bot._client.removeListener('packet', observe)
      if (selectedParticle && selectedCompanion) await context.command(`/companions particles unequip ${selectedCompanion}`, undefined, timeoutMs)
      if (selectedCompanion) await despawn()
      await save()
    }
  }
}

