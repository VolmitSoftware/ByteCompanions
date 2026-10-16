import {readFile} from 'node:fs/promises'
import path from 'node:path'
import {databaseContext,databaseTimeoutMs as timeoutMs} from './database-common.mjs'
import {progressionState} from './progression.mjs'
import {vaultState} from './vault-foundation.mjs'

export default {
  name:'bytecompanions-database-failed-load',
  description:'Reconnect a previously saved player while the owned database is stopped and require failed loads to remain unavailable instead of installing empty defaults.',
  async run(context) {
    const database = await databaseContext(context)
    const transition = JSON.parse(await readFile(path.join(database.directory,'bytecompanions-database-transition.json'),'utf8'))
    const checkpoint = JSON.parse(await readFile(path.join(database.directory,'bytecompanions-database-checkpoint.json'),'utf8'))
    context.expect(transition.kind === 'database-stopped' && transition.receipt.Passed
      && transition.receipt.Action === 'stop' && Date.parse(transition.recordedAt) < Date.now()
      && Date.parse(transition.recordedAt) > Date.parse(checkpoint.recordedAt), 'No fresh driver-recorded database stop')
    context.expect(context.bot.player?.uuid === checkpoint.uuid,'Failed-load player differs from the durable checkpoint')
    await context.step('database outage produces an actual exceptional player load',async () => {
      const deadline = Date.now() + timeoutMs
      let failed = false
      while (Date.now() < deadline) {
        const log = await readFile(path.join(database.directory,'logs/latest.log'),'utf8')
        failed = log.includes(`Could not load companions for ${checkpoint.uuid}`)
        if (failed) break
        await context.sleep(250)
      }
      context.expect(failed,'Player load did not report its database failure within the bound')
      context.report.observations.push({kind:'database-failed-load',uuid:checkpoint.uuid,transition,exceptionObserved:true})
    })
    await context.step('unloaded player cannot replace saved companions or grant progression',async () => {
      const spawn = await context.command('/companions spawn bee',/still loading or could not be loaded/i,timeoutMs)
      const grant = await context.command(`/companions progression give ${context.bot.username} bee 1`,/progression data is not ready/i,timeoutMs)
      const state = await progressionState(context)
      context.expect(!state.ready,'Failed load installed an empty ready progression player',{state})
      const runtime = await context.command('/bcqa state',/BCQA STATE/,timeoutMs)
      context.expect(/base=-1\b/.test(runtime),'Failed load created a companion visual',{runtime})
      context.report.observations.push({kind:'database-unloaded-command-rejection',spawn,grant,state,runtime})
    })
    await context.step('failed loading does not charge the independent real economy ledger',async () => {
      const economy = await vaultState(context)
      context.expect(economy.balance === checkpoint.economy.balance,'Failed loading changed the real balance',{economy,expected:checkpoint.economy})
      context.report.observations.push({kind:'database-failed-load-assets',economy})
    })
  }
}
