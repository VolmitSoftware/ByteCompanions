import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { controls } from './collections.mjs'

const timeoutMs = 15_000

function traffic(actor) {
  let particles = 0
  let bundle
  const frames = []
  const listener = (packet, metadata) => {
    if (metadata.name === 'bundle_delimiter') {
      if (bundle === undefined) bundle = 0
      else {
        if (bundle > 0) frames.push(bundle)
        bundle = undefined
      }
    }
    if (metadata.name === 'world_particles' && packet.particle?.type === 'dust'
      && Math.abs((packet.particle.data?.scale ?? 0) - 0.3) < 0.00001) {
      particles++
      if (bundle !== undefined) bundle++
    }
  }
  actor.bot._client.on('packet', listener)
  return { get particles() { return particles }, frames, close() { actor.bot._client.removeListener('packet', listener) } }
}

export default {
  name: 'bytecompanions-server-polish',
  description: 'Verify two-viewer privacy, persistent audience choices, complete-frame particle reduction, world denial and bonus recovery.',
  async run(context) {
    const { bot } = context
    const root = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(context.server.directory, '.server-source'), 'utf8')), 'Requires isolated instance')
    const originals = new Map()
    const configPath = path.join(root, 'config.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Requires automatic file reloads')
    const ui = controls(context)
    const ownerTraffic = traffic(context)
    let peer, peerUi, peerTraffic, companionId, overlayId, ownerWorld
    const observe = async actor => JSON.parse((await actor.command('/bcqa polish', /BCQA POLISH /, timeoutMs)).split('BCQA POLISH ')[1])
    const state = async actor => JSON.parse((await actor.command('/bcqa collection', /BCQA COLLECTION /, timeoutMs)).split('BCQA COLLECTION ')[1])
    const reload = () => reloadFiles(context)
    const edit = async (relative, value) => {
      const filename = path.join(root, relative)
      if (!originals.has(filename)) originals.set(filename, await readFile(filename, 'utf8'))
      await writeFile(filename, value, 'utf8')
    }
    const settings = async (actor, key, value) => {
      if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
      try {
        await actor.command(`/companions settings ${key} ${value}`, /Collection saved\./, timeoutMs)
      } catch (failure) {
        context.report.observations ??= []
        context.report.observations.push({ kind: 'settings-command-timeout', actor: actor.bot.username, key, value, state: await state(actor) })
        throw failure
      }
    }
    const visible = async (actor, label) => actor.waitUntil(() => actor.bot.entities[companionId]
      && (overlayId === undefined || actor.bot.entities[overlayId]), { timeoutMs, label })
    const quiet = async (actor, counter, label, absent = true) => {
      if (absent) await actor.waitUntil(() => !actor.bot.entities[companionId], { timeoutMs, label })
      await context.sleep(350)
      const before = counter.particles
      await context.sleep(1000)
      context.expect(counter.particles === before, `${label}: particles continued`, { before, after: counter.particles })
      if (absent) context.expect(!actor.bot.entities[companionId], `${label}: companion returned`)
      if (absent && overlayId !== undefined) context.expect(!actor.bot.entities[overlayId], `${label}: overlay leaked`)
    }
    const emitting = async (actor, counter, label) => {
      await visible(actor, label)
      const before = counter.particles
      await actor.waitUntil(() => counter.particles >= before + 25, { timeoutMs, label: `${label} particles` })
    }
    const currentId = async () => {
      const current = await state(context)
      context.expect(current.companions.bee, 'Saved bee selection is absent', current)
      if (companionId !== current.companions.bee.entityId) overlayId = undefined
      companionId = current.companions.bee.entityId
      return companionId
    }
    const config = (hidden = [], bonuses = [], limit = 'FULL') => {
      let text = originalConfig
        .replace(/hiddenWorlds\s*=\s*\[[^\]]*\]/, `hiddenWorlds = ${JSON.stringify(hidden)}`)
        .replace(/bonusDisabledWorlds\s*=\s*\[[^\]]*\]/, `bonusDisabledWorlds = ${JSON.stringify(bonuses)}`)
        .replace(/maximumParticleIntensity\s*=\s*"[A-Z]+"/, `maximumParticleIntensity = "${limit}"`)
      context.expect(text.includes(`hiddenWorlds = ${JSON.stringify(hidden)}`), 'Missing hiddenWorlds setting')
      context.expect(text.includes(`bonusDisabledWorlds = ${JSON.stringify(bonuses)}`), 'Missing bonusDisabledWorlds setting')
      context.expect(text.includes(`maximumParticleIntensity = "${limit}"`), 'Missing maximumParticleIntensity setting')
      return text
    }
    try {
      await context.step('prepare two managed clients and deterministic companion bonuses', async () => {
        await ui.command('/gamemode creative', /game mode|gamemode/i)
        await ui.command('/bcqa move 0 100 0', /BCQA MOVE success=true/)
        await ui.command('/bcqa platform', /BCQA PLATFORM blocks=49/)
        ownerWorld = (await observe(context)).world
        peer = await context.connectActor('BytePolishPeer')
        peerUi = controls(peer)
        peerTraffic = traffic(peer)
        await ui.command(`/op ${peer.bot.username}`, /operator|already/i)
        await ui.command(`/gamemode creative ${peer.bot.username}`, /game mode|gamemode/i)
        await peerUi.command('/bcqa move 1 100 0', /BCQA MOVE success=true/)
        await context.waitUntil(async () => (await state(context)).ready && (await state(peer)).ready, { timeoutMs, label: 'both profiles loaded' })
        await settings(context, 'visibility', 'all')
        await settings(peer, 'visibility', 'all')
        await settings(context, 'particles', 'full')
        await settings(peer, 'particles', 'full')
        await edit('features/attributes/config.toml', `[[companions.bee]]
attribute = "armor"
amount = 10
operation = "ADD_NUMBER"
`)
        await edit('features/effects/config.toml', `[[companions.bee]]
effect = "SPEED"
amplifier = 0
priority = 10
stack-mode = "HIGHEST"
`)
        await edit('features/buffs/config.toml', `[companions.bee]
default-priority = 10

[companions.bee.vanilla.experience]
enabled = false

[companions.bee.vanilla.experience-block]
enabled = true
operation = "ADD_PERCENT"
value = 50
cooldown = 0
`)
        await edit('config.toml', config())
        await reload()
        await ui.command('/companions spawn bee', /equipped bee|already have/)
        await ui.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        await currentId()
        await Promise.all([emitting(context, ownerTraffic, 'owner baseline'), emitting(peer, peerTraffic, 'peer baseline')])
        const beforeOverlay = new Set(Object.keys(peer.bot.entities).map(Number))
        await ui.command('/bcqa privacy overlay add', /BCQA PRIVACY overlay add/)
        const overlay = await peer.waitUntil(() => Object.values(peer.bot.entities)
          .find(entity => entity.name === 'armor_stand' && !beforeOverlay.has(entity.id)), { timeoutMs, label: 'public overlay added' })
        overlayId = overlay.id
        await visible(context, 'owner overlay')
        context.report.observations ??= []
        context.report.observations.push({ world: ownerWorld, companionId, overlayId, peer: peer.bot.username })
      })
      await context.step('Bukkit hidePlayer removes body and particles only from the hidden viewer', async () => {
        await peerUi.command(`/bcqa privacy hide ${bot.player.uuid}`, /BCQA PRIVACY hide/)
        await quiet(peer, peerTraffic, 'Bukkit hidden')
        await emitting(context, ownerTraffic, 'owner remains visible')
      })
      await context.step('Bukkit showPlayer restores the same companion and particles', async () => {
        await peerUi.command(`/bcqa privacy show ${bot.player.uuid}`, /BCQA PRIVACY show/)
        await emitting(peer, peerTraffic, 'Bukkit shown')
      })
      await context.step('true vanished metadata hides others while owner sees their own companion', async () => {
        await ui.command('/bcqa privacy vanish true', /BCQA PRIVACY vanish true/)
        await quiet(peer, peerTraffic, 'vanished owner')
        await emitting(context, ownerTraffic, 'vanished self')
        await ui.command('/bcqa privacy vanish false', /BCQA PRIVACY vanish false/)
        await emitting(peer, peerTraffic, 'vanish cleared')
      })
      await context.step('OWN and NONE preferences hide another player while preserving saved selection', async () => {
        await settings(peer, 'visibility', 'own')
        await quiet(peer, peerTraffic, 'OWN preference')
        await settings(peer, 'visibility', 'none')
        await quiet(peer, peerTraffic, 'NONE preference')
        context.expect((await state(context)).companions.bee.entityId === companionId, 'Viewer preferences changed owner selection')
        await settings(peer, 'visibility', 'all')
        await emitting(peer, peerTraffic, 'ALL preference')
      })
      await context.step('particle OFF preserves body and stops all received frames', async () => {
        await settings(peer, 'particles', 'off')
        await quiet(peer, peerTraffic, 'particle OFF', false)
        await visible(peer, 'body remains with particles off')
      })
      await context.step('REDUCED receives half the complete Angel Ring frames', async () => {
        await settings(peer, 'particles', 'reduced')
        await emitting(peer, peerTraffic, 'reduced particle startup')
        const before = { owner: ownerTraffic.frames.length, peer: peerTraffic.frames.length }
        await context.sleep(4000)
        const counts = { owner: ownerTraffic.frames.length - before.owner, peer: peerTraffic.frames.length - before.peer }
        const ratio = counts.peer / counts.owner
        context.expect(counts.owner > 10 && ratio > 0.35 && ratio < 0.65, 'Reduced frame delivery was not approximately half', counts)
        context.expect(peerTraffic.frames.slice(before.peer).every(count => count === 25), 'Reduced delivery cut an Angel Ring frame', counts)
        context.report.observations.push({ kind: 'reduced-frame-count', ...counts, ratio })
      })
      await context.step('settings GUI changes visibility and particles with persisted profile state', async () => {
        await peerUi.permission('bytecompanions.command.menu', 'false')
        await peerUi.open('/companions settings', 'Viewer settings')
        await peerUi.saved(() => peerUi.click(24))
        context.expect((await state(peer)).profile.viewerPreferences.visibility === 'NONE', 'GUI visibility was not saved')
        await peerUi.saved(() => peerUi.click(42))
        const profile = (await state(peer)).profile.viewerPreferences
        context.expect(profile.visibility === 'NONE' && profile.particleIntensity === 'OFF', 'GUI particle preference was not saved', profile)
        if (peer.bot.currentWindow) peer.bot.closeWindow(peer.bot.currentWindow)
        await peerUi.permission('bytecompanions.command.menu', 'clear')
      })
      await context.step('settings permission and invalid values preserve the stored profile', async () => {
        const before = (await state(peer)).profile.viewerPreferences
        await peerUi.command('/companions settings visibility nonsense', /visibility all\|own\|none/)
        await peerUi.permission('bytecompanions.command.settings', 'false')
        await peerUi.command('/companions settings visibility all', /permission/i)
        context.expect(JSON.stringify((await state(peer)).profile.viewerPreferences) === JSON.stringify(before), 'Rejected setting modified preferences')
        await peerUi.permission('bytecompanions.command.settings', 'clear')
      })
      await context.step('reload retains viewer preferences and selected companion', async () => {
        await reload()
        await currentId()
        const profile = (await state(peer)).profile.viewerPreferences
        context.expect(profile.visibility === 'NONE' && profile.particleIntensity === 'OFF', 'Reload lost preferences', profile)
        await quiet(peer, peerTraffic, 'reload retained hidden preference')
        await settings(peer, 'visibility', 'all')
        await settings(peer, 'particles', 'full')
        await emitting(peer, peerTraffic, 'preferences restored')
      })
      await context.step('server OFF cap suppresses FULL requests without hiding companion bodies', async () => {
        await edit('config.toml', config([], [], 'OFF'))
        await reload()
        await currentId()
        await visible(peer, 'server cap body')
        await Promise.all([quiet(context, ownerTraffic, 'owner server OFF', false), quiet(peer, peerTraffic, 'peer server OFF', false)])
        context.expect((await state(peer)).profile.viewerPreferences.particleIntensity === 'FULL', 'Server cap changed saved preference')
      })
      await context.step('world visual denial preserves selection and active bonuses', async () => {
        await edit('config.toml', config([ownerWorld]))
        await reload()
        await currentId()
        await Promise.all([quiet(context, ownerTraffic, 'hidden world owner'), quiet(peer, peerTraffic, 'hidden world peer')])
        const observed = await observe(context)
        context.expect(observed.armor === 10 && observed.effects.includes('minecraft:speed:0'), 'Visual restriction disabled bonuses', observed)
        await ui.command('/bcqa xp block', /base=10 result=15/)
      })
      await context.step('world bonus denial removes attributes effects and reward multiplier without deselection', async () => {
        await edit('config.toml', config([], [ownerWorld]))
        await reload()
        await currentId()
        await emitting(peer, peerTraffic, 'visuals allowed with bonuses denied')
        await context.waitUntil(async () => {
          const observed = await observe(context)
          return observed.armor === 0 && !observed.effects.some(effect => effect.startsWith('minecraft:speed:'))
        }, { timeoutMs, label: 'bonus removal' })
        await ui.command('/bcqa xp block', /base=10 result=10/)
      })
      await context.step('clearing world restriction restores the selected companion bonuses', async () => {
        await edit('config.toml', config())
        await reload()
        await currentId()
        await emitting(peer, peerTraffic, 'world restrictions cleared')
        await context.waitUntil(async () => {
          const observed = await observe(context)
          return observed.armor === 10 && observed.effects.includes('minecraft:speed:0')
        }, { timeoutMs, label: 'bonuses restored' })
        await ui.command('/bcqa xp block', /base=10 result=15/)
      })
      await context.step('file refresh restores viewer data and selected companions for connected players', async () => {
        await edit('config.toml', config())
        await reload()
        await context.waitUntil(async () => (await state(peer)).ready, { timeoutMs, label: 'preferences reload with player data' })
        const reloaded = await state(peer)
        context.expect(reloaded.profile.viewerPreferences.visibility === 'ALL'
          && reloaded.profile.viewerPreferences.particleIntensity === 'FULL', 'Preferences unavailable after reload', reloaded)
        context.expect(reloaded.coreReady, 'Online viewer core data did not reload', reloaded)
        await currentId()
        await emitting(peer, peerTraffic, 'saved companion remains available after file refresh')
        await peerUi.open('/companions settings', 'Viewer settings')
        await peerUi.saved(() => peerUi.click(42))
        context.expect((await state(peer)).profile.viewerPreferences.particleIntensity === 'OFF', 'Settings mutation failed after reload')
        if (peer.bot.currentWindow) peer.bot.closeWindow(peer.bot.currentWindow)
        await settings(peer, 'particles', 'full')
        const arriving = await context.connectActor('ByteReloadGuest')
        const arrivingUi = controls(arriving)
        await ui.command(`/op ${arriving.bot.username}`, /operator|already/i)
        await ui.command(`/gamemode creative ${arriving.bot.username}`, /game mode|gamemode/i)
        await arrivingUi.command('/bcqa move 2 100 0', /BCQA MOVE success=true/)
        await context.waitUntil(async () => (await state(arriving)).ready, { timeoutMs, label: 'new player profile loaded' })
        await arrivingUi.command('/companions spawn bee', /equipped bee|already have/)
        await arrivingUi.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect|already equipped/)
        companionId = (await state(arriving)).companions.bee.entityId
        overlayId = undefined
        await emitting(peer, peerTraffic, 'existing viewer sees newly joined owner after reload')
        await arrivingUi.command('/companions despawn bee', /unequipped/)
        await edit('config.toml', config())
        await reload()
        await currentId()
        await emitting(peer, peerTraffic, 'selected companion restored after another file refresh')
      })
      await context.step('save hidden preferences for full process restart', async () => {
        await settings(context, 'visibility', 'none')
        await settings(context, 'particles', 'off')
        const checkpoint = await state(context)
        context.expect(checkpoint.profile.viewerPreferences.visibility === 'NONE'
          && checkpoint.profile.viewerPreferences.particleIntensity === 'OFF', 'Restart preferences not stored')
        await writeFile(path.join(context.server.directory, '.qa-server-polish.json'), JSON.stringify(checkpoint), 'utf8')
      })
    } finally {
      ownerTraffic.close()
      peerTraffic?.close()
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      if (peer?.bot.currentWindow) peer.bot.closeWindow(peer.bot.currentWindow)
      if (peer?.bot._client.state === 'play') {
        await peerUi.permission('bytecompanions.command.menu', 'clear')
        await peerUi.permission('bytecompanions.command.settings', 'clear')
      }
      for (const [filename, content] of originals) await writeFile(filename, content, 'utf8')
      if (bot._client.state === 'play') await reload()
    }
  }
}
