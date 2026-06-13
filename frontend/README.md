# SCOUT//DRAFT — frontend

React scout-dossier SPA (tactical intel-terminal theme) for the footy-draft game. Consumes the Spring
`/api/runs` draft API.

## Stack
- Vite + React + TypeScript
- Tailwind v4 (`@tailwindcss/vite`) — theme tokens in `src/index.css`
- Framer Motion — reveal animations
- No router / state lib: a view-state machine in `App.tsx`, server state via `fetch` (`src/api.ts`)

## Run (needs the backend on :8080)
```bash
# terminal 1 — backend (from repo root)
mvn spring-boot:run

# terminal 2 — frontend
cd frontend
npm install
npm run dev          # http://localhost:5173  (proxies /api -> :8080)
```
`npm run build` type-checks (tsc) and produces a production bundle in `dist/`.

## Structure
- `src/api.ts` — typed client + DTO types (mirrors `com.draft.footy.api`).
- `src/theme.ts` — line colours, per-formation pitch coordinates, Scout rating renderer.
- `src/components/` — `PitchView` (interactive draft pitch), `TeamPitch` (read-only squad-in-formation viewer),
  `SpinReveal` (tier-scaled spin suspense), `RatingBadge`, `PositionChip`, `StrengthBars`, terminal `primitives`.
- `src/screens/` — `SetupScreen` → `DraftScreen` (spin reveal → place → league panel) → `PlaybackScreen`
  (matchday-by-matchday, animated live table) → `ResultsScreen` (debrief, clickable team viewer, proj-vs-actual).

## Scope
Full playable loop for **World Draft + Squad First**, Show-Ratings On/Scout/Off (Scout shows ranges/redaction —
the true overall never leaves the server). Deferred: Position First, Classic mode, Continue-Draft resume,
richer setup (era slider / league picker).
