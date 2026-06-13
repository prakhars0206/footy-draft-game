import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api } from '../api'
import type {
  DeclassifiedPlayer, LeagueTeam, Preview, RunState, SeasonView, Slot, SpinView, SquadPlayer, Strength,
} from '../api'
import { lineClasses, lineOf } from '../theme'
import { PitchView } from '../components/PitchView'
import { TeamPitch } from '../components/TeamPitch'
import { RatingBadge } from '../components/RatingBadge'
import { PositionChip } from '../components/PositionChip'
import { Panel, Prompt, Stamp } from '../components/primitives'

const TIER_COLOR: Record<string, string> = {
  JUGGERNAUT: 'text-amber border-amber',
  CONTENDER: 'text-phosphor border-phosphor',
  EUROPEAN: 'text-def border-def',
  'MID-TABLE': 'text-ink border-edge-bright',
  SCRAPPER: 'text-danger border-danger',
}

export function DraftScreen({
  run,
  onRun,
  onSimulated,
}: {
  run: RunState
  onRun: (r: RunState) => void
  onSimulated: (s: SeasonView) => void
}) {
  const [spin, setSpin] = useState<SpinView | null>(null)
  const [selected, setSelected] = useState<SquadPlayer | null>(null)
  const [moveFrom, setMoveFrom] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [declassified, setDeclassified] = useState<{ club: string; season: string; players: DeclassifiedPlayer[] } | null>(null)
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
        setSpin(s); setSelected(null); setMoveFrom(null)
        onRun({ ...run, rerollsRemaining: s.rerollsRemaining })
      } finally { setScanning(false) }
    })

  const doDraft = (slotPosition: string, sofifaId: number) =>
    guard(async () => {
      const res = await api.draft(run.runId, slotPosition, sofifaId)
      onRun(res.state); setSpin(null); setSelected(null)
      setDeclassified({ club: res.club, season: res.season, players: res.declassified })
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
      <div className="flex shrink-0 flex-wrap items-center justify-between gap-3 text-xs">
        <span className="tracking-mega text-ink/60">
          TARGETS <span className="text-amber glow-amber">{run.slotsRemaining}</span>/11
        </span>
        <StrengthStrip s={run.strength} />
        <span className="tracking-mega text-ink/60">
          REROLLS <span className="text-phosphor">{run.rerollsRemaining}/{difficultyRerolls(run.difficulty)}</span>
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
            <Panel label="ACQUISITION" className="flex flex-1 flex-col items-center justify-center gap-4 p-8">
              <Prompt>spin to acquire a target squad</Prompt>
              <motion.button
                whileHover={{ scale: busy ? 1 : 1.03 }} whileTap={{ scale: busy ? 1 : 0.97 }}
                disabled={busy} onClick={doSpin}
                className="relative h-40 w-40 border-2 border-phosphor bg-phosphor/5 font-extrabold tracking-[0.2em] text-phosphor glow-phosphor disabled:opacity-30"
              >
                {scanning ? <span className="caret text-sm">SCANNING</span> : <span className="text-sm">◎ SPIN</span>}
                <span className="absolute inset-2 border border-phosphor/30" />
              </motion.button>
            </Panel>
          ) : (
            <SquadPanel
              spin={spin} selected={selected} busy={busy} canReroll={run.rerollsRemaining > 0}
              onSelect={(p) => { setSelected(p); setMoveFrom(null) }}
              onReroll={doSpin} onDraft={(p, pos) => doDraft(pos, p.sofifaId)}
            />
          )}
          {error && <div className="mt-2 shrink-0 border border-danger/50 bg-danger/10 p-2 text-sm text-danger">! {error}</div>}
          {!complete && !declassified && (
            <div className="mt-2 shrink-0 text-[10px] text-ink/40">
              {moveFrom != null ? '> select a highlighted slot to reposition · click the player again to cancel'
                : selected ? '> select a highlighted pitch slot, or use DEPLOY below'
                  : '> click a deployed player on the pitch to reposition them'}
            </div>
          )}
        </div>
      </div>

      <AnimatePresence>
        {inspect && (
          <Backdrop onClose={() => setInspect(null)}>
            <div className="mb-3 flex items-center justify-between">
              <div className="text-sm font-extrabold text-ink-bright">{inspect.team}</div>
              <span className={`border px-2 py-0.5 text-[10px] ${TIER_COLOR[inspect.tier] ?? 'text-ink border-edge'}`}>{inspect.tier} · {inspect.strength}</span>
            </div>
            <TeamPitch formation={inspect.formation} players={inspect.xi} />
            <button onClick={() => setInspect(null)} className="mt-4 w-full border border-edge py-2 text-sm font-bold tracking-widest text-ink/70 hover:border-amber hover:text-amber">CLOSE</button>
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
      <span className="flex items-center gap-1"><span className="text-ink/35">OVR</span><span className="font-extrabold tabular-nums text-ink-bright glow-phosphor">{s?.overall ?? '—'}</span></span>
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
    <Panel label="SQUAD DECLASSIFIED" accent="amber" className="flex min-h-0 flex-1 flex-col p-3">
      <div className="mb-2 shrink-0">
        <div className="flex items-center gap-2"><Stamp text="DECLASSIFIED" tone="amber" /></div>
        <div className="mt-1 text-sm font-bold text-ink-bright">{data.club} · {data.season}</div>
        <div className="text-[10px] text-ink/40">who you passed on ▸</div>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-2 gap-1.5 overflow-y-auto pr-1">
        {data.players.map((p) => {
          const lc = lineClasses[p.line]
          return (
            <div key={p.sofifaId} className={`flex items-center gap-2 border px-2 py-1 ${p.draftedByYou ? 'border-amber bg-amber/10' : 'border-edge'}`}>
              <span className={`flex h-7 w-8 items-center justify-center border ${lc.border} ${lc.text} text-xs font-extrabold tabular-nums`}>{p.overall}</span>
              <div className="min-w-0 flex-1">
                <div className="truncate text-xs font-bold text-ink-bright">{p.name}</div>
                <div className={`text-[9px] font-bold ${lc.text}`}>{p.position}{p.draftedByYou ? ' · ★ YOURS' : ''}</div>
              </div>
            </div>
          )
        })}
      </div>
      <button disabled={busy} onClick={onNext} className="mt-3 w-full shrink-0 border-2 border-phosphor bg-phosphor/10 py-3 text-sm font-extrabold tracking-[0.3em] text-phosphor glow-phosphor disabled:opacity-50">
        {complete ? 'VIEW LEAGUE ▸' : 'NEXT TARGET ▸'}
      </button>
    </Panel>
  )
}

