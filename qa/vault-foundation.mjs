import { reloadFiles } from './file-reload.mjs'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'
import { enableProgression, progressionProfile, progressionState, sameMap } from './progression.mjs'
import { equipAfterCoreLoad } from './database-common.mjs'

const timeoutMs = 12_000
const text = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()
export async function vaultState(context) {
  const response = await context.command('/bcqa vault', /BCQA VAULT /, timeoutMs)
  return JSON.parse(response.slice(response.indexOf('BCQA VAULT ') + 11))
}
export function expectFoundation(context, state, expected) {
  context.expect(state.vaultPresent && state.providerPresent && state.enabled && state.apiIdentity
    && state.providerOwner === 'Foundation' && state.name === 'Foundation'
    && state.providerClass === 'com.volmit.foundation.integration.vault.FoundationVaultEconomy'
    && state.vaultVersion === expected.vaultVersion && state.vaultMain === expected.vaultMain,
  'The selected service is not the deployed real Foundation/Vault provider', { state, expected })
}
export async function readyProgression(context) {
  let state
  const deadline = Date.now() + timeoutMs
  do {
    state = await progressionState(context)
    if (state?.ready && !state.busy) return state
    await context.sleep(150)
  } while (Date.now() < deadline)
  context.expect(false, 'Progression did not become ready', { state })
}

