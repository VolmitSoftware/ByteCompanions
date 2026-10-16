import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles, rejectFileReload, runtimeReport, writeAndReload } from './file-reload.mjs'

const commandTimeoutMs = 8_000
const effectId = 'angel_ring_effect'
const particleDistanceTolerance = 3
const effectSlots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
const featureNames = ['attributes', 'buffs', 'decorations', 'effects', 'particles']

export function componentText(component) {
  if (component == null) return ''
  if (typeof component === 'string') {
    const trimmed = component.trim()
    if (/^[\[{\"]/.test(trimmed)) {
      try {
        return componentText(JSON.parse(trimmed))
      } catch {}
    }
    return component
  }
  if (Array.isArray(component)) return component.map(componentText).join('')
  if (typeof component !== 'object') return ''
  if (['compound', 'list', 'string'].includes(component.type) && 'value' in component) {
    return componentText(component.value)
  }
  return componentText(component.text) + componentText(component.extra)
}

export function itemText(item) {
  const customName = item?.customName ?? item?.components?.find(component =>
    component.type === 'custom_name' || component.type === 'minecraft:custom_name')?.data ??
    item?.nbt?.value?.display?.value?.Name
  return componentText(customName ?? item?.displayName)
}

async function openMenu(context, command, title) {
  const opened = context.waitForEvent('windowOpen', () => true, commandTimeoutMs)
  await context.command(command)
  const [window] = await opened
  const actualTitle = componentText(window.title)
  context.expect(actualTitle.includes(title), 'Unexpected menu title', { title: actualTitle })
  return window
}

async function assertFeatureVisibility(context, enabled) {
  const messages = []
  const listener = message => messages.push(message)
  context.bot.on('messagestr', listener)
  try {
    await context.command('/companions help', /⇀ version(?: |$)/, commandTimeoutMs)
    for (const feature of featureNames) {
      const present = messages.some(message => new RegExp(`⇀ ${feature}(?: |$)`).test(message))
      context.expect(present === (feature === 'particles' && enabled), `Unexpected help visibility for ${feature}`, { messages })
    }
  } finally {
    context.bot.removeListener('messagestr', listener)
  }
  const completions = await context.bot.tabComplete('/companions ', false, false, commandTimeoutMs)
  const suggestions = completions.map(value => typeof value === 'string' ? value : value.match)
  for (const feature of featureNames) {
    context.expect(suggestions.includes(feature) === (feature === 'particles' && enabled),
      `Unexpected tab completion for ${feature}`, { suggestions })
  }
  const runtime = await runtimeReport(context)
  for (const feature of featureNames) context.expect(runtime.text.includes(`Feature ${feature}: ${enabled}`),
    `Runtime service ${feature} does not match enabled=${enabled}`, runtime)
}

function companionEntities(bot) {
  return Object.values(bot.entities).filter(entity =>
    entity.name === 'armor_stand' &&
    entity.position &&
    entity.position.distanceTo(bot.entity.position) < 24
  )
}

async function findNewCompanion(context, previousIds, label) {
  const entity = await context.waitUntil(
    () => companionEntities(context.bot).find(candidate => !previousIds.has(candidate.id)),
    { timeoutMs: commandTimeoutMs, label }
  )
  context.expect(Number.isFinite(entity.id), 'Companion entity has no numeric protocol ID')
  context.report.observations ??= []
  context.report.observations.push({ kind: 'companion-entity', label, entityId: entity.id, position: { ...entity.position } })
  return entity.id
}

async function despawnCompanion(context, entityId, typeId = 'bee') {
  const response = await context.command(`/companions despawn ${typeId}`, /Your companion has been unequipped\./i, commandTimeoutMs)
  context.expect(response !== undefined, 'Companion despawn was not acknowledged')
  await context.waitUntil(() => context.bot.entities[entityId] === undefined,
    { timeoutMs: commandTimeoutMs, label: `companion ${entityId} removed from the client` })
}

export default {
  name: 'bytecompanions-merged-features',
  description: 'Verify merged commands, companion entities, particle traffic/menu, and reloads with all five feature services and online-player restoration enabled.',
  async run(context) {
    const { bot } = context
    context.expect(typeof context.server.directory === 'string', 'This scenario requires a managed isolated server directory')
    const configPath = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'config.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Enable automatic file reloads in the isolated QA configuration')
    const featureOriginals = new Map()
    let configModified = false
    let particlePackets = 0
    let companionId
    const countParticles = (packet, metadata) => {
      if (metadata.name !== 'world_particles' || packet.particle?.type !== 'dust') return
      if (Math.abs(packet.particle.data?.scale - 0.3) > 0.00001) return
      const position = bot.entities[companionId]?.position
      if (!position || !Number.isFinite(packet.particle.data?.scale)) return
      const distance = Math.hypot(packet.x - position.x, packet.y - position.y, packet.z - position.z)
      if (distance <= particleDistanceTolerance) particlePackets++
    }
    bot._client.on('packet', countParticles)

    try {
      await context.step('root command and explicit help are available', async () => {
        context.expect(bot.entity?.position !== undefined && bot.health > 0, 'Bot did not spawn alive')
        await context.command('/companions', /⇀ version(?: |$)/, commandTimeoutMs)
        await context.command('/companions help', /⇀ version(?: |$)/, commandTimeoutMs)
        await context.command('/companions particles help', /Companion particles/, commandTimeoutMs)
      })

      await context.step('spawn the included bee companion', async () => {
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        await context.command('/companions spawn bee', /You have equipped bee\./i, commandTimeoutMs)
        companionId = await findNewCompanion(context, previousIds, 'initial bee spawn')
      })

      await context.step('equip particles and receive particle packets', async () => {
        const before = particlePackets
        await context.command(`/companions particles equip bee ${effectId}`,
          /Equipped particle effect: angel_ring_effect\./i, commandTimeoutMs)
        await context.waitUntil(() => particlePackets > before,
          { timeoutMs: commandTimeoutMs, label: 'particle packets after equipping the angel ring' })
        context.report.observations.push({ kind: 'particle-packets', stage: 'equipped', received: particlePackets - before })
      })

      await context.step('open the particle menu and receive its inventory', async () => {
        const companionWindow = await openMenu(context, '/companions menu', 'Companion Dashboard')
        await context.waitUntil(() => companionWindow.slots[13] && /Honey Nest/.test(itemText(companionWindow.slots[13])),
          { timeoutMs: commandTimeoutMs, label: 'bee header in selected dashboard' })
        const opened = context.waitForEvent('windowOpen', candidate => candidate !== companionWindow, commandTimeoutMs)
        await bot.clickWindow(20, 0, 0)
        const [window] = await opened
        context.expect(componentText(window.title).includes('Companion Particles'), 'Dashboard did not open the selected companion particle menu')
        context.expect(window.inventoryStart === 54, 'Particle menu should have six rows', { inventoryStart: window.inventoryStart })
        await context.waitUntil(() => effectSlots.some(slot => window.slots[slot] != null),
          { timeoutMs: commandTimeoutMs, label: 'particle effect items in the menu content area' })
        await context.waitUntil(() => window.slots[46] && /Back/.test(itemText(window.slots[46])),
          { timeoutMs: commandTimeoutMs, label: 'particle back button' })
        const returned = context.waitForEvent('windowOpen', candidate => candidate !== window, commandTimeoutMs)
        await bot.clickWindow(46, 0, 0)
        const [returnedWindow] = await returned
        context.expect(componentText(returnedWindow.title).includes('Companion Dashboard'), 'Back button did not return to the selected dashboard')
        const collection = context.waitForEvent('windowOpen', candidate => candidate !== returnedWindow, commandTimeoutMs)
        await bot.clickWindow(46, 0, 0)
        const [collectionWindow] = await collection
        context.expect(componentText(collectionWindow.title).includes('Companion Collection'), 'Dashboard did not return to collection')
        bot.closeWindow(collectionWindow)
        await context.waitUntil(() => bot.currentWindow === null,
          { timeoutMs: commandTimeoutMs, label: 'particle menu closed' })
      })

      for (const feature of ['attributes', 'effects', 'buffs', 'decorations']) {
        await context.step(`changing the ${feature} file refreshes the runtime and restores selections`, async () => {
          const previousIds = new Set(Object.keys(bot.entities).map(Number))
          const filename = path.join(path.dirname(configPath), 'features', feature, 'config.toml')
          const original = await readFile(filename, 'utf8')
          featureOriginals.set(filename, original)
          await writeAndReload(context, filename, `${original}\n# QA watched ${feature} edit ${Date.now()}\n`)
          companionId = await findNewCompanion(context, previousIds, `bee restored after ${feature} file edit`)
          const before = particlePackets
          await context.waitUntil(() => particlePackets > before,
            { timeoutMs: commandTimeoutMs, label: `particles restored after ${feature} file edit` })
        })
      }

      await context.step('removed reload commands cannot replace the active runtime', async () => {
        const before = await runtimeReport(context)
        const suggestions = (await bot.tabComplete('/companions ', false, false, commandTimeoutMs))
          .map(value => typeof value === 'string' ? value : value.match)
        context.expect(!suggestions.includes('reload'), 'Removed reload command is still suggested', { suggestions })
        for (const command of ['/companions reload', '/companions reload confirm',
          ...featureNames.map(feature => `/companions ${feature} reload`)]) await context.command(command)
        await context.sleep(1500)
        const after = await runtimeReport(context)
        context.expect(after.active && after.generation === before.generation, 'Removed command replaced the runtime', { before, after })
        context.expect(bot.entities[companionId] !== undefined, 'Removed reload command removed the companion')
      })

      await context.step('root file change restores the equipped companion and particles', async () => {
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        const oldCompanionId = companionId
        await reloadFiles(context)
        await context.waitUntil(() => bot.entities[oldCompanionId] === undefined,
          { timeoutMs: commandTimeoutMs, label: 'old companion removed during runtime reload' })
        companionId = await findNewCompanion(context, previousIds, 'bee restored after runtime reload')
        const before = particlePackets
        await context.waitUntil(() => particlePackets > before,
          { timeoutMs: commandTimeoutMs, label: 'particle packets restored after runtime reload' })
        context.report.observations.push({ kind: 'particle-packets', stage: 'restored', received: particlePackets - before })
        await context.command(`/companions particles equip bee ${effectId}`,
          /Equipped particle effect: angel_ring_effect\./i, commandTimeoutMs)
      })

      await context.step('disable optional features while keeping the core companion runtime', async () => {
        await assertFeatureVisibility(context, true)
        const match = originalConfig.match(/^\[features\]\r?\n[^[]*/m)
        context.expect(match !== null, 'Missing root features block')
        let block = match[0]
        for (const feature of featureNames) {
          const setting = new RegExp(`^(${feature}\\s*=\\s*)true(?=\\s*(?:#|$))`, 'm')
          context.expect(setting.test(block), `Expected enabled QA feature: ${feature}`)
          block = block.replace(setting, '$1false')
        }
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        configModified = true
        await writeFile(configPath, originalConfig.replace(match[0], block), 'utf8')
        await reloadFiles(context)
        companionId = await findNewCompanion(context, previousIds, 'bee retained with optional features disabled')
        await assertFeatureVisibility(context, false)
        await context.command('/companions particles equip bee angel_ring_effect', /The particles feature is disabled\./, commandTimeoutMs)
        const before = particlePackets
        await context.sleep(750)
        context.expect(particlePackets === before, 'Particle packets continued with particles disabled')
        context.expect(bot.entities[companionId] !== undefined, 'Disabling optional features removed the core companion')
      })

      await context.step('reenable optional features and restore the saved particle selection', async () => {
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        await writeFile(configPath, originalConfig, 'utf8')
        await reloadFiles(context)
        companionId = await findNewCompanion(context, previousIds, 'bee restored after reenable')
        const before = particlePackets
        await context.waitUntil(() => particlePackets > before,
          { timeoutMs: commandTimeoutMs, label: 'angel ring restored after particles were reenabled' })
        await context.command(`/companions particles equip bee ${effectId}`, /Equipped particle effect: angel_ring_effect\./i, commandTimeoutMs)
        await assertFeatureVisibility(context, true)
      })

      await context.step('invalid root TOML fails without removing the active companion', async () => {
        await rejectFileReload(context, { filename: configPath, content: '[features\n' })
        context.expect(bot.entities[companionId] !== undefined, 'Invalid root configuration tore down the active companion')
        const before = particlePackets
        await context.waitUntil(() => particlePackets > before,
          { timeoutMs: commandTimeoutMs, label: 'existing particles continue after rejected configuration' })
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        await writeAndReload(context, configPath, `${originalConfig}\n# QA valid recovery ${Date.now()}\n`)
        companionId = await findNewCompanion(context, previousIds, 'bee restored after valid configuration recovery')
      })

      await context.step('despawn, spawn again, and remove the new companion', async () => {
        await despawnCompanion(context, companionId)
        const previousIds = new Set(Object.keys(bot.entities).map(Number))
        await context.command('/companions spawn bee', /You have equipped bee\./i, commandTimeoutMs)
        companionId = await findNewCompanion(context, previousIds, 'bee spawned after reload and despawn')
        await despawnCompanion(context, companionId)
        context.expect(bot.health > 0, 'Bot did not remain alive through the scenario')
      })
      for (const typeId of ['copper', 'warrior']) {
        await context.step(`spawn and despawn the included ${typeId} companion`, async () => {
          const previousIds = new Set(Object.keys(bot.entities).map(Number))
          await context.command(`/companions spawn ${typeId}`, new RegExp(`You have equipped ${typeId}\\.`, 'i'), commandTimeoutMs)
          const entityId = await findNewCompanion(context, previousIds, `${typeId} default companion spawn`)
          await despawnCompanion(context, entityId, typeId)
        })
      }
    } finally {
      bot._client.removeListener('packet', countParticles)
      try {
        if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      } finally {
        if (configModified) await writeFile(configPath, originalConfig, 'utf8')
        for (const [filename, original] of featureOriginals) await writeFile(filename, original, 'utf8')
        if (bot._client.state === 'play' && (configModified || featureOriginals.size)) await reloadFiles(context)
      }
    }
  }
}

