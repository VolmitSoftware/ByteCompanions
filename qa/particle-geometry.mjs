import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'

const timeoutMs = 15_000
const baselineSha256 = '91109ba972bc7fc031fcfd5c1ee896c9f061d305bd99c4c028972a5e346d4d6a'
const effects = [
  { id: 'dragon_wing_effect', points: 180, scale: 0.4, amount: 1, colors: [0xff8c00, 0xff3214, 0x8b0000] },
  { id: 'umbrella_effect', points: 120, scale: 0.8, amount: 2, colors: [0xb02e26, 0xf9fffe] },
  { id: 'rainbow_umbrella_effect', points: 120, scale: 0.8, amount: 2,
    colors: [0xff0000, 0xffa500, 0xffff00, 0x00ff00, 0x0000ff, 0x9400d3, 0x4b0082] },
  { id: 'angel_ring_effect', points: 25, scale: 0.3, amount: 1, colors: [0xf9fffe] }
]

function watchFrames(bot) {
  let bundle
  const frames = []
  const packet = (value, metadata) => {
    if (metadata.name === 'bundle_delimiter') {
      if (bundle === undefined) bundle = []
      else {
        if (bundle.length) {
          frames.push(bundle)
          if (frames.length > 24) frames.shift()
        }
        bundle = undefined
      }
    } else if (bundle && metadata.name === 'world_particles' && value.particle?.type === 'dust') {
      bundle.push({ x: value.x, y: value.y, z: value.z,
        color: value.particle.data.color & 0xffffff, scale: value.particle.data.scale,
        amount: value.amount, offsetX: value.offsetX, offsetY: value.offsetY, offsetZ: value.offsetZ,
        speed: value.velocityOffset, longDistance: value.longDistance, alwaysShow: value.alwaysShow })
    }
  }
  bot._client.on('packet', packet)
  return { frames, clear() { frames.length = 0 }, close() { bot._client.removeListener('packet', packet) } }
}

function normalize(effect, frame) {
  const origin = frame[0]
  let axisX = 1
  let axisZ = 0
  if (effect.id === 'dragon_wing_effect') {
    const point = frame.find(value => Math.hypot(value.x - origin.x, value.z - origin.z) > 0.0001)
    const length = Math.hypot(point.x - origin.x, point.z - origin.z)
    axisX = (point.x - origin.x) / length
    axisZ = (point.z - origin.z) / length
  }
  const rounded = value => Math.round(value * 100_000) / 100_000
  return frame.map(value => ({
    x: rounded((value.x - origin.x) * axisX + (value.z - origin.z) * axisZ),
    y: rounded(value.y - origin.y),
    z: rounded(-(value.x - origin.x) * axisZ + (value.z - origin.z) * axisX),
    color: value.color, scale: rounded(value.scale), amount: value.amount,
    offsetX: value.offsetX, offsetY: value.offsetY, offsetZ: value.offsetZ,
    speed: value.speed, longDistance: value.longDistance, alwaysShow: value.alwaysShow
  }))
}

