import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api } from '../api'
import type {
  DeclassifiedPlayer, LeagueTeam, Preview, RunState, SeasonReplay, Slot, SpinView, SquadPlayer, Strength,
} from '../api'
import { lineClasses, lineOf } from '../theme'
import { PitchView } from '../components/PitchView'
import { TeamPitch } from '../components/TeamPitch'
import { SpinReveal } from '../components/SpinReveal'
import { RatingBadge } from '../components/RatingBadge'
import { PositionChip } from '../components/PositionChip'
import { Panel, Prompt, Stamp } from '../components/primitives'

const ordinal = (n: number) => {
  const s = ['th', 'st', 'nd', 'rd'], v = n % 100
  return n + (s[(v - 20) % 10] ?? s[v] ?? s[0])
}

const TIER_COLOR: Record<string, string> = {
  JUGGERNAUT: 'text-amber border-amber',
  "TITLE CONTENDER": 'text-phosphor border-phosphor',
  "EUROPEAN CHASER": 'text-def border-def',
  'MID-TABLE': 'text-ink border-edge-bright',
  "RELEGATION SCRAPPER": 'text-danger border-danger',
}

export function DraftScreen({
  run,
  onRun,
  onSimulated,
}: {
  run: RunState
  onRun: (r: RunState) => void
  onSimulated: (r: SeasonReplay) => void
}) {
  const [spin, setSpin] = useState<SpinView | null>(null)
  const [selected, setSelected] = useState<SquadPlayer | null>(null)
  const [moveFrom, setMoveFrom] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [declassified, setDeclassified] = useState<{ club: string; season: string; players: DeclassifiedPlayer[] } | null>(null)
  const [revealing, setRevealing] = useState(false)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [inspect, setInspect] = useState<LeagueTeam | null>(null)

  const complete = run.slotsRemaining === 0
  const openSlots = run.slots.filter((s) => !s.filled)

  useEffect(() => {
    if (complete && !preview && !declassified) api.preview(run.runId).then(setPreview).catch(() => {})
  }, [complete, preview, declassified, run.runId])

  let interactive: Set<number> | undefined
  if (selected) {
    interactive = new Set(openSlots.filter((s) => selected.eligibleSlots.includes(s.position)).map((s) => s.index))
  } else if (moveFrom != null) {
    const mover = run.slots.find((s) => s.index === moveFrom)
    const can = mover?.positions ?? []
    interactive = new Set(openSlots.filter((s) => can.includes(s.position)).map((s) => s.index))
  }

  async function guard(fn: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try { await fn() } catch (e) { setError(e instanceof Error ? e.message : 'request failed') } finally { setBusy(false) }
  }

  const doSpin = () =>
    guard(async () => {
      setScanning(true)
      try {
        const s = await api.spin(run.runId)
        setSpin(s); setSelected(null); setMoveFrom(null); setRevealing(true)
        onRun({ ...run, rerollsRemaining: s.rerollsRemaining })
      } finally { setScanning(false) }
    })

  const doDraft = (slotPosition: string, sofifaId: number) =>
    guard(async () => {
      const res = await api.draft(run.runId, slotPosition, sofifaId)
      onRun(res.state); setSpin(null); setSelected(null); setRevealing(false)
      // The "who you passed on" reveal only adds value when ratings were hidden — skip it in full-ratings mode.
      if (run.showRatings !== 'ON') setDeclassified({ club: res.club, season: res.season, players: res.declassified })
    })

  const onPitchClick = (slot: Slot) => {
    if (selected) { if (interactive?.has(slot.index)) doDraft(slot.position, selected.sofifaId); return }
    if (moveFrom != null) {
      if (slot.index === moveFrom) return setMoveFrom(null)
      if (interactive?.has(slot.index)) {
        const from = moveFrom
        guard(async () => { onRun(await api.move(run.runId, from, slot.index)); setMoveFrom(null) })
      }
      return
    }
    if (slot.filled) setMoveFrom(slot.index)
  }

  return (
    <div className="flex flex-col gap-3 lg:h-[calc(100vh-9rem)]">
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3">
        <span className="eyebrow text-ink/60">
          XI to fill <span className="text-amber">{run.slotsRemaining}</span> / 11
        </span>
        <StrengthStrip s={run.strength} />
        <span className="eyebrow text-ink/60">
          Rerolls <span className="text-phosphor">{run.rerollsRemaining} / {difficultyRerolls(run.difficulty)}</span>
        </span>
      </div>

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[1fr_minmax(440px,520px)]">
        <Panel className="min-h-0 p-2">
          <PitchView formation={run.formation} slots={run.slots} interactive={interactive} selectedIndex={moveFrom} onSlotClick={onPitchClick} />
        </Panel>

        <div className="flex min-h-0 flex-col">
          {declassified ? (
            <DeclassifiedPanel
              data={declassified}
              complete={complete}
              busy={busy}
              onNext={() => { setDeclassified(null); if (!complete) doSpin() }}
            />
          ) : complete ? (
            <LeaguePanel preview={preview} busy={busy} onInspect={setInspect} onRun={() => guard(async () => onSimulated(await api.simulate(run.runId)))} />
          ) : !spin ? (
            <Panel label="The Spin" className="flex flex-1 flex-col items-center justify-center gap-5 p-8">
              <Prompt>spin to draw a club, then draft from its squad</Prompt>
              <motion.button
                whileHover={{ scale: busy ? 1 : 1.03 }} whileTap={{ scale: busy ? 1 : 0.97 }}
                disabled={busy} onClick={doSpin}
                className="relative flex h-40 w-40 items-center justify-center rounded-full border border-amber/50 bg-amber/5 font-display text-lg italic text-amber transition hover:bg-amber/10 disabled:opacity-30"
              >
                {scanning ? <span className="text-base not-italic tracking-[0.2em]">scanning…</span> : <span>Spin</span>}
                <span className="absolute inset-2.5 rounded-full border border-amber/20" />
              </motion.button>
            </Panel>
          ) : revealing ? (
            <SpinReveal spin={spin} onAccess={() => setRevealing(false)} />
          ) : (
            <SquadPanel
              spin={spin} selected={selected} busy={busy} canReroll={run.rerollsRemaining > 0}
              onSelect={(p) => { setSelected(p); setMoveFrom(null) }}
              onReroll={doSpin} onDraft={(p, pos) => doDraft(pos, p.sofifaId)}
            />
          )}
          {error && <div className="mt-2 shrink-0 border border-danger/50 bg-danger/10 p-2 text-sm text-danger">! {error}</div>}
          {!complete && !declassified && !revealing && (
            <div className="mt-2 shrink-0 font-display text-[12px] italic text-ink/40">
              {moveFrom != null ? '— select a highlighted slot to reposition · click the player again to cancel'
                : selected ? '— select a highlighted pitch slot, or use Deploy below'
                  : '— click a deployed player on the pitch to reposition them'}
            </div>
          )}
        </div>
      </div>

      <AnimatePresence>
        {inspect && (
          <Backdrop onClose={() => setInspect(null)}>
            <div className="mb-3 flex items-center justify-between gap-3">
              <div className="truncate font-display text-lg font-semibold text-ink-bright">{inspect.team}</div>
              <span className={`shrink-0 border px-2 py-0.5 text-[10px] uppercase tracking-[0.14em] ${TIER_COLOR[inspect.tier] ?? 'text-ink border-edge'}`}>{inspect.tier} · {inspect.strength}</span>
            </div>
            <TeamPitch formation={inspect.formation} players={inspect.xi} />
            <button onClick={() => setInspect(null)} className="mt-4 w-full border border-edge py-2 text-xs font-semibold uppercase tracking-[0.16em] text-ink/70 hover:border-amber hover:text-amber">Close</button>
          </Backdrop>
        )}
      </AnimatePresence>
    </div>
  )
}

