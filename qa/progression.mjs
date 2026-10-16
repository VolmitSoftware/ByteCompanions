import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { componentText, itemText } from './merged-features.mjs'
import { mutateAndReload, reloadFiles, rejectFileReload, writeAndReload } from './file-reload.mjs'

const timeoutMs = 12_000
const slots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()
const lore = item => plain(item?.components?.find(value => ['lore', 'minecraft:lore'].includes(value.type))?.data
  ?? item?.nbt?.value?.display?.value?.Lore ?? [])
const title = { upgrades: 'Companion Upgrades', purchase: 'Confirm Purchase', boosts: 'Companion Boosters',
  booster: 'Booster Details', active: 'Active Boosts', dashboard: 'Companion Dashboard' }
export const sameMap = (first, second) => JSON.stringify(Object.entries(first).sort()) === JSON.stringify(Object.entries(second).sort())

export const progressionProfile = `server-id = "local"
pause-personal-offline = true
maximum-booster-percent = 60
maximum-reward-multiplier = 10
source-cooldown-millis = 100
allowed-worlds = []
level-thresholds = [0, 100, 300]

[xp-sources]
block_break = 10
mob_kill = 5
fishing = 3

[companions.bee.tracks.agility]
name = "QA Agility"

[[companions.bee.tracks.agility.tiers]]
required-level = 2

[companions.bee.tracks.agility.tiers.cost.items]
EMERALD = 4

[companions.bee.tracks.agility.tiers.additions]
"effect:minecraft:speed" = 1

[[companions.bee.tracks.agility.tiers]]
required-level = 3

[companions.bee.tracks.agility.tiers.cost.items]
EMERALD = 8

[companions.bee.tracks.agility.tiers.additions]
"effect:minecraft:speed" = 1

[companions.bee.tracks.economy]
name = "QA Economy"

[[companions.bee.tracks.economy.tiers]]
required-level = 2

[companions.bee.tracks.economy.tiers.cost]
money = 5

[companions.bee.tracks.economy.tiers.cost.items]
EMERALD = 2

[companions.bee.tracks.economy.tiers.additions]
"effect:minecraft:speed" = 1

[boosters.qa_personal]
name = "QA Personal"
scope = "PERSONAL"
duration-seconds = 600
stacking = "QUEUE"

[boosters.qa_personal.percentages]
companion_xp = 50
experience_block = 25

[boosters.qa_personal.cost]
money = 7

[boosters.qa_personal.cost.items]
EMERALD = 1

[boosters.qa_server]
name = "QA Server"
scope = "SERVER"
duration-seconds = 600
stacking = "QUEUE"

[boosters.qa_server.percentages]
companion_xp = 25
experience_block = 50

[boosters.qa_server.cost.items]
DIAMOND = 2

[boosters.qa_short]
name = "QA Short"
scope = "PERSONAL"
duration-seconds = 2
stacking = "HIGHEST"

[boosters.qa_short.percentages]
block_drops = 10

[boosters.qa_short.cost.items]
EMERALD = 1
`

export function enableProgression(config) {
  return config.replace(/^progression\s*=\s*false/m, 'progression = true')
    .replace(/^boosters\s*=\s*false/m, 'boosters = true')
}

export async function progressionState(actor) {
  const response = await actor.command('/bcqa progression bee', /BCQA PROGRESSION /, timeoutMs)
  return JSON.parse(response.slice(response.indexOf('BCQA PROGRESSION ') + 17))
}

async function stateUntil(context, predicate, label, actor = context, timeout = timeoutMs) {
  const deadline = Date.now() + timeout
  let state
  while (Date.now() < deadline) {
    state = await progressionState(actor)
    if (state && predicate(state)) return state
    await context.sleep(150)
  }
  context.expect(false, label, { state })
}

