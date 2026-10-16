import { readFile } from 'node:fs/promises'
import path from 'node:path'

export async function requireItemsAdderInstance(context) {
  context.expect(/^isolated=true\s*$/m.test(await readFile(path.join(context.server.directory, '.server-source'), 'utf8')),
    'ItemsAdder checks require an isolated server')
}

export async function inspectItemsAdder(context, mode) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    const message = await context.command(`/bcqa itemsadder ${mode}`, /BCQA ITEMSADDER/, deadline - Date.now())
    if (message === 'BCQA ITEMSADDER LOADING') {
      await context.sleep(200)
      continue
    }
    const expected = mode === 'invalid'
      ? 'BCQA ITEMSADDER INVALID PASS unknown=AIR empty=AIR'
      : `BCQA ITEMSADDER ${mode.toUpperCase()} PASS namespace=_iainternal:icon_left_blue material=PAPER`
    context.expect(message === expected, 'ItemsAdder provider or equipped metadata differs', { mode, message, expected })
    return
  }
  context.expect(false, 'ItemsAdder registry or companion state did not become ready', { mode })
}