function StrengthStrip({ s }: { s: Strength | null }) {
  const Cell = ({ label, v, cls }: { label: string; v: number | null | undefined; cls: string }) => (
    <span className="flex items-center gap-1"><span className="text-ink/35">{label}</span><span className={`font-bold tabular-nums ${cls}`}>{v ?? '—'}</span></span>
  )
  return (
    <div className="flex items-center gap-2.5 border border-edge px-2.5 py-1 text-[11px] sm:gap-3">
      <span className="flex items-center gap-1"><span className="text-ink/35">OVR</span><span className="font-bold tabular-nums text-ink-bright">{s?.overall ?? '—'}</span></span>
      <Cell label="ATT" v={s?.attack} cls="text-att" />
      <Cell label="MID" v={s?.midfield} cls="text-mid" />
      <Cell label="DEF" v={s?.defence} cls="text-def" />
      <Cell label="GK" v={s?.gk} cls="text-gk" />
    </div>
  )
}

function DeclassifiedPanel({
  data, complete, busy, onNext,
}: {
  data: { club: string; season: string; players: DeclassifiedPlayer[] }; complete: boolean; busy: boolean; onNext: () => void
}) {
  return (
    <Panel label="Squad Declassified" accent="amber" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="mb-3 shrink-0">
        <div className="font-display text-lg font-semibold text-ink-bright">{data.club}</div>
        <div className="text-[11px] text-ink/45">{data.season} · who you passed on</div>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-2 gap-1.5 overflow-y-auto pr-1">
        {data.players.map((p) => {
          const lc = lineClasses[p.line]
          const dim = !p.eligible && !p.draftedByYou // couldn't be picked into an open slot — greyed out
          return (
            <div key={p.sofifaId} className={`flex items-center gap-2 border px-2 py-1 ${p.draftedByYou ? 'border-amber/60 bg-amber/10' : dim ? 'border-edge/50 opacity-40' : 'border-edge'}`}>
              <span className={`flex h-7 w-8 items-center justify-center border ${lc.border} ${lc.text} text-xs font-bold tabular-nums`}>{p.overall}</span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-xs font-semibold text-ink-bright">{p.name}</div>
                <div className={`text-[9px] font-semibold ${lc.text}`}>{p.position}{p.draftedByYou ? ' · ★ yours' : dim ? ' · n/a' : ''}</div>
              </div>
            </div>
          )
        })}
      </div>
      <button disabled={busy} onClick={onNext} className="mt-3 w-full shrink-0 bg-amber py-3 text-sm font-bold uppercase tracking-[0.18em] text-terminal transition hover:bg-ink-bright disabled:opacity-50">
        {complete ? 'View the League' : 'Next Spin'}
      </button>
    </Panel>
  )
}

