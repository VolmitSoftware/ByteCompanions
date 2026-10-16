import { readFile, readdir, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'

export const reloadTimeoutMs = 45_000

export async function runtimeReport(context, { timeoutMs = 15_000 } = {}) {
  const directory = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'debug')
  const names = await readdir(directory).catch(error => {
    if (error.code === 'ENOENT') return []
    throw error
  })
  const previous = new Map(await Promise.all(names.map(async name => [name, (await stat(path.join(directory, name))).mtimeMs])))
  await context.command('/companions debug', /\.txt(?:[.\s]|$)/, timeoutMs)
  const filename = await context.waitUntil(async () => {
    for (const name of await readdir(directory)) {
      if (name.endsWith('.txt') && (!previous.has(name) || (await stat(path.join(directory, name))).mtimeMs !== previous.get(name))) return name
    }
    return false
  }, { timeoutMs, label: 'new runtime diagnostic' })
  const text = await readFile(path.join(directory, filename), 'utf8')
  const generation = Number(/Generation: (\d+)/.exec(text)?.[1])
  context.expect(Number.isSafeInteger(generation), 'Diagnostic omitted runtime generation', { filename, text })
  return { text, generation, active: text.includes('Active: true') }
}

export async function waitForFileReload(context, previousGeneration, { requirePlayer = true } = {}) {
  let last
  await context.waitUntil(async () => {
    context.expect(context.bot._client.state === 'play', 'Player disconnected during file reload')
    const log = await readFile(path.join(context.server.directory, 'logs', 'latest.log'), 'utf8')
    if (log.lastIndexOf('Plugin enabled successfully') <= log.lastIndexOf('Plugin runtime stopped')) return false
    try {
      last = await runtimeReport(context, { timeoutMs: 3000 })
    } catch (error) {
      if (context.bot._client.state === 'play' && /^Timed out waiting for messagestr after 3000ms$/.test(error.message)) return false
      throw error
    }
    if (!last.active || last.generation <= previousGeneration) return false
    if (!requirePlayer) return true
    return (await context.command('/bcqa api inspect', /BCQA API (?:PASS|LOADING)/, 15_000)).includes('BCQA API PASS')
  }, { timeoutMs: reloadTimeoutMs, intervalMs: 1200, label: 'file change activates replacement runtime and player session' })
  return last
}

export async function writeAndReload(context, filename, content, options) {
  return mutateAndReload(context, () => writeFile(filename, content, 'utf8'), options)
}

export async function mutateAndReload(context, mutate, options) {
  const before = await runtimeReport(context)
  await mutate()
  return waitForFileReload(context, before.generation, options)
}

export async function reloadFiles(context) {
  const filename = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'config.toml')
  const content = await readFile(filename, 'utf8')
  context.expect(/autoReload\s*=\s*true/.test(content), 'File-change reload requires settings.autoReload=true')
  return writeAndReload(context, filename, `${content}\n# QA file refresh ${Date.now()}\n`)
}

export async function rejectFileReload(context, { filename, content, expectedLog = /Configuration hotload rejected|Could not reload ByteCompanions|content.*rejected/i }) {
  return rejectFileMutation(context, () => writeFile(filename, content, 'utf8'), expectedLog)
}

export async function rejectFileMutation(context, mutate, expectedLog = /Configuration hotload rejected|Could not reload ByteCompanions|content.*rejected/i) {
  const before = await runtimeReport(context)
  const log = path.join(context.server.directory, 'logs', 'latest.log')
  const offset = (await readFile(log, 'utf8')).length
  await mutate()
  await context.waitUntil(async () => expectedLog.test((await readFile(log, 'utf8')).slice(offset)),
    { timeoutMs: reloadTimeoutMs, intervalMs: 500, label: 'invalid file change rejected' })
  const after = await runtimeReport(context)
  context.expect(after.active && after.generation === before.generation,
    'Rejected file change replaced the working runtime', { before, after })
  return after
}

export async function assertIsolated(context) {
  const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
  context.expect(/^isolated=true\s*$/m.test(source), 'File reload QA requires an isolated instance')
}
