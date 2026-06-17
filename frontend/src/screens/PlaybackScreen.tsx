import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import type { MatchResult, SeasonReplay } from '../api'
import { Panel, Stamp } from '../components/primitives'
import { SeasonSpine } from '../components/SeasonSpine'
import { userSpine } from '../season'
import { detectScenario, lastName } from '../predictions'
import type { Scenario } from '../predictions'

const BASE_MS = 1100 // matchday advance interval at 1x
const PREDICT_COOLDOWN = 6 // min matchdays between prediction prompts (the final day always asks). Tune for frequency.

const shortClub = (n: string) => n.replace(/\s+\d{2,4}\/\d{2}$/, '').trim()

export interface Pundit { correct: number; total: number }

export function PlaybackScreen({ replay, onFinish }: { replay: SeasonReplay; onFinish: (pundit: Pundit) => void }) {
  const total = replay.matchdays.length
  const [md, setMd] = useState(0)
  const [playing, setPlaying] = useState(false) // start paused — the user presses PLAY when ready
  const [speed, setSpeed] = useState(1)
  const [results, setResults] = useState<Record<number, boolean>>({}) // md -> called it right?
  const [prediction, setPrediction] = useState<Scenario | null>(null)
  const [pick, setPick] = useState<string | null>(null)
  const usedCounts = useRef<Record<string, number>>({}) // scenario tag -> times shown this season (season caps)
  const playingRef = useRef(playing); playingRef.current = playing
  const resumeRef = useRef(false) // was the sim playing when the prompt interrupted it?
  const atEnd = md >= total - 1

  const pundit: Pundit = { correct: Object.values(results).filter(Boolean).length, total: Object.keys(results).length }

  // A "moment" in this matchday's fixture → pause and ask the user to call it. Skips a scenario type used in the
  // last few prompts (variety), and rate-limits by PREDICT_COOLDOWN; the final day always asks.
  useEffect(() => {
    if (prediction || results[md] !== undefined) return
    const sc = detectScenario(replay, md, usedCounts.current)
    if (!sc) return
    const answered = Object.keys(results).map(Number)
    const lastAsked = answered.length ? Math.max(...answered) : -99
    if (sc.finalDay || md - lastAsked >= PREDICT_COOLDOWN) {
      resumeRef.current = playingRef.current // restore play/pause after the prompt
      setPrediction(sc); setPick(null); setPlaying(false)
      usedCounts.current[sc.tag] = (usedCounts.current[sc.tag] ?? 0) + 1
    }
  }, [md, prediction, results, replay])

  // Auto-advance while playing (never through a pending prediction).
  useEffect(() => {
    if (!playing || atEnd || prediction) return
    const t = setTimeout(() => setMd((m) => Math.min(total - 1, m + 1)), BASE_MS / speed)
    return () => clearTimeout(t)
  }, [playing, md, speed, atEnd, total, prediction])

  // Keyboard: space play/pause · ←/→ step · F speed · S/Enter skip. (Disabled while a prediction is up.)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (prediction) return
      const k = e.key.toLowerCase()
      if (e.key === ' ') { e.preventDefault(); setPlaying((p) => !p) }
      else if (e.key === 'ArrowRight') { setPlaying(false); setMd((m) => Math.min(total - 1, m + 1)) }
      else if (e.key === 'ArrowLeft') { setPlaying(false); setMd((m) => Math.max(0, m - 1)) }
      else if (k === 'f') setSpeed((s) => (s >= 4 ? 1 : s * 2))
      else if (k === 's' || e.key === 'Enter') onFinish(pundit)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [total, onFinish, prediction, pundit])

  const day = replay.matchdays[md]
  const matches = [...day.matches].sort((a, b) => Number(b.userMatch) - Number(a.userMatch))
  const userMatch = day.matches.find((m) => m.userMatch)
  const predicting = !!(prediction && userMatch)
  // Your season so far, as a spine. While predicting we hide the current result so the ribbon can't spoil the call.
  const spine = userSpine(replay)
  const spineGames = spine.slice(0, predicting ? md : md + 1)

  // While predicting we show the PRE-match standings (so the table doesn't leak the result) and highlight the
  // fixture's two teams; otherwise the live (post-matchday) table with movement + next fixtures.
  const userTeam = day.table.find((r) => r.you)?.team ?? ''
  const oppTeam = predicting && userMatch ? (userMatch.home === userTeam ? userMatch.away : userMatch.home) : ''
  const rows = predicting ? (replay.matchdays[md - 1]?.table ?? day.table) : day.table

  const prevRank = new Map<string, number>()
  if (md > 0) replay.matchdays[md - 1].table.forEach((r, i) => prevRank.set(r.team, i))
  const nextFixture = new Map<string, { opp: string; home: boolean }>()
  if (md < total - 1) for (const m of replay.matchdays[md + 1].matches) {
    nextFixture.set(m.home, { opp: m.away, home: true })
    nextFixture.set(m.away, { opp: m.home, home: false })
  }

  return (
    <div className="flex flex-col gap-3 lg:h-[calc(100vh-9rem)]">
      {/* header / controls */}
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="font-display text-lg italic text-ink/70">Matchday <span className="font-semibold not-italic text-amber">{day.number}</span> of {total}</span>
          <div className="h-1 w-40 overflow-hidden bg-panel-2">
            <div className="h-full bg-amber transition-all" style={{ width: `${((md + 1) / total) * 100}%` }} />
          </div>
          {pundit.total > 0 && <span className="eyebrow text-ink/45">Pundit <span className="text-amber">{pundit.correct}/{pundit.total}</span></span>}
        </div>
        {!prediction && (
          <div className="flex items-center gap-1.5 text-xs">
            <Ctrl onClick={() => { setPlaying(false); setMd((m) => Math.max(0, m - 1)) }} label="◀" />
            <Ctrl onClick={() => setPlaying((p) => !p)} label={playing ? '⏸ PAUSE' : '▶ PLAY'} wide active />
            <Ctrl onClick={() => { setPlaying(false); setMd((m) => Math.min(total - 1, m + 1)) }} label="▶" />
            <Ctrl onClick={() => setSpeed((s) => (s >= 4 ? 1 : s * 2))} label={`▶▶ ${speed}×`} />
            <Ctrl onClick={() => onFinish(pundit)} label="⏭ SKIP" />
          </div>
        )}
      </div>

      {/* the spine of the season — your run, building game by game */}
      <div className="shrink-0 border border-edge bg-panel/40 px-3 py-2">
        <SeasonSpine games={spineGames} total={total} animate height="h-5" />
      </div>

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[1fr_minmax(360px,440px)]">
        {/* standings (pre-match while predicting, live otherwise) */}
        <Panel label={predicting ? 'Standings · before kickoff' : 'The Table · live'} className="flex min-h-0 flex-col p-4">
          <div className="mb-1 flex shrink-0 items-center gap-2 px-2 text-[10px] tracking-widest text-ink/40">
            <span className="w-9" /><span className="flex-1">CLUB</span>
            {!predicting && <span className="hidden w-28 text-right sm:inline">NEXT</span>}
            <span className="w-8 text-right">P</span><span className="w-9 text-right">GD</span><span className="w-8 text-right">PTS</span>
          </div>
          <div className="min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
            {rows.map((r, i) => {
              const prev = predicting ? undefined : prevRank.get(r.team)
              const delta = prev === undefined ? 0 : prev - i
              const nf = predicting ? undefined : nextFixture.get(r.team)
              const isOpp = predicting && r.team === oppTeam
              return (
              <motion.div
                layout key={r.team} transition={{ type: 'spring', stiffness: 600, damping: 44 }}
                className={`flex items-center gap-2 px-2 py-1 text-sm ${r.you ? 'border border-amber/60 bg-amber/10 text-amber' : isOpp ? 'border border-edge-bright bg-panel-2 text-ink-bright' : 'border border-transparent text-ink/75'}`}
              >
                <span className="flex w-9 items-center justify-end gap-0.5 tabular-nums text-ink/50">
                  {i + 1}
                  {delta > 0 && <span className="text-phosphor text-[10px]">▲</span>}
                  {delta < 0 && <span className="text-danger text-[10px]">▼</span>}
                </span>
                <span className="flex-1 truncate font-bold">{r.team}</span>
                {!predicting && <span className="hidden w-28 truncate text-right text-[10px] text-ink/35 sm:inline">{nf ? `${nf.home ? 'v' : '@'} ${shortClub(nf.opp)}` : '—'}</span>}
                <span className="w-8 text-right tabular-nums text-ink/45">{r.played}</span>
                <span className="w-9 text-right tabular-nums">{r.gd > 0 ? `+${r.gd}` : r.gd}</span>
                <span className="w-8 text-right font-bold tabular-nums">{r.points}</span>
              </motion.div>
              )
            })}
          </div>
        </Panel>

        {/* right column: the prediction prompt, or the matchday results */}
        {predicting && userMatch ? (
          <PredictionView
            scenario={prediction!} mdNumber={day.number} userMatch={userMatch} pick={pick}
            onPick={(opt) => { setPick(opt.key); setResults((r) => ({ ...r, [md]: opt.correct })) }}
            onContinue={() => { setPrediction(null); setPick(null); setPlaying(resumeRef.current) }}
          />
        ) : (
          <Panel label={`Matchday ${day.number} · results`} className="flex min-h-0 flex-col p-4">
            <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto pr-1">
              {matches.map((m, i) => <MatchCard key={i} m={m} verdict={m.userMatch ? results[md] : undefined} />)}
            </div>
            {atEnd && (
              <motion.button
                initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                onClick={() => onFinish(pundit)}
                className="mt-3 w-full shrink-0 bg-amber py-3 text-sm font-bold uppercase tracking-[0.18em] text-terminal transition hover:bg-ink-bright"
              >
                Read the Debrief
              </motion.button>
            )}
          </Panel>
        )}
      </div>

      <div className="shrink-0 text-center text-[10px] text-ink/35">
        SPACE play/pause · ◀ ▶ step matchday · F speed · S skip · ▲▼ vs last MD · big moments pause to be called
      </div>
    </div>
  )
}

