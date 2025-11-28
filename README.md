# MazeGenerator - User Guide

Build large, themed mazes on Paper 1.21.10 without freezing the server. Generation streams cell-by-cell with a time budget per tick, chunk-aware scheduling, and optional hollow walls to reduce block count. Players get live progress via action bar and boss bar, and `/maze status` reports progress on demand.

## What It Does
- Streaming generation and placement (no huge in-memory queues).
- Per-block material randomization using weighted themes (`themes.yml`).
- Configurable time budget, batch size, and chunk-load budget to protect TPS.
- Optional hollow walls; optional closed roof.
- Works across chunks and worlds; chunk loading is rate-limited per tick.
- Tab-complete for arguments; `stop`, `status`, and `reload` subcommands.

Supported: Paper 1.21.10 (`api-version: 1.21`).

## Installation
1. Build or download the plugin JAR.
2. Drop the JAR into your server `plugins/` folder.
3. Start the server once to generate default config and data files.

Files created/used:
- `plugins/MazeGenerator/config.yml` - performance and behavior settings.
- `plugins/MazeGenerator/themes.yml` - material weights per theme.
- `plugins/MazeGenerator/messages.yml` - user-facing messages.

Restart or `/maze reload` the plugin after changing these files.

## Themes (themes.yml)
Themes control block selection via weighted materials for three sections: `floor`, `wall`, and `top`.

Example:
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
```
Weights are positive integers. Higher weight = more likely. If a section has no materials, the plugin falls back to `STONE` for that section.

Tip: Keep top materials lighter (e.g., slabs/logs) for a nice skyline; raise wall variety for a more organic look.

## Messages (messages.yml)
Color codes use `&` and are translated when sent. Relevant keys: `plugin-prefix`, `job-started`, `job-status`, `job-done`, `job-stopped`, `command-error`, `no-permission`, `config-reloaded`.

## Performance & Behavior (config.yml)
Defaults aim to preserve TPS:
- `millis-per-tick: 3` - base time budget per tick used by the builder (auto-tunes with tick spare time).
- `jobs-batch-cells: 64` - how many cells to queue per top-up (lower = smoother).
- `cells-per-job: 16` - cells packed into a single placement job per chunk; auto-clamped by `max-blocks-per-job`.
- `max-blocks-per-job: 2048` - hard cap on estimated blocks in a single job to avoid heavy ticks when using big cells/heights.
- `autotune` - raise/lower the budget based on spare time per tick.
- `defer-wall-fill: false` - fill walls first, then carve (fast initial visual).
- `chunk-loads-per-tick: 1` - max sync chunk loads per tick the builder will attempt.
- `force-chunk-load: true` - if false, builder only works in already loaded chunks.
- `status-interval-jobs: 1000` - chat/action-bar/boss-bar update frequency (per jobs executed).
- `set-block-data: false` - attaches CustomBlockData to placed blocks (costly; keep false unless needed).

Recommended tuning:
- Smoother: `millis-per-tick: 2`, `jobs-batch-cells: 32`, `chunk-loads-per-tick: 0-1`.
- Faster (risk spikes): `millis-per-tick: 4-5`, `jobs-batch-cells: 128`, `chunk-loads-per-tick: 2-3`.

## Command Reference
Base command:
- `/maze` - starts a build with sensible defaults near your position.
- `/maze stop` - stops all active maze builds.
- `/maze status` - shows your active maze progress.
- `/maze reload` - reloads config, messages, and themes (permission `mazegenerator.reload`).

Arguments are `key:value` pairs; order does not matter. Tab-complete suggests keys and common values.

Keys:
- `x`, `y`, `z`: World coordinates (defaults to your position). Tab-complete offers the targeted block coordinates.
- `world`: World name (defaults to your world). Tab-complete lists worlds.
- `mazeSizeX`, `mazeSizeZ`: Maze size in cells (odd numbers enforced internally).
- `cellSize`: Size (in blocks) of each maze cell footprint (default 1).
- `wallHeight`: Vertical wall height (in blocks) below the top.
- `hasExits`: `true|false` - ensure at least one exit; opens borders where paths meet edges.
- `additionalExits`: Extra exits on top of the first (0..N).
- `hasRoom`: `true|false` - carves a central room.
- `roomSizeX`, `roomSizeZ`: Room dimensions (in cells) for the central room.
- `erosion`: 0..1 - probability to punch occasional small holes into nearby walls as you carve.
- `closed`: `true|false` - if true, place a roof over paths as well (otherwise, paths are open to sky).
- `hollow`: `true|false` - if true, build wall cells as a shell (edges only) for huge block savings.
- `themeName`: Theme key from `themes.yml` (tab-complete lists available themes).

Notes:
- Hollow walls build only edges on walls and top, but floors are always fully placed.
- Closed + hollow makes a shell roof over walls and corridors; open leaves corridor tops as AIR.
- Progress is shown on action bar/boss bar for the player who started the job; `/maze status` works for console too.

## Examples
- Basic: `/maze`
- Medium forest: `/maze mazeSizeX:51 mazeSizeZ:51 cellSize:2 wallHeight:4 hasExits:true additionalExits:1 themeName:forest`
- Large desert, open top, hollow walls: `/maze mazeSizeX:75 mazeSizeZ:75 cellSize:3 wallHeight:5 hasExits:true hollow:true closed:false themeName:desert`
- Closed-roof dungeon: `/maze mazeSizeX:61 mazeSizeZ:61 cellSize:2 wallHeight:4 closed:true hasExits:true erosion:0.03 themeName:mountain`
- Alternate world: `/maze world:world_nether x:100 y:80 z:-200 mazeSizeX:41 mazeSizeZ:41 themeName:snowy`

## Troubleshooting & Tips
- Server lag while building:
  - Lower `millis-per-tick` and `jobs-batch-cells`.
  - Set `chunk-loads-per-tick: 0-1` and/or `force-chunk-load: false` to avoid sync chunk loads.
  - Use `hollow:true` and/or increase `cellSize` to reduce blocks.
  - Raise `max-blocks-per-job` only if your server easily handles larger spikes.
- Gaps or uncarved spots: should not occur; report with your command and server version.
- Themes look too uniform: add more materials/weights under `wall`/`top` in `themes.yml`.

Enjoy building massive mazes!
