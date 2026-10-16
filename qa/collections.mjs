import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { componentText, itemText } from './merged-features.mjs'

export const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()
export const slots = [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43]
const lore = item => plain(item?.components?.find(value => value.type === 'lore' || value.type === 'minecraft:lore')?.data
  ?? item?.nbt?.value?.display?.value?.Lore ?? [])
const timeoutMs = 15_000

export async function equipment(context, entityId, expected, label) {
  try {
    await context.waitUntil(() => context.bot.entities[entityId]?.equipment?.some(item => item?.name === expected),
      { timeoutMs, label })
  } finally {
    context.report.observations ??= []
    const entity = context.bot.entities[entityId]
    context.report.observations.push({ kind: 'equipment', entityId, expected, present: !!entity,
      items: entity?.equipment?.map(item => item?.name ?? null) ?? [] })
  }
}

export function controls(context) {
  const { bot } = context
  const command = (text, expected) => context.command(text, expected, timeoutMs)
  const screen = title => context.waitUntil(() => bot.currentWindow && plain(bot.currentWindow.title).includes(title) && bot.currentWindow.slots[49] ? bot.currentWindow : false,
    { timeoutMs, label: title })
  const open = async (text, title) => {
    if (bot.currentWindow && plain(bot.currentWindow.title).includes(title)) return screen(title)
    let opened = false
    const received = () => { opened = true }
    bot._client.on('open_window', received)
    try {
      await command(text)
      await context.waitUntil(() => opened, { timeoutMs, label: `server opens ${title}` })
      return await screen(title)
    } finally {
      bot._client.removeListener('open_window', received)
    }
  }
  const click = async (slot, title) => {
    await context.waitUntil(() => bot.currentWindow?.slots[slot], { timeoutMs, label: `menu action ${slot}` })
    context.report.observations ??= []
    context.report.observations.push({ kind: 'menu-click', title: plain(bot.currentWindow.title), slot,
      item: plain(itemText(bot.currentWindow.slots[slot])) })
    await bot.clickWindow(slot, 1, 0)
    if (title) await screen(title)
  }
  const state = async () => JSON.parse((await command('/bcqa collection', /BCQA COLLECTION /)).split('BCQA COLLECTION ')[1])
  const dashboard = async id => {
    if (bot.currentWindow && plain(bot.currentWindow.title).includes('Companion Dashboard')) {
      await open('/companions collection', 'Companion Collection')
    }
    await open(`/companions menu ${id}`, 'Companion Dashboard')
  }
  const saved = async action => {
    const response = context.waitForMessage(/Collection saved\./, timeoutMs)
    await Promise.all([response, Promise.resolve().then(action)])
  }
  const personalize = async id => { await dashboard(id); await click(42, 'Personalize Companion') }
  const prompt = async (slot, value) => {
    const response = context.waitForMessage(/Enter .* in chat/, timeoutMs)
    await Promise.all([response, click(slot)])
    await context.waitUntil(() => !bot.currentWindow, { timeoutMs, label: 'private prompt' })
    await saved(() => bot.chat(value))
  }
  const outfit = async () => { await open('/companions outfits', 'Saved Outfits'); await click(10, 'Review Outfit') }
  const permission = async (node, value) => command(`/bcqa permission ${bot.player.uuid} ${node} ${value}`, new RegExp(`node=${node} value=${value}`))
  return { command, screen, open, click, state, dashboard, saved, personalize, prompt, outfit, permission }
}

