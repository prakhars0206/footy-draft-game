import { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api } from '../api'
import type { RunState, SeasonView, Slot, SpinView, SquadPlayer } from '../api'
import { lineClasses, lineOf } from '../theme'
import { PitchView } from '../components/PitchView'
import { StrengthBars } from '../components/StrengthBars'
import { RatingBadge } from '../components/RatingBadge'
import { PositionChip } from '../components/PositionChip'
import { Panel, Prompt, Stamp } from '../components/primitives'

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

  const complete = run.slotsRemaining === 0
  const openSlots = run.slots.filter((s) => !s.filled)

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
    try {
      await fn()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'request failed')
    } finally {
      setBusy(false)
    }
  }

  const doSpin = () =>
    guard(async () => {
      setScanning(true)
      try {
        const s = await api.spin(run.runId)
        setSpin(s)
        setSelected(null)
        setMoveFrom(null)
        onRun({ ...run, rerollsRemaining: s.rerollsRemaining })
      } finally {
        setScanning(false)
      }
    })

  const doDraft = (slotPosition: string, sofifaId: number) =>
    guard(async () => {
      const next = await api.draft(run.runId, slotPosition, sofifaId)
      onRun(next)
      setSpin(null)
      setSelected(null)
    })

  const onPitchClick = (slot: Slot) => {
    if (selected) {
      if (interactive?.has(slot.index)) doDraft(slot.position, selected.sofifaId)
      return
    }
    if (moveFrom != null) {
      if (slot.index === moveFrom) return setMoveFrom(null)
      if (interactive?.has(slot.index)) {
        const from = moveFrom
        guard(async () => {
          onRun(await api.move(run.runId, from, slot.index))
          setMoveFrom(null)
        })
      }
      return
    }
    if (slot.filled) setMoveFrom(slot.index)
  }

  return (
    <div className="flex flex-col gap-3 lg:h-[calc(100vh-9rem)]">
      <div className="flex shrink-0 items-center justify-between text-xs">
        <span className="tracking-mega text-ink/60">
          TARGETS REMAINING <span className="text-amber glow-amber">{run.slotsRemaining}</span> / 11
        </span>
        <span className="tracking-mega text-ink/60">
          REROLLS{' '}
          <span className="text-phosphor">
            {run.rerollsRemaining}/{difficultyRerolls(run.difficulty)}
          </span>
        </span>
      </div>

      <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[1fr_minmax(340px,400px)]">
        {/* ---- pitch + strength ---- */}
        <div className="flex min-h-0 flex-col gap-3">
          <Panel className="min-h-0 flex-1 p-2">
            <PitchView
              formation={run.formation}
              slots={run.slots}
              interactive={interactive}
              selectedIndex={moveFrom}
              onSlotClick={onPitchClick}
            />
          </Panel>
          <Panel label="READOUT" className="shrink-0 p-3">
            <StrengthBars strength={run.strength} />
          </Panel>
        </div>

        {/* ---- spin / squad / projection ---- */}
        <div className="flex min-h-0 flex-col">
          {complete ? (
            <ProjectionPanel run={run} busy={busy} onRun={() => guard(async () => onSimulated(await api.simulate(run.runId)))} />
          ) : !spin ? (
            <Panel label="ACQUISITION" className="flex flex-1 flex-col items-center justify-center gap-4 p-8">
              <Prompt>spin to acquire a target squad</Prompt>
              <motion.button
                whileHover={{ scale: busy ? 1 : 1.03 }}
                whileTap={{ scale: busy ? 1 : 0.97 }}
                disabled={busy}
                onClick={doSpin}
                className="relative h-40 w-40 border-2 border-phosphor bg-phosphor/5 font-extrabold tracking-[0.2em] text-phosphor glow-phosphor disabled:opacity-30"
              >
                {scanning ? <span className="caret text-sm">SCANNING</span> : <span className="text-sm">◎ SPIN</span>}
                <span className="absolute inset-2 border border-phosphor/30" />
              </motion.button>
            </Panel>
          ) : (
            <SquadPanel
              spin={spin}
              selected={selected}
              busy={busy}
              canReroll={run.rerollsRemaining > 0}
              onSelect={(p) => {
                setSelected(p)
                setMoveFrom(null)
              }}
              onReroll={doSpin}
              onDraft={(p, pos) => doDraft(pos, p.sofifaId)}
            />
          )}
          {error && <div className="mt-2 shrink-0 border border-danger/50 bg-danger/10 p-2 text-sm text-danger">! {error}</div>}
          <div className="mt-2 shrink-0 text-[10px] text-ink/40">
            {moveFrom != null
              ? '> select a highlighted slot to reposition · click the player again to cancel'
              : selected
                ? '> select a highlighted pitch slot, or use DEPLOY below'
                : '> click a deployed player on the pitch to reposition them'}
          </div>
        </div>
      </div>
    </div>
  )
}