function LeaguePanel({ preview, busy, onRun, onInspect }: { preview: Preview | null; busy: boolean; onRun: () => void; onInspect: (t: LeagueTeam) => void }) {
  const p = preview?.projection
  return (
    <Panel label="THE LEAGUE · PRE-SEASON" accent="amber" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="shrink-0">
        <div className="flex items-end justify-between">
          <div>
            <div className="text-[10px] tracking-mega text-ink/50">PROJECTED</div>
            <div className="text-4xl font-extrabold text-amber glow-amber tabular-nums">{p?.expectedPoints ?? '—'}<span className="text-sm text-ink/50"> pts</span></div>
          </div>
          <div className="text-right text-[10px] text-ink/50">
            YOUR OVR <span className="text-ink-bright">{preview?.userOverall ?? '—'}</span><br />
            LEAGUE MEAN <span className="text-ink-bright">{preview?.leagueMean ?? '—'}</span>
          </div>
        </div>
        <div className="mt-3 space-y-1.5">
          <OddsRow label="WIN LEAGUE" v={p?.winLeague ?? 0} />
          <OddsRow label="TOP 4" v={p?.top4 ?? 0} />
          <OddsRow label="RELEGATION" v={p?.relegation ?? 0} />
        </div>
        <div className="mt-3 mb-1 text-[10px] tracking-mega text-ink/50">OPPONENTS · tap to scout</div>
      </div>
      <div className="min-h-0 flex-1 space-y-1 overflow-y-auto pr-1">
        {(preview?.league ?? []).map((t, i) => (
          <button key={i} onClick={() => onInspect(t)} className="flex w-full items-center gap-2 border border-edge px-2 py-1 text-left text-sm hover:border-edge-bright">
            <span className="w-5 text-right text-ink/40">{i + 1}</span>
            <span className="flex-1 truncate text-ink-bright">{t.team}</span>
            <span className={`border px-1 text-[9px] ${TIER_COLOR[t.tier] ?? 'text-ink border-edge'}`}>{t.tier}</span>
            <span className="w-7 text-right font-extrabold tabular-nums text-amber">{t.strength}</span>
          </button>
        ))}
        {!preview && <div className="caret p-4 text-center text-sm text-ink/50">compiling league dossier</div>}
      </div>
      <motion.button
        whileHover={{ scale: busy ? 1 : 1.02 }} whileTap={{ scale: busy ? 1 : 0.98 }} disabled={busy || !preview} onClick={onRun}
        className="mt-3 w-full shrink-0 border-2 border-phosphor bg-phosphor/10 py-3.5 text-base font-extrabold tracking-[0.3em] text-phosphor glow-phosphor disabled:opacity-50"
      >
        {busy ? 'SIMULATING…' : '▸ RUN SEASON'}
      </motion.button>
    </Panel>
  )
}

