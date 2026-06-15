// Typed client for the DraftRun REST API (mirrors com.draft.footy.api DTOs).

export type ShowRatings = 'ON' | 'SCOUT' | 'OFF'
export type Difficulty = 'EASY' | 'NORMAL' | 'HARD'
export type DraftMode = 'SQUAD_FIRST' | 'POSITION_FIRST'
export type PlayerRatings = 'CAREER' | 'PRIME'
export type LeagueScope = 'CLASSIC' | 'WORLD'
export type RunStatus = 'DRAFTING' | 'SIMULATED'
export type LineName = 'ATT' | 'MID' | 'DEF' | 'GK'

/** Exactly one form is populated per Show-Ratings mode. */
export interface Rating {
  overall: number | null // ON
  low: number | null // SCOUT
  high: number | null // SCOUT
  hidden: boolean // OFF
}

export interface Slot {
  index: number
  position: string
  line: LineName
  filled: boolean
  name: string | null
  nation: string | null
  positions: string[] | null
  rating: Rating | null
  sourceClub: string | null
  sourceSeason: string | null
}

export interface Strength {
  overall: number | null
  attack: number | null
  midfield: number | null
  defence: number | null
  gk: number | null
}

export interface SpinInfo {
  tier: string
}

export interface Odds {
  expectedPoints: number
  winLeague: number
  top4: number
  relegation: number
}

export interface RunState {
  runId: string
  formation: string
  difficulty: Difficulty
  showRatings: ShowRatings
  draftMode: DraftMode
  playerRatings: PlayerRatings
  leagueScope: LeagueScope
  status: RunStatus
  seed: number
  rerollsRemaining: number
  slotsRemaining: number
  strength: Strength | null
  slots: Slot[]
  currentSpin: SpinInfo | null
}

/** Returned by /draft: updated state + the squad you drafted from, revealed (true ratings). */
export interface DeclassifiedPlayer {
  sofifaId: number
  name: string
  position: string
  line: LineName
  overall: number
  draftedByYou: boolean
  eligible: boolean
}
export interface DraftResult {
  state: RunState
  club: string
  season: string
  declassified: DeclassifiedPlayer[]
}

/** A player within a team-viewer XI; stats are null pre-sim, populated post-sim. */
export interface XiSlot {
  position: string
  line: LineName
  name: string
  overall: number
  goals?: number | null
  assists?: number | null
  cleanSheets?: number | null
}
export interface LeagueTeam {
  team: string
  strength: number
  tier: string
  formation: string
  xi: XiSlot[]
  projectedPoints: number
  projectedPos: number
  monteCarlo: MonteCarlo
  you: boolean
}
export interface MonteCarlo {
  sims: number
  mean: number
  min: number; p5: number; p25: number; median: number; p75: number; p95: number; max: number
  title: number; top4: number; top6: number; relegation: number; unbeaten: number; perfect: number
  histMin: number
  histBinWidth: number
  histogram: number[]
  percentile: number | null // the actual season's rank in the cloud (debrief only)
}
export interface Preview {
  projection: Odds
  userOverall: number
  leagueMean: number
  userProjectedPos: number
  league: LeagueTeam[]
  monteCarlo: MonteCarlo
}

export interface SquadPlayer {
  sofifaId: number
  name: string
  nation: string
  positions: string[]
  rating: Rating
  eligibleSlots: string[]
}

export interface SpinClub {
  club: string
  season: string
  league: string | null
  strength: number
  squad: SquadPlayer[]
}
export interface SpinView {
  tier: string
  rerollsRemaining: number
  clubs: SpinClub[]
}

export interface StatRow {
  player: string
  team: string
  value: number
}
export interface TeamRow {
  pos: number
  projectedPos: number
  projectedPoints: number
  team: string
  formation: string
  strength: number
  points: number
  won: number
  drawn: number
  lost: number
  gf: number
  ga: number
  gd: number
  you: boolean
  players: XiSlot[]
  monteCarlo: MonteCarlo | null
}
export interface PlayerAward {
  player: string
  team: string
  goals: number
  assists: number
  cleanSheets: number
}
export interface SeasonView {
  overall: number
  attack: number
  midfield: number
  defence: number
  gk: number
  projection: Odds
  finishPos: number
  points: number
  won: number
  drawn: number
  lost: number
  goalsFor: number
  goalsAgainst: number
  biggestWin: number
  longestWinStreak: number
  table: TeamRow[]
  goldenBoot: StatRow[]
  topAssists: StatRow[]
  goldenGlove: StatRow[]
  playerOfSeason: PlayerAward | null
  monteCarlo: MonteCarlo | null
}

