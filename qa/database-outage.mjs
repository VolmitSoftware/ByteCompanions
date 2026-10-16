import {readFile, writeFile} from 'node:fs/promises'
import path from 'node:path'
import {databaseContext, databaseTimeoutMs as timeoutMs, durablePlayer, requireSameDurable, equipAfterCoreLoad, completeDatabaseCleanup} from './database-common.mjs'
import {enableProgression, progressionProfile} from './progression.mjs'
import {readyProgression, vaultState} from './vault-foundation.mjs'
import {componentText, itemText} from './merged-features.mjs'
import {observeCompanion} from './persistence-common.mjs'
import {mutateAndReload,reloadFiles} from './file-reload.mjs'

const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi, '').trim()
export default {
  name:'bytecompanions-database-outage',
  description:'Real database outage: failed selection saves retain prior SQL rows, journal preparation prevents charging, and explicit retries recover after the owned engine returns.',
  async run(context) {
    const database = await databaseContext(context)
    const {bot} = context
    const uuid = bot.player?.uuid
    context.expect(bot.username === 'ByteVault' && /^[a-f0-9-]{36}$/i.test(uuid ?? ''), 'Expected the established economy player')
    const configPath = path.join(database.directory, 'plugins/ByteCompanions/config.toml')
    const definitionPath = path.join(database.directory, 'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath, 'utf8')
    const definitions = await readFile(definitionPath, 'utf8')
    const companion = observeCompanion(context)
    const record = (kind, values) => context.report.observations.push({kind,...values})
    context.expect(/autoReload\s*=\s*true/.test(original),'Database outage QA requires autoReload=true')
    const reload = () => reloadFiles(context)
    const inventory = () => Object.fromEntries([false,true].map(named => [named?'named':'plain', bot.inventory.items()
      .filter(item => item.name === 'emerald' && /QA Named Emerald/.test(plain(itemText(item))) === named)
      .reduce((sum,item) => sum + item.count,0)]))
    async function close() {
      if (bot.currentWindow) await bot.clickWindow(49,1,0)
      await context.waitUntil(() => !bot.currentWindow,{timeoutMs,label:'server-confirmed database test menu close'})
    }
    const screen = title => context.waitUntil(() => bot.currentWindow?.inventoryStart === 54 && plain(bot.currentWindow.title).includes(title),{timeoutMs,label:title})
    async function purchase() {
      await close()
      await context.command('/companions boosts')
      await screen('Companion Boosters')
      const slot = await context.waitUntil(() => {
        const found = bot.currentWindow.slots.slice(0,54).findIndex(item => plain(itemText(item)).includes('QA Personal'))
        return found >= 0 ? {value:found} : false
      },{timeoutMs,label:'QA Personal booster'})
      await bot.clickWindow(slot.value,1,0)
      await screen('Booster Details')
      await bot.clickWindow(20,1,0)
      await screen('Confirm Purchase')
    }
    let stopped = false
    let before
    let beforeAssets
    let scenarioFailure
    try {
      await context.step('persisted paid state and both external selection tables establish the outage baseline',async () => {
        await mutateAndReload(context,async () => {
          await writeFile(definitionPath,progressionProfile,'utf8')
          await writeFile(configPath,enableProgression(original),'utf8')
        })
        const state = await readyProgression(context)
        context.expect(state.totalXp === 100 && state.tracks.economy === 1 && state.personalBoosters.length === 1,'Established paid state missing',{state})
        await equipAfterCoreLoad(context,'/companions spawn bee',/^You have equipped bee\.$/i,timeoutMs)
        await companion.find('outage baseline bee')
        await context.command('/companions particles equip bee angel_ring_effect',/Equipped particle effect: angel_ring_effect\./i,timeoutMs)
        await companion.requireParticles('outage baseline')
        before = durablePlayer(await database.command('snapshot'),uuid)
        context.expect(before.companions.length === 1 && before.companions[0].companion === 'bee'
          && before.particles.length === 1 && before.particles[0].effect === 'angel_ring_effect'
          && before.progression.length === 1 && before.purchases.length >= 2,'External rows do not contain the saved gameplay state',{before})
        beforeAssets = {inventory:inventory(),economy:await vaultState(context),state}
        context.expect(beforeAssets.economy.balance === 93 && beforeAssets.inventory.plain === 13 && beforeAssets.inventory.named === 4,'Prior actual-provider checkpoint differs',{beforeAssets})
        record('database-outage-baseline',{uuid,before,beforeAssets})
      })
      await context.step('owned database process stops while the Minecraft player remains online',async () => {
        stopped = true
        await database.command('stop')
        context.expect(bot.health > 0 && bot.entity,'Minecraft player disappeared during database stop')
      })
      await context.step('particle and companion write failures report unsaved session changes',async () => {
        const particle = await context.command('/companions particles unequip bee',/could not be saved/i,timeoutMs)
        const selection = await context.command('/companions despawn bee',/could not be saved/i,timeoutMs)
        record('database-save-warnings',{particle,selection})
      })
      await context.step('failed journal preparation rejects purchase before any real money or item debit',async () => {
        await purchase()
        const response = context.waitForMessage(/purchase could not be saved/i,timeoutMs)
        await bot.clickWindow(22,1,0)
        const message = await response
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(economy.balance === beforeAssets.economy.balance
          && JSON.stringify(inventory()) === JSON.stringify(beforeAssets.inventory)
          && !state.credits.qa_personal,'Failed preparation charged money/items or granted credit',{economy,state,inventory:inventory(),message})
        record('database-rejected-before-payment',{economy,state,inventory:inventory(),message})
        await close()
      })
      await context.step('database restart retains every prior durable selection and purchase key',async () => {
        await database.command('start'); stopped = false
        const restored = durablePlayer(await database.command('snapshot'),uuid)
        requireSameDurable(context,restored,before,'An unsuccessful outage write changed durable state')
        record('database-outage-rows-preserved',{before,restored})
      })
      await context.step('explicit selection and particle retries succeed through the recovered pools',async () => {
        await context.command('/companions spawn bee',/You have equipped bee\./i,timeoutMs)
        await companion.find('bee re-equipped after pool recovery')
        await context.command('/companions particles equip bee angel_ring_effect',/Equipped particle effect: angel_ring_effect\./i,timeoutMs)
        await companion.requireParticles('after database pool recovery')
        const restored = durablePlayer(await database.command('snapshot'),uuid)
        requireSameDurable(context,restored,before,'Explicit selection retry changed unrelated durable keys')
      })
      await context.step('a different selection after a failed removal replaces the complete durable companion set',async () => {
        stopped = true
        await database.command('stop')
        await context.command('/companions particles unequip bee',/could not be saved/i,timeoutMs)
        const warning = await context.command('/companions despawn bee',/could not be saved/i,timeoutMs)
        await database.command('start'); stopped = false
        const unchanged = durablePlayer(await database.command('snapshot'),uuid)
        requireSameDurable(context,unchanged,before,'Failed second removal changed durable rows')
        await context.command('/companions spawn rainbow',/You have equipped rainbow\./i,timeoutMs)
        const replaced = durablePlayer(await database.command('snapshot'),uuid)
        const expected = {...before,companions:[{...before.companions[0],companion:'rainbow'}]}
        requireSameDurable(context,replaced,expected,'Replacement retained a stale companion or changed unrelated records')
        record('database-replacement-recovery',{warning,before,replaced,expected})
        await context.command('/companions despawn rainbow',/Your companion has been unequipped\./i,timeoutMs)
        await context.command('/companions spawn bee',/You have equipped bee\./i,timeoutMs)
        await context.command('/companions particles equip bee angel_ring_effect',/Equipped particle effect: angel_ring_effect\./i,timeoutMs)
        await companion.find('bee restored after replacement recovery')
        await companion.requireParticles('replacement recovery')
        requireSameDurable(context,durablePlayer(await database.command('snapshot'),uuid),before,'Restoring the original selection changed durable keys')
      })
      await context.step('explicit purchase retry debits once and commits its credit to the real database',async () => {
        await purchase()
        const response = context.waitForMessage(/Saved successfully/i,timeoutMs)
        await bot.clickWindow(22,1,0); await response
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        context.expect(economy.balance === 86 && inventory().plain === 12 && inventory().named === 4 && state.credits.qa_personal === 1,
          'Recovered purchase did not commit exactly one charge/credit',{economy,state,inventory:inventory()})
        const durable = durablePlayer(await database.command('snapshot'),uuid)
        context.expect(durable.progression[0].credits.qa_personal === 1 && durable.purchases.length === before.purchases.length + 1,
          'Recovered credit or journal missing from external SQL rows',{durable,before})
        await writeFile(path.join(database.directory,'bytecompanions-database-checkpoint.json'),JSON.stringify({uuid,durable,economy,inventory:inventory(),state,recordedAt:new Date().toISOString()},null,2))
        record('database-recovery-checkpoint',{uuid,durable,economy,inventory:inventory(),state})
      })
    } catch (failure) {
      scenarioFailure = failure
    } finally {
      await completeDatabaseCleanup(context,scenarioFailure,{
        restoreDatabase:stopped ? () => database.command('start') : null,
        closeMenu:close,
        closeObserver:() => companion.close(),
        restoreDefinitions:() => writeFile(definitionPath,definitions,'utf8'),
        restoreConfig:() => writeFile(configPath,original,'utf8'),
        reload
      })
    }
  }
}