function OddsRow({ label, v }: { label: string; v: number }) {
  const pct = Math.round(v * 100)
  return (
    <div className="flex items-center gap-2">
      <span className="w-24 text-[10px] tracking-widest text-ink/55">{label}</span>
      <div className="h-2 flex-1 overflow-hidden border border-edge bg-black/40">
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
    <Panel label="SPUN SQUAD" className="flex min-h-0 flex-1 flex-col p-3">
      <div className="mb-2 flex shrink-0 items-center justify-between">
        <div>
          <div className="text-sm font-extrabold tracking-wide text-ink-bright">{spin.club}</div>
          <div className="text-[11px] text-ink/50">{spin.season}{spin.league ? ` · ${spin.league}` : ''}</div>
        </div>
        <button disabled={busy || !canReroll} onClick={onReroll} className="border border-edge px-3 py-1.5 text-xs font-bold tracking-wide text-ink/70 hover:border-amber hover:text-amber disabled:opacity-30">↻ REROLL</button>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-2 gap-1.5 overflow-y-auto pr-1">
        {spin.squad.map((p, i) => {
          const eligible = p.eligibleSlots.length > 0
          const isSel = selected?.sofifaId === p.sofifaId
          return (
            <motion.div key={p.sofifaId} initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: Math.min(i * 0.02, 0.3) }}>
              <button
                disabled={!eligible || busy} onClick={() => onSelect(p)}
                className={`flex h-full w-full items-start gap-2 border p-1.5 text-left transition ${isSel ? 'border-amber bg-amber/10' : eligible ? 'border-edge hover:border-edge-bright' : 'border-edge/50 opacity-40'}`}
              >
                <RatingBadge rating={p.rating} line={lineOf(p.positions[0] ?? 'CM')} size="md" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-bold text-ink-bright">{p.name}</div>
                  <div className="truncate text-[9px] text-ink/45">{p.nation}</div>
                  <div className="mt-0.5 flex flex-wrap gap-0.5">{p.positions.slice(0, 4).map((pos) => <PositionChip key={pos} position={pos} />)}</div>
                  <div className="mt-0.5 text-[9px] text-phosphor">{eligible ? `FITS ${p.eligibleSlots.length}` : 'N·A'}</div>
                </div>
              </button>
            </motion.div>
          )
        })}
      </div>
      <AnimatePresence>
        {selected && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="shrink-0 overflow-hidden">
            <div className="mt-2 border-t border-edge pt-2">
              <div className="mb-2 flex items-center gap-2 text-xs"><Stamp text="PLACE" tone="phosphor" /><span className="font-bold text-ink-bright">{selected.name}</span></div>
              <div className="flex flex-wrap gap-2">
                {selected.eligibleSlots.map((pos) => {
                  const lc = lineClasses[lineOf(pos)]
                  return <button key={pos} disabled={busy} onClick={() => onDraft(selected, pos)} className={`border ${lc.border} ${lc.text} ${lc.glow} px-3 py-1.5 text-sm font-bold disabled:opacity-40`}>DEPLOY ▸ {pos}</button>
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
    <motion.div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
      <motion.div className="w-full max-w-md border-2 border-edge-bright bg-panel p-5" initial={{ scale: 0.9, y: 10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()}>
        {children}
      </motion.div>
    </motion.div>
  )
}

function difficultyRerolls(d: RunState['difficulty']): number {
  return d === 'EASY' ? 3 : d === 'NORMAL' ? 1 : 0
}
