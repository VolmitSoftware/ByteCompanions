import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { reloadFiles } from './file-reload.mjs'
import { observeCompanion } from './persistence-common.mjs'

const timeoutMs = 15_000

function interact(bot, entityId, hand, attack) {
  if (bot.supportFeature('attackUsesOwnPacket')) {
    if (attack) bot._client.write('attack', { entityId })
    else bot._client.write('use_entity', {
      target: entityId, hand: hand === 'OFF_HAND' ? 'off_hand' : 'main_hand',
      location: { x: 0, y: 0, z: 0 }, sneaking: false
    })
  } else {
    bot._client.write('use_entity', {
      target: entityId, mouse: attack ? 1 : 0,
      hand: hand === 'OFF_HAND' ? 1 : 0, sneaking: false
    })
  }
}

export default {
  name: 'bytecompanions-paper-compatibility',
  description: 'With an operator and the public API fixture, verify Component identity and main-hand, off-hand, and attack interaction packets before and after reload on conventional Paper-family servers.',
  async run(context) {
    const { bot } = context
    const configPath = path.join(context.server.directory, 'plugins', 'ByteCompanions', 'config.toml')
    const originalConfig = await readFile(configPath, 'utf8')
    const observer = observeCompanion(context)
    context.report.observations ??= []
    let entityId
    let configured = false
    const reload = () => reloadFiles(context)
    async function state() {
      const message = await context.command('/bcqa state', /BCQA STATE count=\d+ base=-?\d+ overlays=\d+/, timeoutMs)
      const [, count, base, overlays] = message.match(/count=(\d+) base=(-?\d+) overlays=(\d+)/)
      return { count: Number(count), base: Number(base), overlays: Number(overlays) }
    }
    async function restoreEntity(label, previousIds = new Set()) {
      const observedId = await observer.find(label, previousIds)
      const current = await state()
      context.expect(current.base === observedId && bot.entities[current.base] !== undefined,
        'Public API base companion ID differs from the newly observed client entity', { ...current, observedId })
      entityId = current.base
      context.report.observations.push({ kind: 'base-interaction-scope', label, ...current,
        note: 'Interaction assertions target the base ID. Overlay interactions are outside the existing cache contract.' })
    }
    async function oneInteraction(hand, attack, phase) {
      await context.command('/bcqa reset', /BCQA RESET count=0/, timeoutMs)
      const event = context.waitForMessage(new RegExp(`BCQA INTERACT count=1 hand=${hand} attack=${attack} base=${entityId}`), timeoutMs)
      interact(bot, entityId, hand, attack)
      const message = await event
      await context.sleep(400)
      const current = await state()
      context.expect(current.count === 1 && current.base === entityId,
        'One interaction packet changed companion identity or produced duplicate events', { phase, hand, attack, ...current })
      context.report.observations.push({ kind: 'companion-interaction', phase, hand, attack, entityId, message })
    }
    try {
      context.expect(/autoReload\s*=\s*true/.test(originalConfig), 'Scenario requires autoReload=true')
      for (const key of ['dispatchInteractEvent']) {
        context.expect(new RegExp(`${key}\\s*=`).test(originalConfig), `Missing canonical setting ${key}`)
      }
      await context.step('public Component identity and API dependency', async () => {
        await context.command('/bcqa status', /BCQA COMPONENT_IDENTITY PASS/, timeoutMs)
      })
      await context.step('enable interactions and equip a fresh companion', async () => {
        configured = true
        await writeFile(configPath, originalConfig
          .replace(/dispatchInteractEvent\s*=\s*(?:true|false)/, 'dispatchInteractEvent = true'), 'utf8')
        await reload()
        if (bot.game.gameMode !== 'creative') await context.command('/gamemode creative', /game mode|gamemode/i, timeoutMs)
        await context.command('/companions despawn', /unequipped|do not have a companion equipped/i, timeoutMs)
        const previousIds = observer.entityIds()
        await context.command('/companions spawn bee', /You have equipped bee\./, timeoutMs)
        await restoreEntity('fresh companion for interactions', previousIds)
      })
      for (const phase of ['before reload', 'after reload']) {
        if (phase === 'after reload') {
          await context.step('reload replaces the companion and retains interaction dispatch', async () => {
            const oldId = entityId
            const previousIds = observer.entityIds()
            await reload()
            await context.waitUntil(() => bot.entities[oldId] === undefined,
              { timeoutMs, label: 'old interaction target removed during reload' })
            await restoreEntity('companion after reload', previousIds)
          })
        }
        for (const [hand, attack] of [['HAND', false], ['OFF_HAND', false], ['HAND', true]]) {
          await context.step(`one ${hand} attack=${attack} interaction ${phase}`,
            () => oneInteraction(hand, attack, phase))
        }
      }
    } finally {
      observer.close()
      if (configured) {
        try {
          if (bot._client.state === 'play') {
            await context.command('/companions despawn', /unequipped|do not have a companion equipped/i, timeoutMs)
          }
        } finally {
          await writeFile(configPath, originalConfig, 'utf8')
          if (bot._client.state === 'play') await reload()
        }
      }
    }
  }
}
