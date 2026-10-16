import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'
import { reloadFiles, rejectFileReload, runtimeReport, writeAndReload } from './file-reload.mjs'

const timeoutMs = 10_000
const reloadMs = 25_000

function setting(source, section, key, value) {
  const escaped = section.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const block = source.match(new RegExp(`^\\[${escaped}\\]\\r?\\n[^[]*`, 'm'))
  if (!block) throw new Error(`Missing TOML section: ${section}`)
  const entry = new RegExp(`^${key}\\s*=.*$`, 'm')
  if (!entry.test(block[0])) throw new Error(`Missing TOML setting: ${section}.${key}`)
  return source.replace(block[0], block[0].replace(entry, `${key} = ${JSON.stringify(value)}`))
}

async function open(context, command) {
  if (context.bot.currentWindow) context.bot.closeWindow(context.bot.currentWindow)
  const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
  await context.command(command)
  return (await opened)[0]
}

async function findSlot(context, window, expression) {
  try {
    return await context.waitUntil(() => {
      const current = context.bot.currentWindow
      if (!current) return false
      const slot = current.slots.findIndex((item, index) => index < current.inventoryStart && item &&
        expression.test(itemText(item).replace(/§[0-9a-fk-orx]/gi, '').trim()))
      return slot < 0 ? false : { slot, window: current }
    }, { timeoutMs, label: `inventory item ${expression}` })
  } catch (error) {
    const current = context.bot.currentWindow ?? window
    const items = current.slots.flatMap((item, slot) => slot < current.inventoryStart && item
      ? [{ slot, text: itemText(item), displayName: item.displayName, customName: item.customName, components: item.components, nbt: item.nbt }]
      : [])
    context.report.observations ??= []
    context.report.observations.push({ kind: 'inventory-lookup-failed', expected: String(expression), title: componentText(current.title), items })
    throw new Error(`${error.message}; visible items: ${items.map(item => `${item.slot}: ${item.text}`).join(', ')}`, { cause: error })
  }
}

async function enterSection(context, window, expression) {
  const { slot, window: current } = await findSlot(context, window, expression)
  const opened = context.waitForEvent('windowOpen', next => next !== current, timeoutMs)
  await context.bot.clickWindow(slot, 0, 0)
  return (await opened)[0]
}

async function awaitLog(context, offset, expression) {
  await context.waitUntil(async () => expression.test((await readFile(context.server.logPath, 'utf8')).slice(offset)),
    { timeoutMs: reloadMs, intervalMs: 500, label: `server log ${expression}` })
}

