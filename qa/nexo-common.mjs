import { readFile } from 'node:fs/promises'
import path from 'node:path'

export async function requireNexoInstance(context) {
  context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(context.server.directory, '.server-source'), 'utf8')),
    'Nexo checks require an isolated server')
}

export async function inspectNexo(context, mode) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    const message = await context.command(`/bcqa nexo ${mode}`, /BCQA NEXO/, deadline - Date.now())
    if (message === 'BCQA NEXO LOADING') {
      await context.sleep(200)
      continue
    }
    const expected = mode === 'invalid'
      ? 'BCQA NEXO INVALID PASS unknown=AIR empty=AIR'
      : `BCQA NEXO ${mode.toUpperCase()} PASS id=bytecompanions_qa material=DIAMOND`
    context.expect(message === expected, 'Nexo provider or equipped metadata differs', { mode, message, expected })
    return
  }
  context.expect(false, 'Nexo registry or companion state did not become ready', { mode })
}
