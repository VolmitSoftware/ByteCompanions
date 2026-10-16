import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { assertIsolated, runtimeReport, writeAndReload } from './file-reload.mjs'

export default {
  name: 'bytecompanions-file-reload-baseline',
  description: 'Reproduce the old false-restoration setting leaving a connected player permanently unavailable despite a healthy database.',
  async run(context) {
    await assertIsolated(context)
    const filename = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'config.toml')
    const original = await readFile(filename, 'utf8')
    context.expect(/autoReload\s*=\s*true/.test(original), 'Baseline requires automatic root configuration reload')
    context.expect(/loadOnlinePlayersAfterReload\s*=\s*false/.test(original), 'Baseline must retain the original false restoration default')
    const collection = async () => JSON.parse((await context.command('/bcqa collection', /BCQA COLLECTION /, 15_000)).split('BCQA COLLECTION ')[1])
    try {
      await context.step('initial connected player and database are ready', async () => {
        await context.command('/gamemode creative', /game mode|gamemode/i, 15_000)
        await context.command('/bcqa move 0 100 0', /BCQA MOVE success=true/, 15_000)
        await context.command('/bcqa platform', /BCQA PLATFORM blocks=49/, 15_000)
        await context.waitUntil(async () => (await context.command('/bcqa api inspect', /BCQA API (?:PASS|LOADING)/, 15_000)).includes('PASS'),
          { timeoutMs: 30_000, label: 'core player initially ready' })
        await context.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/, 15_000)
      })
      await context.step('selection and viewer preferences save before the root file edit', async () => {
        await context.command('/companions spawn bee', /You have equipped|already have/, 15_000)
        await context.command('/companions settings particles reduced', /Collection saved\./, 15_000)
        const state = await collection()
        context.expect(state.ready && state.companions.bee, 'Initial equipped selection is unavailable', state)
      })
      await context.step('root file change activates a healthy replacement runtime', async () => {
        const changed = original.replace(/viewDistance\s*=\s*(\d+)/, (_, value) => `viewDistance = ${Number(value) + 1}`)
        context.expect(changed !== original, 'Root edit did not change view distance')
        const after = await writeAndReload(context, filename, changed, { requirePlayer: false })
        await context.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/, 15_000)
        context.report.observations ??= []
        context.report.observations.push({ kind: 'baseline-replacement', generation: after.generation, active: after.active })
      })
      await context.step('old core player remains absent while collection and H2 remain healthy', async () => {
        const observed = []
        for (let index = 0; index < 6; index++) {
          const api = await context.command('/bcqa api inspect', /BCQA API (?:PASS|LOADING)/, 15_000)
          const state = await collection()
          context.expect(api === 'BCQA API LOADING', 'Expected old-artifact missing core player was not reproduced', { api, state })
          context.expect(state.ready && state.profile.viewerPreferences.particleIntensity === 'REDUCED', 'Collection should remain ready independently', state)
          observed.push({ at: new Date().toISOString(), api, state })
          if (index < 5) await context.sleep(1500)
        }
        await context.command('/companions spawn bee', /Your companion data is still loading or could not be loaded/, 15_000)
        await context.command('/bcqa h2diagnostics', /BCQA H2_DIAGNOSTICS PASS/, 15_000)
        context.report.observations.push({ kind: 'known-defect-reproduced', observed, runtime: await runtimeReport(context) })
      })
    } finally {
      await writeFile(filename, original, 'utf8')
    }
  }
}