function PredictionView({
  scenario, mdNumber, userMatch, pick, onPick, onContinue,
}: {
  scenario: Scenario; mdNumber: number; userMatch: MatchResult; pick: string | null
  onPick: (opt: { key: string; correct: boolean }) => void; onContinue: () => void
}) {
  const ugf = scenario.home ? userMatch.homeGoals : userMatch.awayGoals
  const uga = scenario.home ? userMatch.awayGoals : userMatch.homeGoals
  const picked = pick ? scenario.options.find((o) => o.key === pick) : null
  const right = picked?.correct
  const userScorers = userMatch.goals.filter((g) => g.home === scenario.home).map((g) => `${lastName(g.scorer)} ${g.minute}'`)

  return (
    <Panel accent="amber" className="flex min-h-0 flex-col p-5">
      <div className="shrink-0 text-center"><Stamp text={scenario.tag} tone="amber" /></div>
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center text-center">
        <div className="font-display text-2xl font-semibold leading-snug text-ink-bright">{scenario.headline}</div>
        <div className="mt-1 text-[12px] text-ink/50">Matchday {mdNumber} · you {scenario.home ? 'vs' : '@'} {scenario.vs}</div>

        {!picked ? (
          <div className="mt-5 w-full max-w-xs">
            <div className="font-display text-[15px] italic text-amber">{scenario.question}</div>
            <div className="mt-3 space-y-2">
              {scenario.options.map((o) => (
                <button
                  key={o.key} onClick={() => onPick(o)}
                  className="w-full border border-edge px-3 py-2.5 text-sm font-semibold text-ink-bright transition hover:border-amber hover:bg-amber/10 hover:text-amber"
                >
                  {o.label}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mt-5">
            <div className={`font-display text-3xl font-black ${right ? 'text-phosphor' : 'text-danger'}`}>
              {right ? 'Called it' : 'Not this time'}
            </div>
            <div className="mt-2 text-sm text-ink/70">
              You said <span className="font-semibold text-ink-bright">{picked.label}</span> ·
              {' '}final <span className="font-bold tabular-nums text-amber">{ugf}–{uga}</span>
            </div>
            {userScorers.length > 0 && <div className="mt-1 text-[11px] text-ink/45">{userScorers.join(' · ')}</div>}
            <button onClick={onContinue}
              className="mt-6 border-2 border-phosphor bg-phosphor/10 px-6 py-2.5 text-sm font-bold uppercase tracking-[0.18em] text-phosphor transition hover:bg-phosphor/20">
              Continue ▸
            </button>
          </motion.div>
        )}
      </div>
    </Panel>
  )
}

function MatchCard({ m, verdict }: { m: MatchResult; verdict?: boolean }) {
  const homeGoals = m.goals.filter((g) => g.home)
  const awayGoals = m.goals.filter((g) => !g.home)
  return (
    <div className={`border p-2 ${m.userMatch ? 'border-amber/60 bg-amber/10' : 'border-edge'}`}>
      {m.userMatch && (
        <div className="mb-1 flex items-center justify-center gap-2">
          <Stamp text="Your Match" tone="amber" />
          {verdict !== undefined && <span className={`text-[10px] font-bold ${verdict ? 'text-phosphor' : 'text-danger'}`}>{verdict ? '✓ called' : '✗ missed'}</span>}
        </div>
      )}
      <div className="flex items-center gap-2 text-sm">
        <span className="flex-1 truncate text-right font-semibold text-ink-bright">{m.home}</span>
        <span className="bg-panel-2 px-2 font-bold tabular-nums text-amber">{m.homeGoals}–{m.awayGoals}</span>
        <span className="flex-1 truncate font-semibold text-ink-bright">{m.away}</span>
      </div>
      {m.goals.length > 0 && (
        <div className="mt-1 flex justify-between gap-2 text-[10px] text-ink/50">
          <span className="flex-1 truncate text-right">{homeGoals.map((g) => `${lastName(g.scorer)} ${g.minute}'`).join(' · ')}</span>
          <span className="text-ink/25">⚽</span>
          <span className="flex-1 truncate">{awayGoals.map((g) => `${lastName(g.scorer)} ${g.minute}'`).join(' · ')}</span>
        </div>
      )}
    </div>
  )
}

function Ctrl({ onClick, label, wide, active }: { onClick: () => void; label: string; wide?: boolean; active?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={`border px-2 py-1.5 font-semibold tracking-wide transition ${wide ? 'px-3' : ''} ${active ? 'border-amber bg-amber/10 text-amber' : 'border-edge text-ink/70 hover:border-amber hover:text-amber'}`}
    >
      {label}
    </button>
  )
}
