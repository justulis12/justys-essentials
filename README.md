# Justys' Essentials

Server-side Fabric essentials mod for Minecraft `1.21.4`.

No client mod is required.

## Features

- Portable storage and utility commands
- Homes, warps, spawn, back, and teleport requests
- Mail, AFK, playtime tracking, death tools, and small QoL commands
- Tree feller, vein miner, villager infinite trading, and item condensing
- World protection toggles like anti-enderman grief, anti-creeper grief, phantom disable, and fire spread control
- Auto-update check against the official GitHub releases
- Configurable permission nodes and command enable/disable toggles

## Commands

### Storage and utility

- `/ec`
- `/trash`, `/trash undo`
- `/condense`
- `/craft`
- `/anvil`
- `/cartography`
- `/smithing`
- `/stonecutter`
- `/loom`
- `/grindstone`
- `/woodcutter`
  Only works when Nemo's Woodcutter is installed.

### Teleport and movement

- `/sethome`, `/homeset`
- `/home`, `/homes`
- `/delhome`, `/homedel`
- `/setdefaulthome`, `/homedefault`
- `/warp`, `/warps`
- `/setwarp`, `/delwarp`
- `/spawn`, `/setspawn`
- `/back`
- `/rtp`
- `/tpa`
- `/tpahere`
- `/tpaccept`
- `/tpdeny`
- `/tpatoggle`
- `/tpacancel`
- `/top`

### Player QoL

- `/mail`
- `/sort`
- `/packup`
- `/afk`
- `/playtime`
- `/playtimetop`
- `/seen`
- `/near`
- `/coinflip`

### Death and recovery

- `/lastdeath`
- `/deathcompass`

### World and admin

- `/beaconrange`
- `/whereami`, `/coords`
- `/biome`
- `/light`
- `/moonphase`
- `/weather`
- `/time`
- `/slimechunk`
- `/chunkinfo`
- `/day`
- `/night`
- `/seed`
- `/enderpeek <player>`
- `/invsee <player>`
- `/justys help`
- `/justys reload`
- `/justys update`
- `/justys doctor`

## Config

Config file:

- `config/justys-essentials.json`

Main config areas:

- `enabled_commands`
  Turn commands on or off.
- `permissions`
  Change permission nodes for any command.
- `rows`
  Ender chest rows. Supported range is `3` to `6`.
- `max_homes_per_player`
  `-1` means unlimited. Ops bypass the limit.
- `tree_feller_*` and `vein_miner_*`
  Control harvesting behavior and tool/sneak requirements.
- `max_tree_blocks`, `max_vein_blocks`
  Safety limits for harvesting.
- `villager_infinite_trading`
  Makes villager and wandering trader trades effectively unlimited.
- `anti_enderman_grief`, `anti_creeper_grief`, `disable_phantoms`, `no_fire_spread`
  Optional world behavior toggles.

## Permissions

Default permission nodes use the `justysessentials.*` prefix.

Every command permission can be changed in the config.

## Data Files

- `gameDir/justysessentials/storage.json`
- `gameDir/justysessentials/player-data.json`
- `gameDir/justysessentials/mail.json`
- `gameDir/justysessentials/spawn.json`

## Notes

- `/woodcutter` only registers if Nemo's Woodcutter is installed.
- `/craft` uses a nearby real crafting table when Visual Workbench is installed.
- `/anvil` uses a nearby real anvil when Easy Anvils is installed.
- Command enable/disable changes require a restart because commands are registered on startup.
- `/justys update` stages a newer jar and applies it after the server fully stops.