// ---- matchday playback ----
export interface Goal {
  scorer: string
  minute: number
  home: boolean
}
export interface MatchResult {
  home: string
  away: string
  homeGoals: number
  awayGoals: number
  userMatch: boolean
  goals: Goal[]
}
export interface SnapRow {
  team: string
  you: boolean
  played: number
  won: number
  drawn: number
  lost: number
  gf: number
  ga: number
  gd: number
  points: number
}
export interface Matchday {
  number: number
  matches: MatchResult[]
  table: SnapRow[]
}
export interface SeasonReplay {
  matchdays: Matchday[]
  debrief: SeasonView
}

export interface CreateRunConfig {
  formation?: string
  difficulty?: Difficulty
  showRatings?: ShowRatings
  draftMode?: DraftMode
  playerRatings?: PlayerRatings
  leagueScope?: LeagueScope
  eraFrom?: number | null
  eraTo?: number | null
  seed?: number | null
}

const BASE = '/api/runs'

async function req<T>(path: string, method: 'GET' | 'POST', body?: unknown): Promise<T> {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const err = await res.json()
      if (err?.message) msg = err.message
    } catch {
      /* non-JSON error body */
    }
    throw new Error(msg)
  }
  return res.json() as Promise<T>
}

export const api = {
  createRun: (cfg: CreateRunConfig) => req<RunState>('', 'POST', cfg),
  getRun: (id: string) => req<RunState>(`/${id}`, 'GET'),
  spin: (id: string) => req<SpinView>(`/${id}/spin`, 'POST'),
  draft: (id: string, slotPosition: string, sofifaId: number) =>
    req<DraftResult>(`/${id}/draft`, 'POST', { slotPosition, sofifaId }),
  move: (id: string, fromSlot: number, toSlot: number) =>
    req<RunState>(`/${id}/move`, 'POST', { fromSlot, toSlot }),
  preview: (id: string) => req<Preview>(`/${id}/preview`, 'GET'),
  simulate: (id: string) => req<SeasonReplay>(`/${id}/simulate`, 'POST'),
}

// ─── The Almanac (read-only dataset explorer, /api/explore) ──────────────────
export interface AlmanacMeta {
  leagues: string[]
  years: number[]
}
export interface AlmanacClub {
  club: string
  season: string
  league: string
  strength: number
  tier: string
  topPlayer: string
  topOverall: number
}
export interface AlmanacRosterPlayer {
  sofifaId: number
  name: string
  nation: string
  positions: string[]
  line: LineName
  overall: number
}
export interface AlmanacSquad {
  club: string
  season: string
  league: string
  strength: number
  tier: string
  formation: string
  xi: XiSlot[] // backend AlmanacSlot {position, line, name, overall} ≡ XiSlot
  roster: AlmanacRosterPlayer[]
}

export interface ClubQuery {
  league?: string
  from?: number
  to?: number
  q?: string
  sort?: 'strength' | 'club' | 'season'
  limit?: number
}

async function exploreReq<T>(path: string): Promise<T> {
  const res = await fetch('/api/explore' + path)
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json() as Promise<T>
}

export const explore = {
  meta: () => exploreReq<AlmanacMeta>('/meta'),
  clubs: (p: ClubQuery = {}) => {
    const qs = new URLSearchParams()
    if (p.league) qs.set('league', p.league)
    if (p.from != null) qs.set('from', String(p.from))
    if (p.to != null) qs.set('to', String(p.to))
    if (p.q) qs.set('q', p.q)
    if (p.sort) qs.set('sort', p.sort)
    if (p.limit != null) qs.set('limit', String(p.limit))
    const s = qs.toString()
    return exploreReq<AlmanacClub[]>('/clubs' + (s ? '?' + s : ''))
  },
  squad: (club: string, season: string) =>
    exploreReq<AlmanacSquad>(`/squad?club=${encodeURIComponent(club)}&season=${encodeURIComponent(season)}`),
}
