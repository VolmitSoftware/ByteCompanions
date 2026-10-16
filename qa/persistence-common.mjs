export const commandTimeoutMs = 8_000
export const restoreTimeoutMs = 15_000
export const effectId = 'angel_ring_effect'
export const companionAlreadyEquipped = /You already have (?:that|an exclusive) companion equipped\./i

function companionEntities(bot) {
  return Object.values(bot.entities).filter(entity =>
    entity.name === 'armor_stand' && entity.position && bot.entity?.position &&
    entity.position.distanceTo(bot.entity.position) < 24
  )
}

export function requirePersistencePlayer(context) {
  const { bot } = context
  context.expect(bot.username === 'BytePersist', 'Both persistence runs require --username BytePersist', {
    username: bot.username
  })
  context.expect(bot.entity?.position !== undefined && bot.health > 0, 'Persistence player did not spawn alive')
  context.report.observations ??= []
  context.report.observations.push({ kind: 'persistence-player', username: bot.username, uuid: bot.player?.uuid })
}

export function observeCompanion(context) {
  const { bot } = context
  let entityId
  let matchedPackets = 0
  let dustPackets = 0
  const countParticles = (packet, metadata) => {
    if (metadata.name !== 'world_particles' || packet.particle?.type !== 'dust') return
    dustPackets++
    const scale = packet.particle.data?.scale
    if (!Number.isFinite(scale) || Math.abs(scale - 0.3) > 0.00001) return
    const position = bot.entities[entityId]?.position
    if (!position) return
    const distance = Math.hypot(packet.x - position.x, packet.y - position.y, packet.z - position.z)
    if (distance <= 3) matchedPackets++
  }
  bot._client.on('packet', countParticles)

  return {
    entityIds() {
      return new Set(companionEntities(bot).map(entity => entity.id))
    },
    async find(label, previousIds = new Set()) {
      const entity = await context.waitUntil(
        () => companionEntities(bot).find(candidate => !previousIds.has(candidate.id)),
        { timeoutMs: restoreTimeoutMs, label }
      )
      context.expect(Number.isFinite(entity.id), 'Companion has no numeric protocol entity ID')
      entityId = entity.id
      context.report.observations.push({
        kind: 'companion-entity', label, entityId, position: { ...entity.position },
        nearbyArmorStands: companionEntities(bot).length
      })
      return entityId
    },
    async requireParticles(stage) {
      const before = matchedPackets
      await context.waitUntil(() => matchedPackets >= before + 3, {
        timeoutMs: restoreTimeoutMs, label: `${stage}: angel ring dust packets near companion ${entityId}`
      })
      context.expect(bot.entities[entityId] !== undefined, 'Particle source companion disappeared')
      context.report.observations.push({
        kind: 'particle-packets', stage, entityId, effectId,
        received: matchedPackets - before, totalMatched: matchedPackets, totalDust: dustPackets
      })
    },
    async requireRemoved() {
      await context.waitUntil(() => bot.entities[entityId] === undefined, {
        timeoutMs: commandTimeoutMs, label: `persisted companion ${entityId} removed through normal despawn`
      })
      context.report.observations.push({ kind: 'companion-removed', entityId })
    },
    close() {
      bot._client.removeListener('packet', countParticles)
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    }
  }
}
