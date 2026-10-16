import { readFile, realpath } from 'node:fs/promises'
import path from 'node:path'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { createHash } from 'node:crypto'

const execute = promisify(execFile)
const databaseRoot = path.resolve('C:/VolmitSoftware/ByteCompanions/.qa/database-acceptance')
export const databaseTimeoutMs = 30_000

export async function completeDatabaseCleanup(context, primaryFailure, actions) {
  const failures = []
  const attempt = async (phase, action) => {
    try { await action(); return true }
    catch (failure) {
      failures.push({phase,failure})
      return false
    }
  }
  const databaseReady = !actions.restoreDatabase || await attempt('restore-database',actions.restoreDatabase)
  await attempt('close-menu',actions.closeMenu)
  await attempt('close-observer',actions.closeObserver)
  const definitionsReady = await attempt('restore-definitions',actions.restoreDefinitions)
  const configReady = await attempt('restore-config',actions.restoreConfig)
  if (databaseReady && definitionsReady && configReady) await attempt('reload-restored-config',actions.reload)
  else {
    context.report.observations ??= []
    context.report.observations.push({kind:'database-cleanup-reload-skipped',databaseReady,definitionsReady,configReady})
  }
  if (failures.length) {
    context.report.observations ??= []
    context.report.observations.push({kind:'database-cleanup-failures',primaryFailure:primaryFailure?.message,
      failures:failures.map(({phase,failure}) => ({phase,message:failure.message}))})
    throw new AggregateError([...(primaryFailure ? [primaryFailure] : []),...failures.map(value => value.failure)],
      [primaryFailure?.message,...failures.map(({phase,failure}) => `${phase}: ${failure.message}`)].filter(Boolean).join('; '))
  }
  if (primaryFailure) throw primaryFailure
}

export function controllerFailureReceipt(stdout, engine, action) {
  if (typeof stdout !== 'string') return null
  try {
    const receipt = JSON.parse(stdout.slice(stdout.indexOf('{')))
    if (receipt.Passed !== false || receipt.Engine !== engine || receipt.Action !== action
      || typeof receipt.CheckedUtc !== 'string' || !Number.isFinite(Date.parse(receipt.CheckedUtc))
      || typeof receipt.Error !== 'string' || receipt.Error.length > 8000) return null
    return {Passed:false,Engine:engine,Action:action,CheckedUtc:receipt.CheckedUtc,Error:receipt.Error,
      ...(typeof receipt.ErrorType === 'string' && receipt.ErrorType.length <= 512 ? {ErrorType:receipt.ErrorType} : {}),
      ...(Number.isSafeInteger(receipt.ErrorLine) && receipt.ErrorLine > 0 ? {ErrorLine:receipt.ErrorLine} : {})}
  } catch { return null }
}

export async function equipAfterCoreLoad(context, command, success, timeoutMs = databaseTimeoutMs) {
  const observation = {kind:'core-load-command', command, timeoutMs, attempts:[]}
  context.report.observations ??= []
  context.report.observations.push(observation)
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const response = await context.command(command, /.+/, deadline - Date.now())
    observation.attempts.push({at:new Date().toISOString(), response})
    if (success.test(response)) return response
    context.expect(response === 'Your companion data is still loading or could not be loaded. Rejoin or ask an administrator.',
      'Equip returned an unexpected response while waiting for core data', {command,response})
    const remaining = deadline - Date.now()
    if (remaining > 0) await context.sleep(Math.min(150,remaining))
  }
  context.expect(false,'Core companion data did not load within the bounded equip deadline',observation)
}

