import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'

const timeoutMs = 15_000

async function reportFiles(directory) {
  try {
    return (await readdir(directory)).filter(name => name.endsWith('.txt'))
  } catch (error) {
    if (error.code === 'ENOENT') return []
    throw error
  }
}

export default {
  name: 'bytecompanions-diagnostics',
  description: 'Verify permission-visible diagnostics, explicit upload syntax, and local reports before and after runtime reload.',
  async run(context) {
    const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'debug')
    const initial = await reportFiles(directory)
    await context.step('diagnostics is available to an authorized operator', async () => {
      const matches = await context.bot.tabComplete('/companions debug ', false, false, timeoutMs)
      context.expect(matches.some(value => (typeof value === 'string' ? value : value.match) === 'upload'),
        'Explicit upload argument is missing from completion', { matches })
      await context.command('/companions debug invalid', /Usage: \/companions debug \[upload\]/, timeoutMs)
      await context.command('/companions debug upload extra', /Unexpected argument "extra"\./, timeoutMs)
      context.expect((await reportFiles(directory)).length === initial.length, 'Invalid arguments created a report')
    })
    for (const stage of ['initial', 'after reload']) {
      if (stage === 'after reload') {
        await reloadFiles(context)
      }
      await context.step(`save a local report ${stage}`, async () => {
        const previous = new Set(await reportFiles(directory))
        await context.command('/companions debug', /Saved ByteCompanions debug dump to /, timeoutMs)
        const file = (await reportFiles(directory)).find(name => !previous.has(name))
        context.expect(typeof file === 'string', 'Local diagnostic report was not created')
        const report = await readFile(path.join(directory, file), 'utf8')
        context.expect(report.includes('ByteCompanions runtime'), 'Plugin contribution is missing')
        context.expect(report.includes('Active: true'), 'Report captured an inactive runtime')
        context.expect(report.includes('Companion definitions: 5'), 'Fresh companion definitions are missing')
        context.expect(report.includes('Feature particles: true'), 'Feature state is missing')
        context.expect(report.includes('Configuration source: config.toml'), 'TOML configuration source is missing')
        context.expect(report.includes('Default language: en_US'), 'Default language is missing')
        context.expect(report.includes('Player tick tasks:'), 'Owned scheduler snapshot is missing')
        context.expect(/Storage queue .+ pending operations:/.test(report), 'Ordered storage snapshot is missing')
        context.expect(report.includes('Particle packet entries:'), 'Particle traffic counters are missing')
        context.expect(!/password\s*[:=]|jdbc:(?:h2|mysql)|username\s*[:=]/i.test(report),
          'Report exposed storage credentials or connection configuration')
        context.report.observations ??= []
        context.report.observations.push({ kind: 'diagnostic-report', stage, file, bytes: Buffer.byteLength(report) })
      })
    }
  }
}