export default {
  name: 'bytecompanions-collections',
  description: 'Verify collection filters, durable cosmetics/outfits, rejected transitions, interactions and private trials.',
  async run(context) {
    const { bot } = context
    const ui = controls(context)
    context.report.observations ??= []
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')), 'Requires isolated instance')
    await ui.command('/gamemode creative', /game mode|gamemode/i)
    await ui.command('/bcqa move 0 100 0', /BCQA MOVE success=true/)
    await ui.command('/bcqa platform', /BCQA PLATFORM blocks=49/)
    const peer = await context.connectActor('ByteCollPeer')
    await ui.command(`/gamemode creative ${peer.bot.username}`, /game mode|gamemode/i)
    await ui.command(`/tp ${peer.bot.username} 1 100 0`, /Teleported/)
    await peer.waitUntil(() => peer.bot.entity.position.distanceTo(bot.entity.position) < 3, { timeoutMs, label: 'observer adjacent' })
    const peerSpawns = new Set()
    const peerMessages = []
    const packet = (data, meta) => { if (meta.name === 'spawn_entity') peerSpawns.add(data.entityId) }
    const message = text => peerMessages.push(String(text))
    peer.bot._client.on('packet', packet)
    peer.bot.on('messagestr', message)
    try {
      await context.step('collection loads and filtered favorites can be saved', async () => {
        await context.waitUntil(async () => (await ui.state()).ready, { timeoutMs, label: 'collection loaded' })
        await ui.dashboard('bee')
        await ui.saved(() => ui.click(37))
        context.expect((await ui.state()).profile.favorites.includes('bee'), 'Favorite did not persist')
        await ui.open('/companions collection', 'Companion Collection')
        await ui.click(1)
        await context.waitUntil(() => slots.filter(slot => /Honey Nest/.test(plain(itemText(bot.currentWindow?.slots[slot])))).length === 1
          && slots.filter(slot => bot.currentWindow?.slots[slot]).length === 1, { timeoutMs, label: 'favorites filter' })
        await ui.permission('bytecompanions.companion.bee', 'false')
        await ui.click(2)
        await context.waitUntil(() => /Access: owned/.test(plain(itemText(bot.currentWindow?.slots[2])))
          && !slots.some(slot => /Honey Nest/.test(plain(itemText(bot.currentWindow?.slots[slot])))), { timeoutMs, label: 'owned filter and contents' })
        context.expect(!slots.some(slot => /Honey Nest/.test(plain(itemText(bot.currentWindow?.slots[slot])))), 'Locked favorite passed owned filter')
        await ui.click(2)
        await context.waitUntil(() => /Honey Nest/.test(plain(itemText(bot.currentWindow?.slots[10]))), { timeoutMs, label: 'locked favorites' })
        await ui.permission('bytecompanions.companion.bee', 'clear')
        await ui.click(1)
        await context.waitUntil(() => lore(bot.currentWindow?.slots[1]).includes('Access: all'), { timeoutMs, label: 'favorites filter reset' })
        await ui.click(2)
        await context.waitUntil(() => /Access: all/.test(plain(itemText(bot.currentWindow?.slots[2])))
          && slots.filter(slot => bot.currentWindow?.slots[slot]).length === 8, { timeoutMs, label: 'all companions restored' })
        for (const category of ['charms', 'miniatures', 'nature']) {
          await ui.click(6)
          await context.waitUntil(() => {
            const names = slots.map(slot => plain(itemText(bot.currentWindow?.slots[slot]))).filter(Boolean)
            const contentMatches = category === 'nature' ? names.length === 2 && names.some(name => name.includes('Honey Nest'))
              && names.some(name => name.includes('Moss Forager')) : names.length === (category === 'charms' ? 1 : 5)
            return lore(bot.currentWindow?.slots[6]) === category && contentMatches
          }, { timeoutMs, label: `category ${category} and contents` })
        }
        const natureNames = slots.map(slot => plain(itemText(bot.currentWindow?.slots[slot]))).filter(Boolean)
        context.expect(natureNames.length === 2 && natureNames.some(name => name.includes('Honey Nest'))
          && natureNames.some(name => name.includes('Moss Forager')), 'Nature category did not contain exactly bee and mushroom', { natureNames })
        await ui.click(6)
        await context.waitUntil(() => lore(bot.currentWindow?.slots[6]).includes('All categories')
          && slots.filter(slot => bot.currentWindow?.slots[slot]).length === 8, { timeoutMs, label: 'category filter reset' })
      })
      await context.step('owned companion remains visible to a nearby observer', async () => {
        await ui.command('/companions spawn bee', /You have equipped/)
        const companion = (await ui.state()).companions.bee
        await context.waitUntil(() => bot.entities[companion.entityId], { timeoutMs, label: 'owner companion' })
        await context.waitUntil(() => peerSpawns.has(companion.entityId), { timeoutMs, label: 'observer visibility positive control' })
        context.report.observations.push({ kind: 'public-visibility', entityId: companion.entityId, observerSaw: true })
      })
      await context.step('private name, approved variant and formation apply', async () => {
        await ui.personalize('bee'); await ui.prompt(20, 'Honey Buddy'); await ui.screen('Personalize Companion')
        await ui.click(22, 'Companion Variants'); await ui.saved(() => ui.click(10)); await ui.screen('Personalize Companion')
        await ui.click(24, 'Companion Formation'); await ui.saved(() => ui.click(21)); await ui.screen('Personalize Companion')
        const state = await ui.state()
        context.expect(state.profile.styles.bee.nickname === 'Honey Buddy' && state.profile.styles.bee.variantId === 'moss'
          && state.profile.styles.bee.formationId === 'line', 'Personal choices missing', state)
        context.expect(!peerMessages.some(text => text.includes('Honey Buddy')), 'Private name input was broadcast')
        await equipment(context, state.companions.bee.entityId, 'jack_o_lantern', 'approved variant equipment')
        await context.waitUntil(() => peer.bot.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'jack_o_lantern'),
          { timeoutMs, label: 'observer approved variant equipment' })
        await context.waitUntil(() => [bot, peer.bot].every(viewer => viewer.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'torch')),
          { timeoutMs, label: 'variant hand equipment reaches owner and observer' })
        await ui.click(22, 'Companion Variants'); await ui.saved(() => ui.click(48)); await ui.screen('Personalize Companion')
        context.expect(!(await ui.state()).profile.styles.bee.variantId, 'Default variant did not clear the saved override')
        await equipment(context, state.companions.bee.entityId, 'bee_nest', 'default equipment restored live')
        await context.waitUntil(() => peer.bot.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'bee_nest'),
          { timeoutMs, label: 'observer default equipment restored' })
        await context.waitUntil(() => [bot, peer.bot].every(viewer => !viewer.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'torch')),
          { timeoutMs, label: 'default clears variant hand equipment for both viewers' })
        context.report.observations.push({ kind: 'variant-slot-cleared', entityId: state.companions.bee.entityId,
          ownerItems: bot.entities[state.companions.bee.entityId].equipment.map(item => item?.name ?? null),
          observerItems: peer.bot.entities[state.companions.bee.entityId].equipment.map(item => item?.name ?? null) })
        await ui.click(22, 'Companion Variants'); await ui.saved(() => ui.click(10)); await ui.screen('Personalize Companion')
        context.expect((await ui.state()).profile.styles.bee.variantId === 'moss', 'Variant was not selected again')
        await equipment(context, state.companions.bee.entityId, 'jack_o_lantern', 'variant equipment reapplied live')
        await context.waitUntil(() => peer.bot.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'jack_o_lantern'),
          { timeoutMs, label: 'observer variant equipment reapplied' })
        await context.waitUntil(() => [bot, peer.bot].every(viewer => viewer.entities[state.companions.bee.entityId]?.equipment?.some(item => item?.name === 'torch')),
          { timeoutMs, label: 'variant hand equipment reapplied for both viewers' })
      })
      await context.step('two companions save as one outfit with their particle choices', async () => {
        await ui.command('/companions spawn warrior', /You have equipped/)
        await ui.command('/companions particles equip bee angel_ring_effect', /equipped|selected/i)
        await ui.personalize('bee'); await ui.prompt(20, 'Honey Buddy'); await ui.screen('Personalize Companion')
        await ui.open('/companions outfits', 'Saved Outfits')
        await ui.prompt(48, 'adventure'); await ui.screen('Saved Outfits')
        const outfit = (await ui.state()).profile.outfits.adventure
        context.expect(outfit.companionIds.length === 2 && outfit.styles.bee.particleId === 'angel_ring_effect', 'Outfit snapshot missing choices', outfit)
      })
      await context.step('locked outfits reject before changing current companions', async () => {
        await ui.command('/companions despawn', /unequipped/)
        await ui.command('/companions spawn copper', /You have equipped/)
        await ui.permission('bytecompanions.companion.warrior', 'false')
        await ui.outfit()
        const rejected = context.waitForMessage(/Could not apply this change:/, timeoutMs)
        await Promise.all([rejected, ui.click(48)])
        context.expect(Object.keys((await ui.state()).companions).join(',') === 'copper', 'Rejected outfit changed current companions')
        await ui.command('/attribute @s minecraft:armor get', /value of attribute .* is 5(?:\.0)?(?:\s|$)/i)
        await ui.permission('bytecompanions.companion.warrior', 'clear')
      })
      for (const event of ['equip', 'unequip']) {
        await context.step(`cancelled ${event} leaves the entire outfit unchanged`, async () => {
          await ui.command(`/bcqa collectioncancel ${event}`, /BCQA COLLECTION_CANCEL/)
          await ui.outfit()
          const rejected = context.waitForMessage(/Could not apply this change:/, timeoutMs)
          await Promise.all([rejected, ui.click(48)])
          context.expect(Object.keys((await ui.state()).companions).join(',') === 'copper', 'Cancelled outfit partially applied')
          await ui.command('/attribute @s minecraft:armor get', /value of attribute .* is 5(?:\.0)?(?:\s|$)/i)
          await ui.command('/bcqa collectioncancel clear', /BCQA COLLECTION_CANCEL/)
        })
      }
      await context.step('outfit restores companion names, variants and particles together', async () => {
        await ui.outfit(); await ui.saved(() => ui.click(48))
        const state = await ui.state()
        context.expect(Object.keys(state.companions).sort().join(',') === 'bee,warrior', 'Wrong restored companions', state)
        context.expect(state.profile.styles.bee.nickname === 'Honey Buddy' && state.profile.styles.bee.particleId === 'angel_ring_effect', 'Cosmetics not restored', state)
        await ui.command('/attribute @s minecraft:armor get', /value of attribute .* is 0(?:\.0)?(?:\s|$)/i)
      })
      await context.step('remove all particles saves both companions in one operation', async () => {
        await ui.command('/companions particles equip warrior angel_ring_effect', /equipped|selected/i)
        await ui.command('/companions particles unequip', /unequipped/i)
        const state = await ui.state()
        context.expect(!state.profile.styles.bee.particleId && !state.profile.styles.warrior.particleId, 'Bulk particle removal lost an update', state)
        await ui.outfit(); await ui.saved(() => ui.click(48))
      })
      await context.step('stay holds position and call resumes following', async () => {
        await ui.command('/companions interact stay bee', /Your companion responded/)
        const held = (await ui.state()).companions.bee
        await ui.command(`/bcqa move ${bot.entity.position.x + 2} ${bot.entity.position.y} ${bot.entity.position.z}`, /BCQA MOVE/)
        await new Promise(resolve => setTimeout(resolve, 2600))
        const stayed = (await ui.state()).companions.bee
        context.expect(stayed.mode === 'STAY' && Math.hypot(stayed.x-held.x,stayed.y-held.y,stayed.z-held.z) < 0.15, 'Stay did not hold position', { held, stayed })
        context.report.observations.push({ kind: 'stay', held, stayed, owner: { ...bot.entity.position } })
        await ui.command('/companions interact call bee', /Your companion responded/)
        context.expect((await ui.state()).companions.bee.mode === 'FOLLOW', 'Call did not resume follow')
      })
      await context.step('sit, wave and pet use bounded interaction feedback', async () => {
        await new Promise(resolve => setTimeout(resolve, 2100))
        await ui.command('/companions interact sit warrior', /Your companion responded/)
        context.expect((await ui.state()).companions.warrior.mode === 'SIT', 'Sit mode missing')
        await ui.command('/companions interact wave warrior', /Wait a moment/)
        await new Promise(resolve => setTimeout(resolve, 2100))
        await ui.command('/companions interact wave warrior', /Your companion responded/)
        await new Promise(resolve => setTimeout(resolve, 2100))
        await ui.command('/companions interact pet warrior', /Your companion responded/)
      })
      await context.step('equipped companion dashboard cannot create a duplicate private trial', async () => {
        await ui.dashboard('bee')
        context.expect(!/Try privately/.test(plain(itemText(bot.currentWindow.slots[16]))), 'Equipped companion still offers a trial')
        const before = await ui.state()
        const ids = new Set(Object.keys(bot.entities).map(Number))
        await bot.clickWindow(16, 0, 0)
        await context.sleep(800)
        context.expect(!Object.values(bot.entities).some(entity => entity.name === 'armor_stand' && !ids.has(entity.id)),
          'Equipped companion gained a duplicate trial entity')
        context.expect((await ui.state()).companions.bee.entityId === before.companions.bee.entityId, 'Trial guard replaced the real companion')
      })
      for (const locked of [true, false]) await context.step(`${locked ? 'locked' : 'owned unequipped'} trial is private, expires and never changes selections`, async () => {
        await ui.permission('bytecompanions.companion.lantern', locked ? 'false' : 'true')
        const before = await ui.state()
        const ids = new Set(Object.keys(bot.entities).map(Number))
        await ui.dashboard('lantern')
        const trial = context.waitForMessage(/Private trial started/, timeoutMs)
        await Promise.all([trial, ui.click(16)])
        const entity = await context.waitUntil(() => Object.values(bot.entities).find(value => value.name === 'armor_stand' && !ids.has(value.id)),
          { timeoutMs, label: 'private trial entity' })
        await new Promise(resolve => setTimeout(resolve, 800))
        context.expect(!peerSpawns.has(entity.id), 'Trial leaked to observer')
        context.report.observations.push({ kind: 'private-trial', entityId: entity.id, observerSaw: peerSpawns.has(entity.id), selectedIds: Object.keys(before.companions) })
        await ui.command('/attribute @s minecraft:armor get', /value of attribute .* is 0(?:\.0)?(?:\s|$)/i)
        context.expect(Object.keys((await ui.state()).companions).sort().join(',') === Object.keys(before.companions).sort().join(','), 'Trial changed equipped choices')
        context.expect(bot.experience.level === 0 && bot.experience.points === 0, 'Trial granted experience')
        await context.waitUntil(() => !bot.entities[entity.id], { timeoutMs: 8000, label: 'trial expiry' })
        context.report.observations.push({ kind: 'trial-expired', entityId: entity.id, present: !!bot.entities[entity.id] })
        await ui.permission('bytecompanions.companion.lantern', 'clear')
      })
      await context.step('managed reload restores durable choices and resets temporary behavior', async () => {
        await reloadFiles(context)
        await context.waitUntil(async () => (await ui.state()).companions.bee && (await ui.state()).companions.warrior,
          { timeoutMs: 30_000, label: 'collection reload restore' })
        const state = await ui.state()
        context.expect(state.profile.favorites.includes('bee') && state.profile.outfits.adventure
          && state.profile.styles.bee.nickname === 'Honey Buddy' && state.companions.warrior.mode === 'FOLLOW', 'Reload lost saved choices', state)
        await writeFile(path.join(directory, '.qa-collections.json'), JSON.stringify({ profile: state.profile }))
        context.report.observations.push({ kind: 'reload-profile', state })
      })
    } finally {
      peer.bot._client.removeListener('packet', packet)
      peer.bot.removeListener('messagestr', message)
    }
  }
}
