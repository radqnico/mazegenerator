Title: “We built a 200x200 maze on a live server without lagging anyone”

Format: 60s vertical; quick cuts; captions on screen; no filler.

What to show
- Particle preview + per-phase boss bars (generation/placement).
- LayDown mode hugging terrain (show maze draped over hills).
- Massive size (no caps) + hollow walls for block savings.
- TPS overlay staying green; chat only once/min, action bar ticking.
- Config snippets for performance (millis-per-tick, chunk-loads-per-tick, request-confirm, layDown).

Script (voiceover + visuals)
0-5s – Hook: timelapse of walls rising + TPS graph. “Can a 200x200 maze run on a busy server without lag? This plugin says yes.”
5-15s – Preview: `/maze ... layDown:true` with particles hugging terrain; dual boss bars labeled Generation/Placement. “It previews, then streams blocks in phases.”
15-35s – Features: Tab-complete options; hollow/closed toggles; themes. “Every block is themed, walls can be hollow, and there’s no size cap.”
35-45s – Performance: Show config and chunk budgets. “You tune tick budget, chunk loads, and even spill to disk to keep RAM flat.”
45-55s – Proof: Action bar ticking each second, chat ping at 1/min, TPS stable. “Per-phase HUD keeps you updated; TPS stays green.”
55-60s – CTA: “Grab MazeGenerator 1.8.1 for Paper 1.21.x. Drop in `plugins/`, set `request-confirm` or build instantly, and let the server do the work.”

Filming tips
- Use replay/timelapse for builds; overlay captions for commands/config keys.
- Show a terrain-following maze to highlight LayDown.
- Keep cuts tight; no “hey guys” intros; let visuals + short lines carry the story.
