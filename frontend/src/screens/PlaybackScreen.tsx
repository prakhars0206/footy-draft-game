import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import type { MatchResult, SeasonReplay } from '../api'
import { Panel, Stamp } from '../components/primitives'

const BASE_MS = 1100 // matchday advance interval at 1x

export function PlaybackScreen({ replay, onFinish }: { replay: SeasonReplay; onFinish: () => void }) {
  const total = replay.matchdays.length
  const [md, setMd] = useState(0)
  const [playing, setPlaying] = useState(true)
  const [speed, setSpeed] = useState(1)
  const atEnd = md >= total - 1

  // Auto-advance while playing.
  useEffect(() => {
    if (!playing || atEnd) return
    const t = setTimeout(() => setMd((m) => Math.min(total - 1, m + 1)), BASE_MS / speed)
    return () => clearTimeout(t)
  }, [playing, md, speed, atEnd, total])

  // Keyboard: space play/pause · ←/→ step · F speed · S/Enter skip.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase()
      if (e.key === ' ') { e.preventDefault(); setPlaying((p) => !p) }
      else if (e.key === 'ArrowRight') { setPlaying(false); setMd((m) => Math.min(total - 1, m + 1)) }
      else if (e.key === 'ArrowLeft') { setPlaying(false); setMd((m) => Math.max(0, m - 1)) }
      else if (k === 'f') setSpeed((s) => (s >= 4 ? 1 : s * 2))
      else if (k === 's' || e.key === 'Enter') onFinish()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [total, onFinish])

  const day = replay.matchdays[md]
  const matches = [...day.matches].sort((a, b) => Number(b.userMatch) - Number(a.userMatch))

  return (
    <div className="flex flex-col gap-3 lg:h-[calc(100vh-9rem)]">
      {/* header / controls */}
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-sm tracking-mega text-ink/60">MATCHDAY <span className="text-amber glow-amber">{day.number}</span> / {total}</span>
          <div className="h-1.5 w-40 overflow-hidden border border-edge bg-black/40">
            <div className="h-full bg-amber transition-all" style={{ width: `${((md + 1) / total) * 100}%` }} />
          </div>
        </div>
        <div className="flex items-center gap-1.5 text-xs">
          <Ctrl onClick={() => { setPlaying(false); setMd((m) => Math.max(0, m - 1)) }} label="◀" />
          <Ctrl onClick={() => setPlaying((p) => !p)} label={playing ? '⏸ PAUSE' : '▶ PLAY'} wide active />
          <Ctrl onClick={() => { setPlaying(false); setMd((m) => Math.min(total - 1, m + 1)) }} label="▶" />
          <Ctrl onClick={() => setSpeed((s) => (s >= 4 ? 1 : s * 2))} label={`▶▶ ${speed}×`} />
          <Ctrl onClick={onFinish} label="⏭ SKIP" />
        </div>
      </div>

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[1fr_minmax(360px,440px)]">
        {/* live table */}
        <Panel label="LIVE TABLE" className="flex min-h-0 flex-col p-3">
          <div className="mb-1 flex shrink-0 items-center gap-2 px-2 text-[10px] tracking-widest text-ink/40">
            <span className="w-6" /><span className="flex-1">CLUB</span><span className="w-8 text-right">P</span><span className="w-9 text-right">GD</span><span className="w-8 text-right">PTS</span>
          </div>
          <div className="min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
            {day.table.map((r, i) => (
              <motion.div
                layout key={r.team} transition={{ type: 'spring', stiffness: 600, damping: 44 }}
                className={`flex items-center gap-2 px-2 py-1 text-sm ${r.you ? 'border border-amber bg-amber/10 text-amber glow-amber' : 'border border-transparent text-ink/75'}`}
              >
                <span className="w-6 text-right tabular-nums text-ink/50">{i + 1}</span>
                <span className="flex-1 truncate font-bold">{r.team}</span>
                <span className="w-8 text-right tabular-nums text-ink/45">{r.played}</span>
                <span className="w-9 text-right tabular-nums">{r.gd > 0 ? `+${r.gd}` : r.gd}</span>
                <span className="w-8 text-right font-extrabold tabular-nums">{r.points}</span>
              </motion.div>
            ))}
          </div>
        </Panel>

        {/* matchday results */}
        <Panel label={`MATCHDAY ${day.number} RESULTS`} className="flex min-h-0 flex-col p-3">
          <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto pr-1">
            {matches.map((m, i) => <MatchCard key={i} m={m} />)}
          </div>
          {atEnd && (
            <motion.button
              initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              onClick={onFinish}
              className="mt-3 w-full shrink-0 border-2 border-phosphor bg-phosphor/10 py-3 text-sm font-extrabold tracking-[0.3em] text-phosphor glow-phosphor"
            >
              ▸ VIEW DEBRIEF
            </motion.button>
          )}
        </Panel>
      </div>

      <div className="shrink-0 text-center text-[10px] text-ink/35">
        SPACE play/pause · ◀ ▶ step matchday · F speed · S skip to results
      </div>
    </div>
  )
}

function MatchCard({ m }: { m: MatchResult }) {
  const surname = (n: string) => n.trim().split(/\s+/).pop() ?? n
  const homeGoals = m.goals.filter((g) => g.home)
  const awayGoals = m.goals.filter((g) => !g.home)
  return (
    <div className={`border p-2 ${m.userMatch ? 'border-amber bg-amber/10' : 'border-edge'}`}>
      {m.userMatch && <div className="mb-1 text-center"><Stamp text="YOUR MATCH" tone="amber" /></div>}
      <div className="flex items-center gap-2 text-sm">
        <span className="flex-1 truncate text-right font-bold text-ink-bright">{m.home}</span>
        <span className="border border-edge px-2 font-extrabold tabular-nums text-amber">{m.homeGoals}–{m.awayGoals}</span>
        <span className="flex-1 truncate font-bold text-ink-bright">{m.away}</span>
      </div>
      {m.goals.length > 0 && (
        <div className="mt-1 flex justify-between gap-2 text-[10px] text-ink/50">
          <span className="flex-1 truncate text-right">{homeGoals.map((g) => `${surname(g.scorer)} ${g.minute}'`).join(' · ')}</span>
          <span className="text-ink/25">⚽</span>
          <span className="flex-1 truncate">{awayGoals.map((g) => `${surname(g.scorer)} ${g.minute}'`).join(' · ')}</span>
        </div>
      )}
    </div>
  )
}

function Ctrl({ onClick, label, wide, active }: { onClick: () => void; label: string; wide?: boolean; active?: boolean }) {
  return (
    <button
      onClick={onClick}
      className={`border px-2 py-1.5 font-bold tracking-wide ${wide ? 'px-3' : ''} ${active ? 'border-phosphor text-phosphor glow-phosphor' : 'border-edge text-ink/70 hover:border-amber hover:text-amber'}`}
    >
      {label}
    </button>
  )
}
