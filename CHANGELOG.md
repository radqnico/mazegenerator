# Changelog

## 1.8.1
- Phase-aware progress: separate generation and placement tracking with per-phase boss bars; action bar updates every second, chat once per minute.
- Configurable preview skip: `request-confirm=false` builds immediately without particle preview; new `build-no-preview` message.
- Adaptive placement batching and optional disk spillover to limit RAM (`placement-max-pending`, `disk-spill.*`), with size strings (`10M`, `2G`) parsed via `SizeParser`.
- Removed maze size/height caps; only world Y-range is enforced.
- Removed unused non-stream `MazePlacer` implementation.
- Refined tab-completion to include `confirm`/`cancel` and refactored command handling.
- Preview height columns render top-down.
- Separated chat update cadence (1/min) from bossbar/action bar cadence (1/sec).

> Changes prior to 1.7.3 are not listed here.
