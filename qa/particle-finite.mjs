import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'

const effectIds = ['angel_ring', 'aura', 'batman', 'cloud', 'double_helix', 'dragon_wing', 'love_particle', 'magic_aura', 'magic', 'music', 'orb', 'pendulum', 'pulse', 'rainbow', 'rainbow_umbrella', 'rotating_ring', 'scanner', 'umbrella'].map(id => `${id}_effect`)
const durations = { rainbow_effect: 16_000, double_helix_effect: 12_000, scanner_effect: 12_000, rotating_ring_effect: 24_000 }
const timeoutMs = 15_000

export default {
  name: 'bytecompanions-particle-finite',
  description: 'Require finite packet fields from every particle effect and complete Batman frames across movement, teleport, reload, removal, and re-equip. Checks protocol output, not Minecraft rendering.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Particle QA requires an isolated managed instance')
    const config = await readFile(path.join(directory, 'plugins/ByteCompanions/config.toml'), 'utf8')
    context.expect(/particles\s*=\s*true/.test(config) && /autoReload\s*=\s*true/.test(config),
      'Particles and automatic file reload must be enabled')
    const artifactSha256 = createHash('sha256').update(await readFile(path.join(directory, 'plugins/ByteCompanions.jar'))).digest('hex')
    context.report.particleFinite = { artifactSha256, effects: [], movement: [], lifecycle: [], invalidPackets: 0, totalPackets: 0 }
    const evidence = context.report.particleFinite
    let bundle
    let active
    let allFrames = 0
    let selected = false
    let equipped = false
    const bodies = () => Object.values(bot.entities).filter(entity => entity.name === 'armor_stand' && entity.position.distanceTo(bot.entity.position) < 24)
    function completeFrame(packets) {
      if (!packets.length) return
      allFrames++
      if (!active) return
      active.frames++
      if (Math.hypot(bot.entity.velocity.x, bot.entity.velocity.z) > 0.02) active.movingFrames++
      active.frameSizes.add(packets.length)
      if (active.id === 'batman_effect') {
        const anchor = bodies()[0]?.position
        if (!anchor || packets.some(point => Math.hypot(point.x - anchor.x, point.y - anchor.y, point.z - anchor.z) > 6)) active.staleFrames++
      }
      if (active.samples.length < 3) active.samples.push(packets)
    }
    function observe(packet, metadata) {
      if (metadata.name === 'bundle_delimiter') {
        if (bundle === undefined) bundle = []
        else { completeFrame(bundle); bundle = undefined }
      } else if (metadata.name === 'world_particles') {
        evidence.totalPackets++
        const point = { x: packet.x, y: packet.y, z: packet.z, type: packet.particle?.type,
          amount: packet.amount, offsetX: packet.offsetX, offsetY: packet.offsetY, offsetZ: packet.offsetZ,
          speed: packet.velocityOffset, longDistance: packet.longDistance, alwaysShow: packet.alwaysShow }
        if (packet.particle?.data?.scale !== undefined) point.scale = packet.particle.data.scale
        if (packet.particle?.data?.color !== undefined) {
          point.rawColor = packet.particle.data.color
          point.color = point.rawColor & 0xffffff
        }
        const invalidFields = Object.fromEntries(Object.entries(point).filter(([key, value]) => ['x', 'y', 'z', 'amount', 'offsetX', 'offsetY', 'offsetZ', 'speed', 'scale', 'color', 'rawColor'].includes(key) && !Number.isFinite(value)).map(([key, value]) => [key, String(value)]))
        if (Object.keys(invalidFields).length) { evidence.invalidPackets++; point.invalidFields = invalidFields }
        if (bundle) bundle.push(point)
        else completeFrame([point])
      }
    }
    bot._client.on('packet', observe)
    async function moveTo(x, z) {
      await context.command(`/bcqa move ${x} 100 ${z}`, /BCQA MOVE success=true/, timeoutMs)
      await context.waitUntil(() => Math.abs(bot.entity.position.x - x) < 0.1 && Math.abs(bot.entity.position.z - z) < 0.1,
        { timeoutMs, label: 'teleport acknowledged' })
      await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, timeoutMs)
    }
    async function capture(id, stage, durationMs) {
      const startPackets = evidence.totalPackets
      const startInvalid = evidence.invalidPackets
      active = { id, stage, durationMs, frames: 0, movingFrames: 0, distanceMoved: 0, staleFrames: 0, frameSizes: new Set(), samples: [] }
      if (stage === 'moving') {
        const center = bot.entity.position.clone()
        const targets = [[1.2, 1.2], [-1.2, 1.2], [-1.2, -1.2], [1.2, -1.2]]
        let target = 0
        let previous = center
        const end = Date.now() + durationMs
        bot.setControlState('sneak', true)
        bot.setControlState('forward', true)
        try {
          while (Date.now() < end) {
            const [x, z] = targets[target]
            const destination = center.offset(x, 0, z)
            if (bot.entity.position.distanceTo(destination) < 0.4) target = (target + 1) % targets.length
            await bot.lookAt(destination.offset(0, bot.entity.height, 0), true)
            await context.sleep(100)
            active.distanceMoved += bot.entity.position.distanceTo(previous)
            previous = bot.entity.position.clone()
          }
        } finally { bot.clearControlStates() }
      } else if (durationMs) await context.sleep(durationMs)
      else await context.waitUntil(() => active.frames >= 3, { timeoutMs, label: `${stage}: three complete Batman frames` })
      const result = { ...active, frameSizes: [...active.frameSizes], packets: evidence.totalPackets - startPackets, invalidPackets: evidence.invalidPackets - startInvalid }
      active = undefined
      if (stage === 'equipped') evidence.effects.push(result)
      else if (stage === 'moving') evidence.movement.push(result)
      else evidence.lifecycle.push(result)
      context.expect(result.frames >= 3 && result.packets > 0, 'Insufficient particle frames', { id, stage, frames: result.frames })
      context.expect(result.invalidPackets === 0, 'Nonfinite particle packet fields', { id, stage, invalidPackets: result.invalidPackets, samples: result.samples })
      if (stage === 'moving') context.expect(result.distanceMoved > 2 && result.movingFrames >= 3,
        'Effect disappeared while the owner was moving', { id, distanceMoved: result.distanceMoved, movingFrames: result.movingFrames })
      if (id === 'batman_effect') {
        context.expect(result.staleFrames === 0, 'Batman particles retained a stale companion anchor', { stage, staleFrames: result.staleFrames })
        context.expect(result.frameSizes.length === 1 && result.frameSizes[0] === 120, 'Batman frame must contain all 120 points', { stage, frameSizes: result.frameSizes })
        context.expect(result.samples.every(frame => frame.every(point => point.type === 'dust' && point.amount === 2 && Math.abs(point.scale - 0.4) < 0.00001
          && point.offsetX === 0 && point.offsetY === 0 && point.offsetZ === 0 && point.speed === 0)), 'Batman packet style or spread changed', { stage })
      }
    }
    try {
      await context.step('prepare isolated particle observer and companion', async () => {
        await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await moveTo(0, 0)
        await context.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
        equipped = true
        await context.waitUntil(() => bodies().length > 0, { timeoutMs, label: 'companion body arrives' })
      })
      for (const id of effectIds) await context.step(`${id}: all observed numeric fields are finite`, async () => {
        await context.command(`/companions particles equip bee ${id}`, new RegExp(`Equipped particle effect: ${id}\\.`), timeoutMs)
        selected = true
        await context.sleep(500)
        await capture(id, 'equipped', durations[id] ?? 5_000)
        await capture(id, 'moving', 8_000)
        await moveTo(0, 0)
      })
      await context.step('Batman re-equip restores complete finite frames', async () => {
        await context.command('/companions particles equip bee batman_effect', /Equipped particle effect: batman_effect\./, timeoutMs)
        await context.sleep(500)
        await capture('batman_effect', 're-equipped')
      })
      await context.step('Batman movement reset resumes complete finite frames', async () => {
        const previous = bot.entity.position.clone()
        const previousBody = bodies()[0].position.clone()
        bot.setControlState('forward', true)
        try { await context.waitUntil(() => bot.entity.position.distanceTo(previous) > 0.3, { timeoutMs: 3000, label: 'real player movement' }) }
        finally { bot.clearControlStates() }
        await context.waitUntil(() => bodies().some(entity => entity.position.distanceTo(previousBody) > 0.05), { timeoutMs, label: 'companion follows player movement' })
        let settled = false
        const settleDeadline = Date.now() + 5000
        while (Date.now() < settleDeadline && !settled) {
          const anchor = bodies()[0]?.position.clone()
          await context.sleep(250)
          settled = anchor && bodies().some(entity => entity.position.distanceTo(anchor) < 0.1) && bot.entity.position.distanceTo(previous) > 0.3
        }
        context.expect(settled, 'Companion did not settle after following the player')
        await context.sleep(500)
        await capture('batman_effect', 'settled')
      })
      await context.step('Batman teleport rebuild resumes complete finite frames', async () => {
        const previous = bodies()[0].position.clone()
        await moveTo(160, 160)
        await context.waitUntil(() => bodies().some(entity => entity.position.distanceTo(bot.entity.position) < 8 && entity.position.distanceTo(previous) > 100),
          { timeoutMs, label: 'companion arrives at teleport destination' })
        await context.sleep(500)
        await capture('batman_effect', 'teleported')
      })
      await context.step('Batman reload restores selected effect with finite frames', async () => {
        const previousFrames = allFrames
        await reloadFiles(context)
        await context.waitUntil(() => bodies().length > 0 && allFrames > previousFrames, { timeoutMs, label: 'reload restores companion and particles' })
        await context.sleep(500)
        await capture('batman_effect', 'reloaded')
      })
      await context.step('Batman removal stops delivery and re-equip resumes', async () => {
        await context.command('/companions particles unequip bee', /Particle effect unequipped\./, timeoutMs)
        selected = false
        await context.sleep(400)
        const stopped = evidence.totalPackets
        await context.sleep(1000)
        context.expect(evidence.totalPackets === stopped, 'Particle delivery continued after removal')
        await context.command('/companions particles equip bee batman_effect', /Equipped particle effect: batman_effect\./, timeoutMs)
        selected = true
        await context.sleep(400)
        await capture('batman_effect', 'resumed')
      })
      await context.step('remove companion and verify all observed packet fields stayed finite', async () => {
        await context.command('/companions particles unequip bee', /Particle effect unequipped\./, timeoutMs)
        selected = false
        const ids = bodies().map(entity => entity.id)
        await context.command('/companions despawn bee', /Your companion has been unequipped\./, timeoutMs)
        equipped = false
        await context.waitUntil(() => ids.every(id => !bot.entities[id]), { timeoutMs, label: 'companion removal' })
        await context.sleep(400)
        const stopped = evidence.totalPackets
        await context.sleep(750)
        context.expect(evidence.totalPackets === stopped, 'Packets continued after companion removal')
        const expectedIds = [...effectIds].sort()
        context.expect(evidence.invalidPackets === 0, 'Nonfinite particle fields were observed', evidence)
        for (const [name, captures, stage] of [['effects', evidence.effects, 'equipped'], ['movement', evidence.movement, 'moving']]) {
          context.expect(captures.length === expectedIds.length
            && JSON.stringify(captures.map(capture => capture.id).sort()) === JSON.stringify(expectedIds)
            && captures.every(capture => capture.stage === stage), 'Incomplete particle effect coverage', { name, captures })
        }
        const expectedStages = ['re-equipped', 'settled', 'teleported', 'reloaded', 'resumed'].sort()
        context.expect(evidence.lifecycle.length === expectedStages.length
          && JSON.stringify(evidence.lifecycle.map(capture => capture.stage).sort()) === JSON.stringify(expectedStages)
          && evidence.lifecycle.every(capture => capture.id === 'batman_effect'), 'Incomplete Batman lifecycle coverage', evidence.lifecycle)
      })
    } finally {
      bot.clearControlStates()
      bot._client.removeListener('packet', observe)
      if (selected) await context.command('/companions particles unequip bee', undefined, timeoutMs)
      if (equipped) await context.command('/companions despawn bee', undefined, timeoutMs)
    }
  }
}
