# MazeGenerator — User Guide

This guide explains how to install, configure, and use MazeGenerator on a Paper (or Folia) server. It covers themes, command arguments, performance tips, and example commands — including “extreme” builds to challenge your server.

## What It Does

MazeGenerator builds large, themed mazes in your world without freezing the server. It generates the layout and places blocks over time with a per‑tick time budget, chunk‑aware scheduling, and optional hollow walls to reduce block count.

Key features:
- Streaming generation and placement — no giant in‑memory queues.
- Per‑block material randomization using weighted themes (themes.yml).
- Configurable time budget and batch size to protect TPS.
- Optional hollow walls; optional closed roof.
- Works across chunks and worlds; loads chunks on demand so nothing is “missing”.
- Tab‑complete makes arguments easier; “stop” subcommand to cancel builds.

Supported: Paper 1.21.x (plugin `api-version: 1.21`). Folia is also supported and will schedule region‑safe tasks automatically when available.

### Why It’s Fast and Lightweight

- Time‑boxed placement per tick: The plugin only does a small, configurable amount of work each tick so gameplay stays responsive.
- Streaming, not hoarding: It generates and places cells on the fly; it never builds huge job lists in memory.
- Chunk‑aware scheduling: It groups work by chunk and only forces a small number of chunk loads at a time (configurable), avoiding big stutters.
- Smart carving order: Walls never overwrite carved corridors; no wasteful redo work.
- Hollow option: Shell‑only walls dramatically reduce block writes for large mazes.

---

## Installation

1) Build or download the plugin JAR.
2) Drop the JAR into your server’s `plugins/` folder.
3) Start the server once to generate default config and data files.

Files created/used:
- `plugins/MazeGenerator/config.yml` — performance and behavior settings.
- `plugins/MazeGenerator/themes.yml` — material weights per theme.
- `plugins/MazeGenerator/messages.yml` — user-facing messages.

Restart or `/reload` the plugin after making changes to these files.

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
- `plugin-prefix`, `job-started`, `job-status`, `job-done`, `job-stopped`, `command-error`.

---

## Performance & Behavior (config.yml)

Defaults aim to preserve TPS out of the box:
- `millis-per-tick: 3` — base time budget per tick used by the builder.
- `jobs-batch-cells: 64` — how many cells to queue per top‑up (lower = smoother).
- `autotune:` — automatically raise/lower the budget based on spare time per tick.
  - `min-millis-per-tick: 1`, `max-millis-per-tick: 8`
  - `spare-high: 18` (increase budget only with plenty of room)
  - `spare-low: 12` (back off faster when tick gets tight)
- `defer-wall-fill: false` — fill walls first, then carve (instant visual walls, then corridors appear).
- `chunk-loads-per-tick: 1` — at most this many new chunks are sync‑loaded per tick from the pending buffer.

Important:
- The builder never “skips” far chunks: if a cell’s chunk isn’t loaded, it will be loaded on demand (synchronously) before placing; or (for buffered cells) up to `chunk-loads-per-tick` per tick to avoid stutters.
- If you want zero chunk loads during placement, set `chunk-loads-per-tick: 0`. Then only already‑loaded chunks are placed; remaining cells place later once chunks become loaded by normal gameplay.

Recommended tuning:
- Smoother (slower): `millis-per-tick: 2`, `jobs-batch-cells: 32`, `chunk-loads-per-tick: 0–1`.
- Faster (risk of spikes): `millis-per-tick: 4–5`, `jobs-batch-cells: 128`, `chunk-loads-per-tick: 2–3`.

---

## Command Reference

Base command:
- `/maze` — starts a build with sensible defaults near your position.
- `/maze stop` — stops all active maze builds.

Arguments are `key:value` pairs; order doesn’t matter. Tab‑complete suggests keys and, for some keys, values.

Keys:
- `x`, `y`, `z`: World coordinates (defaults to your position). Tab‑complete offers the targeted block coordinates.
- `world`: World name (defaults to your world). Tab‑complete lists worlds.
- `mazeSizeX`, `mazeSizeZ`: Maze size in cells (odd numbers are enforced internally).
- `cellSize`: Size (in blocks) of each maze cell footprint (default 1).
- `wallHeight`: Vertical wall height (in blocks) below the top.
- `hasExits`: `true|false` — ensure at least one exit; opens borders where paths meet edges.
- `additionalExits`: Extra exits on top of the first (0..N).
- `hasRoom`: `true|false` — carves a central room.
- `roomSizeX`, `roomSizeZ`: Room dimensions (in cells) for the central room.
- `erosion`: 0..1 — probability to punch occasional small holes into nearby walls as you carve.
- `closed`: `true|false` — if true, place a roof over paths as well (otherwise, paths are open to sky).
- `hollow`: `true|false` — if true, build wall cells as a shell (edges only) for huge block savings.
- `themeName`: Theme key from `themes.yml` (tab‑complete lists available themes).

Notes:
- Per‑block randomization: Each block’s material is chosen using your theme weights; cells are not uniform.
- Hollow walls build only edges on walls and top, but floors are always fully placed.
- Closed + hollow makes a “shell” roof over walls and corridors; open (closed:false) leaves corridor tops as AIR.

---

## Examples

Basic, near you:
- `/maze` (defaults: 5×5 cells, cellSize:1, wallHeight:3, open top, no exits, no room, theme: desert)

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

---

## Troubleshooting & Tips

- Server lag while building:
  - Lower `millis-per-tick` and `jobs-batch-cells` in `config.yml`.
  - Set `chunk-loads-per-tick: 0–1` to avoid loading too many new chunks in a single tick.
  - Use `hollow:true` and/or increase `cellSize` to dramatically reduce block count.

- Gaps or uncarved spots:
  - Should not occur — walls never overwrite carved paths. If you see this, share your command and server version.

- Themes look too uniform:
  - Increase variety by adding more materials and weights in `themes.yml` for `wall`/`top`.

- Performance on Folia:
  - The plugin uses Folia’s region scheduler automatically when available for region‑safe calls.

---

## Extreme Commands (Challenge Yourself)

Warning: These will stress even strong servers. Use with care and consider running alone on the server.

1) Mega open-sky labyrinth (very large footprint, hollow walls):
- `/maze mazeSizeX:151 mazeSizeZ:151 cellSize:3 wallHeight:6 hollow:true closed:false hasExits:true additionalExits:3 themeName:desert`

2) Dense “city” maze with closed roof:
- `/maze mazeSizeX:121 mazeSizeZ:121 cellSize:2 wallHeight:6 closed:true hollow:false hasExits:true additionalExits:4 themeName:mountain`

3) Jungle ruins with erosion and a big central room:
- `/maze mazeSizeX:101 mazeSizeZ:101 cellSize:2 wallHeight:4 hasRoom:true roomSizeX:15 roomSizeZ:11 erosion:0.05 hasExits:true themeName:jungle`

4) Nether sprawl in another world (be mindful of lava):
- `/maze world:world_nether x:0 y:80 z:0 mazeSizeX:99 mazeSizeZ:99 cellSize:2 wallHeight:4 closed:false hollow:true hasExits:true themeName:desert`

5) Thin but huge footprint (stretch your I/O):
- `/maze mazeSizeX:201 mazeSizeZ:61 cellSize:2 wallHeight:3 closed:false hollow:true hasExits:true additionalExits:2 themeName:forest`

If TPS dips too much, cancel with `/maze stop`, lower sizes, or reduce `config.yml` budgets.

---

Enjoy building massive mazes!
