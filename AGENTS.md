# AGENTS.md

This file is the canonical agent handoff for this repository.
If details here conflict with README or older notes, trust this file and current code.

## Agent Quick Start (30s)

1. Read this file first, then skim `player.js`, `settings.js`, `playlist.js`, and `track-edit.js`.
2. Serve over HTTP (`python3 -m http.server 8080`) and verify basic playback before editing behavior.
3. Preserve storage schema and tri-state `restricted` semantics unless explicitly changing data model.
4. Keep edits minimal in `player.js`; many features share state and rendering assumptions.
5. If behavior or persistence changes, update this file in the same change.

## Project Snapshot

- App type: static web app (no bundler, no npm runtime required)
- Stack: HTML + CSS + ES module JavaScript
- Entry page: `index.html`
- Main runtime module: `player.js`
- Playlist/state modules: `playlist.js`, `settings.js`, `track-edit.js`
- Data folder: `playlists/`
- Utility/data-prep scripts: `scripts/`

## Run And Verify

Serve from HTTP (YouTube IFrame API does not work from file://):

```bash
python3 -m http.server 8080
# or
npx serve .
```

Open `http://localhost:8080`.

Quick smoke test after changes:

1. App loads and player iframe initializes.
2. Playlist picker opens and switches playlist.
3. Track filter and year filter work.
4. Prev/Next and keyboard controls work.
5. Settings overlay opens; scan buttons render counts.

## Architecture And Responsibilities

### `index.html`

- Contains full app shell and all static DOM anchors.
- Loads only one script: `<script type="module" src="player.js"></script>`.

### `player.js`

- App orchestrator and startup path.
- Loads YouTube IFrame API dynamically and defines `window.onYouTubeIframeAPIReady`.
- Builds playlist index from `playlists/playlists.json` plus custom playlists from IndexedDB/localStorage.
- Switches playlists, restores per-playlist state, applies overrides.
- Handles playback, error recovery, scan logic, deep links, filters, selection, and rendering.
- Uses two rendering modes:
  - Full DOM render below `FULL_RENDER_THRESHOLD` (5000 visible rows).
  - Virtual scrolling at/above threshold.

### `settings.js`

- Manages settings overlay UI and user toggles.
- Persists app settings and failed-video list.
- Bridges settings actions to callbacks supplied by `player.js` (scan, clear, playlist refresh).

### `playlist.js`

- Custom playlist storage and CRUD.
- IndexedDB primary store (`yt-pl-player` / `custom-playlists`) with localStorage fallback and migration.
- Per-playlist state storage (resume/filter/selection/removals/overrides) in localStorage.
- Import parsers for JSON/TSV/CSV.
- Export logic: downloads playlist as ZIP (JSON + TSV) using JSZip CDN, with JSON fallback.

### `track-edit.js`

- Lazy-built modal for per-track edits.
- Edits videoId, title, year.
- Stores per-track videoId history in localStorage (up to 5 entries per track key).

## Data Model

Playlist JSON (remote/custom) expected shape:

```json
{
  "title": "Playlist Title",
  "fetchedAt": "2026-01-01T00:00:00.000Z",
  "items": [
    {
      "videoId": "dQw4w9WgXcQ",
      "title": "Artist - Song",
      "year": 1992,
      "restricted": true
    }
  ]
}
```

Notes:

- `restricted` is effectively tri-state:
  - `true`: known unplayable/restricted.
  - `false`: known playable (explicit).
  - missing: unknown/unscanned.
- `title` is split on first `" - "` for UI artist/subtitle rendering.
- `playlists/playlists.json` currently supports:
  - string entries (path/URL)
  - object entries with inline metadata `{ url, title, playableCount?, restrictedCount? }`

## Persistence Keys

LocalStorage keys used by app runtime:

- `yt-pl-player.resume.v1`: global `{ playlistUrl }`
- `yt-pl-player.pl-state.v1`: per-playlist state map
  - includes `index`, `positionSec`, `filter`, `selected`, `removed`, `sortAlpha`
  - plus `restricted`, `videoIdOverrides`, `trackOverrides`
- `yt-pl-player.settings.v1`: settings
  - `hideRestricted`, `disableRestricted`, `stopAtUnplayable`, `scanConsecFailThreshold`, `hiddenPlaylists`, `nameOverrides`
- `yt-pl-player.failed.v1`: failed/restricted video IDs array
- `yt-pl-player.vid-history.v1`: per-track videoId history

IndexedDB:

- DB: `yt-pl-player`
- Store: `custom-playlists`

## Core Runtime Behavior

- Playback does not cycle at boundaries.
- Next/prev navigation respects current visible set (filter + year + selection exposure + hide restricted).
- Deep-link query params supported:
  - `pl` playlist URL (or custom id mapping)
  - `v` target videoId
  - `t` fallback title
- Selection actions include clear/invert/select enabled/select disabled/copy TSV/copy player link/remove selected/add selected.
- Removing selected tracks is only allowed on custom playlists and persists removed videoIds in per-playlist state.
- Add selected tracks appends to another custom playlist, skipping duplicate videoIds silently.
- Selection menu includes a per-playlist `Sort tracks alphabetically` toggle.
- Track edit UX:
  - Double-click or `E` on focused track opens editor.
  - `Cmd/Ctrl+Z` supports undo of recent videoId overrides (stack size 10).

### Scanner Behavior

- Scan checks tracks by trying to load them in YT player.
- During scan, only `PLAYING` marks success.
- `BUFFERING` is not treated as success.
- Permanent YT errors tracked: `100`, `101`, `150`.
- Consecutive failure stop threshold is user-configurable (`scanConsecFailThreshold`).

## Known Mismatches / Gotchas

- README mentions seek `+-5s`; code currently seeks `+-10s` on left/right arrow.
- There is an older handoff at `.github/instructions/player-instructions.md`; parts are stale.
- `playlists/playlists.json` currently includes a gzip playlist entry (`./trance db.json.gz`), and `.gz` is handled by `fetchJson` via `DecompressionStream`.
- Restricted behavior is controlled by two toggles:
  - Hide restricted from list (`hideRestricted`)
  - Disable click/play on restricted (`disableRestricted`)
- Settings playlists list is alphabetically sorted (case-insensitive), capped to 4 visible rows before internal scroll.
- Settings supports creating empty custom playlists from a plus button; deleting custom playlists requires confirmation.

## Scripts Folder Purpose

These scripts are for dataset enrichment/cleanup workflows (not app runtime):

- `scripts/fetch_video_info.py`
  - Calls YouTube Data API (`gapikey` env var), batched metadata fetch with resume support.
- `scripts/add-restricted.sh`
  - Adds `restricted` column to gz TSV based on `video_info.json` embeddable status.
- `scripts/extract-by-videoid.sh`
  - Extracts rows by video IDs from gz TSV.
- `scripts/match-video-by_artist+title.sh`
  - Matches playlist tracks to years using normalized artist-title logic; emits unmatched rows.
- `scripts/discogs-search-years.sh`
  - Fills missing years via Discogs search API with rate limiting.
- `scripts/enrich-videos-by_year.sh`
  - Applies year TSV back into playlist JSON.
- `scripts/enrich-and-clean-pl.sh`
  - Enriches/cleans TSV and deduplicates duplicate videoId rows.

## Change Guidance For Agents

1. Prefer minimal edits in `player.js`; many features share state and DOM assumptions.
2. Keep tri-state `restricted` semantics intact.
3. Preserve per-playlist override keys and state schema in `playlist.js`.
4. If modifying keyboard/filter logic, verify it still ignores input/textarea focus.
5. If updating playlist download/import behavior, keep backward compatibility for existing files.
6. Update this file when behavior or storage schema changes.