function LeaguePanel({ preview, busy, onRun, onInspect }: { preview: Preview | null; busy: boolean; onRun: () => void; onInspect: (t: LeagueTeam) => void }) {
  const p = preview?.projection
  return (
    <Panel label="The League · pre-season" accent="amber" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="shrink-0">
        <div className="flex items-end justify-between">
          <div>
            <div className="eyebrow text-ink/50">Projected Finish</div>
            <div className="font-display text-5xl font-black leading-none text-amber">
              {preview ? ordinal(preview.userProjectedPos) : '—'}
              <span className="ml-1 font-sans text-sm font-normal not-italic text-ink/50">· {p?.expectedPoints ?? '—'} pts</span>
            </div>
          </div>
          <div className="text-right text-[10px] text-ink/50">
            <span className="eyebrow">Your OVR</span> <span className="text-ink-bright">{preview?.userOverall ?? '—'}</span><br />
            <span className="eyebrow">League Mean</span> <span className="text-ink-bright">{preview?.leagueMean ?? '—'}</span>
          </div>
        </div>
        <div className="mt-4 space-y-1.5">
          <OddsRow label="Win League" v={p?.winLeague ?? 0} />
          <OddsRow label="Top 4" v={p?.top4 ?? 0} />
          <OddsRow label="Relegation" v={p?.relegation ?? 0} />
        </div>
        <div className="mt-4 mb-1 flex items-center gap-2 border-b border-edge pb-1">
          <span className="eyebrow w-5 text-right text-ink/45">#</span><span className="eyebrow flex-1 text-ink/45">Opponents · tap to scout</span><span className="eyebrow w-11 text-right text-ink/45">Proj</span><span className="eyebrow w-7 text-right text-ink/45">OVR</span>
        </div>
      </div>
      <div className="min-h-0 flex-1 space-y-1 overflow-y-auto pr-1">
        {(preview?.league ?? []).map((t, i) => (
          <button key={i} onClick={() => onInspect(t)} className="flex w-full items-center gap-2 border border-transparent px-2 py-1 text-left text-sm hover:border-edge-bright">
            <span className="w-5 text-right tabular-nums text-ink/40">{t.projectedPos}</span>
            <span className="flex-1 truncate text-ink-bright">{t.team}</span>
            <span className={`border px-1 text-[9px] uppercase tracking-[0.12em] ${TIER_COLOR[t.tier] ?? 'text-ink border-edge'}`}>{t.tier}</span>
            <span className="w-11 text-right text-[11px] tabular-nums text-ink/55">{t.projectedPoints} pt</span>
            <span className="w-7 text-right font-bold tabular-nums text-amber">{t.strength}</span>
          </button>
        ))}
        {!preview && <div className="p-4 text-center font-display text-sm italic text-ink/50">compiling the league dossier…</div>}
      </div>
      <motion.button
        whileHover={{ scale: busy ? 1 : 1.02 }} whileTap={{ scale: busy ? 1 : 0.98 }} disabled={busy || !preview} onClick={onRun}
        className="mt-3 w-full shrink-0 bg-amber py-3.5 text-base font-bold uppercase tracking-[0.18em] text-terminal transition hover:bg-ink-bright disabled:opacity-50"
      >
        {busy ? 'Playing the season…' : 'Play the Season'}
      </motion.button>
    </Panel>
  )
}

