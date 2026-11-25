<div align="center">

# 🌍 MultiWorld-Core

**A Multiverse-style world manager built for Folia.**

Create, import and manage as many worlds as you want on a Folia server, with per-world rules, linked portals and clickable chat menus.

![Folia](https://img.shields.io/badge/Folia-1.21.4%20→%2026.3-FF00BA?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange?style=for-the-badge)
![Build](https://img.shields.io/github/actions/workflow/status/barnifbx/MultiWorld-Core---Folia-Alternative-/build.yml?style=for-the-badge&label=build)

*Created by the FlowSMP Team AKA FlowStudios. [FlowSMP](https://store.flowsmp.net).*

[**⬇ Downloads**](#-downloads) · [Releases](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/releases)

</div>

---

## Why?

Folia has no working Bukkit world API. `Bukkit#createWorld` is stubbed out, so Multiverse-Core and most other world managers don't work on it.
MultiWorld-Core creates worlds through Folia's own server internals. Everything else runs on the correct region thread, so the plugin is fully `folia-supported`.

## ✨ Features

| | |
|---|---|
| 🌐 **World creation** | Normal, nether and end worlds. Types: `normal`, `flat`, `amplified`, `large_biomes` and an empty `void` type with a spawn platform. Optional seed, custom generator plugins, structures on/off. |
| 📦 **Import** | Upload any world folder and load it with one command. |
| ⚙️ **Per-world rules** | Gamemode (enforced), difficulty, PvP, hunger, weather, animal/monster spawning, custom spawn point, respawn world, alias, hidden worlds. |
| 🔒 **Access control** | Lock a world behind `multiworld.access.<world>`. |
| 🌀 **Linked portals** | Send nether and end portals of one world to another world. Coordinates are scaled 8:1 for the nether, and a portal or end platform is built when none exists. |
| 🎮 **Gamerules** | Set any gamerule in any world without being there. |
| 💬 **Clickable chat** | Click a world in `/mw list` to teleport. Click to confirm deletion. Every message can be edited in `messages.yml` (hex colors supported). |
| 🧵 **Folia-safe** | Region, entity and global schedulers are used everywhere. Players are never touched from the wrong thread. |

## 📥 Downloads

Folia changes its internals with every Minecraft version, so there is **one jar per Folia version**. Pick the jar that matches your server:

| Folia version | Download | Java | New worlds load |
|---|---|---|---|
| **26.3** | [MultiWorld-Core-1.0.0-folia-26.3.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-26.3.jar) | 25 | instantly |
| **26.2** | [MultiWorld-Core-1.0.0-folia-26.2.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-26.2.jar) | 25 | instantly |
| **26.1.2** | [MultiWorld-Core-1.0.0-folia-26.1.2.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-26.1.2.jar) | 25 | instantly |
| **1.21.11** | [MultiWorld-Core-1.0.0-folia-1.21.11.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.11.jar) | 21 | instantly |
| **1.21.8** | [MultiWorld-Core-1.0.0-folia-1.21.8.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.8.jar) | 21 | after restart |
| **1.21.7** | [MultiWorld-Core-1.0.0-folia-1.21.7.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.7.jar) | 21 | after restart |
| **1.21.6** | [MultiWorld-Core-1.0.0-folia-1.21.6.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.6.jar) | 21 | after restart |
| **1.21.5** | [MultiWorld-Core-1.0.0-folia-1.21.5.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.5.jar) | 21 | after restart |
| **1.21.4** | [MultiWorld-Core-1.0.0-folia-1.21.4.jar](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/raw/dist/MultiWorld-Core-1.0.0-folia-1.21.4.jar) | 21 | after restart |

All jars are also attached to every [release](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/releases).

> **"After restart"**: before 1.21.11, Folia has to preload the spawn area of a new world. That only works while the server starts, so on these versions `/mw create`, `/mw import` and `/mw load` save the world, and it loads on the next restart. Everything else works the same.

## 🚀 Installation

1. Download the jar for **your** Folia version from the table above.
2. Put it in your server's `plugins/` folder.
3. Restart the server. Your existing worlds are registered automatically.

The startup log shows which version the jar was built for: `MultiWorld-Core enabled for Folia 26.1.x`.

## 🧭 Commands

Main command `/multiworld`. Aliases: `/mw`, `/mwc`, `/mv`.

| Command | Description |
|---|---|
| `/mw list` | All worlds, clickable to teleport |
| `/mw info [world]` | Every setting of a world |
| `/mw who [world]` | Players in a world |
| `/mw tp <world> [player]` | Teleport to a world's spawn |
| `/mw spawn [player]` | Go to the spawn of your current world |
| `/mw setspawn` | Set the world spawn to your position |
| `/mw create <name> <normal\|nether\|end> [-t type] [-s seed] [-g generator] [-n]` | Create a world (`-n` = no structures) |
| `/mw import <name> <normal\|nether\|end> [-g generator]` | Load an uploaded world folder |
| `/mw load <world>` | Load a registered world that isn't loaded |
| `/mw remove <world>` | Stop managing a world (files are kept) |
| `/mw delete <world>` → `/mw confirm` | Delete a world. Players are moved out, files are removed on the next restart. |
| `/mw modify <world> <setting> <value>` | Change a world setting (see below) |
| `/mw gamerule <world> <rule> <value>` | Set a gamerule |
| `/mw link <nether\|end> <from> <to>` | Link portals between worlds |
| `/mw unlink <nether\|end> <world>` | Back to vanilla portal behaviour |
| `/mw reload` | Reload `config.yml`, `messages.yml` and `worlds.yml` |

### World settings (`/mw modify`)

| Setting | Values |
|---|---|
| `alias` | display name, or `none` |
| `gamemode` | `survival` `creative` `adventure` `spectator` `none` |
| `difficulty` | `peaceful` `easy` `normal` `hard` `none` |
| `pvp` `hunger` `weather` `animals` `monsters` | `true` / `false` |
| `hidden` | hide from `/mw list` |
| `access-permission` | require `multiworld.access.<world>` |
| `respawn-world` | a world name, or `none` |
| `auto-load` | load on startup |

### Examples

```
/mw create skyblock normal -t void
/mw create resource normal -s 12345 -n
/mw create resource_nether nether
/mw link nether resource resource_nether
/mw modify skyblock pvp false
/mw modify event gamemode adventure
/mw modify event access-permission true
```

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `multiworld.admin` | op | Everything below, plus all management commands |
| `multiworld.command.tp` | op | `/mw tp <world>` |
| `multiworld.command.tp.others` | op | `/mw tp <world> <player>` |
| `multiworld.command.spawn` | op | `/mw spawn` |
| `multiworld.command.spawn.others` | op | `/mw spawn <player>` |
| `multiworld.command.list` / `.info` / `.who` | op | Info commands |
| `multiworld.access.<world>` / `multiworld.access.*` | op | Enter access-locked worlds |
| `multiworld.gamemode.bypass` | op | Keep your gamemode when changing worlds |
| `multiworld.seehidden` | op | See hidden worlds in `/mw list` |

## 🛠 Configuration

`config.yml`
```yaml
runtime-loading: true      # load new worlds instantly (false = load after next restart)
respawn:
  use-bed: true            # beds / anchors win over a world's respawn-world
join:
  world: ""                # send joining players to this world's spawn
  first-join-only: true
portals:
  nether-delay-ticks: 80   # time in a linked nether portal (creative = instant)
```

Per-world settings are stored in `worlds.yml`. Messages are in `messages.yml`.

## ⚠️ Folia limitations

- **No live unloading.** Folia cannot safely unload a world while the server runs. `/mw delete` moves players out and deletes the files on the next restart. `/mw remove` stops managing a world from the next restart.
- **Version-bound.** World loading uses server internals, so each Folia version needs its own jar (see [Downloads](#-downloads)). Using the wrong jar will fail on startup.
- If a new world ever fails to load instantly, set `runtime-loading: false`. Worlds then load during startup, which is the most reliable path.

## 🧱 Building

GitHub Actions builds every push to `main` for all supported Folia versions at once:
- each build is published as a release
- the newest jars are always on the [`dist` branch](https://github.com/barnifbx/MultiWorld-Core---Folia-Alternative-/tree/dist)

To build locally (needs Gradle 9, plus Java 21 for 1.21.x or Java 25 for 26.x):

```
gradle build -Pmc=26.1.2
```

`-Pmc` picks the Folia version. The version-specific world loaders are in [`versions/`](versions). The jar ends up in `build/libs/`.

## 📜 Credits

- World creation on Folia is adapted from [MoreFoWorld](https://github.com/Folia-Inquisitors/MoreFoWorld) (MIT). See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).
- Command design inspired by [Multiverse-Core](https://github.com/Multiverse/Multiverse-Core). No Multiverse code is used.

<div align="center">

**Created by the FlowSMP Team AKA FlowStudios** · [flowsmp.net](https://store.flowsmp.net) · [Discord](https://dc.flowsmp.net)

</div>
