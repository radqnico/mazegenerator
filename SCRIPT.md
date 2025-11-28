Title: “I asked a Minecraft server to build a mega-maze while players are online”

Format: 60s vertical, jump-cut pacing, on-screen captions, quick overlays of config/UI. Tone: concise, curious, technical-but-friendly.

Hook (0-5s)
- Cold open with a timelapse of walls rising. Voice: “Can you build a 200x200 maze on a live server without lagging everyone out? This plugin can.”

Context (5-15s)
- Show `/maze` command and preview particles. Voice: “It previews the maze with particles, then streams blocks with a time budget per tick so TPS stays stable.”
- On-screen: quick overlay of the boss bars for Generation/Placement.

Features (15-40s)
- Clip: Tab-completing options (`cellSize`, `hollow`, `closed`, `themeName`). Voice: “Every block is themed—desert, forest, snowy—and walls can be hollow to cut block count.”
- Clip: Action bar/boss bars updating. Voice: “It reports progress by phase: generation, placement. Chat pings once a minute; boss bars update every second.”
- Clip: Config snippet (`millis-per-tick`, `chunk-loads-per-tick`, `request-confirm`). Voice: “You tune performance: per-tick budget, chunk load budget, even skip preview to build instantly.”
- Clip: Massive maze rendering across chunks. Voice: “No size caps. It’ll stream across chunks and even spill to disk if memory gets tight.”

Proof (40-55s)
- Side-by-side: TPS graph stable vs. maze building. Voice: “Even at 200x200, TPS stays green because it batches by chunk and auto-scales job size.”
- Quick cut: `/maze stop` and `/maze status` showing phase and percentage.

CTA (55-60s)
- Voice: “Want it? Grab MazeGenerator for Paper 1.21.x. Drop it in `plugins/`, tweak `config.yml`, and let your server build while everyone keeps playing.”
- On-screen text: “MazeGenerator 1.8.1 — Streamed, themed mazes without lag.” Include a short link/QR to the download.

Notes for filming
- Use replay/timelapse for the build.
- Overlay captions for commands/config keys.
- Keep cuts tight; no filler B-roll; avoid “hey guys” intros.