function OddsRow({ label, v }: { label: string; v: number }) {
  const pct = Math.round(v * 100)
  return (
    <div className="flex items-center gap-2">
      <span className="eyebrow w-24 text-ink/55">{label}</span>
      <div className="h-1.5 flex-1 overflow-hidden bg-panel-2">
        <motion.div className="h-full bg-amber" initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ duration: 0.6 }} />
      </div>
      <span className="w-8 text-right text-xs tabular-nums text-amber">{pct}%</span>
    </div>
  )
}

function SquadPanel({
  spin, selected, busy, canReroll, onSelect, onReroll, onDraft,
}: {
  spin: SpinView; selected: SquadPlayer | null; busy: boolean; canReroll: boolean
  onSelect: (p: SquadPlayer) => void; onReroll: () => void; onDraft: (p: SquadPlayer, position: string) => void
}) {
  return (
    <Panel label="The Spun Squad" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="mb-3 flex shrink-0 items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-display text-lg font-semibold text-ink-bright">{spin.club}</div>
          <div className="text-[11px] text-ink/50">{spin.season}{spin.league ? ` · ${spin.league}` : ''}</div>
        </div>
        <button disabled={busy || !canReroll} onClick={onReroll} className="shrink-0 border border-edge px-3 py-1.5 text-xs font-semibold tracking-wide text-ink/70 hover:border-amber hover:text-amber disabled:opacity-30">↻ Reroll</button>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-2 gap-1.5 overflow-y-auto pr-1">
        {spin.squad.map((p, i) => {
          const eligible = p.eligibleSlots.length > 0
          const isSel = selected?.sofifaId === p.sofifaId
          return (
            <motion.div key={p.sofifaId} initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: Math.min(i * 0.02, 0.3) }}>
              <button
                disabled={!eligible || busy} onClick={() => onSelect(p)}
                className={`flex h-full w-full items-start gap-2 border p-1.5 text-left transition ${isSel ? 'border-amber/60 bg-amber/10' : eligible ? 'border-edge hover:border-edge-bright' : 'border-edge/50 opacity-40'}`}
              >
                <RatingBadge rating={p.rating} line={lineOf(p.positions[0] ?? 'CM')} size="md" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold text-ink-bright">{p.name}</div>
                  <div className="truncate text-[9px] text-ink/45">{p.nation}</div>
                  <div className="mt-0.5 flex flex-wrap gap-0.5">{p.positions.slice(0, 4).map((pos) => <PositionChip key={pos} position={pos} />)}</div>
                  <div className="mt-0.5 text-[9px] text-phosphor">{eligible ? `fits ${p.eligibleSlots.length}` : 'n/a'}</div>
                </div>
              </button>
            </motion.div>
          )
        })}
      </div>
      <AnimatePresence>
        {selected && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="shrink-0 overflow-hidden">
            <div className="mt-2 border-t border-edge pt-3">
              <div className="mb-2 flex items-center gap-2 text-xs"><Stamp text="Place" tone="phosphor" /><span className="font-semibold text-ink-bright">{selected.name}</span></div>
              <div className="flex flex-wrap gap-2">
                {selected.eligibleSlots.map((pos) => {
                  const lc = lineClasses[lineOf(pos)]
                  return <button key={pos} disabled={busy} onClick={() => onDraft(selected, pos)} className={`border ${lc.border} ${lc.text} px-3 py-1.5 text-sm font-semibold transition hover:bg-panel-2 disabled:opacity-40`}>Deploy → {pos}</button>
                })}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Panel>
  )
}

function Backdrop({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <motion.div className="fixed inset-0 z-[60] flex items-center justify-center bg-terminal/85 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
      <motion.div className="w-full max-w-lg border border-edge-bright bg-panel p-6" initial={{ scale: 0.9, y: 10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()}>
        {children}
      </motion.div>
    </motion.div>
  )
}

function difficultyRerolls(d: RunState['difficulty']): number {
  return d === 'EASY' ? 3 : d === 'NORMAL' ? 1 : 0
}