function verifyFrame(context, effect, frame, baselineMode) {
  context.expect(frame.length === effect.points, 'Particle frame sample count changed', {
    effectId: effect.id, expected: effect.points, actual: frame.length
  })
  for (const [index, value] of frame.entries()) {
    const knownBaselinePoint = baselineMode && effect.id === 'dragon_wing_effect' && index === 90
    context.expect(knownBaselinePoint
      ? [value.x, value.y, value.z].every(Number.isNaN) && Number.isFinite(value.scale)
      : [value.x, value.y, value.z, value.scale].every(Number.isFinite), 'Particle coordinates or scale are not finite, or the known baseline defect changed', {
      effectId: effect.id, sampleIndex: index, value,
      decoded: { x: String(value.x), y: String(value.y), z: String(value.z), scale: String(value.scale) }
    })
    context.expect(Math.abs(value.scale - effect.scale) < 0.00001 && value.amount === effect.amount,
      'Particle scale or per-sample count changed', { effectId: effect.id, value })
    context.expect(effect.colors.includes(value.color), 'Particle dust color changed', { effectId: effect.id, value })
    context.expect(value.offsetX === 0 && value.offsetY === 0 && value.offsetZ === 0 && value.speed === 0,
      'Fixed geometry gained random spread or velocity', { effectId: effect.id, value })
    context.expect(value.longDistance === true && value.alwaysShow === true, 'Particle delivery flags changed')
  }
  context.expect(new Set(frame.map(value => value.color)).size === effect.colors.length, 'An expected color is missing', { effectId: effect.id })
  if (effect.id === 'angel_ring_effect') {
    const center = frame.reduce((sum, value) => ({ x: sum.x + value.x / frame.length, z: sum.z + value.z / frame.length }), { x: 0, z: 0 })
    for (const value of frame) {
      context.expect(Math.abs(Math.hypot(value.x - center.x, value.z - center.z) - 0.16) < 0.00001,
        'Angel ring radius changed')
      context.expect(Math.abs(value.y - frame[0].y) < 0.00001, 'Angel ring is not planar')
    }
  } else if (effect.points === 120) {
    const origin = frame[0]
    for (let layer = 0; layer < 6; layer++) {
      for (const value of frame.slice(layer * 20, (layer + 1) * 20)) {
        context.expect(Math.abs(value.y - origin.y + layer * 0.04) < 0.00001, 'Umbrella layer height changed')
        context.expect(Math.abs(Math.hypot(value.x - origin.x, value.z - origin.z) - layer * 0.14) < 0.00001,
          'Umbrella layer radius changed')
      }
    }
  } else {
    const normalized = normalize(effect, frame).filter((value, index) => index !== 90)
    if (!baselineMode) context.expect(Math.abs(frame[90].x - frame[0].x) < 0.00001
      && Math.abs(frame[90].z - frame[0].z) < 0.00001
      && Math.abs(frame[90].y - frame[0].y + 0.15) < 0.00001,
    'Corrected DragonWing half-turn point must sit 0.15 below its origin', { actual: frame[90], origin: frame[0] })
    context.expect(normalized.every(value => Math.abs(value.z) < 0.00001), 'Dragon wings are no longer a vertical plane')
    context.expect(Math.max(...normalized.map(value => value.x)) > 0.5 && Math.min(...normalized.map(value => value.x)) < -0.5,
      'Dragon wing left/right span changed')
    context.expect(Math.max(...normalized.map(value => value.y)) - Math.min(...normalized.map(value => value.y)) > 0.35,
      'Dragon wing vertical span disappeared')
  }
  const geometry = normalize(effect, frame)
  if (effect.id === 'dragon_wing_effect') {
    delete geometry[90].x
    delete geometry[90].y
    delete geometry[90].z
  }
  return geometry
}

