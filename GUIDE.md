# MazeGenerator - User Guide

This guide explains how to install, configure, and use MazeGenerator on a Paper server. It covers themes, command arguments, performance tips, progress HUDs, and example commands - including “extreme” builds to challenge your server.

## What It Does

MazeGenerator builds large, themed mazes in your world without freezing the server. It generates the layout and places blocks over time with a per‑tick time budget, chunk‑aware scheduling, and optional hollow walls to reduce block count.

Key features:
- Streaming generation and placement - no giant in‑memory queues; optional disk spillover to keep RAM usage bounded.
- Per‑block material randomization using weighted themes (`themes.yml`).
- Configurable time budget and batch size to protect TPS; chunk loading can be budgeted or disabled.
- Optional hollow walls; optional closed roof; no hard limits on maze size/height (still must fit world Y range).
- Two-phase HUD: generation and placement each have their own boss bar; action bar updates every second, chat once per minute.
- Tab‑complete makes arguments easier; `stop`, `reload`, and configurable preview skip (`request-confirm`) for instant builds.

Supported: Paper 1.21.x (`api-version: 1.21`).

---

## Installation

1) Build or download the plugin JAR.
2) Drop the JAR into your server’s `plugins/` folder.
3) Start the server once to generate default config and data files.

Files created/used:
- `plugins/MazeGenerator/config.yml` - performance and behavior settings.
- `plugins/MazeGenerator/themes.yml` - material weights per theme.
- `plugins/MazeGenerator/messages.yml` - user‑facing messages.

Restart or `/reload` the plugin after making changes to these files, or use `/maze reload`.

---

## Themes (themes.yml)

Themes control block selection via weighted materials for three sections: `floor`, `wall`, and `top`.

Example (included by default):

```
desert:
  floor:
    SANDSTONE: 20
    RED_SANDSTONE: 10
  wall:
    SAND: 40
    CHISELED_SANDSTONE: 20
  top:
    SANDSTONE: 3

forest:
  floor:
    GRASS_BLOCK: 20
    DIRT: 2
  wall:
    OAK_LOG: 30
    OAK_LEAVES: 25
  top:
    OAK_LOG: 3
```

Weights are positive integers. Higher weight = more likely. If a section has no materials, the plugin falls back to `STONE` for that section.

Tip: Keep top materials lighter (e.g., slabs/logs) for a nice skyline; raise wall variety for a more organic look.

---

## Messages (messages.yml)

Color codes use `&` (legacy), translated at send time. Relevant keys:
- `plugin-prefix`, `job-started`, `job-status`, `job-done`, `job-stopped`, `command-error`, `no-permission`, `config-reloaded`.

---

## Performance & Behavior (config.yml)

Defaults aim to preserve TPS out of the box:
- `millis-per-tick: 3` - base time budget per tick used by the builder.
- `jobs-batch-cells: 64` - how many cells to queue per top‑up (lower = smoother).
- `autotune:` - automatically raise/lower the budget based on spare time per tick.
  - `min-millis-per-tick: 1`, `max-millis-per-tick: 8`
  - `spare-high: 18` (increase budget only with plenty of room)
  - `spare-low: 12` (back off faster when tick gets tight)
- `cells-per-job` / `max-blocks-per-job` - adaptive batching; large cell/height mazes auto-scale batch size.
- `defer-wall-fill: false` - fill walls first, then carve (instant visual walls, then corridors appear).
- `chunk-loads-per-tick: 1` - at most this many new chunks are sync‑loaded per tick from the pending buffer.
- `force-chunk-load: true` - if true, the builder may load up to `chunk-loads-per-tick` new chunks per tick from its buffer; if false, it only places in already‑loaded chunks (buffering the rest until chunks load naturally).
- `placement-max-pending` - memory budget for pending cells; when exceeded and `disk-spill.enabled` is true, pending cells spill to disk.
- `disk-spill.enabled` / `disk-spill.max-file-size` - optional temp-file spillover to keep RAM flat on huge mazes.
- `request-confirm` - if false, skip the preview/confirm flow and build immediately.

Important:
- With `force-chunk-load: true`, the builder loads at most `chunk-loads-per-tick` new chunks per tick from its buffer to avoid spikes.
- With `force-chunk-load: false`, it will not load chunks; work in unloaded chunks is buffered until those chunks are loaded by normal gameplay.
- If you want absolutely zero chunk loads, set `force-chunk-load: false` (then `chunk-loads-per-tick` has no effect).

