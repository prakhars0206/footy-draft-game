import { useEffect, useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { api } from '../api'
import type {
  DeclassifiedPlayer, LeagueTeam, Preview, RunState, SeasonReplay, ShowRatings, Slot, SpinClub, SpinView, SquadPlayer, Strength,
} from '../api'
import { lineClasses, lineOf } from '../theme'
import { PitchView } from '../components/PitchView'
import { TeamPitch } from '../components/TeamPitch'
import { SpinReveal } from '../components/SpinReveal'
import { ScoutRating } from '../components/ScoutRating'
import { PositionChip } from '../components/PositionChip'
import { ForecastCone } from '../components/ForecastCone'
import { Panel, Prompt, Stamp } from '../components/primitives'

const ordinal = (n: number) => {
  const s = ['th', 'st', 'nd', 'rd'], v = n % 100
  return n + (s[(v - 20) % 10] ?? s[v] ?? s[0])
}
const pct = (v: number) => `${Math.round(v * 100)}%`

const TIER_COLOR: Record<string, string> = {
  ICONIC: 'text-amber border-amber',
  ELITE: 'text-phosphor border-phosphor',
  PEDIGREE: 'text-def border-def',
  STEADY: 'text-ink border-edge-bright',
  MINNOW: 'text-danger border-danger',
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
  const [chosenClub, setChosenClub] = useState<SpinClub | null>(null)
  const [selected, setSelected] = useState<SquadPlayer | null>(null)
  const [moveFrom, setMoveFrom] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [declassified, setDeclassified] = useState<{ club: string; season: string; players: DeclassifiedPlayer[] } | null>(null)
  const [revealing, setRevealing] = useState(false)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [selectedTeam, setSelectedTeam] = useState<LeagueTeam | null>(null)

  const complete = run.slotsRemaining === 0
  const openSlots = run.slots.filter((s) => !s.filled)

  useEffect(() => {
    if (complete && !preview && !declassified) api.preview(run.runId).then(setPreview).catch(() => {})
  }, [complete, preview, declassified, run.runId])
  // Default the inspected team to your own XI once the league loads.
  useEffect(() => {
    if (preview && !selectedTeam) setSelectedTeam(preview.league.find((t) => t.you) ?? preview.league[0] ?? null)
  }, [preview, selectedTeam])

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
        setSpin(s); setChosenClub(null); setSelected(null); setMoveFrom(null); setRevealing(true)
        onRun({ ...run, rerollsRemaining: s.rerollsRemaining })
      } finally { setScanning(false) }
    })

  const doDraft = (slotPosition: string, sofifaId: number) =>
    guard(async () => {
      const res = await api.draft(run.runId, slotPosition, sofifaId)
      onRun(res.state); setSpin(null); setChosenClub(null); setSelected(null); setRevealing(false)
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
        <Panel className="flex min-h-0 flex-col p-2">
          {complete && selectedTeam ? (
            <>
              <div className="mb-2 flex shrink-0 items-center justify-between gap-2 px-1">
                <div className="min-w-0 truncate font-display text-base font-semibold text-ink-bright">
                  {selectedTeam.team}{selectedTeam.you && <span className="text-amber"> · your XI</span>}
                </div>
                <span className={`shrink-0 border px-2 py-0.5 text-[10px] uppercase tracking-[0.14em] ${TIER_COLOR[selectedTeam.tier] ?? 'text-ink border-edge'}`}>{selectedTeam.tier} · {selectedTeam.strength}</span>
              </div>
              <div className="min-h-0 flex-1"><TeamPitch formation={selectedTeam.formation} players={selectedTeam.xi} fill /></div>
            </>
          ) : (
            <PitchView formation={run.formation} slots={run.slots} interactive={interactive} selectedIndex={moveFrom} onSlotClick={onPitchClick} />
          )}
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
            <LeaguePanel preview={preview} selected={selectedTeam} busy={busy} onSelect={setSelectedTeam} onRun={() => guard(async () => onSimulated(await api.simulate(run.runId)))} />
          ) : !spin ? (
            <Panel label="The Spin" className="flex flex-1 flex-col items-center justify-center gap-5 p-8">
              <Prompt>spin for a tier, then choose your club</Prompt>
              <motion.button
                whileHover={{ scale: busy ? 1 : 1.03 }} whileTap={{ scale: busy ? 1 : 0.97 }}
                disabled={busy} onClick={doSpin}
                className="relative flex h-40 w-40 items-center justify-center rounded-full border border-amber/50 bg-amber/5 font-display text-lg italic text-amber transition hover:bg-amber/10 disabled:opacity-30"
              >
                {scanning ? <span className="text-base not-italic tracking-[0.2em]">scanning…</span> : <span>Spin</span>}
                <span className="absolute inset-2.5 rounded-full border border-amber/20" />
              </motion.button>
            </Panel>
          ) : revealing || !chosenClub ? (
            <SpinReveal spin={spin} onPick={(c) => { setChosenClub(c); setSelected(null); setRevealing(false) }} />
          ) : (
            <SquadPanel
              club={chosenClub} tier={spin.tier} showRatings={run.showRatings}
              selected={selected} busy={busy} canReroll={run.rerollsRemaining > 0}
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
        {data.players.map((p, i) => {
          const lc = lineClasses[p.line]
          const dim = !p.eligible && !p.draftedByYou // couldn't be picked into an open slot — greyed out
          return (
            <div key={p.sofifaId} className={`flex items-center gap-2 border px-2 py-1 ${p.draftedByYou ? 'border-amber/60 bg-amber/10' : dim ? 'border-edge/50 opacity-40' : 'border-edge'}`}>
              <span className={`relative flex h-7 w-8 items-center justify-center overflow-hidden border ${lc.border} ${lc.text} text-xs font-bold tabular-nums`}>
                {p.overall}
                {/* the redaction wipes away — declassifying the true rating */}
                <motion.span className="redacted absolute inset-0" initial={{ scaleX: 1 }} animate={{ scaleX: 0 }} style={{ transformOrigin: 'right' }} transition={{ delay: 0.15 + Math.min(i * 0.03, 0.6), duration: 0.45, ease: 'easeInOut' }} />
              </span>
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

function LeaguePanel({ preview, selected, busy, onSelect, onRun }: { preview: Preview | null; selected: LeagueTeam | null; busy: boolean; onSelect: (t: LeagueTeam) => void; onRun: () => void }) {
  const mc = selected?.monteCarlo
  return (
    <Panel label="The League · pre-season" accent="amber" className="flex min-h-0 flex-1 flex-col p-4">
      <div className="shrink-0">
        <div className="flex items-end justify-between gap-2">
          <div className="min-w-0">
            <div className="eyebrow text-ink/50">{selected?.you ? 'Your projected finish' : 'Projected finish'}</div>
            <div className="font-display text-5xl font-black leading-none text-amber">
              {selected ? ordinal(selected.projectedPos) : '—'}
              <span className="ml-1 font-sans text-sm font-normal text-ink/50">· {selected?.projectedPoints ?? '—'} pts</span>
            </div>
            {selected && <div className="mt-0.5 truncate text-[11px] text-ink/45">{selected.you ? 'your XI' : selected.team}</div>}
          </div>
          <span className={`shrink-0 border px-2 py-0.5 text-[10px] uppercase tracking-[0.14em] ${TIER_COLOR[selected?.tier ?? ''] ?? 'text-ink border-edge'}`}>{selected?.tier} · {selected?.strength}</span>
        </div>
        {mc && (
          <div className="mt-3">
            <div className="eyebrow mb-1 text-ink/40">Points forecast</div>
            <ForecastCone mc={mc} height={52} />
            <div className="mt-1 text-[11px] text-ink/45">
              <span className="font-display italic">{mc.sims.toLocaleString()} simulated seasons</span> · likely <span className="tabular-nums text-ink-bright">{mc.p25}–{mc.p75}</span> pts
              {mc.unbeaten > 0 && <> · unbeaten <span className="tabular-nums text-phosphor">{pct(mc.unbeaten)}</span></>}
            </div>
            <div className="mt-3 space-y-1.5">
              <OddsRow label="Title" v={mc.title} />
              <OddsRow label="Top 4" v={mc.top4} />
              <OddsRow label="Relegation" v={mc.relegation} />
            </div>
          </div>
        )}
        <div className="mt-4 mb-1 flex items-center gap-2 border-b border-edge pb-1">
          <span className="eyebrow w-5 text-right text-ink/45">#</span><span className="eyebrow flex-1 text-ink/45">League · tap a team</span><span className="eyebrow w-11 text-right text-ink/45">Proj</span><span className="eyebrow w-7 text-right text-ink/45">OVR</span>
        </div>
      </div>
      <div className="min-h-0 flex-1 space-y-1 overflow-y-auto pr-1">
        {(preview?.league ?? []).map((t, i) => {
          const isSel = selected?.team === t.team
          return (
          <button key={i} onClick={() => onSelect(t)}
            className={`flex w-full items-center gap-2 border px-2 py-1 text-left text-sm transition ${isSel ? 'border-amber bg-amber/10' : t.you ? 'border-amber/40 hover:border-amber/70' : 'border-transparent hover:border-edge-bright'}`}>
            <span className="w-5 text-right tabular-nums text-ink/40">{t.projectedPos}</span>
            <span className={`flex-1 truncate ${t.you ? 'font-semibold text-amber' : 'text-ink-bright'}`}>{t.team}{t.you && ' ★'}</span>
            <span className={`border px-1 text-[9px] uppercase tracking-[0.12em] ${TIER_COLOR[t.tier] ?? 'text-ink border-edge'}`}>{t.tier}</span>
            <span className="w-11 text-right text-[11px] tabular-nums text-ink/55">{t.projectedPoints} pt</span>
            <span className="w-7 text-right font-bold tabular-nums text-amber">{t.strength}</span>
          </button>
          )
        })}
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
  club, tier, showRatings, selected, busy, canReroll, onSelect, onReroll, onDraft,
}: {
  club: SpinClub; tier: string; showRatings: ShowRatings; selected: SquadPlayer | null; busy: boolean; canReroll: boolean
  onSelect: (p: SquadPlayer) => void; onReroll: () => void; onDraft: (p: SquadPlayer, position: string) => void
}) {
  const scouted = showRatings !== 'ON' // SCOUT/OFF → frame the squad as a classified scouting dossier
  return (
    <Panel label={scouted ? `Scout Dossier · ${tier.toLowerCase()}` : `Draft from · ${tier.toLowerCase()}`} accent={scouted ? 'amber' : 'edge'} className="flex min-h-0 flex-1 flex-col p-4">
      <div className="mb-3 flex shrink-0 items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <div className="truncate font-display text-lg font-semibold text-ink-bright">{club.club}</div>
            {scouted && <Stamp text={showRatings === 'OFF' ? 'Classified' : 'Scouted'} tone="amber" />}
          </div>
          <div className="text-[11px] text-ink/50">
            {club.season}{club.league ? ` · ${club.league}` : ''}
            {scouted && <span className="text-ink/35"> · {showRatings === 'OFF' ? 'no intel — ratings blacked out' : 'intel ranges · read with caution'}</span>}
          </div>
        </div>
        <button disabled={busy || !canReroll} onClick={onReroll} className="shrink-0 border border-edge px-3 py-1.5 text-xs font-semibold tracking-wide text-ink/70 hover:border-amber hover:text-amber disabled:opacity-30">↻ Reroll</button>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-2 gap-1.5 overflow-y-auto pr-1">
        {club.squad.map((p, i) => {
          const eligible = p.eligibleSlots.length > 0
          const isSel = selected?.sofifaId === p.sofifaId
          return (
            <motion.div key={p.sofifaId} initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: Math.min(i * 0.02, 0.3) }}>
              <button
                disabled={!eligible || busy} onClick={() => onSelect(p)}
                className={`flex h-full w-full items-start gap-2 border p-1.5 text-left transition ${isSel ? 'border-amber/60 bg-amber/10' : eligible ? 'border-edge hover:border-edge-bright' : 'border-edge/50 opacity-40'}`}
              >
                <ScoutRating rating={p.rating} line={lineOf(p.positions[0] ?? 'CM')} />
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

function difficultyRerolls(d: RunState['difficulty']): number {
  return d === 'EASY' ? 3 : d === 'NORMAL' ? 1 : 0
}
