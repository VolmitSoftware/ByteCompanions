import { reloadFiles } from './file-reload.mjs'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const timeoutMs = 15_000

export default {
  name: 'bytecompanions-integrations',
  description: 'Exercise the external public API and real PlaceholderAPI through selection, hidden visuals, asynchronous requests, and reload.',
  async run(context) {
    context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(context.server.directory, '.server-source'), 'utf8')),
      'Integration checks require an isolated server')
    async function inspect(expected) {
      const deadline = Date.now() + timeoutMs
      while (Date.now() < deadline) {
        const value = await context.command('/bcqa api inspect', /BCQA API/, deadline - Date.now())
        if (value === 'BCQA API LOADING') {
          await context.sleep(100)
          continue
        }
        context.expect(expected.test(value), 'Unexpected public API state', {value,expected:String(expected)})
        return value
      }
      context.expect(false, 'Public API player never became available')
    }
    async function placeholder(mode, key, expected) {
      const value = await context.command(`/bcqa papi ${mode} %bytecompanions_${key}%`, /BCQA PAPI/, timeoutMs)
      context.expect(value === `BCQA PAPI [${expected}]`, 'Placeholder value differs', {mode,key,expected,value})
    }
    await context.step('external API resolves the current loaded player and shared services', async () => {
      await inspect(/BCQA API PASS selected= count=0 spawned=0$/)
    })
    await context.step('real PlaceholderAPI resolves empty selections and unknown parameters', async () => {
      await placeholder('owner','current','')
      await placeholder('owner','has-equipped_bee','false')
      await placeholder('owner','no-such-parameter','%bytecompanions_no-such-parameter%')
    })
    await context.step('public API equips a companion using the shared type and player identities', async () => {
      await context.command('/bcqa api equip bee', /BCQA API EQUIP success=true/, timeoutMs)
      await inspect(/selected=bee count=1 spawned=1$/)
    })
    await context.step('owner and asynchronous placeholder requests observe the equipped selection', async () => {
      for (const mode of ['owner','async']) {
        await placeholder(mode,'current','bee')
        await placeholder(mode,'has-equipped_bee','true')
      }
    })
    await context.step('permission placeholders resolve through the actual expansion', async () => {
      await placeholder('owner','has-unlocked_bee','true')
      await placeholder('async','has-unlocked_bee','true')
      await placeholder('owner','has-unlocked_missing_definition','false')
    })
    await context.step('hiding public API visuals retains selected companion placeholders', async () => {
      await context.command('/bcqa api hide', /selected=bee count=1 spawned=0$/, timeoutMs)
      await placeholder('async','has-equipped_bee','true')
      await context.command('/bcqa api show', /selected=bee count=1 spawned=1$/, timeoutMs)
    })
    await context.step('public API unequip immediately updates owner and asynchronous placeholders', async () => {
      await context.command('/bcqa api unequip bee', /BCQA API UNEQUIP success=true/, timeoutMs)
      await inspect(/selected= count=0 spawned=0$/)
      await placeholder('owner','current','')
      await placeholder('async','has-equipped_bee','false')
    })
    await context.step('plugin reload publishes a working API and replacement expansion', async () => {
      await reloadFiles(context)
      await inspect(/selected= count=0 spawned=0$/)
      await context.command('/bcqa api equip bee', /BCQA API EQUIP success=true/, timeoutMs)
      await placeholder('owner','current','bee')
      await placeholder('async','has-equipped_bee','true')
    })
    await context.step('PlaceholderAPI reload retains the embedded expansion', async () => {
      await context.command('/papi reload', /\d+\s+placeholder hook\(s\) registered!/, timeoutMs)
      await placeholder('owner','current','bee')
      await placeholder('async','has-equipped_bee','true')
    })
    await context.step('final public API cleanup removes the selection', async () => {
      await context.command('/bcqa api unequip bee', /BCQA API UNEQUIP success=true/, timeoutMs)
      await inspect(/selected= count=0 spawned=0$/)
    })
  }
}
