import type { SeasonReplay, MatchResult } from './api'

// Scenario-based match prediction for the playback. We scan the user's fixture each matchday for a "moment"
// (title clash, the unbeaten run on the line, a banana skin, …) using only the state GOING INTO the matchday,
// and build a multiple-choice prompt. The result is already fixed by the sim — you're testing how well you
// read your own team. Pure + deterministic (same replay → same prompts).

export type PredictKind = 'result' | 'yesno' | 'scoreline' | 'scorer'
export interface PredictOption { key: string; label: string; correct: boolean }
export interface Scenario {
  tag: string        // small label, e.g. "The Run on the Line"
  headline: string   // the dramatic stakes line
  question: string
  kind: PredictKind
  options: PredictOption[]
  vs: string         // opponent (short name)
  home: boolean      // user at home?
  finalDay: boolean  // always prompts, ignores cooldown
}

const surname = (n: string) => n.trim().split(/\s+/).pop() ?? n
const shortClub = (n: string) => n.replace(/\s+\d{2,4}\/\d{2}.*$/, '').trim()

type Res = 'W' | 'D' | 'L'
interface UserMatch { gf: number; ga: number; home: boolean; opp: string; res: Res; m: MatchResult }

function userName(replay: SeasonReplay): string {
  return replay.matchdays[replay.matchdays.length - 1]?.table.find((r) => r.you)?.team ?? ''
}
function userMatchOf(m: MatchResult, you: string): UserMatch {
  const home = m.home === you
  const gf = home ? m.homeGoals : m.awayGoals
  const ga = home ? m.awayGoals : m.homeGoals
  return { gf, ga, home, opp: home ? m.away : m.home, res: gf > ga ? 'W' : gf < ga ? 'L' : 'D', m }
}
function findUserMatch(md: { matches: MatchResult[] }, you: string): UserMatch | null {
  const m = md.matches.find((x) => x.userMatch)
  return m ? userMatchOf(m, you) : null
}
function priorResults(replay: SeasonReplay, md: number, you: string): UserMatch[] {
  const out: UserMatch[] = []
  for (let i = 0; i < md; i++) { const um = findUserMatch(replay.matchdays[i], you); if (um) out.push(um) }
  return out
}
function trailing(prior: UserMatch[], ok: (r: Res) => boolean): number {
  let n = 0
  for (let i = prior.length - 1; i >= 0; i--) { if (ok(prior[i].res)) n++; else break }
  return n
}
function talisman(prior: UserMatch[]): { name: string; goals: number } | null {
  const tally: Record<string, number> = {}
  for (const um of prior) for (const g of um.m.goals) if (g.home === um.home) tally[g.scorer] = (tally[g.scorer] || 0) + 1
  let best: { name: string; goals: number } | null = null
  for (const [name, goals] of Object.entries(tally)) if (!best || goals > best.goals) best = { name, goals }
  return best
}

function resultOpts(um: UserMatch): PredictOption[] {
  return [
    { key: 'W', label: 'Win', correct: um.res === 'W' },
    { key: 'D', label: 'Draw', correct: um.res === 'D' },
    { key: 'L', label: 'Lose', correct: um.res === 'L' },
  ]
}
function yesno(yes: string, no: string, yesCorrect: boolean): PredictOption[] {
  return [{ key: 'Y', label: yes, correct: yesCorrect }, { key: 'N', label: no, correct: !yesCorrect }]
}
function scorelineOpts(gf: number, ga: number, seed: number): PredictOption[] {
  const actual = `${gf}-${ga}`
  const pool = ['1-0', '2-0', '2-1', '1-1', '0-0', '0-1', '1-2', '3-1', '3-0']
  const keys = new Set<string>([actual])
  let i = Math.abs(seed) + 1
  while (keys.size < 4) { keys.add(pool[i % pool.length]); i++ }
  return [...keys].slice(0, 4)
    .sort((a, b) => {
      const [ah, aa] = a.split('-').map(Number), [bh, ba] = b.split('-').map(Number)
      return (ah + aa) - (bh + ba) || ah - bh
    })
    .map((s) => ({ key: s, label: s.replace('-', '–'), correct: s === actual }))
}
function scorerOpts(replay: SeasonReplay, um: UserMatch, md: number): PredictOption[] {
  const first = um.m.goals.filter((g) => g.home === um.home).sort((a, b) => a.minute - b.minute)[0]?.scorer ?? ''
  const xi = replay.debrief.table.find((t) => t.you)?.players ?? []
  const cands = xi.filter((p) => p.line === 'ATT' || p.line === 'MID').map((p) => p.name)
  const uniq: string[] = []
  for (const n of [first, ...cands]) { if (n && !uniq.includes(n)) uniq.push(n); if (uniq.length >= 4) break }
  const arr = uniq.slice(0, 4)
  for (let i = arr.length - 1; i > 0; i--) { const j = (md * 7 + i * 13) % (i + 1);[arr[i], arr[j]] = [arr[j], arr[i]] }
  return arr.map((n) => ({ key: n, label: surname(n), correct: n === first }))
}
const finalHeadline = (pos: number) =>
  pos === 1 ? 'Win and you are champions.' : pos > 0 && pos <= 4 ? 'A top-four place rests on this.'
    : pos >= 18 ? 'Survival is on the line.' : 'The final day — finish in style.'

