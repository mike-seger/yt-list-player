# Copilot Instructions

Use `AGENTS.md` at the repository root as the primary source of project context.

## Priority Order

1. `AGENTS.md` (canonical architecture, behavior, persistence, script workflow)
2. Current source code (`player.js`, `settings.js`, `playlist.js`, `track-edit.js`, `index.html`)
3. `README.md` and `.github/instructions/player-instructions.md` (supplementary; may be stale)

## Working Rules

- Prefer minimal, targeted edits and preserve existing behavior.
- Keep tri-state `restricted` semantics intact (`true` | `false` | missing).
- Preserve localStorage/IndexedDB schemas unless migration is explicitly requested.
- When behavior or schema changes, update `AGENTS.md` in the same change.