export async function databaseContext(context) {
  const directory = context.server.directory
  context.expect(/^bytecompanions-database-[a-z0-9-]+$/.test(path.basename(directory))
    && /^isolated=true\s*$/m.test(await readFile(path.join(directory, '.server-source'), 'utf8')),
  'Database acceptance requires an owned isolated Multiplexor instance')
  const inputs = JSON.parse(await readFile(path.join(directory, 'bytecompanions-database-inputs.json'), 'utf8'))
  context.expect(['mysql', 'mariadb'].includes(inputs.engine), 'Unexpected database engine')
  context.expect(['paper12111', 'folia2612'].includes(inputs.profile), 'Unexpected database profile')
  const control = await realpath(inputs.controlScript)
  const relative = path.relative(databaseRoot, control)
  context.expect(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'Database controller is outside the owned QA directory')
  const controlHash = createHash('sha256').update(await readFile(control)).digest('hex')
  context.expect(controlHash === inputs.controlSha256.toLowerCase(), 'Database controller changed after deployment')
  context.report.observations ??= []
  async function command(action) {
    context.expect(['start', 'stop', 'snapshot'].includes(action), 'Unexpected database control action')
    let stdout
    try {
      ;({ stdout } = await execute('pwsh', ['-NoProfile', '-File', control, '-Engine', inputs.engine, '-Action', action,
        ...(action === 'snapshot' ? ['-Profile', inputs.profile] : [])],
        { timeout: 120_000, maxBuffer: 2_000_000, windowsHide: true }))
    } catch (failure) {
      const receipt = controllerFailureReceipt(failure.stdout, inputs.engine, action)
      context.report.observations.push({kind:'database-control-failure',engine:inputs.engine,action,
        exitCode:typeof failure.code === 'number' ? failure.code : null,
        receiptAvailable:receipt !== null,receipt})
      throw new Error(`Owned database ${action} failed (exit ${failure.code ?? 'unknown'}); inspect private controller evidence`)
    }
    const receipt = JSON.parse(stdout.slice(stdout.indexOf('{')))
    context.expect(receipt.Passed === true && receipt.Engine === inputs.engine && receipt.Action === action,
      'Database controller did not confirm the requested operation')
    context.report.observations.push({kind:'database-control', engine:inputs.engine, action,
      checkedUtc:receipt.CheckedUtc, processId:receipt.ProcessId, listenerOpen:receipt.ListenerOpen})
    if (action !== 'snapshot') return receipt
    const snapshotFile = await realpath(receipt.SnapshotPath)
    const snapshotRelative = path.relative(databaseRoot, snapshotFile)
    context.expect(snapshotRelative && !snapshotRelative.startsWith('..') && !path.isAbsolute(snapshotRelative),
      'Snapshot is outside the owned evidence directory')
    const bytes = await readFile(snapshotFile)
    const snapshot = JSON.parse(bytes)
    context.expect(snapshot.Engine === inputs.engine && typeof snapshot.ServerVersion === 'string', 'Snapshot engine identity missing')
    context.report.observations.push({kind:'database-snapshot', engine:inputs.engine, serverVersion:snapshot.ServerVersion,
      path:snapshotFile, sha256:createHash('sha256').update(bytes).digest('hex')})
    return snapshot
  }
  return {directory, inputs, command}
}

const normalizedUuid = value => String(value).replaceAll('-', '').toLowerCase()
const data = row => typeof row.data_json === 'string' ? JSON.parse(row.data_json) : row.data_json
const sorted = rows => rows.sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))
const boosts = values => sorted((values ?? []).map(value => ({activationId:value.activationId, boosterId:value.boosterId,
  scope:value.scope, percentages:value.percentages})))

export function durablePlayer(snapshot, uuid) {
  const same = value => normalizedUuid(value) === normalizedUuid(uuid)
  return {
    companions: sorted(snapshot.Core.companions_players.filter(row => same(row.player_id))
      .map(row => ({player:row.player_name, companion:row.companion_type_id}))),
    particles: sorted(snapshot.Particles.bcp_users.filter(row => same(row.owner_id))
      .map(row => ({companion:row.companion_type_id, effect:row.effect_id}))),
    progression: sorted(snapshot.Core.bc_progression_players.filter(row => same(row.player_id)).map(row => {
      const state = data(row)
      return {serverId:row.server_id, companions:state.companions, credits:state.credits, boosters:boosts(state.boosters)}
    })),
    purchases: sorted(snapshot.Core.bc_progression_purchases.filter(row => same(row.player_id))
      .map(row => ({serverId:row.server_id, transactionId:row.transaction_id, status:row.status, data:data(row)}))),
    globalBoosters: sorted(snapshot.Core.bc_progression_global.map(row => ({serverId:row.server_id, boosters:boosts(data(row).boosters)})))
  }
}

export function requireSameDurable(context, actual, expected, label) {
  const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === 'object'
    ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value
  context.expect(JSON.stringify(canonical(actual)) === JSON.stringify(canonical(expected)), label, {actual, expected})
}
