const timeoutMs = 8_000

export default {
  name: 'bytecompanions-command-permissions',
  description: 'Run with --no-op and a fresh username to verify command permissions are enforced before gameplay actions.',
  async run(context) {
    await context.step('help remains accessible without command permissions', async () => {
      await context.command('/companions help', /⇀ language(?: |$)/, timeoutMs)
      const matches = await context.bot.tabComplete('/companions ', false, false, timeoutMs)
      const suggestions = matches.map(value => typeof value === 'string' ? value : value.match)
      context.expect(suggestions.includes('help'), 'Help completion missing')
      context.expect(suggestions.every(value => value === 'help' || value === 'language'), 'Unauthorized commands were suggested', { suggestions })
    })
    for (const command of ['menu', 'spawn bee', 'despawn', 'debug', 'debug upload', 'config', 'version']) {
      await context.step(`deny /companions ${command}`, async () => {
        await context.sleep(1_100)
        await context.command(`/companions ${command}`, /You do not have permission to perform this action\./, timeoutMs)
        context.expect(context.bot.currentWindow === null, 'Unauthorized command opened an inventory')
      })
    }
    for (const action of ['menu bee', 'equip bee angel_ring_effect', 'unequip bee']) {
      await context.step(`deny particles ${action}`, async () => {
        await context.sleep(1_100)
        await context.command(`/companions particles ${action}`, /You do not have permission to perform this action\./, timeoutMs)
      })
    }
  }
}
