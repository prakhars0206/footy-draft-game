import { useState } from 'react'
import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import type { LineName, SeasonView } from '../api'
import { Panel, Stamp } from '../components/primitives'
import { lineClasses } from '../theme'

export function ResultsScreen({ season, onNewRun }: { season: SeasonView; onNewRun: () => void }) {
  const [tableOpen, setTableOpen] = useState(false)
  const proj = season.projection.expectedPoints
  const delta = season.points - proj
  const verdict =
    delta >= 6
      ? { text: 'OVERPERFORMED', tone: 'phosphor' as const }
      : delta <= -6
        ? { text: 'UNDERPERFORMED', tone: 'danger' as const }
        : { text: 'AS EXPECTED', tone: 'amber' as const }
  const unbeaten = season.lost === 0
  const perfect = season.won === 38

  return (
    <motion.div
      className="space-y-4"
      initial="hidden"
      animate="show"
      variants={{ show: { transition: { staggerChildren: 0.08 } } }}
    >
      <Reveal>
        <Panel label="DEBRIEF" accent={perfect ? 'phosphor' : 'edge'} className="p-6 text-center">
          {perfect && <Stamp text="38-0 · PERFECT SEASON" tone="phosphor" className="mb-3" />}
          <div className="text-xs tracking-mega text-ink/50">FINAL STANDING</div>
          <div className="my-2 text-6xl font-extrabold text-ink-bright glow-phosphor">
            {ordinal(season.finishPos)}
          </div>
          <div className="flex items-center justify-center gap-3">
            <span className="text-sm text-ink/60">
              projected {proj} pts · finished {season.points} pts
            </span>
            <Stamp text={verdict.text} tone={verdict.tone} />
          </div>
          {unbeaten && !perfect && (
            <div className="mt-2 text-sm text-phosphor">◆ UNBEATEN — {season.won}W {season.drawn}D</div>
          )}
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
        <Panel label="YOUR XI · INTEL CONFIRMED" className="p-4">
          <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-4">
            {season.yourXI.map((p, i) => {
              const lc = lineClasses[p.line as LineName]
              return (
                <div key={i} className="flex items-center gap-2 border border-edge px-2 py-1.5">
                  <span className={`flex h-7 w-8 items-center justify-center border ${lc.border} ${lc.text} text-xs font-extrabold tabular-nums`}>
                    {p.overall}
                  </span>
                  <div className="min-w-0">
                    <div className="truncate text-xs font-bold text-ink-bright">{p.name}</div>
                    <div className={`text-[9px] font-bold ${lc.text}`}>{p.position}</div>
                  </div>
                </div>
              )
            })}
          </div>
        </Panel>
      </Reveal>

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
        <Panel label="FINAL TABLE" className="p-4">
          <button onClick={() => setTableOpen((o) => !o)} className="mb-2 text-xs text-amber">
            {tableOpen ? '▾ collapse' : '▸ expand full table'}
          </button>
          <div className="space-y-1">
            {(tableOpen ? season.table : season.table.slice(0, 6)).map((t) => (
              <div
                key={t.pos}
                className={`flex items-center gap-3 px-2 py-1 text-sm ${
                  t.you ? 'border border-amber bg-amber/10 text-amber glow-amber' : 'text-ink/70'
                }`}
              >
                <span className="w-6 text-right tabular-nums text-ink/50">{t.pos}</span>
                <span className="flex-1 truncate font-bold">{t.team}</span>
                <span className="tabular-nums text-ink/50">{t.won}-{t.drawn}-{t.lost}</span>
                <span className="w-10 text-right tabular-nums">{gdStr(t.gd)}</span>
                <span className="w-8 text-right font-extrabold tabular-nums">{t.points}</span>
              </div>
            ))}
            {!tableOpen && season.finishPos > 6 && (
              <div className="pt-1 text-center text-[10px] text-ink/40">… expand to find your XI ({ordinal(season.finishPos)}) …</div>
            )}
          </div>
        </Panel>
      </Reveal>

      <Reveal>
        <button
          onClick={onNewRun}
          className="w-full border-2 border-phosphor bg-phosphor/10 py-4 text-lg font-extrabold tracking-[0.3em] text-phosphor glow-phosphor"
        >
          ▸ NEW RUN
        </button>
      </Reveal>
    </motion.div>
  )
}

function Reveal({ children }: { children: ReactNode }) {
  return (
    <motion.div variants={{ hidden: { opacity: 0, y: 14 }, show: { opacity: 1, y: 0 } }}>{children}</motion.div>
  )
}

function Mini({ label, v }: { label: string; v: number }) {
  return (
    <div>
      <div className="text-lg font-extrabold tabular-nums text-ink-bright">{v}</div>
      <div className="text-[9px] tracking-widest text-ink/50">{label}</div>
    </div>
  )
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
      <div className="flex items-baseline justify-between">
        <span className="text-[10px] tracking-mega text-ink/50">{label}</span>
        <span className="text-lg font-extrabold tabular-nums text-amber">{pct}%</span>
      </div>
      <div className="mt-2 h-1.5 overflow-hidden border border-edge bg-black/40">
        <motion.div
          className="h-full bg-amber"
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.7 }}
        />
      </div>
    </Panel>
  )
}

function Leaders({ title, rows, unit }: { title: string; rows: SeasonView['goldenBoot']; unit: string }) {
  return (
    <Panel label={title} className="p-4">
      <div className="space-y-1">
        {rows.slice(0, 5).map((r, i) => (
          <div key={i} className="flex items-center gap-2 text-sm">
            <span className="w-5 text-right text-ink/40">{i + 1}</span>
            <span className="flex-1 truncate font-bold text-ink-bright">{r.player}</span>
            <span className="max-w-32 truncate text-[10px] text-ink/40">{r.team}</span>
            <span className="w-10 text-right font-extrabold tabular-nums text-amber">
              {r.value}
              {unit}
            </span>
          </div>
        ))}
      </div>
    </Panel>
  )
}

const gd = (s: SeasonView) => gdStr(s.goalsFor - s.goalsAgainst)
const gdStr = (n: number) => (n > 0 ? `+${n}` : `${n}`)
function ordinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] ?? s[v] ?? s[0])
}