export default {
  name: 'bytecompanions-volmlib-foundation',
  description: 'Bounded configuration and language editor, persistence, rejected edits, and automatic file reload checks on the isolated QA server.',
  async run(context) {
    const { bot } = context
    context.expect(typeof context.server.directory === 'string' && typeof context.server.logPath === 'string', 'Managed server paths are required')
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions')
    const configPath = path.join(directory, 'config.toml')
    const languagePath = path.join(directory, 'languages', 'en_US.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    const originalLanguage = await readFile(languagePath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Start this scenario with settings.autoReload=true')
    let editedLanguage
    try {
      await context.step('configuration editor rejects invalid values and saves valid values', async () => {
        const distance = Number(originalConfig.match(/^viewDistance\s*=\s*(\d+)/m)?.[1])
        context.expect(Number.isFinite(distance), 'Missing numeric viewDistance')
        let window = await open(context, '/companions config')
        context.expect(componentText(window.title).includes('Configuration'), 'Configuration editor title missing')
        window = await enterSection(context, window, /^performance$/)
        let entry = await findSlot(context, window, /^viewDistance$/)
        let prompt = context.waitForEvent('messagestr', message => /Enter a new value for performance \/ viewDistance\. Type cancel to return\./.test(message), timeoutMs)
        await bot.clickWindow(entry.slot, 0, 0)
        await prompt
        await context.command('-1', /Unable to edit configuration:/, timeoutMs)
        context.expect((await readFile(configPath, 'utf8')).includes(`viewDistance = ${distance}`), 'Invalid editor value changed the file')
        window = await open(context, '/companions config')
        window = await enterSection(context, window, /^performance$/)
        entry = await findSlot(context, window, /^viewDistance$/)
        prompt = context.waitForEvent('messagestr', message => /Enter a new value for performance \/ viewDistance\. Type cancel to return\./.test(message), timeoutMs)
        await bot.clickWindow(entry.slot, 0, 0)
        await prompt
        await context.command(String(distance + 1), /Saved performance \/ viewDistance\. Changes apply automatically\./, reloadMs)
        context.expect(new RegExp(`viewDistance\\s*=\\s*${distance + 1}(?:\\s|$)`).test(await readFile(configPath, 'utf8')), 'Valid editor value was not persisted')
        if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      })

      await context.step('personal language survives runtime reload and persists to disk', async () => {
        await context.command('/companions language self de_DE', /de_DE\./, timeoutMs)
        await context.command('/companions particles help', /Begleiterpartikel/, timeoutMs)
        const preferences = await readFile(path.join(directory, 'languages', 'language-preferences.properties'), 'utf8')
        context.expect(typeof bot.player?.uuid === 'string' && preferences.includes(bot.player.uuid) && preferences.includes('de_DE'), 'Personal locale was not persisted')
        await reloadFiles(context)
        await context.command('/companions particles help', /Begleiterpartikel/, timeoutMs)
        await context.command('/companions language self reset', /your language now uses the server default\./, timeoutMs)
      })

      await context.step('language editor saves a message retained by file reload', async () => {
        let window = await open(context, '/companions language server edit en_US')
        const search = await findSlot(context, window, /^Search messages$/)
        const prompt = context.waitForEvent('messagestr', message => /Search message keys or text\./.test(message), timeoutMs)
        await bot.clickWindow(search.slot, 0, 0)
        await prompt
        const searched = context.waitForEvent('windowOpen', () => true, timeoutMs)
        await context.command('particles.title')
        window = (await searched)[0]
        const message = await findSlot(context, window, /^particles\.title$/)
        const editing = context.waitForEvent('messagestr', text => /Type a new value for particles\.title in chat\./.test(text), timeoutMs)
        await bot.clickWindow(message.slot, 0, 0)
        await editing
        await context.command('QA language saved', /Saved en_US\. Language selections are unchanged\./, timeoutMs)
        editedLanguage = await readFile(languagePath, 'utf8')
        context.expect(editedLanguage.includes('QA language saved'), 'Language editor did not persist its value')
        if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
        await reloadFiles(context)
        await context.command('/companions particles help', /QA language saved/, timeoutMs)
      })

      await context.step('invalid root file preserves the working runtime', async () => {
        await rejectFileReload(context, { filename: configPath, content: '[features\n' })
        await context.command('/companions particles help', /QA language saved/, timeoutMs)
        await writeAndReload(context, configPath, originalConfig)
      })

      await context.step('automatic configuration reload applies valid changes and rejects invalid ones', async () => {
        const automatic = setting(originalConfig, 'settings', 'autoReload', true)
        const disabled = await writeAndReload(context, configPath, setting(automatic, 'features', 'effects', false))
        context.expect(disabled.text.includes('Feature effects: false'), 'Effects remained active after the file change', disabled)
        const rejected = await rejectFileReload(context, { filename: configPath, content: '[features\n' })
        context.expect(rejected.text.includes('Feature effects: false'), 'Invalid configuration re-enabled effects', rejected)
        await writeAndReload(context, configPath, automatic)
      })

      await context.step('automatic language reload applies valid edits and retains them after invalid edits', async () => {
        const changed = setting(editedLanguage, 'particles', 'title', 'QA language hotload')
        await writeFile(languagePath, changed, 'utf8')
        await context.waitUntil(async () => {
          const response = await context.command('/companions particles help', /QA language hotload|QA language saved/, timeoutMs)
          return response.includes('QA language hotload')
        }, { timeoutMs: reloadMs, intervalMs: 1_200, label: 'localized title updated by language watcher' })
        const logOffset = (await readFile(context.server.logPath, 'utf8')).length
        await writeFile(languagePath, '[broken\n', 'utf8')
        await awaitLog(context, logOffset, /Rejected language change in en_US.toml/)
        await context.command('/companions particles help', /QA language hotload/, timeoutMs)
      })
    } finally {
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      await writeFile(languagePath, originalLanguage, 'utf8')
      const current = await readFile(configPath, 'utf8')
      if (current !== originalConfig) await writeAndReload(context, configPath, originalConfig)
      else context.expect((await runtimeReport(context)).active, 'Original runtime is not active during cleanup')
      await context.command('/companions language self reset', /your language now uses the server default\./, timeoutMs)
    }
  }
}
