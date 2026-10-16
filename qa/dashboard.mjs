import { readFile, writeFile, unlink } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { componentText, itemText } from './merged-features.mjs'

const timeoutMs = 12_000
const titles = { collection: 'Companion Collection', dashboard: 'Companion Dashboard', particles: 'Companion Particles', bonuses: 'Companion Bonuses' }
const contentSlots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()

function lore(item) {
  return plain(item?.components?.find(value => value.type === 'lore' || value.type === 'minecraft:lore')?.data
    ?? item?.nbt?.value?.display?.value?.Lore ?? [])
}

function interact(bot, entityId, hand = 'HAND', attack = false) {
  if (bot.supportFeature('attackUsesOwnPacket')) {
    if (attack) bot._client.write('attack', { entityId })
    else bot._client.write('use_entity', { target: entityId, hand: hand === 'OFF_HAND' ? 'off_hand' : 'main_hand',
      location: { x: 0, y: 0, z: 0 }, sneaking: false })
  } else bot._client.write('use_entity', { target: entityId, mouse: attack ? 1 : 0, hand: hand === 'OFF_HAND' ? 1 : 0, sneaking: false })
}

export default {
  name: 'bytecompanions-dashboard',
  description: 'Verify collection, dashboard, particle and bonus navigation, real permission changes, owner-only interactions, and stale inventory closure across reload.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(typeof directory === 'string' && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Dashboard QA requires an isolated managed instance')
    const configPath = path.join(directory, 'plugins', 'ByteCompanions', 'config.toml')
    const original = await readFile(configPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original), 'Dashboard QA requires autoReload=true')
    context.report.observations ??= []
    const permissions = new Set()
    const generatedDefinitions = []
    const speedId = bot.registry.effectsByName.Speed.id
    const preparedConfig = original.replace(/dispatchInteractEvent\s*=\s*(true|false)/, 'dispatchInteractEvent = false')
    let peer
    let configured = false
    let equipped = false
    let entityId
    let dust = 0
    let latestClick
    let staleClick
    const originalWrite = bot._client.write
    bot._client.write = function (name, value) {
      if (name === 'window_click') latestClick = { name, value }
      return originalWrite.call(this, name, value)
    }
    const particles = (packet, metadata) => {
      if (metadata.name === 'world_particles' && packet.particle?.type === 'dust'
        && Math.abs(packet.particle.data?.scale - 0.3) < 0.00001) dust++
    }
    bot._client.on('packet', particles)
    const reload = () => reloadFiles(context)
    function playerUuid(actor) {
      const target = actor.player?.uuid
      context.expect(typeof target === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(target),
        'Permission check requires the connected player UUID', { username: actor.username, target })
      return target
    }
    async function permission(node, value, target = playerUuid(bot)) {
      const reply = await context.command(`/bcqa permission ${target} ${node} ${value}`, /BCQA PERMISSION/, timeoutMs)
      context.expect(reply.includes(`player=${target} node=${node} value=${value}`)
        && (value === 'clear' || reply.includes(`effective=${value}`)), 'Permission attachment did not take effect', { reply })
      const key = `${target} ${node}`
      if (value === 'clear') permissions.delete(key)
      else permissions.add(key)
    }
    async function close(actor = context, serverConfirmed = true) {
      const window = actor.bot.currentWindow
      if (window) {
        if (serverConfirmed && window.inventoryStart === 54
          && Object.values(titles).some(title => plain(window.title).includes(title))) {
          await actor.bot.clickWindow(49, 0, 0)
        } else actor.bot.closeWindow(window)
      }
      await actor.waitUntil(() => actor.bot.currentWindow === null, { timeoutMs, label: 'inventory closed' })
    }
    async function screen(kind) {
      const window = await context.waitUntil(() => {
        const current = bot.currentWindow
        return current && plain(current.title).includes(titles[kind]) ? current : false
      }, { timeoutMs, label: titles[kind] })
      context.expect(window.inventoryStart === 54, 'Dashboard screens must have six rows', { title: plain(window.title), slots: window.inventoryStart })
      return window
    }
    async function open(command, kind) {
      await close()
      const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
      await context.command(command)
      await opened
      return screen(kind)
    }
    async function navigate(slot, kind, button = 0) {
      const previous = bot.currentWindow
      const opened = context.waitForEvent('windowOpen', value => value !== previous, timeoutMs)
      await bot.clickWindow(slot, button, 0)
      await opened
      return screen(kind)
    }
    async function item(slot, expression) {
      return context.waitUntil(() => {
        const value = bot.currentWindow?.slots[slot]
        return value && expression.test(plain(itemText(value)) + ' ' + lore(value)) ? value : false
      }, { timeoutMs, label: `slot ${slot} matches ${expression}` })
    }
    async function content(expression) {
      return context.waitUntil(() => {
        const slot = contentSlots.find(value => expression.test(plain(itemText(bot.currentWindow?.slots[value]))))
        return slot === undefined ? false : { slot }
      }, { timeoutMs, label: `content item ${expression}` })
    }
    async function base() {
      const response = await context.command('/bcqa state', /BCQA STATE/, timeoutMs)
      return Number(response.match(/base=(-?\d+)/)[1])
    }
    async function body() {
      entityId = await base()
      context.expect(entityId >= 0, 'No selected bee in public API state')
      await context.waitUntil(() => bot.entities[entityId], { timeoutMs, label: 'equipped bee visible to real client' })
    }
    async function expectNoWindow(action, actors = [context]) {
      for (const actor of actors) await close(actor)
      let opens = 0
      const opened = () => opens++
      for (const actor of actors) actor.bot.on('windowOpen', opened)
      try {
        await action()
        await context.sleep(600)
        context.expect(opens === 0 && actors.every(actor => actor.bot.currentWindow === null), 'Filtered interaction opened an inventory', { opens })
      } finally {
        for (const actor of actors) actor.bot.removeListener('windowOpen', opened)
      }
    }
    try {
      await context.step('prepare real owner and keep external interaction dispatch disabled', async () => {
        context.expect(/dispatchInteractEvent\s*=/.test(original), 'Missing external interaction setting')
        configured = true
        await writeFile(configPath, preparedConfig, 'utf8')
        await reload()
        if (bot.game.gameMode !== 'creative') await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await context.command('/bcqa move 0 100 0', /BCQA MOVE success=true/, timeoutMs)
        await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, timeoutMs)
        await context.command('/companions despawn', /unequipped|do not have a companion equipped/i, timeoutMs)
      })
      await context.step('collection pagination reaches all generated definitions and returns to first page', async () => {
        const source = await readFile(path.join(directory, 'plugins', 'ByteCompanions', 'companions', 'bee.toml'), 'utf8')
        for (let index = 1; index <= 30; index++) {
          const suffix = String(index).padStart(2, '0')
          const destination = path.join(directory, 'plugins', 'ByteCompanions', 'companions', `zzqa${suffix}.toml`)
          context.expect(source.includes('Honey Nest'), 'Stock bee definition is missing its expected name')
          await writeFile(destination, source.replace('Honey Nest', `QA Collection ${suffix}`), { encoding: 'utf8', flag: 'wx' })
          generatedDefinitions.push(destination)
        }
        await reload()
        await open('/companions menu', 'collection')
        await item(53, /next/i)
        await item(4, /Page 1 of 2/i)
        context.expect(contentSlots.filter(slot => bot.currentWindow.slots[slot]).length === 28, 'First collection page was not full')
        await bot.clickWindow(53, 0, 0)
        await content(/QA Collection 30/)
        await item(45, /previous/i)
        await item(4, /Page 2 of 2/i)
        const last = await content(/QA Collection 30/)
        await navigate(last.slot, 'dashboard')
        await item(13, /QA Collection 30/)
        context.expect(await base() === -1, 'Pagination item click equipped a companion')
        await navigate(46, 'collection')
        await content(/QA Collection 30/)
        await item(4, /Page 2 of 2/i)
        await bot.clickWindow(45, 0, 0)
        await content(/Honey Nest/i)
        await item(4, /Page 1 of 2/i)
        await close()
        for (const destination of generatedDefinitions) await unlink(destination)
        generatedDefinitions.length = 0
        await reload()
      })
      await context.step('collection click opens a dashboard without equipping', async () => {
        await open('/companions menu', 'collection')
        const bee = await content(/Honey Nest/i)
        await navigate(bee.slot, 'dashboard')
        await item(13, /Honey Nest/i)
        context.expect(await base() === -1, 'Collection click equipped the companion')
      })
      await context.step('selection permission shows locked equip control', async () => {
        await permission('bytecompanions.companion.bee', 'false')
        await open('/companions menu bee', 'dashboard')
        await item(22, /locked|permission|unavailable/i)
        await bot.clickWindow(22, 0, 0)
        await context.sleep(300)
        context.expect(await base() === -1, 'Locked companion was equipped')
        await permission('bytecompanions.companion.bee', 'clear')
      })
      await context.step('equip click rechecks permission changed after opening', async () => {
        await open('/companions menu bee', 'dashboard')
        await item(22, /equip|summon/i)
        await permission('bytecompanions.command.spawn', 'false')
        await bot.clickWindow(22, 0, 0)
        await context.sleep(300)
        context.expect(await base() === -1, 'Stale equip control bypassed changed permission')
        await permission('bytecompanions.command.spawn', 'clear')
      })
      await context.step('equip button creates the companion and implicit menu selects its dashboard', async () => {
        await open('/companions menu bee', 'dashboard')
        const reply = context.waitForMessage(/You have equipped bee\./, timeoutMs)
        await bot.clickWindow(22, 0, 0)
        staleClick = latestClick
        await reply
        equipped = true
        await body()
        await open('/companions menu', 'dashboard')
        await item(22, /remove|despawn|unequip/i)
      })
      await context.step('particle picker equips visible particles and marks selected entry', async () => {
        await navigate(20, 'particles')
        const angel = await content(/angel.*ring/i)
        const before = dust
        const reply = context.waitForMessage(/Equipped particle effect:/, timeoutMs)
        await bot.clickWindow(angel.slot, 0, 0)
        await reply
        await context.waitUntil(() => dust > before, { timeoutMs, label: 'selected angel ring particle packets' })
        await screen('particles')
        await item(angel.slot, /equipped/i)
        context.report.observations.push({ kind: 'dashboard-particles', selected: 'angel_ring_effect', received: dust - before })
      })
      await context.step('particle removal permission is checked on stale control', async () => {
        await permission('bytecompanions.command.particles.unequip', 'false')
        const before = dust
        await bot.clickWindow(48, 0, 0)
        await context.waitUntil(() => dust > before, { timeoutMs, label: 'denied particle removal retains delivery' })
        await permission('bytecompanions.command.particles.unequip', 'clear')
      })
      await context.step('particle and bonus back buttons return to selected dashboard', async () => {
        await navigate(46, 'dashboard')
        await context.waitUntil(() => bot.entity.effects[speedId]?.amplifier === 0,
          { timeoutMs, label: 'default Bee applies Speed I to the real player' })
        await navigate(24, 'bonuses')
        const speed = await content(/Potion:.*Speed/i)
        const speedItem = await item(speed.slot, /Current potion level: 1/i)
        const details = lore(speedItem)
        for (const expected of [/Active/, /Configured potion level: 1/, /Companion potion level: 1/,
          /Current potion level: 1/, /Contributing companions: bee/]) {
          context.expect(expected.test(details), 'Bee bonus description differs from its applied effect', { expected: String(expected), details })
        }
        context.report.observations.push({ kind: 'bonus-content', items: contentSlots.flatMap(slot => {
          const value = bot.currentWindow?.slots[slot]
          return value ? [{ slot, name: plain(itemText(value)), lore: lore(value) }] : []
        }) })
        context.expect(await base() === entityId, 'Viewing bonuses changed the equipped companion')
        await navigate(46, 'dashboard')
        await navigate(46, 'collection')
        const bee = await content(/Honey Nest/i)
        await navigate(bee.slot, 'dashboard', 1)
      })
      await context.step('own main-hand interaction opens dashboard with external dispatch disabled', async () => {
        await close()
        const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
        interact(bot, entityId)
        await opened
        await screen('dashboard')
        await item(13, /Honey Nest/i)
      })
      for (const [hand, attack] of [['OFF_HAND', false], ['HAND', true]]) {
        await context.step(`own ${hand} attack=${attack} interaction does not open a dashboard`, async () => {
          await expectNoWindow(() => interact(bot, entityId, hand, attack))
          context.expect(await base() === entityId, 'Filtered interaction changed equipped companion')
        })
      }
      await context.step('visitor interaction does not open or control the owner dashboard', async () => {
        peer = await context.connectActor(bot.username === 'ByteMenuPeer' ? 'ByteMenuGuest' : 'ByteMenuPeer')
        await permission('bytecompanions.command.menu', 'true', playerUuid(peer.bot))
        await context.command(`/gamemode creative ${peer.bot.username}`, /game mode|gamemode/i, timeoutMs)
        await context.command(`/tp ${peer.bot.username} 1 100 0`, /Teleported/, timeoutMs)
        await peer.waitUntil(() => peer.bot.entities[entityId], { timeoutMs, label: 'visitor receives owner companion' })
        await expectNoWindow(() => interact(peer.bot, entityId), [context, peer])
        context.expect(await base() === entityId, 'Visitor changed owner companion')
      })
      await context.step('menu permission blocks command and own-companion interaction', async () => {
        await permission('bytecompanions.command.menu', 'false')
        await context.command('/companions menu bee', /You do not have permission/, timeoutMs)
        await expectNoWindow(() => interact(bot, entityId))
        await permission('bytecompanions.command.menu', 'clear')
      })
      await context.step('reload closes old inventory and rejects its captured click', async () => {
        await open('/companions menu bee', 'dashboard')
        const previous = entityId
        await reload()
        await context.waitUntil(() => bot.currentWindow === null && !bot.entities[previous], { timeoutMs, label: 'old dashboard and companion closed on reload' })
        await body()
        context.expect(staleClick?.name === 'window_click', 'No real prior inventory click was captured')
        bot._client.write(staleClick.name, staleClick.value)
        await context.sleep(500)
        context.expect(bot.currentWindow === null && await base() === entityId, 'Stale inventory packet mutated the new runtime')
        await open('/companions menu', 'dashboard')
      })
      await context.step('remove particles and companion through dashboard controls', async () => {
        await navigate(20, 'particles')
        const removed = context.waitForMessage(/Unequipped particle effect|unequipped|removed/i, timeoutMs)
        await bot.clickWindow(48, 0, 0)
        await removed
        await context.sleep(400)
        const before = dust
        await context.sleep(600)
        context.expect(dust === before, 'Particle removal did not stop delivery')
        await navigate(46, 'dashboard')
        const previous = entityId
        const reply = context.waitForMessage(/Your companion has been unequipped\./, timeoutMs)
        await bot.clickWindow(22, 0, 0)
        await reply
        equipped = false
        await context.waitUntil(() => !bot.entities[previous], { timeoutMs, label: 'dashboard removal reaches client' })
        context.expect(await base() === -1, 'Dashboard removal left a selected companion')
        await close()
      })
      await context.step('disabled feature controls explain their state and remove active effects', async () => {
        await context.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
        equipped = true
        await body()
        await context.command('/companions particles equip bee angel_ring_effect', /Equipped particle effect:/, timeoutMs)
        await context.waitUntil(() => bot.entity.effects[speedId]?.amplifier === 0, { timeoutMs, label: 'Bee effect active before disabling' })
        const features = preparedConfig.match(/^\[features\]\r?\n[^[]*/m)
        context.expect(features !== null, 'Missing typed feature switches')
        await writeFile(configPath, preparedConfig.replace(features[0], features[0].replace(/=\s*true/g, '= false')), 'utf8')
        await reload()
        await body()
        await context.waitUntil(() => !bot.entity.effects[speedId], { timeoutMs, label: 'disabled potion service releases Speed' })
        await context.sleep(400)
        const before = dust
        await context.sleep(600)
        context.expect(dust === before, 'Disabled particles continued to emit')
        await open('/companions menu bee', 'dashboard')
        await item(20, /disabled/i)
        await navigate(24, 'bonuses')
        for (const name of [/^Attributes$/, /^Potion Effects$/, /^Gameplay Bonuses$/]) {
          const entry = await content(name)
          await item(entry.slot, /Disabled by this server/i)
        }
        await close()
        await writeFile(configPath, preparedConfig, 'utf8')
        await reload()
      })
    } finally {
      bot._client.write = originalWrite
      bot._client.removeListener('packet', particles)
      const failures = []
      for (const actor of [context, peer].filter(Boolean)) {
        try { await close(actor, false) } catch (error) { failures.push(error) }
      }
      for (const key of [...permissions]) {
        const [target, node] = key.split(' ')
        try { await permission(node, 'clear', target) } catch (error) { failures.push(error) }
      }
      if (equipped) {
        try { await context.command('/companions despawn bee', /unequipped|do not have a companion equipped/i, timeoutMs) }
        catch (error) { failures.push(error) }
      }
      if (configured) {
        for (const destination of generatedDefinitions) {
          try { await unlink(destination) } catch (error) { failures.push(error) }
        }
        await writeFile(configPath, original, 'utf8')
        try { await reload() } catch (error) { failures.push(error) }
      }
      if (failures.length) throw new AggregateError(failures, 'Dashboard scenario cleanup failed')
    }
  }
}
