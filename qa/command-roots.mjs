import { writeFile } from 'node:fs/promises'
import path from 'node:path'
import { assertIsolated, reloadFiles } from './file-reload.mjs'
import { componentText } from './merged-features.mjs'

const timeoutMs = 15_000
export const roots = ['companions', 'bytecompanions']
const namespaceRoots = ['bytecompanions:companions', 'bytecompanions:bytecompanions']

export async function plainRoots(context) {
  for (const root of roots) {
    await context.command(`/${root}`, /⇀ version(?: |$)/, timeoutMs)
    await context.command(`/${root} version`, /ByteCompanions.*v\d/, timeoutMs)
  }
}

export async function fallbackDispatch(context) {
  for (const root of namespaceRoots) {
    await context.command(`/${root} version`, /ByteCompanions.*v\d/, timeoutMs)
  }
}

export async function commandTree(context, stage) {
  let packet
  const receive = value => { packet = value }
  context.bot._client.on('declare_commands', receive)
  try {
    await context.command('/bcqa commands', /BCQA COMMANDS_REFRESHED/, timeoutMs)
    await context.waitUntil(() => packet, { timeoutMs, label: 'refreshed command tree reaches client' })
    context.expect(Array.isArray(packet.nodes) && Number.isInteger(packet.rootIndex), 'Malformed command tree', packet)
    const names = packet.nodes[packet.rootIndex].children.map(index => packet.nodes[index].extraNodeData?.name)
      .filter(name => typeof name === 'string').sort()
    context.expect(roots.every(root => names.includes(root)), 'Plain command root missing from client command tree', { names })
    context.expect(namespaceRoots.every(root => names.includes(root)), 'Standard namespaced root missing from client command tree', { names })
    context.expect(names.includes('volmit'), 'Shared Volmit command disappeared from client suggestions', { names })
    context.expect(names.includes('minecraft:help') || names.includes('bukkit:help'),
      'Foreign namespaced help commands disappeared from client suggestions', { names })
    const suggestions = {}
    for (const root of roots) {
      suggestions[root] = (await context.bot.tabComplete(`/${root} `, false, false, timeoutMs))
        .map(value => typeof value === 'string' ? value : value.match).sort()
      context.expect(['help', 'menu', 'version'].every(command => suggestions[root].includes(command)),
        'Root subcommand suggestions are incomplete', { root, suggestions: suggestions[root] })
    }
    context.expect(JSON.stringify(suggestions.companions) === JSON.stringify(suggestions.bytecompanions),
      'Plain roots expose different subcommands', suggestions)
    context.report.commandTrees ??= []
    context.report.commandTrees.push({ stage, roots: names, suggestions })
    return names
  } finally {
    context.bot._client.removeListener('declare_commands', receive)
  }
}

export default {
  name: 'bytecompanions-command-roots',
  description: 'Verify plain command roots, client suggestions, GUI dispatch, and hot-reload registration.',
  async run(context) {
    await assertIsolated(context)
    await context.step('both plain roots show help and installed version', async () => plainRoots(context))
    await context.step('both plain roots open the companion collection menu', async () => {
      for (const root of roots) {
        const opened = context.waitForEvent('windowOpen', () => true, timeoutMs)
        await context.command(`/${root} menu`)
        const [window] = await opened
        context.expect(componentText(window.title).includes('Companion Collection'), 'Alias opened the wrong menu')
        await context.waitUntil(() => context.bot.currentWindow?.slots[49], { timeoutMs, label: 'collection close button' })
        await context.bot.clickWindow(49, 1, 0)
        await context.waitUntil(() => !context.bot.currentWindow, { timeoutMs, label: 'collection menu closes' })
      }
    })
    await context.step('client command tree and subcommands expose both plain roots', async () => commandTree(context, 'initial'))
    await context.step('standard namespaced fallbacks remain available for explicit dispatch', async () => fallbackDispatch(context))
    await context.step('plain roots and client suggestions survive accepted file reload', async () => {
      await reloadFiles(context)
      await plainRoots(context)
      await fallbackDispatch(context)
      await commandTree(context, 'reloaded')
    })
    await context.step('command registration is ready for a full process restart', async () => {
      await writeFile(path.join(context.server.directory, '.qa-command-roots.json'), JSON.stringify({
        username: context.bot.username, uuid: context.bot.player.uuid
      }), 'utf8')
    })
  }
}
