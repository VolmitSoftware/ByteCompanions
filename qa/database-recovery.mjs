import {readFile,writeFile} from 'node:fs/promises'
import path from 'node:path'
import {databaseContext,databaseTimeoutMs as timeoutMs,durablePlayer,requireSameDurable} from './database-common.mjs'
import {enableProgression,progressionProfile} from './progression.mjs'
import {readyProgression,vaultState} from './vault-foundation.mjs'
import {componentText,itemText} from './merged-features.mjs'
import {observeCompanion,companionAlreadyEquipped} from './persistence-common.mjs'
import {mutateAndReload} from './file-reload.mjs'

export default {
  name:'bytecompanions-database-recovery',
  description:'After a verified process restart or database restore, require automatic companion/particle restoration and exact durable progression, purchase, booster and inventory state.',
  async run(context) {
    const database = await databaseContext(context)
    const checkpoint = JSON.parse(await readFile(path.join(database.directory,'bytecompanions-database-checkpoint.json'),'utf8'))
    const transition = JSON.parse(await readFile(path.join(database.directory,'bytecompanions-database-transition.json'),'utf8'))
    context.expect(context.bot.player?.uuid === checkpoint.uuid,'Recovery player differs from the durable checkpoint')
    context.expect(['server-restart','database-restored'].includes(transition.kind)
      && Date.parse(transition.recordedAt) > Date.parse(checkpoint.recordedAt),'Recovery transition is missing or stale')
    if (transition.kind === 'server-restart') context.expect(transition.previousProcessExited && transition.previousProcessIds.length > 0,'Full server process exit is unproven')
    else context.expect(transition.receipt.Passed && transition.receipt.Action === 'start','Database restore was not confirmed')
    const companion = observeCompanion(context)
    const configPath = path.join(database.directory,'plugins/ByteCompanions/config.toml')
    const definitionPath = path.join(database.directory,'plugins/ByteCompanions/features/progression/config.toml')
    const original = await readFile(configPath,'utf8')
    const definitions = await readFile(definitionPath,'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original),'Database recovery QA requires autoReload=true')
    try {
      await context.step('saved bee and angel ring restore before any equip command',async () => {
        await companion.find('automatic external database recovery')
        await companion.requireParticles(transition.kind)
        await context.command('/companions spawn bee',companionAlreadyEquipped,timeoutMs)
        await context.command('/companions particles equip bee angel_ring_effect',/angel_ring_effect is already equipped/i,timeoutMs)
      })
      await context.step('external rows preserve selections, paid tiers, credits, boosters and journal identities',async () => {
        const actual = durablePlayer(await database.command('snapshot'),checkpoint.uuid)
        requireSameDurable(context,actual,checkpoint.durable,'Recovery changed durable database keys')
        context.report.observations.push({kind:'database-durable-recovery',actual,expected:checkpoint.durable,transition})
      })
      await context.step('restored progression and real payment assets match the committed checkpoint',async () => {
        await mutateAndReload(context,async () => {
          await writeFile(definitionPath,progressionProfile,'utf8')
          await writeFile(configPath,enableProgression(original),'utf8')
        })
        const state = await readyProgression(context)
        const economy = await vaultState(context)
        const plain = value => componentText(value).replace(/§[0-9a-fk-orx]/gi,'')
        const inventory = Object.fromEntries([false,true].map(named => [named?'named':'plain',context.bot.inventory.items()
          .filter(item => item.name === 'emerald' && /QA Named Emerald/.test(plain(itemText(item))) === named)
          .reduce((sum,item) => sum + item.count,0)]))
        context.expect(state.totalXp === checkpoint.state.totalXp && state.tracks.economy === 1
          && state.credits.qa_personal === 1 && state.personalBoosters[0]?.activationId === checkpoint.state.personalBoosters[0]?.activationId
          && economy.balance === 86 && inventory.plain === 12 && inventory.named === 4,'Recovery lost state or duplicated a payment',{state,economy,inventory,checkpoint})
        context.report.observations.push({kind:'database-gameplay-recovery',state,economy,inventory})
      })
      await context.step('recovered paid bonus and saved particle selection still work after reload',async () => {
        const speed = context.bot.registry.effectsByName.Speed.id
        await context.waitUntil(() => context.bot.entity.effects?.[speed]?.amplifier === 6,{timeoutMs,label:'paid Speed VII after external database recovery'})
        await companion.find('companion after database recovery reload')
        await companion.requireParticles('recovery reload')
      })
    } finally {
      companion.close()
      await mutateAndReload(context,async () => {
        await writeFile(definitionPath,definitions,'utf8')
        await writeFile(configPath,original,'utf8')
      })
    }
  }
}
