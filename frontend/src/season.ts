import type { SeasonReplay } from './api'

export type Result = 'W' | 'D' | 'L'
export interface SpineGame {
  md: number
  r: Result
  gf: number
  ga: number
  opp: string
  home: boolean
}

/** Drop the trailing " 2018/19" era tag from a club label. */
export const shortClub = (n: string) => n.replace(/\s+\d{2,4}\/\d{2}$/, '').trim()

/** The user's season as an ordered list of results — derived from the replay's per-matchday fixtures. */
export function userSpine(replay: SeasonReplay): SpineGame[] {
  const out: SpineGame[] = []
  for (const day of replay.matchdays) {
    const m = day.matches.find((x) => x.userMatch)
    if (!m) continue
    const userTeam = day.table.find((r) => r.you)?.team
    const home = m.home === userTeam
    const gf = home ? m.homeGoals : m.awayGoals
    const ga = home ? m.awayGoals : m.homeGoals
    out.push({ md: day.number, r: gf > ga ? 'W' : gf < ga ? 'L' : 'D', gf, ga, opp: home ? m.away : m.home, home })
  }
  return out
}

/** Length of the unbeaten run from matchday 1 (until the first defeat). */
export function unbeatenPrefix(games: SpineGame[]): number {
  let n = 0
  for (const g of games) {
    if (g.r === 'L') break
    n++
  }
  return n
}
