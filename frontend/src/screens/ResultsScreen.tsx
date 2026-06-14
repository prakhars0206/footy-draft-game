import { useState } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import type { SeasonView, TeamRow } from '../api'
import { Panel, Stamp } from '../components/primitives'
import { TeamPitch } from '../components/TeamPitch'
import { Distribution } from '../components/Distribution'

export function ResultsScreen({ season, onNewRun }: { season: SeasonView; onNewRun: () => void }) {
  const [tableView, setTableView] = useState<'actual' | 'projected'>('actual')
  const [team, setTeam] = useState<TeamRow | null>(null)

  const proj = season.projection.expectedPoints
  const projPos = season.table.find((t) => t.you)?.projectedPos ?? season.finishPos
  const move = projPos - season.finishPos // + = finished HIGHER than the bookies projected
  // Verdict is driven by the Monte-Carlo percentile — did this season beat most of the campaigns this squad could
  // have had? (Falls back to projected-vs-actual position when there's no cloud, e.g. the stateless demo.)
  const pctile = season.monteCarlo?.percentile ?? null
  const verdict =
    pctile != null
      ? (pctile >= 70 ? { text: 'OVERPERFORMED', tone: 'phosphor' as const }
        : pctile <= 30 ? { text: 'UNDERPERFORMED', tone: 'danger' as const }
          : { text: 'AS EXPECTED', tone: 'amber' as const })
      : (move >= 3 ? { text: 'OVERPERFORMED', tone: 'phosphor' as const }
        : move <= -3 ? { text: 'UNDERPERFORMED', tone: 'danger' as const }
          : { text: 'AS EXPECTED', tone: 'amber' as const })
  const unbeaten = season.lost === 0
  const perfect = season.won === 38

  const rows = tableView === 'projected'
    ? [...season.table].sort((a, b) => a.projectedPos - b.projectedPos)
    : season.table

  return (
    <motion.div className="space-y-5" initial="hidden" animate="show" variants={{ show: { transition: { staggerChildren: 0.07 } } }}>
      <Reveal>
        <Panel label="The Debrief" accent={perfect ? 'phosphor' : 'edge'} className="p-7 text-center">
          {perfect && <div className="mb-3"><Stamp text="38-0 · Perfect Season" tone="phosphor" /></div>}
          <div className="eyebrow text-ink/45">Final Standing</div>
          <div className="my-1 font-display text-7xl font-black leading-none text-ink-bright">{ordinal(season.finishPos)}</div>
          <div className="mx-auto mt-3 flex max-w-xl flex-wrap items-center justify-center gap-x-3 gap-y-2">
            <span className="font-display text-[15px] italic text-ink/60">
              projected {ordinal(projPos)} ({proj} pts) — finished on {season.points}
            </span>
            <Stamp text={verdict.text} tone={verdict.tone} />
          </div>
          {unbeaten && !perfect && <div className="mt-3 font-display text-sm italic text-phosphor">unbeaten — {season.won}W {season.drawn}D</div>}
        </Panel>
      </Reveal>

      <Reveal>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Tile label="Record" value={`${season.won}-${season.drawn}-${season.lost}`} sub="W-D-L" />
          <Tile label="Points" value={season.points} sub={`proj ${proj}`} />
          <Tile label="Goals" value={`${season.goalsFor}/${season.goalsAgainst}`} sub={`GD ${gd(season)}`} />
          <Tile label="Big Win" value={`+${season.biggestWin}`} sub={`streak ${season.longestWinStreak}`} />
        </div>
      </Reveal>

      {season.playerOfSeason && (
        <Reveal>
          <Panel label="Player of the Season · league-wide" accent="amber" className="flex items-center gap-4 p-5">
            <span className="text-2xl text-amber">★</span>
            <div className="min-w-0 flex-1">
              <div className="truncate font-display text-2xl font-semibold text-ink-bright">{season.playerOfSeason.player}</div>
              <div className="truncate text-[12px] text-ink/50">{season.playerOfSeason.team}</div>
            </div>
            <div className="flex gap-5 text-center">
              <Mini label="G" v={season.playerOfSeason.goals} />
              <Mini label="A" v={season.playerOfSeason.assists} />
              <Mini label="CS" v={season.playerOfSeason.cleanSheets} />
            </div>
          </Panel>
        </Reveal>
      )}

      {season.monteCarlo && season.monteCarlo.percentile != null ? (
        <Reveal>
          <Panel label={`Against the Odds · ${season.monteCarlo.sims.toLocaleString()} simulated seasons`} accent="amber" className="p-5">
            <div className="grid gap-6 sm:grid-cols-[1.5fr_1fr]">
              <div>
                <div className="flex items-baseline gap-3">
                  <div className="font-display text-5xl font-black leading-none text-amber">{ordinal(season.monteCarlo.percentile)}</div>
                  <div className="font-display text-[15px] italic text-ink/70">{cloudPhrase(season.monteCarlo.percentile)}</div>
                </div>
                <div className="mt-1 text-[12px] text-ink/50">
                  your <span className="tabular-nums text-ink-bright">{season.points}</span> pts beat <span className="tabular-nums text-ink-bright">{season.monteCarlo.percentile}%</span> of the seasons this squad could have had
                </div>
                <div className="mt-4"><Distribution mc={season.monteCarlo} actual={season.points} height="h-24" /></div>
                <div className="mt-2 flex gap-4 text-[10px] text-ink/45">
                  <span className="flex items-center gap-1"><i className="inline-block h-2 w-2 bg-amber" />your season</span>
                  <span className="flex items-center gap-1"><i className="inline-block h-2 w-2 bg-ink/45" />median ({season.monteCarlo.median})</span>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-x-5 gap-y-2 self-center sm:grid-cols-1">
                <Mc label="Title" v={season.monteCarlo.title} />
                <Mc label="Top 4" v={season.monteCarlo.top4} />
                <Mc label="Relegation" v={season.monteCarlo.relegation} />
                <Mc label="Unbeaten" v={season.monteCarlo.unbeaten} tone="phosphor" />
              </div>
            </div>
          </Panel>
        </Reveal>
      ) : (
        <Reveal>
          <div className="grid gap-3 sm:grid-cols-3">
            <Odds label="Win League" v={season.projection.winLeague} />
            <Odds label="Top 4" v={season.projection.top4} />
            <Odds label="Relegation" v={season.projection.relegation} />
          </div>
        </Reveal>
      )}

      <Reveal>
        <div className="grid gap-4 sm:grid-cols-3">
          <Leaders title="Golden Boot" rows={season.goldenBoot} unit="G" />
          <Leaders title="Playmakers" rows={season.topAssists} unit="A" />
          <Leaders title="Golden Glove" rows={season.goldenGlove} unit="CS" />
        </div>
      </Reveal>

      <Reveal>
        <Panel label="Final Table · tap a team to scout" className="p-5">
          <div className="mb-3 flex items-center justify-between gap-2">
            <div className="inline-flex divide-x divide-edge border border-edge text-xs">
              {(['actual', 'projected'] as const).map((v) => (
                <button key={v} onClick={() => setTableView(v)} className={`px-3 py-1 font-semibold tracking-wide ${tableView === v ? 'bg-amber/12 text-amber' : 'text-ink/50 hover:text-ink-bright'}`}>
                  {v === 'actual' ? 'ACTUAL' : 'PROJECTED'}
                </button>
              ))}
            </div>
            {tableView === 'actual' && (
              <div className="flex items-center gap-3 text-[10px] text-ink/40">
                <span className="flex items-center gap-1"><i className="inline-block h-2 w-2 bg-amber" />champion</span>
                <span className="flex items-center gap-1"><i className="inline-block h-2 w-2 bg-phosphor" />top 4</span>
                <span className="flex items-center gap-1"><i className="inline-block h-2 w-2 bg-danger" />drop</span>
              </div>
            )}
          </div>
          {/* column header */}
          <div className="mb-1 flex items-center gap-2 px-2 text-[10px] text-ink/40">
            <span className="w-6 text-right">#</span>
            <span className="flex-1">Club</span>
            {tableView === 'actual' ? (
              <>
                <span className="w-10 text-right">+/−</span>
                <span className="hidden w-6 text-right md:inline">P</span>
                <span className="hidden w-6 text-right md:inline">W</span>
                <span className="hidden w-6 text-right md:inline">D</span>
                <span className="hidden w-6 text-right md:inline">L</span>
                <span className="hidden w-8 text-right sm:inline">GF</span>
                <span className="hidden w-8 text-right sm:inline">GA</span>
                <span className="w-9 text-right">GD</span>
                <span className="w-8 text-right">Pts</span>
              </>
            ) : (
              <>
                <span className="w-12 text-right">Shape</span>
                <span className="hidden w-14 text-right sm:inline">OVR</span>
                <span className="w-12 text-right">Proj</span>
              </>
            )}
          </div>
          <div>
            {rows.map((t) => {
              const zone = t.pos === 1 ? 'text-amber' : t.pos <= 4 ? 'text-phosphor' : t.pos >= 18 ? 'text-danger' : 'text-ink/45'
              const divider = tableView === 'actual' && (t.pos === 4 || t.pos === 17) ? 'mb-1 border-b border-dashed border-edge-bright/50 pb-1' : 'py-0.5'
              return (
              <button
                key={t.team + t.pos}
                onClick={() => setTeam(t)}
                className={`flex w-full items-center gap-2 px-2 py-1.5 text-left text-sm ${divider} ${t.you ? 'border border-amber/60 bg-amber/10 text-amber' : 'border border-transparent text-ink/75 hover:border-edge-bright'}`}
              >
                <span className={`w-6 text-right font-semibold tabular-nums ${t.you ? '' : zone}`}>{tableView === 'projected' ? t.projectedPos : t.pos}</span>
                <span className="flex-1 truncate font-semibold">{t.team}</span>
                {tableView === 'actual'
                  ? <>
                      <Movement move={t.projectedPos - t.pos} />
                      <span className="hidden w-6 text-right tabular-nums text-ink/45 md:inline">{t.won + t.drawn + t.lost}</span>
                      <span className="hidden w-6 text-right tabular-nums text-ink/45 md:inline">{t.won}</span>
                      <span className="hidden w-6 text-right tabular-nums text-ink/45 md:inline">{t.drawn}</span>
                      <span className="hidden w-6 text-right tabular-nums text-ink/45 md:inline">{t.lost}</span>
                      <span className="hidden w-8 text-right tabular-nums text-ink/55 sm:inline">{t.gf}</span>
                      <span className="hidden w-8 text-right tabular-nums text-ink/55 sm:inline">{t.ga}</span>
                      <span className="w-9 text-right tabular-nums">{gdStr(t.gd)}</span>
                      <span className="w-8 text-right font-bold tabular-nums">{t.points}</span>
                    </>
                  : <>
                      <span className="w-12 text-right text-[10px] text-ink/50">{t.formation}</span>
                      <span className="hidden w-14 text-right text-[10px] tabular-nums text-ink/40 sm:inline">OVR {t.strength}</span>
                      <span className="w-12 text-right font-bold tabular-nums text-amber">{t.projectedPoints}<span className="text-[9px] font-normal text-ink/40">pts</span></span>
                    </>}
              </button>
              )
            })}
          </div>
        </Panel>
      </Reveal>

      <Reveal>
        <button onClick={onNewRun} className="w-full bg-amber py-4 text-base font-bold uppercase tracking-[0.18em] text-terminal transition hover:bg-ink-bright">New Run</button>
      </Reveal>

      <AnimatePresence>
        {team && (
          <Backdrop onClose={() => setTeam(null)}>
            <div className="mb-3 flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="truncate font-display text-lg font-semibold text-ink-bright">{team.team}</div>
                <div className="text-[11px] text-ink/50">{team.formation} · finished {ordinal(team.pos)} · projected {ordinal(team.projectedPos)} ({team.projectedPoints} pts)</div>
              </div>
              <div className="shrink-0 text-right text-[11px]">
                <div className="font-bold tabular-nums text-amber">{team.points} pts</div>
                <div className="text-ink/50">{team.won}-{team.drawn}-{team.lost}</div>
              </div>
            </div>
            <TeamPitch formation={team.formation} players={team.players} showStats />
            <button onClick={() => setTeam(null)} className="mt-4 w-full border border-edge py-2 text-xs font-semibold uppercase tracking-[0.16em] text-ink/70 hover:border-amber hover:text-amber">Close</button>
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
  return <div><div className="font-display text-2xl font-semibold tabular-nums text-ink-bright">{v}</div><div className="eyebrow text-ink/45">{label}</div></div>
}

function Tile({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <Panel className="p-4 text-center">
      <div className="eyebrow text-ink/45">{label}</div>
      <div className="my-1 font-display text-3xl font-semibold tabular-nums text-ink-bright">{value}</div>
      {sub && <div className="text-[11px] text-ink/40">{sub}</div>}
    </Panel>
  )
}

function Mc({ label, v, tone = 'amber' }: { label: string; v: number; tone?: 'amber' | 'phosphor' }) {
  return (
    <div className="flex items-baseline justify-between gap-2 border-b border-edge pb-1">
      <span className="eyebrow text-ink/50">{label}</span>
      <span className={`font-bold tabular-nums ${tone === 'phosphor' ? 'text-phosphor' : 'text-amber'}`}>{Math.round(v * 100)}%</span>
    </div>
  )
}

const cloudPhrase = (p: number) =>
  p >= 97 ? 'a season for the ages'
    : p >= 85 ? 'a remarkable campaign'
      : p >= 65 ? 'a strong season'
        : p >= 40 ? 'about par for this squad'
          : p >= 15 ? 'a frustrating year'
            : 'a campaign to forget'

function Odds({ label, v }: { label: string; v: number }) {
  const pct = Math.round(v * 100)
  return (
    <Panel className="p-4">
      <div className="flex items-baseline justify-between"><span className="eyebrow text-ink/50">{label}</span><span className="text-lg font-bold tabular-nums text-amber">{pct}%</span></div>
      <div className="mt-2 h-1.5 overflow-hidden bg-panel-2"><motion.div className="h-full bg-amber" initial={{ width: 0 }} animate={{ width: `${pct}%` }} transition={{ duration: 0.7 }} /></div>
    </Panel>
  )
}

function Leaders({ title, rows, unit }: { title: string; rows: SeasonView['goldenBoot']; unit: string }) {
  return (
    <Panel label={title} className="p-4">
      <div className="space-y-2">
        {rows.slice(0, 5).map((r, i) => (
          <div key={i} className="flex items-start gap-2 text-sm">
            <span className="w-4 shrink-0 text-right tabular-nums text-ink/40">{i + 1}</span>
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold text-ink-bright">{r.player}</div>
              <div className="truncate text-[10px] text-ink/40">{r.team}</div>
            </div>
            <span className="w-10 shrink-0 text-right font-bold tabular-nums text-amber">{r.value}{unit}</span>
          </div>
        ))}
      </div>
    </Panel>
  )
}

function Backdrop({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return (
    <motion.div className="fixed inset-0 z-[60] flex items-center justify-center bg-terminal/85 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
      <motion.div className="w-full max-w-lg border border-edge-bright bg-panel p-6" initial={{ scale: 0.9, y: 10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()}>{children}</motion.div>
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
