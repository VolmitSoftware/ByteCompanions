import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles, writeAndReload } from './file-reload.mjs'
import { componentText, itemText } from './merged-features.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000
const contentSlots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()
const lore = item => plain(item?.components?.find(value => value.type === 'lore' || value.type === 'minecraft:lore')?.data
  ?? item?.nbt?.value?.display?.value?.Lore ?? [])
const savedName = 'Studio Honey QA'

export default {
  name: 'bytecompanions-companion-studio',
  description: 'Exercise private drafts, native previews, owner-only particles, passive bonuses, validation, save and managed reload.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Studio QA requires an isolated instance')
    const root = path.join(directory, 'plugins', 'ByteCompanions')
    const beePath = path.join(root, 'companions', 'bee.toml')
    const configPath = path.join(root, 'config.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Studio QA requires automatic file reloads')
    const originalBee = await readFile(beePath, 'utf8')
    const originals = new Map()
    for (const feature of ['attributes', 'buffs', 'effects']) {
      const file = path.join(root, 'features', feature, 'config.toml')
      originals.set(file, await readFile(file, 'utf8'))
    }
    context.report.observations ??= []
    const owner = observeCompanion(context)
    let peer
    let editId = 'bee'
    let peerDust = 0
    let previewId
    const peerSpawns = new Set()
    const peerMessages = []
    const peerPacket = (packet, metadata) => {
      if (metadata.name === 'spawn_entity' || metadata.name === 'named_entity_spawn') peerSpawns.add(packet.entityId)
      if (metadata.name === 'world_particles' && packet.particle?.type === 'dust'
        && Math.abs((packet.particle.data?.scale ?? 0) - 0.3) < 0.00001) peerDust++
    }
    const peerMessage = message => peerMessages.push(message)
    const command = (text, expected, timeout = timeoutMs) => context.command(text, expected, timeout)
    const permission = value => command(`/bcqa permission ${bot.player.uuid} bytecompanions.command.edit ${value}`,
      new RegExp(`node=bytecompanions.command.edit value=${value}`))
    async function ready() {
      await context.waitUntil(async () => (await command('/bcqa api inspect', /BCQA API/)).includes('BCQA API PASS'),
        { timeoutMs: 30_000, label: 'loaded companion player' })
    }
    async function screen(title) {
      return context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title).includes(title) ? bot.currentWindow : false,
        { timeoutMs, label: title })
    }
    async function item(slot, expected) {
      return context.waitUntil(() => {
        const value = bot.currentWindow?.slots[slot]
        return value && expected.test(plain(itemText(value))) ? value : false
      }, { timeoutMs, label: `slot ${slot}: ${expected}` })
    }
    async function home() {
      const initialTitle = plain(bot.currentWindow?.title)
      if (!bot.currentWindow || !/Companion Studio|Edit Setting|Edit Companion|Choose a Preset|Review Companion Changes/.test(initialTitle)) {
        await command(`/companions edit ${editId}`)
        await screen('Companion Studio')
      }
      for (let depth = 0; depth < 4; depth++) {
        await context.waitUntil(() => bot.currentWindow, { timeoutMs, label: 'studio navigation inventory' })
        const title = plain(bot.currentWindow.title)
        if (title.includes('Companion Studio')) break
        context.expect(/Edit Setting|Edit Companion|Choose a Preset|Review Companion Changes/.test(title),
          'Unexpected menu on the route to companion studio', { title })
        await item(46, /Back/)
        await bot.clickWindow(46, 0, 0)
        await context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title) !== title,
          { timeoutMs, label: 'studio back navigation completed' })
      }
      await screen('Companion Studio')
      await item(19, /Appearance/)
      await item(51, /Review & Save/)
      await context.waitUntil(() => {
        const heading = lore(bot.currentWindow?.slots[4])
        return heading.length > 0 && !heading.includes('Checking changes')
      }, { timeoutMs, label: 'studio draft ready for input' })
    }
    async function section(slot) {
      await home()
      await bot.clickWindow(slot, 0, 0)
      await screen(slot === 23 ? 'Choose a Preset' : 'Edit Companion')
    }
    async function find(label) {
      for (let page = 0; page < 12; page++) {
        await context.waitUntil(() => contentSlots.some(slot => bot.currentWindow?.slots[slot]), { timeoutMs, label: 'studio page contents' })
        const slot = contentSlots.find(slot => label.test(plain(itemText(bot.currentWindow?.slots[slot]))))
        if (slot !== undefined) return slot
        if (!/Next/.test(plain(itemText(bot.currentWindow?.slots[53])))) break
        const previous = contentSlots.map(slot => plain(itemText(bot.currentWindow.slots[slot]))).join('\n')
        await bot.clickWindow(53, 0, 0)
        await context.waitUntil(() => bot.currentWindow
          && contentSlots.map(slot => plain(itemText(bot.currentWindow.slots[slot]))).join('\n') !== previous,
        { timeoutMs, label: 'next studio page contents' })
      }
      throw new Error(`Studio field not found: ${label}; ${contentSlots.map(slot => plain(itemText(bot.currentWindow?.slots[slot]))).join(', ')}`)
    }
    async function field(category, label) {
      await section(category)
      await bot.clickWindow(await find(label), 0, 0)
      await screen('Edit Setting')
      await item(22, /Type a value|Toggle setting/)
    }
    async function textValue(category, label, value, rejected = false) {
      await field(category, label)
      const prompt = context.waitForMessage(/Enter .* in chat/, timeoutMs)
      await bot.clickWindow(22, 0, 0)
      await prompt
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'private input closed inventory' })
      const reply = rejected ? context.waitForMessage(/Change rejected:/, timeoutMs) : null
      bot.chat(value)
      if (reply) await reply
      await screen('Edit Companion')
      if (!rejected) {
        const slot = await find(label)
        context.expect(lore(bot.currentWindow.slots[slot]).includes(value), 'Draft did not show typed value', { value, lore: lore(bot.currentWindow.slots[slot]) })
      }
    }
    async function preset(category, name, rejected = false) {
      await section(category)
      if (category !== 23) {
        await item(47, /preset/)
        await bot.clickWindow(47, 0, 0)
        await screen('Choose a Preset')
      }
      const label = new RegExp(`^${name.replaceAll('_', ' ').replaceAll('-', ' ')}$`, 'i')
      const slot = await find(label)
      const reply = rejected ? context.waitForMessage(/Change rejected:/, timeoutMs) : null
      const previousWindow = bot.currentWindow.id
      await bot.clickWindow(slot, 0, 0)
      if (reply) await reply
      await context.waitUntil(() => bot.currentWindow && bot.currentWindow.id !== previousWindow,
        { timeoutMs, label: 'preset result replaced the old window' })
      await screen(rejected ? 'Companion Studio' : category === 23 ? 'Choose a Preset' : 'Edit Companion')
      if (category === 23 && !rejected) {
        const choice = await find(label)
        await context.waitUntil(() => lore(bot.currentWindow?.slots[choice]).includes('Included in this draft'),
          { timeoutMs, label: 'particle marked included' })
      }
    }
    async function discard() {
      await home()
      const reply = context.waitForMessage(/Draft discarded/, timeoutMs)
      await bot.clickWindow(49, 0, 0)
      await reply
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'discard closed studio' })
    }
    async function preview() {
      const previousIds = owner.entityIds()
      await home()
      await item(48, /Preview in the world/)
      const reply = context.waitForMessage(/Private preview started/, timeoutMs)
      await bot.clickWindow(48, 0, 0)
      await reply
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'world preview closed inventory' })
      previewId = await owner.find('private studio preview', previousIds)
      return previewId
    }
    async function assertPassive() {
      const state = await command('/bcqa api inspect', /BCQA API PASS/)
      context.expect(state.includes('count=0 spawned=0'), 'Preview changed owned companion selections', { state })
      for (const effect of ['Speed', 'Haste']) {
        context.expect(!bot.entity.effects[bot.registry.effectsByName[effect].id], `Preview granted ${effect}`)
      }
      await command('/attribute @s minecraft:armor get', /value of attribute .* is 0(?:\.0)?(?:\s|$)/i)
    }
    try {
      await context.step('enable survival bonuses and place an independent observer nearby', async () => {
        await ready()
        await command('/companions setup')
        await screen('Companion Setup')
        await item(24, /Survival/)
        await bot.clickWindow(24, 0, 0)
        await screen('Confirm Setup')
        await item(22, /Apply setup/)
        const saved = context.waitForMessage(/Companion setup saved/, 45_000)
        await bot.clickWindow(22, 0, 0)
        await saved
        await ready()
        await command('/companions despawn', /unequipped|do not have a companion equipped/)
        await command('/gamemode creative', /game mode|gamemode/i)
        await command('/bcqa move 0 100 0', /BCQA MOVE success=true/)
        await command('/bcqa platform', /BCQA PLATFORM blocks=49/)
        peer = await context.connectActor(bot.username === 'ByteStudioPeer' ? 'ByteStudioGuest' : 'ByteStudioPeer')
        peer.bot._client.on('packet', peerPacket)
        peer.bot.on('messagestr', peerMessage)
        await command(`/gamemode creative ${peer.bot.username}`, /game mode|gamemode/i)
        await command(`/tp ${peer.bot.username} 1 100 0`, /Teleported/)
        await peer.waitUntil(() => peer.bot.entity.position.distanceTo(bot.entity.position) < 3, { timeoutMs, label: 'peer adjacent to editor' })
      })
      await context.step('ordinary companion is visible to peer before testing preview privacy', async () => {
        await command('/companions spawn bee', /You have equipped/)
        const id = await owner.find('ordinary companion visibility control')
        await peer.waitUntil(() => peer.bot.entities[id], { timeoutMs, label: 'peer sees normal companion' })
        await command('/companions despawn', /unequipped/)
        await context.waitUntil(() => !bot.entities[id] && !peer.bot.entities[id], { timeoutMs, label: 'ordinary companion removed from both clients' })
        await context.waitUntil(() => !bot.entity.effects[bot.registry.effectsByName.Speed.id], { timeoutMs, label: 'ordinary speed removed' })
      })
      await context.step('editor requires its own administrator permission', async () => {
        await permission('false')
        await command('/companions edit bee', /do not have permission/)
        context.expect(bot.currentWindow === null, 'Denied editor opened an inventory')
        await permission('clear')
      })
      await context.step('typed names remain private and discard preserves exact content bytes', async () => {
        await textValue(19, /^display › item › name$/, 'Studio Cancelled QA')
        context.expect(await readFile(beePath, 'utf8') === originalBee, 'Draft edit wrote companion before save')
        await discard()
        context.expect(await readFile(beePath, 'utf8') === originalBee, 'Discard changed companion file')
        for (const [file, original] of originals) context.expect(await readFile(file, 'utf8') === original, 'Discard changed a bonus file', { file })
        context.expect(!peerMessages.some(message => message.includes('Studio Cancelled QA')), 'Private input was broadcast to peer')
      })
      await context.step('another menu command cancels private input without mutating its draft', async () => {
        await field(19, /^display › item › name$/)
        const captured = lore(bot.currentWindow.slots[4])
        const prompt = context.waitForMessage(/Enter .* in chat/, timeoutMs)
        await bot.clickWindow(22, 0, 0)
        await prompt
        await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'studio prompt awaiting input' })
        await command('/companions config')
        await screen('Configuration')
        const publicMessage = peer.waitForMessage(/STUDIO_PROMPT_HANDOFF/, timeoutMs)
        bot.chat('STUDIO_PROMPT_HANDOFF')
        await publicMessage
        context.expect(plain(bot.currentWindow?.title).includes('Configuration'), 'Old prompt replaced the configuration menu')
        await field(19, /^display › item › name$/)
        context.expect(lore(bot.currentWindow.slots[4]) === captured, 'New command failed to invalidate studio input')
        await discard()
        context.expect(await readFile(beePath, 'utf8') === originalBee, 'Prompt handoff changed companion file')
      })
      await context.step('invalid materials are rejected without changing the draft', async () => {
        await textValue(20, /^equipment › HEAD › material$/, 'NOT_A_MINECRAFT_MATERIAL', true)
        context.expect(await readFile(beePath, 'utf8') === originalBee, 'Invalid material changed disk')
      })
      await context.step('external edits reject stale saves without overwriting server content', async () => {
        const external = originalBee + '\n# Server edit during companion studio session.\n'
        const activeConfig = await readFile(configPath, 'utf8')
        await writeAndReload(context, configPath, activeConfig.replace(/autoReload\s*=\s*true/, 'autoReload = false'))
        try {
          await textValue(19, /^display › item › name$/, 'Studio Stale QA')
          await writeFile(beePath, external, 'utf8')
          await home()
          await bot.clickWindow(51, 0, 0)
          await screen('Review Companion Changes')
          await item(22, /Save for everyone/)
          const rejected = context.waitForMessage(/Change rejected:.*changed while the studio was open/, timeoutMs)
          await bot.clickWindow(22, 0, 0)
          await rejected
          context.expect(await readFile(beePath, 'utf8') === external, 'Stale save overwrote external content')
          await ready()
          await discard()
        } finally {
          await writeFile(beePath, originalBee, 'utf8')
          await writeAndReload(context, configPath, activeConfig)
        }
      })
      await context.step('appearance and equipment edits show their draft values', async () => {
        await textValue(19, /^display › item › name$/, savedName)
        await textValue(20, /^equipment › HEAD › material$/, 'GOLD_BLOCK')
      })
      await context.step('movement presets can be removed and restored', async () => {
        await preset(21, 'hover')
        await section(21)
        await item(47, /preset/)
        await bot.clickWindow(47, 0, 0)
        await screen('Choose a Preset')
        const absent = await find(/^hover$/i)
        context.expect(lore(bot.currentWindow.slots[absent]).includes('Not included'), 'Hover was not removed from draft')
        await preset(21, 'hover')
      })
      await context.step('particle and bonus presets update a draft while bonuses stay active globally', async () => {
        await preset(23, 'angel_ring_effect')
        await preset(30, 'HASTE')
        await preset(29, 'armor')
        await textValue(29, /^1 › amount$/, '10')
        context.expect(/^effects = true$/m.test(await readFile(configPath, 'utf8')), 'Survival potion feature is off')
        context.expect(/^attributes = true$/m.test(await readFile(configPath, 'utf8')), 'Survival attribute feature is off')
      })
      await context.step('general pickup XP obeys the current server capability', async () => {
        const spigot = /spigot|craftbukkit/i.test(bot.game.serverBrand ?? '')
        await preset(32, 'experience', spigot)
        if (!spigot) await preset(32, 'experience')
        context.report.observations.push({ kind: 'studio-general-xp', serverBrand: bot.game.serverBrand, rejected: spigot })
      })
      await context.step('native preview and its particles reach only the editing player', async () => {
        const dustBefore = peerDust
        await preview()
        await owner.requireParticles('private studio preview')
        await context.sleep(1000)
        context.expect(!peer.bot.entities[previewId] && !peerSpawns.has(previewId), 'Private preview entity reached peer', { previewId })
        context.expect(peerDust === dustBefore, 'Private preview particles reached peer', { dustBefore, peerDust })
        context.expect(bot.entities[previewId]?.equipment?.some(stack => stack?.name === 'gold_block'), 'Preview did not use draft equipment')
        await assertPassive()
        context.report.observations.push({ kind: 'studio-private-preview', previewId, peerSawEntity: peerSpawns.has(previewId), peerDust: peerDust - dustBefore })
        const retiredId = previewId
        let removed = false
        let respawned = false
        let dustPackets = 0
        let standSpawns = 0
        const lifecyclePacket = (packet, metadata) => {
          if (metadata.name === 'entity_destroy' && packet.entityIds.includes(retiredId)) removed = true
          if (metadata.name === 'respawn') respawned = true
          if (metadata.name === 'spawn_entity' && packet.type === bot.registry.entitiesByName.armor_stand.id) standSpawns++
          if (metadata.name === 'world_particles' && packet.particle?.type === 'dust'
            && Math.abs((packet.particle.data?.scale ?? 0) - 0.3) < 0.00001) dustPackets++
        }
        bot._client.on('packet', lifecyclePacket)
        try {
          const death = context.waitForEvent('death', () => true, timeoutMs)
          await command('/kill @s')
          await death
          await context.waitUntil(() => removed && respawned && bot.health > 0,
            { timeoutMs, label: 'death removed private preview and owner respawned' })
          await command('/bcqa move 0 100 0', /BCQA MOVE success=true/)
          await context.waitUntil(() => bot.entity.position.distanceTo(peer.bot.entity.position) < 3,
            { timeoutMs, label: 'respawned owner returned to observer' })
          await home()
          await item(48, /Preview in the world/)
          await context.sleep(500)
          const dustAfter = dustPackets
          await context.sleep(1000)
          context.expect(dustPackets === dustAfter && standSpawns === 0,
            'Private preview reappeared after death', { retiredId, dustAfter, dustPackets, standSpawns })
          context.expect(!bot.entities[retiredId], 'Retired preview remained in the owner entity list')
          await assertPassive()
          context.report.observations.push({ kind: 'studio-preview-death', retiredId, removed, respawned,
            dustAfterRespawn: dustPackets - dustAfter, spawnsAfterDeath: standSpawns })
        } finally {
          bot._client.removeListener('packet', lifecyclePacket)
        }
        await preview()
        await owner.requireParticles('private studio preview after respawn')
        context.expect(!peer.bot.entities[previewId] && !peerSpawns.has(previewId), 'Restarted private preview reached peer')
        context.expect(peerDust === dustBefore, 'Restarted private preview particles reached peer')
        await assertPassive()
      })
      await context.step('revoked save permission preserves server content', async () => {
        await home()
        await bot.clickWindow(51, 0, 0)
        await screen('Review Companion Changes')
        await item(22, /Save for everyone/)
        await permission('false')
        const denied = context.waitForMessage(/do not have permission/, timeoutMs)
        await bot.clickWindow(22, 0, 0)
        await denied
        context.expect(await readFile(beePath, 'utf8') === originalBee, 'Revoked save changed companion file')
        await permission('clear')
      })
      await context.step('save applies content, restores online players and removes private preview', async () => {
        await home()
        await bot.clickWindow(51, 0, 0)
        await screen('Review Companion Changes')
        await item(22, /Save for everyone/)
        const saved = context.waitForMessage(/Companion saved for the whole server/, 45_000)
        await bot.clickWindow(22, 0, 0)
        await saved
        await ready()
        await context.waitUntil(() => !bot.entities[previewId], { timeoutMs, label: 'preview removed during managed save' })
        const source = await readFile(beePath, 'utf8')
        context.expect(source.includes(savedName) && source.includes('GOLD_BLOCK') && source.includes('angel_ring_effect'), 'Save did not persist all companion edits')
        const effects = await readFile(path.join(root, 'features', 'effects', 'config.toml'), 'utf8')
        const attributes = await readFile(path.join(root, 'features', 'attributes', 'config.toml'), 'utf8')
        context.expect(effects.includes('HASTE') && /amount\s*=\s*10(?:\.0)?(?:\s|$)/m.test(attributes), 'Save did not persist bonus edits')
        context.expect(!peerMessages.some(message => message.includes(savedName)), 'Typed display name leaked into peer chat')
      })
      await context.step('saved companion equips publicly with its real bonuses', async () => {
        await command('/companions spawn bee', /You have equipped/)
        const id = await owner.find('saved studio companion')
        await peer.waitUntil(() => peer.bot.entities[id], { timeoutMs, label: 'saved companion visible to peer' })
        await context.waitUntil(() => bot.entity.effects[bot.registry.effectsByName.Haste.id], { timeoutMs, label: 'saved haste applies only after real equip' })
        await command('/attribute @s minecraft:armor get', /value of attribute .* is 10(?:\.0)?(?:\s|$)/i)
        await command('/companions despawn', /unequipped/)
        await context.waitUntil(() => !bot.entities[id] && !peer.bot.entities[id], { timeoutMs, label: 'saved companion removed' })
      })
      await context.step('managed reload retains saved name and equipment in the editor', async () => {
        await reloadFiles(context)
        await field(19, /^display › item › name$/)
        context.expect(lore(bot.currentWindow.slots[4]).includes(savedName), 'Reload lost saved name')
        await field(20, /^equipment › HEAD › material$/)
        context.expect(lore(bot.currentWindow.slots[4]).includes('GOLD_BLOCK'), 'Reload lost saved head equipment')
        await discard()
      })
      await context.step('duplicate creates a separate companion and preserves the original', async () => {
        const source = await readFile(beePath, 'utf8')
        await home()
        await item(38, /Copy as a new companion/)
        const prompt = context.waitForMessage(/Enter a new companion ID/, timeoutMs)
        await bot.clickWindow(38, 0, 0)
        await prompt
        await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'copy ID input' })
        bot.chat('studio_copy')
        await screen('Companion Studio')
        await item(4, /studio_copy/)
        await context.waitUntil(() => lore(bot.currentWindow.slots[4]).includes('Unsaved draft'), { timeoutMs, label: 'copy draft validated' })
        editId = 'studio_copy'
        await textValue(19, /^display › item › name$/, 'Studio Copy QA')
        await home()
        await bot.clickWindow(51, 0, 0)
        await screen('Review Companion Changes')
        await item(22, /Save for everyone/)
        const saved = context.waitForMessage(/Companion saved for the whole server/, 45_000)
        await bot.clickWindow(22, 0, 0)
        await saved
        await ready()
        const copy = await readFile(path.join(root, 'companions', 'studio_copy.toml'), 'utf8')
        context.expect(copy.includes('Studio Copy QA') && copy.includes('GOLD_BLOCK') && copy.includes('angel_ring_effect'), 'Copy lost companion fields')
        for (const feature of ['attributes', 'effects']) {
          context.expect(/\[\[companions\.studio_copy\]\]/.test(await readFile(path.join(root, 'features', feature, 'config.toml'), 'utf8')), 'Copy lost bonus rows', { feature })
        }
        context.expect(await readFile(beePath, 'utf8') === source, 'Copy save changed original companion')
      })
      await context.step('reset restores the saved copy and discard keeps both companions intact', async () => {
        const copyPath = path.join(root, 'companions', 'studio_copy.toml')
        const originalCopy = await readFile(copyPath, 'utf8')
        await textValue(19, /^display › item › name$/, 'Temporary Copy Name')
        await home()
        await item(40, /Reset draft/)
        await bot.clickWindow(40, 0, 0)
        await screen('Companion Studio')
        await context.waitUntil(() => lore(bot.currentWindow?.slots[4]).includes('No unsaved changes'), { timeoutMs, label: 'copy reset finished' })
        await field(19, /^display › item › name$/)
        context.expect(lore(bot.currentWindow.slots[4]).includes('Studio Copy QA'), 'Copy reset did not restore saved name')
        await discard()
        context.expect(await readFile(copyPath, 'utf8') === originalCopy, 'Reset/discard wrote copy content')
        await writeFile(path.join(directory, '.qa-companion-studio.json'), JSON.stringify({ name: savedName, head: 'GOLD_BLOCK',
          source: await readFile(beePath, 'utf8'), copySource: originalCopy }, null, 2))
      })
    } finally {
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      owner.close()
      peer?.bot._client.removeListener('packet', peerPacket)
      peer?.bot.removeListener('messagestr', peerMessage)
      await permission('clear')
    }
  }
}