export default {
  name: 'bytecompanions-vault-foundation',
  description: 'Actual Foundation economy through official Vault or VaultUnlocked: mixed costs, insufficient funds, duplicate and stale confirmation, permissions, reload and durable checkpoint.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Real economy QA requires an isolated managed instance')
    const expected = JSON.parse(await readFile(path.join(directory, 'bytecompanions-vault-inputs.json'), 'utf8'))
    const configPath = path.join(directory, 'plugins/ByteCompanions/config.toml')
    const definitionsPath = path.join(directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionsPath, 'utf8')
    const permissions = new Set()
    const speed = bot.registry.effectsByName.Speed.id
    const originalWrite = bot._client.write
    let duplicate = false
    let hold = false
    let heldPayment
    let payment
    let checkpoint
    let scenarioFailure
    context.report.observations ??= []
    const trace = { kind: 'gui-protocol-trace', events: [], dropped: 0 }
    context.report.observations.push(trace)
    const itemSummary = item => item ? { name: item.name, count: item.count, text: text(itemText(item)).slice(0, 160) } : null
    const windowSummary = () => ({ id: bot.currentWindow?.id ?? null, title: text(bot.currentWindow?.title),
      cursor: itemSummary(bot.currentWindow?.selectedItem) })
    function observe(direction, name, value, extra = {}) {
      if (trace.events.length >= 1000) { trace.dropped++; return }
      trace.events.push({ at: new Date().toISOString(), direction, name, windowId: value?.windowId,
        stateId: value?.stateId, slot: value?.slot, mouseButton: value?.mouseButton, mode: value?.mode,
        itemCount: value?.items?.length, cursorPresent: value?.cursorItem != null,
        window: windowSummary(), ...extra })
    }
    const incoming = (value, metadata) => {
      if (['open_window', 'close_window', 'window_items', 'set_slot'].includes(metadata.name)) {
        observe('incoming', metadata.name, value)
      }
    }
    bot._client.on('packet', incoming)
    bot._client.write = function (name, value) {
      if (name === 'window_click' || name === 'close_window' || name.includes('tick')) {
        observe('outgoing', name, value, { held: hold && value.slot === 22 && value.mouseButton === 1,
          selectedItem: itemSummary(bot.currentWindow?.slots[value.slot]) })
      }
      if (hold && name === 'window_click' && value.slot === 22 && value.mouseButton === 1) {
        heldPayment = { name, value: structuredClone(value) }
        hold = false
        return
      }
      const result = originalWrite.call(this, name, value)
      if (name === 'window_click' && value.slot === 22 && value.mouseButton === 1) {
        payment = { name, value: structuredClone(value) }
        if (duplicate) { duplicate = false; originalWrite.call(this, name, value) }
      }
      return result
    }
    const record = (kind, data) => context.report.observations.push({ kind, ...data })
    const reload = () => reloadFiles(context)
    const emeralds = named => bot.inventory.items().filter(item => item.name === 'emerald'
      && /QA Named Emerald/.test(text(itemText(item))) === named).reduce((sum, item) => sum + item.count, 0)
    async function close() {
      if (bot.currentWindow) await bot.clickWindow(49, 1, 0)
      await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'menu closed' })
    }
    const screen = title => context.waitUntil(() => bot.currentWindow?.inventoryStart === 54
      && text(bot.currentWindow.title).includes(title), { timeoutMs, label: title })
    async function open(command, title) { await close(); await context.command(command); await screen(title) }
    async function content(name) {
      const slot = (await context.waitUntil(() => {
        const slot = bot.currentWindow?.slots.slice(0, 54).findIndex(item => text(itemText(item)).includes(name))
        return slot >= 0 ? { slot } : false
      }, { timeoutMs, label: name })).slot
      observe('selection', 'content', { windowId: bot.currentWindow?.id, slot },
        { requestedName: name, selectedItem: itemSummary(bot.currentWindow?.slots[slot]) })
      return slot
    }
    async function upgrade() {
      await open('/companions upgrades bee', 'Companion Upgrades')
      await bot.clickWindow(await content('QA Economy'), 1, 0)
      await screen('Confirm Purchase')
    }
    async function booster() {
      await open('/companions boosts', 'Companion Boosters')
      await bot.clickWindow(await content('QA Personal'), 1, 0)
      await screen('Booster Details')
      await bot.clickWindow(20, 1, 0)
      await screen('Confirm Purchase')
    }
    async function confirm(expression) {
      const response = context.waitForMessage(expression, timeoutMs)
      await bot.clickWindow(22, 1, 0)
      await response
    }
    async function permission(node, value) {
      const id = bot.player?.uuid
      context.expect(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id ?? ''), 'Player UUID missing')
      const response = await context.command(`/bcqa permission ${id} ${node} ${value}`, /BCQA PERMISSION/, timeoutMs)
      context.expect(value === 'clear' || response.includes(`effective=${value}`), 'Permission was not applied', { response })
      if (value === 'clear') permissions.delete(node); else permissions.add(node)
    }
    async function balance(value) {
      await close()
      await context.command(`/foundation:economy set ${bot.username} ${value}`, /Updated .* to /, timeoutMs)
      const actual = await vaultState(context)
      expectFoundation(context, actual, expected)
      context.expect(actual.balance === value, 'Foundation command and Vault balance disagree', { actual, value })
    }
    try {
      await context.step('the installed Vault implementation selects the real Foundation provider', async () => {
        await context.command('/bcqa guitrace start', /BCQA GUI /, timeoutMs)
        const state = await vaultState(context)
        expectFoundation(context, state, expected)
        record('actual-provider', { state, expected })
      })
      await context.step('isolated progression profile and real Foundation ledger are ready', async () => {
        await writeFile(definitionsPath, progressionProfile, 'utf8')
        await writeFile(configPath, enableProgression(original), 'utf8')
        await reload()
        const state = await readyProgression(context)
        context.expect(state.totalXp === 0 && Object.keys(state.tracks).length === 0, 'Real-provider player is not fresh', { state })
        await context.command(`/companions progression give ${bot.username} bee 100`, /Saved 100 companion XP/, timeoutMs)
        await equipAfterCoreLoad(context, '/companions spawn bee', /^You have equipped bee\.$/i)
        await context.waitUntil(() => bot.entity.effects?.[speed]?.amplifier === 5, { timeoutMs, label: 'base Bee Speed VI' })
        await context.command('/bcqa paymentitems 16 4', /BCQA PAYMENT_ITEMS/, timeoutMs)
        await context.waitUntil(() => emeralds(false) === 16 && emeralds(true) === 4, { timeoutMs, label: 'payment inventory' })
        await balance(100)
      })
      await context.step('mixed money and item upgrade is charged once under duplicate confirmation', async () => {
        await upgrade()
        duplicate = true
        await confirm(/Saved successfully/)
        await context.waitUntil(() => emeralds(false) === 14 && bot.entity.effects?.[speed]?.amplifier === 6,
          { timeoutMs, label: 'two plain emerald debit and Speed VII' })
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(payment && state.tracks.economy === 1 && economy.balance === 95 && emeralds(true) === 4,
          'Duplicate mixed purchase changed exact debit or benefit', { state, economy, payment })
        record('actual-mixed-purchase', { state, economy, plain: emeralds(false), named: emeralds(true), amplifier: 6 })
      })
      await context.step('insufficient real funds leave items, credits and balance unchanged', async () => {
        await balance(1)
        await booster()
        await confirm(/do not have enough money|insufficient funds|declined this payment/i)
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(economy.balance === 1 && !state.credits.qa_personal && emeralds(false) === 14 && emeralds(true) === 4,
          'Insufficient-funds attempt changed assets', { state, economy })
        record('actual-insufficient-funds', { state, economy, plain: emeralds(false), named: emeralds(true) })
      })
      await context.step('revoked purchase permission rejects an already opened confirmation', async () => {
        await balance(100)
        await booster()
        await permission('bytecompanions.command.boosts', 'false')
        await confirm(/permission requirements|permission/i)
        context.expect((await vaultState(context)).balance === 100 && !(await readyProgression(context)).credits.qa_personal
          && emeralds(false) === 14, 'Revoked permission still charged or granted')
        await permission('bytecompanions.command.boosts', 'clear')
      })
      await context.step('real provider sells one booster credit for the exact combined cost', async () => {
        await booster()
        duplicate = true
        await confirm(/Saved successfully/)
        await context.waitUntil(() => emeralds(false) === 13, { timeoutMs, label: 'one booster emerald debit' })
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(state.credits.qa_personal === 1 && economy.balance === 93 && emeralds(true) === 4,
          'Repeated confirmation duplicated real-provider debit or credit', { state, economy })
        record('actual-booster-purchase', { state, economy, plain: emeralds(false), named: emeralds(true) })
      })
      await context.step('runtime reload rejects a captured payment packet without a second debit', async () => {
        await booster()
        const currentWindow = bot.currentWindow.id
        hold = true
        await bot.clickWindow(22, 1, 0)
        const stale = structuredClone(heldPayment)
        context.expect(stale?.value.slot === 22 && stale.value.mouseButton === 1 && stale.value.windowId === currentWindow
          && bot.currentWindow?.id === currentWindow && Number.isInteger(stale.value.stateId),
        'Held payment did not belong to the live confirmation', { stale, currentWindow })
        context.expect((await vaultState(context)).balance === 93 && (await readyProgression(context)).credits.qa_personal === 1,
          'Held packet reached the server before reload')
        await reload()
        await readyProgression(context)
        originalWrite.call(bot._client, stale.name, stale.value)
        await context.sleep(500)
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(state.tracks.economy === 1 && state.credits.qa_personal === 1 && economy.balance === 93
          && emeralds(false) === 13 && emeralds(true) === 4, 'Stale payment changed persisted balances or benefits', { state, economy })
        record('actual-stale-payment', { state, economy, slot: stale.value.slot, windowId: stale.value.windowId,
          stateId: stale.value.stateId, currentWindowAtCapture: currentWindow, heldBeforeReload: true })
      })
      await context.step('purchased credit activates without another Foundation withdrawal', async () => {
        await open('/companions boosts', 'Companion Boosters')
        await bot.clickWindow(await content('QA Personal'), 1, 0)
        await screen('Booster Details')
        const receipt = context.waitForMessage(/Transaction or activation ID: [0-9a-f-]{36}/i, timeoutMs)
        await bot.clickWindow(24, 1, 0)
        const response = await receipt
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(!state.credits.qa_personal && state.personalBoosters.length === 1 && state.rates.companion_xp === 50
          && economy.balance === 93, 'Activation duplicated charge, credit or effect', { state, economy, response })
        record('actual-activation', { state, economy, response })
      })
      await context.step('reload retains paid tier, active booster and real ledger balance', async () => {
        await close()
        const before = await readyProgression(context)
        await reload()
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        expectFoundation(context, economy, expected)
        context.expect(sameMap(state.tracks, before.tracks) && sameMap(state.credits, before.credits)
          && state.personalBoosters[0]?.activationId === before.personalBoosters[0]?.activationId && economy.balance === 93,
        'Reload lost or duplicated real-provider purchase state', { state, before, economy })
        checkpoint = { recordedAt: new Date().toISOString(), state, economy, expected, profile: progressionProfile,
          inventory: { plain: emeralds(false), named: emeralds(true) }, player: bot.username }
        await writeFile(path.join(directory, 'bytecompanions-vault-checkpoint.json'), JSON.stringify(checkpoint, null, 2))
        record('actual-reload-checkpoint', checkpoint)
      })
    } catch (failure) {
      scenarioFailure = failure
      throw failure
    } finally {
      observe('snapshot', 'scenario-cleanup', {})
      let observationFailure
      try {
        const observation = await context.command('/bcqa guitrace stop', /BCQA GUI FILE /, timeoutMs)
        const filename = observation.match(/BCQA GUI FILE (gui-trace-[a-f0-9-]{36}\.json)$/)?.[1]
        context.expect(filename, 'Observer did not return a scoped trace filename', { observation })
        record('server-gui-trace', JSON.parse(await readFile(path.join(directory, 'plugins/ByteCompanionsQA', filename), 'utf8')))
      } catch (failure) {
        record('server-gui-trace-failure', { message: failure.message, primaryFailure: scenarioFailure?.message })
        observationFailure = failure
      }
      bot._client.removeListener('packet', incoming)
      bot._client.write = originalWrite
      await close()
      for (const node of [...permissions]) await permission(node, 'clear')
      await context.command('/companions despawn bee')
      await writeFile(definitionsPath, definitions, 'utf8')
      await writeFile(configPath, original, 'utf8')
      await reload()
      if (observationFailure && !scenarioFailure) throw observationFailure
    }
  }
}
