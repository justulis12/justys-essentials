# Justys' Essentials

Portable server-side utility commands for Fabric 1.21.4.

Justys' Essentials adds portable storage, workstation commands, optional mod integrations, beacon range control, and in-game updating.

## Features

- `/ec`
  Opens a portable ender chest.
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

## Config

The config file is written as `config/justys-essentials.json`.

Important fields:

- `rows`
  Portable ender chest row count, clamped to 3-6.
- `enabled_commands`
  Toggle workstation and utility commands.
- `permissions`
  Override permission nodes.
- `updater_note`
  Documents that `/justys update` uses the official Justys' Essentials GitHub releases.

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
- Portable crafting may conflict with mods that heavily change crafting-table behavior.
- Command registration changes from config toggles require a restart.