export default {
  name: 'bytecompanions-particle-geometry',
  description: 'Verify complete dust-particle frames for four fixed shapes through movement, rotation, teleport, and reload; record normalized geometry for artifact comparison.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(typeof directory === 'string' && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Particle geometry QA requires an isolated managed instance')
    const config = await readFile(path.join(directory, 'plugins', 'ByteCompanions', 'config.toml'), 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(config), 'Use autoReload=true')
    const artifactSha256 = createHash('sha256').update(await readFile(path.join(directory, 'plugins', 'ByteCompanions.jar'))).digest('hex')
    const baselineMode = artifactSha256 === baselineSha256
    const baselineFolia = baselineMode && bot.game.serverBrand === 'Folia'
    context.report.geometryComparison = { mode: baselineMode ? 'baseline-known-defect' : 'optimized-all-finite', artifactSha256,
      excludedCoordinates: [{ effectId: 'dragon_wing_effect', sampleIndex: 90, reason: 'Original 9110 half-turn emitted NaN; style and packet count remain compared' }],
      omittedStages: baselineFolia ? [{ stage: 'teleported', effects: effects.map(effect => effect.id),
        reason: '9110 Folia async teleport leaves the equipped companion behind; demonstrated in retained baseline-folia2612-r3 failure. Baseline geometry capture does not validate teleport support.' }] : [] }
    context.report.observations ??= []
    const watcher = watchFrames(bot)
    const fingerprints = new Map()
    const appearances = new Map()
    let spawnSequence = 0
    const appeared = entity => {
      if (entity.name === 'armor_stand') appearances.set(entity.id, ++spawnSequence)
    }
    bot.on('entitySpawn', appeared)
    let equipped = false
    let selected = false
    let companionId
    async function capture(effect, stage) {
      watcher.clear()
      await context.waitUntil(() => watcher.frames.length >= 3, { timeoutMs, label: `${effect.id}: three complete frames ${stage}` })
      const frames = watcher.frames.slice(0, 3)
      const geometry = frames.map(frame => verifyFrame(context, effect, frame, baselineMode))
      const hashes = geometry.map(frame => createHash('sha256').update(JSON.stringify(frame)).digest('hex'))
      context.expect(hashes.every(value => value === hashes[0]), 'Fixed geometry changes between consecutive frames', { effectId: effect.id, stage, hashes })
      if (fingerprints.has(effect.id)) context.expect(hashes[0] === fingerprints.get(effect.id),
        'Particle shape or style changed across lifecycle transitions', { effectId: effect.id, stage, expected: fingerprints.get(effect.id), actual: hashes[0] })
      else fingerprints.set(effect.id, hashes[0])
      context.report.observations.push({ kind: 'particle-geometry', effectId: effect.id, stage,
        frames: frames.length, packetsPerFrame: effect.points, particlesPerPacket: effect.amount,
        normalizedSha256: hashes[0], origin: { x: frames[0][0].x, y: frames[0][0].y, z: frames[0][0].z },
        halfTurn: effect.id === 'dragon_wing_effect' ? { sampleIndex: 90,
          status: baselineMode ? 'known-baseline-NaN' : 'corrected-finite',
          samples: frames.map(frame => ({ x: String(frame[90].x), y: String(frame[90].y), z: String(frame[90].z),
            relativeX: baselineMode ? null : frame[90].x - frame[0].x,
            relativeY: baselineMode ? null : frame[90].y - frame[0].y,
            relativeZ: baselineMode ? null : frame[90].z - frame[0].z })) } : undefined,
        normalizedGeometry: stage === 'equipped' ? geometry[0] : undefined })
    }
    async function findCompanion(afterSequence = 0) {
      let entity
      try {
        entity = await context.waitUntil(() => Object.values(bot.entities).find(value => value.name === 'armor_stand'
          && appearances.get(value.id) > afterSequence && value.position?.distanceTo(bot.entity.position) < 24),
        { timeoutMs, label: 'current companion body' })
      } catch (error) {
        context.expect(false, error.message, { afterSequence, spawnSequence, playerPosition: bot.entity.position,
          bodies: Object.values(bot.entities).filter(value => value.name === 'armor_stand').map(value => ({
            id: value.id, appearance: appearances.get(value.id), position: value.position })) })
      }
      companionId = entity.id
    }
    async function moveTo(x, z) {
      await context.command(`/bcqa move ${x} 100 ${z}`, /BCQA MOVE success=true/, timeoutMs)
      await context.waitUntil(() => Math.abs(bot.entity.position.x - x) < 0.1 && Math.abs(bot.entity.position.z - z) < 0.1,
        { timeoutMs, label: 'teleport acknowledged by the client' })
      await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, timeoutMs)
    }
    try {
      await context.step('prepare the controlled companion and packet observer', async () => {
        if (bot.game.gameMode !== 'creative') await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await moveTo(0, 0)
        await context.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
        equipped = true
        await findCompanion()
      })
      for (const [index, effect] of effects.entries()) {
        await context.step(`${effect.id}: equip and validate complete frames`, async () => {
          await context.command(`/companions particles equip bee ${effect.id}`, /Equipped particle effect:/, timeoutMs)
          selected = true
          await context.sleep(750)
          await capture(effect, 'equipped')
        })
        await context.step(`${effect.id}: move and resume after settling`, async () => {
          const before = bot.entity.position.clone()
          bot.setControlState('forward', true)
          try {
            await context.waitUntil(() => bot.entity.position.distanceTo(before) > 0.2,
              { timeoutMs: 3_000, label: 'real client movement' })
          } finally {
            bot.clearControlStates()
          }
          await context.sleep(1_000)
          await capture(effect, 'settled')
        })
        await context.step(`${effect.id}: rotate and preserve geometry`, async () => {
          await bot.look(Math.PI * (index + 1) / 2, 0, true)
          await context.sleep(500)
          await capture(effect, 'rotated')
        })
        if (!baselineFolia) await context.step(`${effect.id}: teleport and restore delivery`, async () => {
          const previous = spawnSequence
          await moveTo((index + 1) * 160, (index + 1) * 160)
          await findCompanion(previous)
          await context.sleep(750)
          await capture(effect, 'teleported')
        })
        await context.step(`${effect.id}: reload and preserve selected shape`, async () => {
          const previous = spawnSequence
          await reloadFiles(context)
          await findCompanion(previous)
          await context.sleep(750)
          await capture(effect, 'reloaded')
        })
      }
      await context.step('unequip particles and remove the companion', async () => {
        await context.command('/companions particles unequip bee', /Unequipped particle effect|unequipped|removed/i, timeoutMs)
        selected = false
        await context.command('/companions despawn bee', /Your companion has been unequipped\./, timeoutMs)
        equipped = false
        await context.waitUntil(() => !bot.entities[companionId], { timeoutMs, label: 'companion removed' })
        await context.sleep(500)
        watcher.clear()
        await context.sleep(750)
        context.expect(watcher.frames.length === 0, 'Particle frames continued after removal')
      })
    } finally {
      bot.clearControlStates()
      watcher.close()
      bot.removeListener('entitySpawn', appeared)
      if (selected) await context.command('/companions particles unequip bee', undefined, timeoutMs)
      if (equipped) await context.command('/companions despawn bee', undefined, timeoutMs)
    }
  }
}
