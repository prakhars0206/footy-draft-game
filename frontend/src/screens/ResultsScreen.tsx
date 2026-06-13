import { useState } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import type { SeasonView, TeamRow } from '../api'
import { Panel, Stamp } from '../components/primitives'
import { TeamPitch } from '../components/TeamPitch'

export function ResultsScreen({ season, onNewRun }: { season: SeasonView; onNewRun: () => void }) {
  const [tableOpen, setTableOpen] = useState(false)
  const [tableView, setTableView] = useState<'actual' | 'projected'>('actual')
  const [team, setTeam] = useState<TeamRow | null>(null)

  const proj = season.projection.expectedPoints
  const projPos = season.table.find((t) => t.you)?.projectedPos ?? season.finishPos
  const move = projPos - season.finishPos // + = finished HIGHER than the bookies projected
  // Verdict is the achievement (where you finished vs where you were projected) — what actually matters in a season.
  const verdict =
    move >= 3 ? { text: 'OVERPERFORMED', tone: 'phosphor' as const }
      : move <= -3 ? { text: 'UNDERPERFORMED', tone: 'danger' as const }
        : { text: 'AS EXPECTED', tone: 'amber' as const }
  const unbeaten = season.lost === 0
  const perfect = season.won === 38

  const rows = tableView === 'projected'
    ? [...season.table].sort((a, b) => a.projectedPos - b.projectedPos)
    : season.table
  const shown = tableOpen ? rows : rows.slice(0, 6)

  return (
    <motion.div className="space-y-4" initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: 0.07 } } }}>
      <Reveal>
        <Panel label="DEBRIEF" accent={perfect ? 'phosphor' : 'edge'} className="p-6 text-center">
          {perfect && <Stamp text="38-0 · PERFECT SEASON" tone="phosphor" className="mb-3" />}
          <div className="text-xs tracking-mega text-ink/50">FINAL STANDING</div>
          <div className="my-2 text-6xl font-extrabold text-ink-bright glow-phosphor">{ordinal(season.finishPos)}</div>
          <div className="flex flex-wrap items-center justify-center gap-3">
            <span className="text-sm text-ink/60">
              projected {ordinal(projPos)} ({proj} pts) → finished {ordinal(season.finishPos)} ({season.points} pts)
            </span>
            <Stamp text={verdict.text} tone={verdict.tone} />
          </div>
          {unbeaten && !perfect && <div className="mt-2 text-sm text-phosphor">◆ UNBEATEN — {season.won}W {season.drawn}D</div>}
        </Panel>
      </Reveal>

      <Reveal>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Tile label="RECORD" value={`${season.won}-${season.drawn}-${season.lost}`} sub="W-D-L" />
          <Tile label="POINTS" value={season.points} sub={`proj ${proj}`} />
          <Tile label="GOALS" value={`${season.goalsFor}/${season.goalsAgainst}`} sub={`GD ${gd(season)}`} />
          <Tile label="BIG WIN" value={`+${season.biggestWin}`} sub={`streak ${season.longestWinStreak}`} />
        </div>
      </Reveal>

      {season.playerOfSeason && (
        <Reveal>
          <Panel label="PLAYER OF THE SEASON · LEAGUE-WIDE" accent="amber" className="flex items-center gap-4 p-4">
            <span className="text-3xl">★</span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-xl font-extrabold text-amber glow-amber">{season.playerOfSeason.player}</div>
              <div className="truncate text-[11px] text-ink/50">{season.playerOfSeason.team}</div>
            </div>
            <div className="flex gap-4 text-center">
              <Mini label="G" v={season.playerOfSeason.goals} />
              <Mini label="A" v={season.playerOfSeason.assists} />
              <Mini label="CS" v={season.playerOfSeason.cleanSheets} />
            </div>
          </Panel>
        </Reveal>
      )}

      <Reveal>
        <div className="grid gap-3 sm:grid-cols-3">
          <Odds label="WIN LEAGUE" v={season.projection.winLeague} />
          <Odds label="TOP 4" v={season.projection.top4} />
          <Odds label="RELEGATION" v={season.projection.relegation} />
        </div>
      </Reveal>

      <Reveal>
        <div className="grid gap-4 sm:grid-cols-3">
          <Leaders title="GOLDEN BOOT" rows={season.goldenBoot} unit="G" />
          <Leaders title="PLAYMAKERS" rows={season.topAssists} unit="A" />
          <Leaders title="GOLDEN GLOVE" rows={season.goldenGlove} unit="CS" />
        </div>
      </Reveal>

      <Reveal>
        <Panel label="FINAL TABLE · tap a team to scout" className="p-4">
          <div className="mb-2 flex items-center justify-between text-xs">
            <div className="inline-flex border border-edge">
              {(['actual', 'projected'] as const).map((v) => (
                <button key={v} onClick={() => setTableView(v)} className={`px-3 py-1 font-bold tracking-wide ${tableView === v ? 'bg-amber/15 text-amber' : 'text-ink/50'}`}>
                  {v === 'actual' ? 'ACTUAL' : 'PROJECTED'}
                </button>
              ))}
            </div>
            <button onClick={() => setTableOpen((o) => !o)} className="text-amber">{tableOpen ? '▾ collapse' : '▸ expand'}</button>
          </div>
          <div className="space-y-1">
            {shown.map((t) => (
              <button
                key={t.team + t.pos}
                onClick={() => setTeam(t)}
                className={`flex w-full items-center gap-2 px-2 py-1 text-left text-sm ${t.you ? 'border border-amber bg-amber/10 text-amber glow-amber' : 'border border-transparent text-ink/75 hover:border-edge-bright'}`}
              >
                <span className="w-6 text-right tabular-nums text-ink/50">{tableView === 'projected' ? t.projectedPos : t.pos}</span>
                <span className="flex-1 truncate font-bold">{t.team}</span>
                {tableView === 'actual'
                  ? <><Movement move={t.projectedPos - t.pos} /><span className="hidden w-16 text-right tabular-nums text-ink/50 sm:inline">{t.won}-{t.drawn}-{t.lost}</span><span className="w-9 text-right tabular-nums">{gdStr(t.gd)}</span><span className="w-8 text-right font-extrabold tabular-nums">{t.points}</span></>
                  : <><span className="border border-edge px-1 text-[9px] text-ink/50">{t.formation}</span><span className="hidden w-14 text-right text-[10px] tabular-nums text-ink/40 sm:inline">OVR {t.strength}</span><span className="w-12 text-right font-extrabold tabular-nums text-amber">{t.projectedPoints}<span className="text-[9px] font-normal text-ink/40">pts</span></span></>}
              </button>
            ))}
            {!tableOpen && season.finishPos > 6 && tableView === 'actual' && (
              <div className="pt-1 text-center text-[10px] text-ink/40">… expand to find your XI ({ordinal(season.finishPos)}) …</div>
            )}
          </div>
        </Panel>
      </Reveal>

      <Reveal>
        <button onClick={onNewRun} className="w-full border-2 border-phosphor bg-phosphor/10 py-4 text-lg font-extrabold tracking-[0.3em] text-phosphor glow-phosphor">▸ NEW RUN</button>
      </Reveal>

      <AnimatePresence>
        {team && (
          <Backdrop onClose={() => setTeam(null)}>
            <div className="mb-3 flex items-start justify-between">
              <div className="min-w-0">
                <div className="truncate text-sm font-extrabold text-ink-bright">{team.team}</div>
                <div className="text-[10px] text-ink/50">{team.formation} · finished {ordinal(team.pos)} · projected {ordinal(team.projectedPos)} ({team.projectedPoints} pts)</div>
              </div>
              <div className="text-right text-[11px]">
                <div className="font-extrabold tabular-nums text-amber">{team.points} pts</div>
                <div className="text-ink/50">{team.won}-{team.drawn}-{team.lost}</div>
              </div>
            </div>
            <TeamPitch formation={team.formation} players={team.players} showStats />
            <button onClick={() => setTeam(null)} className="mt-4 w-full border border-edge py-2 text-sm font-bold tracking-widest text-ink/70 hover:border-amber hover:text-amber">CLOSE</button>
          </Backdrop>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function Movement({ move }: { move: number }) {
  if (move === 0) return <span className="w-10 text-right text-[10px] text-ink/30">=</span>
  const up = move > 0
  return <span className={`w-10 text-right text-[10px] tabular-nums ${up ? 'text-phosphor' : 'text-danger'}`}>{up ? '▲' : '▼'}{Math.abs(move)}</span>
}

function Reveal({ children }: { children: ReactNode }) {
  return <motion.div variants={{ hidden: { opacity: 0, y: 14 }, show: { opacity: 1, y: 0 } }}>{children}</motion.div>
}

function Mini({ label, v }: { label: string; v: number }) {
  return <div><div className="text-lg font-extrabold tabular-nums text-ink-bright">{v}</div><div className="text-[9px] tracking-widest text-ink/50">{label}</div></div>
}

function Tile({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <Panel className="p-3 text-center">
      <div className="text-[10px] tracking-mega text-ink/50">{label}</div>
      <div className="my-1 text-2xl font-extrabold tabular-nums text-ink-bright">{value}</div>
      {sub && <div className="text-[10px] text-ink/40">{sub}</div>}
    </Panel>
  )
}

function Odds({ label, v }: { label: string; v: number }) {
  const pct = Math.round(v * 100)
  return (
    <Panel className="p-3">
      <div className="flex items-baseline justify-between"><span className="text-[10px] tracking-mega text-ink/50">{label}</span><span className="text-lg font-extrabold tabular-nums text-amber">{pct}%</span></div>
      <div className="mt-2 h-1.5 overflow-hidden border border-edge bg-black/40"><motion.div className="h-full bg-amber" initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ duration: 0.7 }} /></div>
    </Panel>
  )
}