/** The scenario for the user's match this matchday, or null if it's an ordinary game. Priority = most salient first. */
export function detectScenario(replay: SeasonReplay, md: number): Scenario | null {
  const total = replay.matchdays.length
  const you = userName(replay)
  const um = findUserMatch(replay.matchdays[md], you)
  if (!um) return null

  const pre = md > 0 ? replay.matchdays[md - 1].table : null
  const userPos = pre ? pre.findIndex((r) => r.you) + 1 : 0
  const oppPos = pre ? pre.findIndex((r) => r.team === um.opp) + 1 : 0
  const userPts = pre?.find((r) => r.you)?.points ?? 0
  const oppPts = pre?.find((r) => r.team === um.opp)?.points ?? 0
  const prior = priorResults(replay, md, you)
  const unbeaten = trailing(prior, (r) => r !== 'L')
  const wins = trailing(prior, (r) => r === 'W')
  const losses = trailing(prior, (r) => r === 'L')
  const totalLosses = prior.filter((u) => u.res === 'L').length
  const opp = shortClub(um.opp)
  const late = md >= 6
  const base = (tag: string, headline: string, question: string, kind: PredictKind, options: PredictOption[]): Scenario =>
    ({ tag, headline, question, kind, options, vs: opp, home: um.home, finalDay: md === total - 1 })

  if (md === total - 1)
    return base('Final Day', finalHeadline(userPos), 'How does the finale go?', 'result', resultOpts(um))
  if (pre && md >= total - 6 && userPos === 1 && pre[1] && userPts - pre[1].points >= 6)
    return base('The Clincher', `Win and the title is all but sealed.`, 'Do you get it done?', 'yesno', yesno('We win it', 'We slip', um.res === 'W'))
  if (late && userPos >= 1 && userPos <= 2 && oppPos >= 1 && oppPos <= 2)
    return base('Summit Clash', `First plays second — ${opp} stand in your way.`, 'Call the score (your goals first):', 'scoreline', scorelineOpts(um.gf, um.ga, md))
  if (totalLosses === 0 && md >= 24)
    return base('Invincible Watch', `${md} unbeaten. Immortality is on.`, 'Still unbeaten after this?', 'yesno', yesno('Yes — unbeaten', 'No — it ends', um.res !== 'L'))
  if (unbeaten >= 8 && oppPos > 0 && oppPos <= 8)
    return base('The Run on the Line', `${unbeaten} unbeaten, and a real test in ${opp}.`, 'Does the run survive?', 'yesno', yesno('It survives', 'It ends', um.res !== 'L'))
  if (late && pre && userPos <= 6 && oppPos > 0 && oppPos <= 6 && Math.abs(userPts - oppPts) <= 4)
    return base('Six-Pointer', `A huge one in the race with ${opp}.`, 'How does it go?', 'result', resultOpts(um))
  if (late && pre && userPos >= 15 && oppPos >= 15)
    return base('Relegation Six-Pointer', `A scrap for survival with ${opp}.`, 'How does it go?', 'result', resultOpts(um))
  if (late && pre && userPos >= 12 && oppPos > 0 && oppPos <= 4)
    return base('Giant-Killing', `${opp} sit near the top. Your shot at a scalp.`, 'Cause an upset?', 'yesno', yesno('We shock them', 'No upset', um.res === 'W'))
  if (late && pre && userPos > 0 && userPos <= 6 && oppPos >= 14 && !um.home)
    return base('Banana Skin', `A tricky trip to struggling ${opp}.`, 'Avoid the slip-up?', 'yesno', yesno('We handle it', 'We slip up', um.res !== 'L'))
  const tal = talisman(prior)
  if (tal && tal.goals >= 6 && um.gf >= 1)
    return base('Talisman', `${surname(tal.name)} has ${tal.goals} this season.`, 'Who opens the scoring for you?', 'scorer', scorerOpts(replay, um, md))
  if (wins >= 5)
    return base('On a Roll', `${wins} straight wins.`, `Make it ${wins + 1}?`, 'yesno', yesno('Win again', 'Streak ends', um.res === 'W'))
  if (losses >= 3)
    return base('Stop the Rot', `${losses} defeats on the spin.`, 'End the slide?', 'yesno', yesno('We respond', 'More misery', um.res !== 'L'))
  return null
}