export default {
  name: 'bytecompanions-progression',
  description: 'Real command, inventory, potion and eligible-ore mining checks for optional progression, item/money purchases, ambiguous payment reconciliation and scoped queued boosters.',
  async run(context) {
    const { bot } = context
    const directory = context.server.directory
    context.expect(typeof directory === 'string' && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
      'Progression QA requires an isolated managed instance')
    const configPath = path.join(directory, 'plugins/ByteCompanions/config.toml')
    const definitionsPath = path.join(directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionsPath, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original), 'Progression QA requires autoReload=true')
    const permissions = new Set()
    const speed = bot.registry.effectsByName.Speed.id
    let peer
    let latestClick
    let replayNextClick = false
    let checkpoint
    let paymentClick
    context.report.observations ??= []
    const record = (kind, value) => context.report.observations.push({ kind, ...value })
    const originalWrite = bot._client.write
    bot._client.write = function (name, value) {
      const result = originalWrite.call(this, name, value)
      if (name === 'window_click') {
        latestClick = { name, value }
        if (replayNextClick) {
          replayNextClick = false
          originalWrite.call(this, name, value)
        }
      }
      return result
    }
    const reload = () => reloadFiles(context)
    const emeralds = named => bot.inventory.items().filter(item => item.name === 'emerald'
      && /QA Named Emerald/.test(plain(itemText(item))) === named).reduce((total, item) => total + item.count, 0)
    async function permission(node, value) {
      const id = bot.player?.uuid
      context.expect(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(id ?? ''), 'Connected player UUID missing')
      const response = await context.command(`/bcqa permission ${id} ${node} ${value}`, /BCQA PERMISSION/, timeoutMs)
      context.expect(value === 'clear' || response.includes(`effective=${value}`), 'Permission change not effective', { response })
      if (value === 'clear') permissions.delete(node)
      else permissions.add(node)
    }
    async function close(actor = context) {
      if (actor.bot.currentWindow) await actor.bot.clickWindow(49, 1, 0)
      await actor.waitUntil(() => actor.bot.currentWindow === null, { timeoutMs, label: 'menu closed' })
    }
    async function screen(kind) {
      return context.waitUntil(() => {
        const window = bot.currentWindow
        return window?.inventoryStart === 54 && plain(window.title).includes(title[kind]) ? window : false
      }, { timeoutMs, label: title[kind] })
    }
    async function open(command, kind) {
      await close()
      await context.command(command)
      return screen(kind)
    }
    async function click(slot, kind) {
      await bot.clickWindow(slot, 1, 0)
      if (kind) return screen(kind)
    }
    async function item(slot, expression) {
      return context.waitUntil(() => {
        const item = bot.currentWindow?.slots[slot]
        return item && expression.test(plain(itemText(item)) + ' ' + lore(item)) ? item : false
      }, { timeoutMs, label: `slot ${slot} matches ${expression}` })
    }
    async function content(expression) {
      const found = await context.waitUntil(() => {
        const slot = slots.find(slot => expression.test(plain(itemText(bot.currentWindow?.slots[slot]))))
        return slot === undefined ? false : { slot }
      }, { timeoutMs, label: `content ${expression}` })
      return found.slot
    }
    async function upgrade(name = /QA Agility/) {
      await open('/companions upgrades bee', 'upgrades')
      await click(await content(name), 'purchase')
    }
    async function booster(name = /QA Personal/) {
      await open('/companions boosts', 'boosts')
      await click(await content(name), 'booster')
    }
    async function confirm(expression, next) {
      const response = context.waitForMessage(expression, timeoutMs)
      await click(22)
      const reply = await response
      if (next) await screen(next)
      return reply
    }
    async function economy(action = 'status') {
      const response = await context.command(`/bcqaeconomy ${action}`, /BCQA ECONOMY player=/, timeoutMs)
      const parsed = Object.fromEntries([...response.matchAll(/(balance|withdrawals|deposits)=([0-9.]+)/g)].map(match => [match[1], Number(match[2])]))
      context.expect(Object.keys(parsed).length === 3, 'Economy response missing fields', { response })
      return parsed
    }
    async function mineNaturalOre() {
      await close()
      await context.command(`/give ${bot.username} minecraft:netherite_pickaxe 1`)
      await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'netherite_pickaxe'), { timeoutMs, label: 'mining tool arrives' })
      await bot.equip(bot.inventory.items().find(item => item.name === 'netherite_pickaxe'), 'hand')
      const response = await context.command('/bcqa naturalore', /BCQA NATURAL_ORE/, timeoutMs)
      const coordinates = [...response.matchAll(/[xyz]=(-?\d+)/g)].map(match => Number(match[1]))
      context.expect(coordinates.length === 3, 'Natural ore position missing', { response })
      const position = bot.entity.position.clone().set(...coordinates)
      const block = await context.waitUntil(() => {
        const block = bot.blockAt(position)
        return block?.name === 'diamond_ore' ? block : false
      }, { timeoutMs, label: 'natural ore visible' })
      await bot.dig(block)
      await context.waitUntil(() => bot.blockAt(position)?.name !== 'diamond_ore', { timeoutMs, label: 'real player breaks ore' })
    }
    try {
      await context.step('optional commands are absent with default feature switches', async () => {
        context.expect(/^progression\s*=\s*false/m.test(original) && /^boosters\s*=\s*false/m.test(original), 'Expected disabled defaults')
        const suggestions = (await bot.tabComplete('/companions ', false, false, timeoutMs)).map(value => typeof value === 'string' ? value : value.match)
        context.expect(['upgrades', 'boosts', 'progression', 'booster', 'transactions'].every(value => !suggestions.includes(value)),
          'Disabled optional command leaked into completion', { suggestions })
        context.expect(await progressionState(context) === null, 'Disabled optional service was constructed')
      })
      await context.step('enable isolated progression profile and equip a real companion', async () => {
        await mutateAndReload(context, async () => {
          await writeFile(definitionsPath, progressionProfile, 'utf8')
          await writeFile(configPath, enableProgression(original), 'utf8')
        })
        await stateUntil(context, value => value.ready && value.totalXp === 0, 'Fresh progression did not load')
        if (bot.game.gameMode !== 'creative') await context.command(`/gamemode creative ${bot.username}`)
        await context.waitUntil(() => bot.game.gameMode === 'creative', { timeoutMs, label: 'creative preparation mode' })
        await context.command('/bcqa move 0 100 0', /BCQA MOVE success=true/, timeoutMs)
        await context.command('/bcqa platform', /BCQA PLATFORM/, timeoutMs)
        if (bot.game.gameMode !== 'survival') await context.command(`/gamemode survival ${bot.username}`)
        await context.waitUntil(() => bot.game.gameMode === 'survival', { timeoutMs, label: 'survival mining mode' })
        await context.command('/companions spawn bee', /You have equipped bee/, timeoutMs)
        await context.waitUntil(() => bot.entity.effects?.[speed]?.amplifier === 5, { timeoutMs, label: 'base Bee Speed VI reaches client' })
        await economy('reset 100')
      })
      await context.step('menu and administrator permissions are independent', async () => {
        await permission('bytecompanions.command.upgrades', 'false')
        await context.command('/companions upgrades bee', /You do not have permission/, timeoutMs)
        context.expect(bot.currentWindow === null, 'Denied upgrade command opened a menu')
        await permission('bytecompanions.command.upgrades', 'clear')
        await permission('bytecompanions.command.progression.give', 'false')
        await context.command(`/companions progression give ${bot.username} bee 100`, /You do not have permission/, timeoutMs)
        context.expect((await progressionState(context)).totalXp === 0, 'Denied XP grant changed state')
        await permission('bytecompanions.command.progression.give', 'clear')
      })
      await context.step('locked upgrade and invalid XP cannot alter progress or inventory', async () => {
        await context.command('/bcqa paymentitems 16 4', /BCQA PAYMENT_ITEMS/, timeoutMs)
        await context.waitUntil(() => emeralds(false) === 16 && emeralds(true) === 4, { timeoutMs, label: 'plain and named payment items received' })
        await upgrade()
        await item(22, /Requirements not met/)
        await click(22)
        context.expect((await progressionState(context)).tracks.agility === undefined && emeralds(false) === 16, 'Locked upgrade spent resources')
        await close()
        for (const amount of ['-1', '1.5', '9223372036854775808']) {
          await context.command(`/companions progression give ${bot.username} bee ${amount}`, /whole number/, timeoutMs)
        }
        context.expect((await progressionState(context)).totalXp === 0, 'Invalid XP changed progress')
      })
      await context.step('real mining of eligible unmarked ore awards configured source XP', async () => {
        await mineNaturalOre()
        const state = await stateUntil(context, value => value.totalXp === 10, 'Natural ore did not award exactly 10 companion XP')
        record('eligible-ore-xp', { totalXp: state.totalXp })
        await context.command(`/companions progression give ${bot.username} bee 90`, /Saved 90 companion XP/, timeoutMs)
        const level = await progressionState(context)
        context.expect(level.level === 2 && level.totalXp === 100, 'Level threshold was not reached')
      })
      await context.step('named items cannot pay a plain-item upgrade cost', async () => {
        await context.command('/bcqa paymentitems 0 4', /BCQA PAYMENT_ITEMS/, timeoutMs)
        await context.waitUntil(() => emeralds(false) === 0 && emeralds(true) === 4, { timeoutMs, label: 'named-only inventory' })
        await upgrade()
        await confirm(/required plain items/, 'upgrades')
        context.expect((await progressionState(context)).tracks.agility === undefined && emeralds(true) === 4, 'Named items funded an upgrade')
      })
      await context.step('successful item purchase debits once and changes actual Speed effect', async () => {
        await close()
        await context.command('/bcqa paymentitems 16 4', /BCQA PAYMENT_ITEMS/, timeoutMs)
        await context.waitUntil(() => emeralds(false) === 16 && emeralds(true) === 4, { timeoutMs, label: 'payment inventory restored' })
        await upgrade()
        await item(13, /4 × Emerald/)
        replayNextClick = true
        await confirm(/Saved successfully/, 'upgrades')
        const state = await stateUntil(context, value => value.tracks.agility === 1, 'First tier not persisted')
        await context.waitUntil(() => emeralds(false) === 12 && bot.entity.effects?.[speed]?.amplifier === 6,
          { timeoutMs, label: 'exact item debit and Speed VII' })
        context.expect(emeralds(true) === 4, 'Named items were consumed')
        record('item-purchase', { tier: state.tracks.agility, plain: emeralds(false), named: emeralds(true), amplifier: bot.entity.effects[speed].amplifier })
      })
      await context.step('permission revoked after confirmation opened blocks payment', async () => {
        await close()
        await context.command(`/companions progression give ${bot.username} bee 200`, /Saved 200 companion XP/, timeoutMs)
        await upgrade()
        await permission('bytecompanions.companion.bee', 'false')
        await confirm(/permission requirements/, 'upgrades')
        context.expect((await progressionState(context)).tracks.agility === 1 && emeralds(false) === 12, 'Stale permission allowed purchase')
        await permission('bytecompanions.companion.bee', 'clear')
      })
      await context.step('maximum tier remains bounded under repeated confirmation packets', async () => {
        await upgrade()
        replayNextClick = true
        await confirm(/Saved successfully/, 'upgrades')
        await stateUntil(context, value => value.tracks.agility === 2, 'Second tier not saved')
        await context.waitUntil(() => emeralds(false) === 4 && bot.entity.effects?.[speed]?.amplifier === 7,
          { timeoutMs, label: 'second exact debit and Speed VIII' })
        await item(await content(/QA Agility/), /Fully upgraded/)
        context.expect(emeralds(true) === 4, 'Named items changed at max tier')
      })
      await context.step('combined item and money purchase uses the Vault API service exactly once', async () => {
        await booster()
        await click(20, 'purchase')
        await item(13, /Money required: 7/)
        replayNextClick = true
        await confirm(/Saved successfully/, 'booster')
        paymentClick = structuredClone(latestClick)
        const state = await stateUntil(context, value => value.credits.qa_personal === 1, 'Purchased booster credit missing')
        const balance = await economy()
        await context.waitUntil(() => emeralds(false) === 3, { timeoutMs, label: 'combined purchase consumes one plain emerald' })
        context.expect(balance.balance === 93 && balance.withdrawals === 1 && balance.deposits === 0 && emeralds(true) === 4, 'Combined purchase charged more than once', balance)
        record('money-purchase', { ...balance, credits: state.credits.qa_personal })
      })
      await context.step('rejected payment leaves upgrade and balance unchanged', async () => {
        await economy('mode reject')
        await upgrade(/QA Economy/)
        await confirm(/declined this payment/, 'upgrades')
        const balance = await economy()
        context.expect(balance.balance === 93 && balance.withdrawals === 2 && !(await progressionState(context)).tracks.economy && emeralds(false) === 3 && emeralds(true) === 4,
          'Rejected payment changed the benefit or balance', balance)
      })
      await context.step('ambiguous debit blocks retries and records a resolvable transaction', async () => {
        await economy('mode unknown')
        await upgrade(/QA Economy/)
        await confirm(/payment result is uncertain/)
        const state = await stateUntil(context, value => value.busy, 'Ambiguous payment did not lock purchases')
        context.expect(!state.tracks.economy, 'Uncertain payment granted benefit automatically')
        await close()
        await context.command('/companions upgrades bee', /purchase is pending|administrator.*review/i, timeoutMs)
        const balance = await economy()
        context.expect(balance.balance === 88 && balance.withdrawals === 3 && balance.deposits === 0 && emeralds(false) === 3 && emeralds(true) === 4, 'Ambiguous debit retried, refunded or consumed items', balance)
        const entry = context.waitForMessage(/[0-9a-f-]{36}.*Player:.*UPGRADE/, timeoutMs)
        await context.command('/companions transactions list')
        const response = await entry
        const transaction = response.match(/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}/i)?.[0]
        context.expect(transaction, 'Transaction ID missing from administrator listing', { response })
        checkpoint = { transaction }
        record('ambiguous-payment', { transaction, ...balance, busy: state.busy })
      })
      await context.step('manual reconciliation grants once without another debit or refund', async () => {
        await economy('mode success')
        await permission('bytecompanions.command.transactions.list', 'false')
        await context.command('/companions transactions list', /You do not have permission/, timeoutMs)
        await permission('bytecompanions.command.transactions.list', 'clear')
        await permission('bytecompanions.command.transactions.resolve', 'false')
        await context.command(`/companions transactions resolve ${checkpoint.transaction} grant`, /You do not have permission/, timeoutMs)
        context.expect((await progressionState(context)).busy && !(await progressionState(context)).tracks.economy,
          'Denied administrator reconciliation changed the purchase')
        await permission('bytecompanions.command.transactions.resolve', 'clear')
        await context.command(`/companions transactions resolve ${checkpoint.transaction} grant`, /Reconciliation saved/, timeoutMs)
        const state = await stateUntil(context, value => !value.busy && value.tracks.economy === 1, 'Reconciliation did not publish tier')
        await context.command(`/companions transactions resolve ${checkpoint.transaction} grant`, /Reconciliation saved/, timeoutMs)
        const repeated = await progressionState(context)
        context.expect(repeated.revision === state.revision && sameMap(repeated.tracks, state.tracks)
          && sameMap(repeated.credits, state.credits), 'Repeated reconciliation granted another benefit', { state, repeated })
        const balance = await economy()
        context.expect(balance.balance === 88 && balance.withdrawals === 3 && balance.deposits === 0, 'Reconciliation moved payment again', balance)
        await context.waitUntil(() => bot.entity.effects?.[speed]?.amplifier === 8, { timeoutMs, label: 'reconciled Speed IX' })
        record('reconciliation', { transaction: checkpoint.transaction, tier: state.tracks.economy, ...balance })
      })
      await context.step('personal booster activation consumes one credit despite duplicate clicks', async () => {
        await context.command(`/companions booster give ${bot.username} qa_personal 2`, /Saved 2 qa_personal booster credits/, timeoutMs)
        await booster()
        await item(13, /Owned activations: 3/)
        replayNextClick = true
        const message = context.waitForMessage(/Saved successfully/, timeoutMs)
        const receipt = context.waitForMessage(/Transaction or activation ID: [0-9a-f-]{36}/i, timeoutMs)
        await click(24)
        await message
        const receiptText = await receipt
        await screen('active')
        const state = await stateUntil(context, value => value.personalBoosters.length === 1 && value.credits.qa_personal === 2,
          'Duplicate activation consumed multiple credits')
        context.expect(state.rates.companion_xp === 50, 'Personal booster rate incorrect', { state })
        context.expect(receiptText.includes(state.personalBoosters[0].activationId), 'Activation receipt does not identify the saved boost',
          { receipt: receiptText, activation: state.personalBoosters[0].activationId })
        record('activation-receipt', { receipt: receiptText, activation: state.personalBoosters[0].activationId })
      })
      await context.step('overlapping personal boosters queue and do not multiply their rates', async () => {
        await booster()
        const message = context.waitForMessage(/Saved successfully/, timeoutMs)
        await click(24)
        await message
        await screen('active')
        const state = await stateUntil(context, value => value.personalBoosters.length === 2, 'Second booster was not queued')
        context.expect(state.credits.qa_personal === 1 && state.personalBoosters.filter(value => value.queued).length === 1
          && state.rates.companion_xp === 50, 'Queue stacking or credit count incorrect', { state })
        await item(await content(/QA Personal/), /Running|Queued/)
        record('personal-queue', { boosters: state.personalBoosters, credits: state.credits })
      })
      await context.step('server boost is shared across distant real players and combined rates are capped', async () => {
        await close()
        peer = await context.connectActor('ByteProgressPeer')
        await context.command(`/op ${peer.bot.username}`, /operator/i, timeoutMs)
        await peer.command('/bcqa move 8192 100 8192', /BCQA MOVE success=true/, 90_000)
        await peer.waitUntil(() => peer.bot.entity.position.distanceTo(peer.bot.entity.position.clone().set(8192, 100, 8192)) < 1,
          { timeoutMs, label: 'peer reaches separate region' })
        await peer.command('/bcqa platform', /BCQA PLATFORM/, timeoutMs)
        await stateUntil(context, value => value.ready, 'Peer progression did not load', peer)
        const ownerThread = await context.command(`/bcqa owner ${peer.bot.username}`, /BCQA OWNER/, timeoutMs)
        if (/folia|canvas/i.test(context.server.instance)) context.expect(ownerThread.includes('target=false'), 'Regional actors are not on separate owners', { ownerThread })
        await context.command('/companions booster start qa_server', /Saved successfully/, timeoutMs)
        const owner = await stateUntil(context, value => value.rates.companion_xp === 60, 'Combined rate did not respect cap')
        const visitor = await progressionState(peer)
        context.expect(visitor.personalBoosters.length === 0 && visitor.rates.companion_xp === 25
          && visitor.rates.experience_block === 50 && owner.rates.experience_block === 60,
          'Personal booster leaked or server booster missing for second player', { owner, visitor })
        const peerReward = await peer.command('/bcqa xp block', /BCQA SYNTHETIC_XP/, timeoutMs)
        context.expect(/base=10 result=15\b/.test(peerReward), 'Server boost did not affect a real peer without a companion', { peerReward })
        record('scoped-boosters', { owner, visitor, peerReward, ownerThread })
      })
      await context.step('boosted real ore XP uses one additive capped multiplier', async () => {
        const before = (await progressionState(context)).totalXp
        await mineNaturalOre()
        const after = await stateUntil(context, value => value.totalXp === before + 16, 'Boosted source XP was multiplied twice or uncapped')
        record('boosted-ore-xp', { before, after: after.totalXp, original: 10, percent: 60 })
      })
      await context.step('short booster expires without consuming another credit', async () => {
        await context.command(`/companions booster give ${bot.username} qa_short 1`, /Saved 1 qa_short booster credits/, timeoutMs)
        await booster(/QA Short/)
        const message = context.waitForMessage(/Saved successfully/, timeoutMs)
        await click(24)
        await message
        await stateUntil(context, value => value.rates.block_drops === 10, 'Short booster did not activate')
        const expired = await stateUntil(context, value => value.rates.block_drops === 0
          && !value.personalBoosters.some(boost => boost.boosterId === 'qa_short'), 'Short booster did not expire', context, 8_000)
        context.expect(expired.credits.qa_short === 0, 'Expiry restored or spent an extra credit')
      })
      await context.step('administrator cancellation removes only the selected queued activation', async () => {
        await close()
        const before = await progressionState(context)
        const queued = before.personalBoosters.find(value => value.queued)
        context.expect(queued, 'Expected a queued activation to cancel')
        await context.command(`/companions booster cancel ${queued.activationId}`, /Saved successfully/, timeoutMs)
        const after = await stateUntil(context, value => value.personalBoosters.length === 1, 'Cancelled activation remained')
        context.expect(after.rates.companion_xp === 60 && after.credits.qa_personal === before.credits.qa_personal,
          'Cancellation changed remaining rate or refunded a credit', { before, after })
      })
      await context.step('booster-only reload restores online data without re-equipping companions', async () => {
        await close()
        await context.command('/companions despawn bee', /unequipped/, timeoutMs)
        const boosterOnly = enableProgression(original).replace(/^progression\s*=\s*true/m, 'progression = false')
        await writeAndReload(context, configPath, boosterOnly)
        const state = await stateUntil(context, value => value.ready && value.rates.experience_block === 60,
          'Booster-only online data did not reload')
        const selected = await context.command('/bcqa state', /BCQA STATE/, timeoutMs)
        context.expect(/base=-1\b/.test(selected), 'Booster-only reload re-equipped a companion', { selected })
        const reward = await context.command('/bcqa xp block', /BCQA SYNTHETIC_XP/, timeoutMs)
        context.expect(/base=10 result=16\b/.test(reward), 'Booster-only reward was lost or multiplied incorrectly', { reward })
        record('booster-only-reload', { state, selected, reward })
        await writeAndReload(context, configPath, enableProgression(original))
        await stateUntil(context, value => value.ready, 'Progression did not restore after booster-only check')
        await context.command('/companions spawn bee', /You have equipped bee/, timeoutMs)
      })
      await context.step('invalid canonical config preserves paid progress and accepted runtime', async () => {
        const before = await progressionState(context)
        await rejectFileReload(context, { filename: definitionsPath, content: 'companions = [\n' })
        const after = await progressionState(context)
        context.expect(after.totalXp === before.totalXp && sameMap(after.tracks, before.tracks)
          && after.serverBoosters[0].activationId === before.serverBoosters[0].activationId, 'Failed preflight replaced paid runtime state')
        await writeFile(definitionsPath, progressionProfile, 'utf8')
      })
      await context.step('reload preserves tiers credits and expiry while closing stale confirmation', async () => {
        await booster()
        await click(20, 'purchase')
        const before = await progressionState(context)
        const stale = paymentClick
        await reload()
        await context.waitUntil(() => bot.currentWindow === null, { timeoutMs, label: 'old purchase inventory closed' })
        context.expect(stale?.value?.slot === 22 && stale.value.mouseButton === 1,
          'No actual right-click payment-confirmation packet was captured', { stale })
        bot._client.write(stale.name, stale.value)
        const after = await stateUntil(context, value => value.ready, 'Reloaded progression did not become ready')
        context.expect(after.totalXp === before.totalXp && sameMap(after.tracks, before.tracks)
          && sameMap(after.credits, before.credits), 'Reload or stale click changed paid state', { before, after })
        context.expect(after.serverBoosters[0].expiresAt === before.serverBoosters[0].expiresAt, 'Server booster duration reset on reload')
        const balance = await economy()
        context.expect(balance.withdrawals === 3, 'Stale click charged money after reload', balance)
        checkpoint = { ...checkpoint, state: after, profile: progressionProfile, recordedAt: new Date().toISOString() }
        await writeFile(path.join(directory, 'bytecompanions-progression-checkpoint.json'), JSON.stringify(checkpoint, null, 2), 'utf8')
        record('reload-persistence', { before, after, ...balance })
      })
    } finally {
      bot._client.write = originalWrite
      const failures = []
      for (const node of [...permissions]) {
        try { await permission(node, 'clear') } catch (error) { failures.push(error) }
      }
      try { if (bot.currentWindow) bot.closeWindow(bot.currentWindow) } catch (error) { failures.push(error) }
      try { await context.command('/companions despawn bee') } catch (error) { failures.push(error) }
      try {
        await mutateAndReload(context, async () => {
          const restores = await Promise.allSettled([
            writeFile(definitionsPath, definitions, 'utf8'), writeFile(configPath, original, 'utf8')])
          const rejected = restores.filter(result => result.status === 'rejected').map(result => result.reason)
          if (rejected.length) throw new AggregateError(rejected, 'Could not restore progression configuration')
        })
      } catch (error) { failures.push(error) }
      if (failures.length) throw new AggregateError(failures, 'Progression QA cleanup failed')
    }
  }
}
