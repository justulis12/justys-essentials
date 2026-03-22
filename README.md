# Justys' Essentials

Simple server-side utility mod for Fabric 1.21.4.

This mod adds useful commands, portable menus, teleport tools, harvesting helpers, world toggles, and an auto-update check.

## Main Commands

- `/ec`
  Portable ender chest.
- `/craft`, `/anvil`, `/cartography`, `/smithing`, `/stonecutter`, `/loom`, `/grindstone`
  Portable workstation commands.
- `/woodcutter`
  Only works when Nemo's Woodcutter is installed.
- `/sethome`, `/homeset`, `/home`, `/homes`, `/delhome`, `/homedel`, `/setdefaulthome`, `/homedefault`
  Home commands.
- `/warp`, `/warps`, `/setwarp`, `/delwarp`
  Warp commands. Setting and deleting warps is op-only.
- `/tpa`, `/tpaccept`, `/tpdeny`, `/tpatoggle`, `/tpacancel`
  Teleport request commands. `/tpa` can also open a player menu.
- `/back`
  Go back to your last saved location.
- `/trash`, `/trash undo`
  Trash menu with one-step undo.
- `/condense`
  Compress common items into blocks.
- `/beaconrange`
  Add extra beacon range.
- `/afk`
  Toggle AFK status.
- `/playtime`
  Show your tracked playtime.
- `/lastdeath`
  Show your last death location.
- `/deathcompass`
  Get a compass pointing to your last death.
- `/coinflip`
  Flip a coin.
- `/whereami`, `/coords`, `/biome`, `/light`, `/moonphase`, `/weather`, `/time`, `/slimechunk`, `/chunkinfo`
  World info commands.
- `/day`, `/night`, `/seed`
  Simple world/admin commands.
- `/enderpeek <player>`, `/invsee <player>`
  Op-only inventory viewing/editing.

## Permissions

Default permission nodes use the `justysessentials.*` prefix.

All command permission nodes can be changed in the `permissions` section of the config.

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

Config file:

- `config/justys-essentials.json`

Important settings:

- `rows`
  Ender chest rows. Allowed range is `3` to `6`.
- `enabled_commands`
  Turn commands on or off.
- `permissions`
  Change permission nodes if needed.
- `max_homes_per_player`
  `-1` means unlimited. Ops bypass the limit.
- `tree_feller_enabled`, `vein_miner_enabled`
  Turn harvesting helpers on or off.
- `tree_feller_require_sneak`, `vein_miner_require_sneak`
  Separate sneak settings for tree feller and vein miner.
- `tree_feller_require_axe`, `vein_miner_require_pickaxe`
  Require the right tool type.
- `max_tree_blocks`, `max_vein_blocks`
  Safety limits.
- `villager_infinite_trading`
  Makes villagers and wandering traders effectively unlimited.
- `anti_enderman_grief`, `anti_creeper_grief`, `disable_phantoms`, `no_fire_spread`
  Optional world behavior toggles.

Data files:

- `gameDir/justysessentials/storage.json`
- `gameDir/justysessentials/player-data.json`

## Update Command

`/justys update` does not replace the jar while the server is running.

What it does:

1. Checks the latest GitHub release.
2. Compares it with the running mod version.
3. If a newer version exists, it downloads and stages it.
4. The new jar is applied after the server stops.
5. The new version loads on the next restart.

The same check also runs automatically on server start and logs to console.

## Notes

- `/woodcutter` only works with Nemo's Woodcutter.
- Portable crafting uses a nearby real crafting table when Visual Workbench is installed.
- Portable anvils use a nearby real anvil when Easy Anvils is installed.
- Command registration changes from config toggles require a restart.
- Home and TPA command groups can be turned off in `enabled_commands`.
- Tree feller supports leaf clearing and simple sapling replanting.