Recommended tuning:
- Smoother (slower): `millis-per-tick: 2`, `jobs-batch-cells: 32`, `chunk-loads-per-tick: 0–1`.
- Faster (risk of spikes): `millis-per-tick: 4–5`, `jobs-batch-cells: 128`, `chunk-loads-per-tick: 2–3`.

---

## Command Reference

Base command:
- `/maze` - starts a build with sensible defaults near your position. If `request-confirm` is true, it shows a preview first and waits for `/maze confirm`; if false, it starts building immediately.
- `/maze stop` - stops all active maze builds.
- `/maze reload` - reloads config, messages, and themes (permission `mazegenerator.reload`).

Arguments are `key:value` pairs; order doesn’t matter. Tab‑complete suggests keys and, for some keys, values.

Keys:
- `x`, `y`, `z`: World coordinates (defaults to your position). Tab‑complete offers the targeted block coordinates.
- `world`: World name (defaults to your world). Tab‑complete lists worlds.
- `mazeSizeX`, `mazeSizeZ`: Maze size in cells (odd numbers are enforced internally).
- `cellSize`: Size (in blocks) of each maze cell footprint (default 1).
- `wallHeight`: Vertical wall height (in blocks) below the top.
- `hasExits`: `true|false` - ensure at least one exit; opens borders where paths meet edges.
- `additionalExits`: Extra exits on top of the first (0..N).
- `hasRoom`: `true|false` - carves a central room.
- `roomSizeX`, `roomSizeZ`: Room dimensions (in cells) for the central room.
- `erosion`: 0..1 - probability to punch occasional small holes into nearby walls as you carve.
- `closed`: `true|false` - if true, place a roof over paths as well (otherwise, paths are open to sky).
- `hollow`: `true|false` - if true, build wall cells as a shell (edges only) for huge block savings.
- `themeName`: Theme key from `themes.yml` (tab‑complete lists available themes).

Notes:
- Per‑block randomization: Each block’s material is chosen using your theme weights; cells are not uniform.
- Hollow walls build only edges on walls and top, but floors are always fully placed.
- Closed + hollow makes a “shell” roof over walls and corridors; open (closed:false) leaves corridor tops as AIR.
- No hard cap on maze size or cell/wall height; only world Y-range is enforced.

---

## Examples

Basic, near you:
- `/maze` (defaults: 5�-5 cells, cellSize:1, wallHeight:3, open top, no exits, no room, theme: desert)

Medium sized, forest theme, two exits:
- `/maze mazeSizeX:51 mazeSizeZ:51 cellSize:2 wallHeight:4 hasExits:true additionalExits:1 themeName:forest`

Large desert maze, open sky corridors, faster build but hollow walls:
- `/maze mazeSizeX:75 mazeSizeZ:75 cellSize:3 wallHeight:5 hasExits:true hollow:true closed:false themeName:desert`

Big “dungeon” feel, closed roof, occasional holes (erosion):
- `/maze mazeSizeX:61 mazeSizeZ:61 cellSize:2 wallHeight:4 closed:true hasExits:true erosion:0.03 themeName:mountain`

Central room showcase:
- `/maze mazeSizeX:39 mazeSizeZ:39 hasRoom:true roomSizeX:9 roomSizeZ:7 hasExits:true themeName:jungle`

Build at coordinates in another world:
- `/maze world:world_nether x:100 y:80 z:-200 mazeSizeX:41 mazeSizeZ:41 themeName:snowy`

Stop any in‑progress builds:
- `/maze stop`

Reload configuration, messages, and themes:
- `/maze reload` (permission `mazegenerator.reload`)

---

## Progress HUD & Notifications

- Chat: once per minute, includes the current phase (`generation` or `placement`), percentage, chunk budget, and time budget.
- Boss bars: one per phase with color coding; action bar mirrors the active phase every second.

## Troubleshooting & Tips

- Server lag while building:
  - Lower `millis-per-tick` and `jobs-batch-cells` in `config.yml`.
  - Set `chunk-loads-per-tick: 0–1` and/or `force-chunk-load: false` to avoid loading too many new chunks during a tick.
  - Use `hollow:true` and/or increase `cellSize` to dramatically reduce block count.

- Gaps or uncarved spots:
  - Should not occur - walls never overwrite carved paths. If you see this, share your command and server version.

- Themes look too uniform:
  - Increase variety by adding more materials and weights in `themes.yml` for `wall`/`top`.


---

Enjoy building massive mazes!





