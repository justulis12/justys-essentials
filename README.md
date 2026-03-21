# Justys' Essentials

Portable server-side utility commands for Fabric 1.21.4.

Justys' Essentials adds portable storage, workstation commands, optional mod integrations, beacon range control, homes/TPA, harvesting helpers, and in-game updating.

## Features

- `/ec`
  Opens a portable ender chest.
- Built-in tree feller
  Sneak-break a log, nether stem, or huge mushroom block to harvest connected matching blocks.
- Built-in vein miner
  Sneak-break an ore block or ancient debris to mine the connected matching vein.
- `/justys reload`
  Reloads the mod config.
- `/justys update`
  Checks the latest release, downloads a newer jar if available, and stages it to replace the current jar after the server fully stops.
- `/craft`
- `/anvil`
- `/trash`
- `/stonecutter`
- `/loom`
- `/grindstone`
- `/woodcutter`
  Only registers when Nemo's Woodcutter is installed.
- `/beaconrange`
  Adds a global bonus to beacon range.
- `/sethome [name]`
- `/homeset [name]`
- `/home [name]`
- `/homes`
- `/delhome <name>`
- `/homedel <name>`
- `/setdefaulthome <name>`
- `/homedefault <name>`
- `/back`
- `/tpa <player>`
- `/tpaccept`
- `/tpdeny`
- `/tpatoggle`
- `/tpacancel`
  TPA requests show clickable `[Accept]` and `[Deny]` buttons in chat.
- `/cartography`
- `/smithing`
- `/enderpeek <player>`
  Op-only read-only ender chest view.
- `/invsee <player>`
  Op-only read-only inventory view.

## Permissions

Default permission nodes use the `justysessentials.*` prefix:

- `justysessentials.ec`
- `justysessentials.reload`
- `justysessentials.update`
- `justysessentials.craft`
- `justysessentials.anvil`
- `justysessentials.trash`
- `justysessentials.stonecutter`
- `justysessentials.loom`
- `justysessentials.grindstone`
- `justysessentials.woodcutter`
- `justysessentials.beaconrange`
- `justysessentials.home`
- `justysessentials.sethome`
- `justysessentials.delhome`
- `justysessentials.homes`
- `justysessentials.setdefaulthome`
- `justysessentials.tpa`
- `justysessentials.tpaccept`
- `justysessentials.tpdeny`
- `justysessentials.cartography`
- `justysessentials.smithing`
- `justysessentials.back`

## Config

The config file is written as `config/justys-essentials.json`.

Important fields:

- `rows`
  Portable ender chest row count, clamped to 3-6.
- `enabled_commands`
  Toggle `/ec` and the portable workstation and utility commands.
- `permissions`
  Override permission nodes.
- `tree_feller_enabled`, `vein_miner_enabled`
  Toggle the harvesting helpers.
- `tree_feller_require_sneak`, `vein_miner_require_sneak`
  Separate sneak requirements for tree feller and vein miner.
- `tree_feller_require_axe`, `vein_miner_require_pickaxe`
  Require the expected tool type before harvesting chains trigger.
- `max_tree_blocks`, `max_vein_blocks`
  Safety limits for chained harvesting.
- `max_homes_per_player`
  Player home limit. `-1` means unlimited, and ops bypass the limit.
- `villager_infinite_trading`
  Makes villagers and wandering traders effectively unlimited when enabled.
- `updater_note`
  Documents that `/justys update` uses the official Justys' Essentials GitHub releases.

Home data is stored separately from Teleport Commands at:

- `gameDir/justysessentials/storage.json`

The home storage JSON intentionally matches the Teleport Commands player-home structure, so existing home data can be copied into this file after the first run creates it.

## How `/justys update` Works

`/justys update` does not hot-swap the running jar.

Instead it:

1. Calls the GitHub Releases API for the latest release.
2. Finds the first `.jar` asset matching `justys-essentials-`.
3. Compares that release version to the currently loaded mod version.
4. If a newer version exists, downloads it next to the current jar as a staged pending file.
5. Starts a built-in background helper that waits for the current server process to stop and then swaps the jar automatically.

If the mod is already current, the command reports that it is up to date. If a newer release exists, the replacement happens after shutdown and the new jar is used on the next restart.

On each server start, the mod also performs the same update check automatically and logs the result to the console.

## Notes

- `/woodcutter` only works with Nemo's Woodcutter.
- Portable crafting uses a nearby real crafting table when Visual Workbench is installed.
- Portable anvils use a nearby real anvil when Easy Anvils is installed.
- Command registration changes from config toggles require a restart.
- Home command toggles are `enabled_commands.home_commands` and `enabled_commands.tpa_commands`.
- The built-in harvesting helpers support gathered drops, free replanting, and 2x2 dark oak / jungle sapling replanting.