function Leaders({ title, rows, unit }: { title: string; rows: SeasonView['goldenBoot']; unit: string }) {
  return (
    <Panel label={title} className="p-4">
      <div className="space-y-1.5">
        {rows.slice(0, 5).map((r, i) => (
          <div key={i} className="flex items-start gap-2 text-sm">
            <span className="w-4 shrink-0 text-right text-ink/40">{i + 1}</span>
            <div className="min-w-0 flex-1">
              <div className="truncate font-bold text-ink-bright">{r.player}</div>
              <div className="truncate text-[10px] text-ink/40">{r.team}</div>
            </div>
            <span className="w-10 shrink-0 text-right font-extrabold tabular-nums text-amber">{r.value}{unit}</span>
          </div>
        ))}
      </div>
    </Panel>
  )
}

function Backdrop({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return (
    <motion.div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
      <motion.div className="w-full max-w-lg border-2 border-edge-bright bg-panel p-5" initial={{ scale: 0.9, y: 10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()}>{children}</motion.div>
    </motion.div>
  )
}

const gd = (s: SeasonView) => gdStr(s.goalsFor - s.goalsAgainst)
const gdStr = (n: number) => (n > 0 ? `+${n}` : `${n}`)
function ordinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] ?? s[v] ?? s[0])
}