function ProjectionPanel({ run, busy, onRun }: { run: RunState; busy: boolean; onRun: () => void }) {
  const p = run.projection
  return (
    <Panel label="PRE-SEASON DOSSIER" accent="amber" className="flex flex-1 flex-col p-5">
      <Prompt>the bookies have run the numbers</Prompt>
      <div className="my-5 text-center">
        <div className="text-[10px] tracking-mega text-ink/50">PROJECTED FINISH</div>
        <div className="my-1 text-5xl font-extrabold text-amber glow-amber tabular-nums">{p?.expectedPoints ?? '—'}</div>
        <div className="text-[10px] tracking-mega text-ink/50">POINTS</div>
      </div>
      <div className="space-y-2">
        <OddsRow label="WIN LEAGUE" v={p?.winLeague ?? 0} />
        <OddsRow label="TOP 4" v={p?.top4 ?? 0} />
        <OddsRow label="RELEGATION" v={p?.relegation ?? 0} />
      </div>
      <div className="mt-4 text-center text-[10px] text-ink/40">
        the gap between this and reality is the whole game
      </div>
      <div className="flex-1" />
      <motion.button
        whileHover={{ scale: busy ? 1 : 1.02 }}
        whileTap={{ scale: busy ? 1 : 0.98 }}
        disabled={busy}
        onClick={onRun}
        className="mt-4 w-full border-2 border-phosphor bg-phosphor/10 py-4 text-lg font-extrabold tracking-[0.3em] text-phosphor glow-phosphor disabled:opacity-50"
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
  spin,
  selected,
  busy,
  canReroll,
  onSelect,
  onReroll,
  onDraft,
}: {
  spin: SpinView
  selected: SquadPlayer | null
  busy: boolean
  canReroll: boolean
  onSelect: (p: SquadPlayer) => void
  onReroll: () => void
  onDraft: (p: SquadPlayer, position: string) => void
}) {
  return (
    <Panel label="SPUN SQUAD" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="mb-3 flex shrink-0 items-center justify-between">
        <div>
          <div className="text-sm font-extrabold tracking-wide text-ink-bright">{spin.club}</div>
          <div className="text-[11px] text-ink/50">
            {spin.season}
            {spin.league ? ` · ${spin.league}` : ''}
          </div>
        </div>
        <button
          disabled={busy || !canReroll}
          onClick={onReroll}
          className="border border-edge px-3 py-1.5 text-xs font-bold tracking-wide text-ink/70 hover:border-amber hover:text-amber disabled:opacity-30"
        >
          ↻ REROLL
        </button>
      </div>

      <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto pr-1">
        {spin.squad.map((p, i) => {
          const eligible = p.eligibleSlots.length > 0
          const isSel = selected?.sofifaId === p.sofifaId
          return (
            <motion.div key={p.sofifaId} initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: Math.min(i * 0.025, 0.4) }}>
              <button
                disabled={!eligible || busy}
                onClick={() => onSelect(p)}
                className={`flex w-full items-center gap-3 border p-2 text-left transition ${
                  isSel ? 'border-amber bg-amber/10' : eligible ? 'border-edge hover:border-edge-bright' : 'border-edge/50 opacity-40'
                }`}
              >
                <RatingBadge rating={p.rating} line={lineOf(p.positions[0] ?? 'CM')} size="md" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-bold text-ink-bright">{p.name}</div>
                  <div className="truncate text-[10px] text-ink/50">{p.nation}</div>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {p.positions.map((pos) => (
                      <PositionChip key={pos} position={pos} />
                    ))}
                  </div>
                </div>
                {eligible ? <span className="text-[10px] text-phosphor">FITS {p.eligibleSlots.length}</span> : <span className="text-[10px] text-ink/30">N·A</span>}
              </button>
            </motion.div>
          )
        })}
      </div>

      <AnimatePresence>
        {selected && (
          <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }} className="shrink-0 overflow-hidden">
            <div className="mt-3 border-t border-edge pt-3">
              <div className="mb-2 flex items-center gap-2 text-xs">
                <Stamp text="PLACE" tone="phosphor" />
                <span className="font-bold text-ink-bright">{selected.name}</span>
              </div>
              <div className="flex flex-wrap gap-2">
                {selected.eligibleSlots.map((pos) => {
                  const lc = lineClasses[lineOf(pos)]
                  return (
                    <button
                      key={pos}
                      disabled={busy}
                      onClick={() => onDraft(selected, pos)}
                      className={`border ${lc.border} ${lc.text} ${lc.glow} px-3 py-1.5 text-sm font-bold disabled:opacity-40`}
                    >
                      DEPLOY ▸ {pos}
                    </button>
                  )
                })}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Panel>
  )
}

function difficultyRerolls(d: RunState['difficulty']): number {
  return d === 'EASY' ? 3 : d === 'NORMAL' ? 1 : 0
}
