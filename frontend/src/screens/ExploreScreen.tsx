import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { explore } from '../api'
import type { AlmanacClub, AlmanacMeta, AlmanacSquad, ClubQuery } from '../api'
import { lineClasses } from '../theme'
import { Panel, Stamp } from '../components/primitives'
import { TeamPitch } from '../components/TeamPitch'

const ERAS: { label: string; from: number | null }[] = [
  { label: 'ALL-TIME', from: null },
  { label: '2020s', from: 2020 },
  { label: '2010s', from: 2010 },
]
const SORTS: { label: string; value: NonNullable<ClubQuery['sort']> }[] = [
  { label: 'STRONGEST', value: 'strength' },
  { label: 'RECENT', value: 'season' },
  { label: 'A–Z', value: 'club' },
]

/** Tier → accent. ICONIC gets the gold; the rest fade down. */
function tierTone(tier: string): string {
  switch (tier) {
    case 'ICONIC': return 'text-amber'
    case 'ELITE': return 'text-phosphor'
    case 'PEDIGREE': return 'text-ink-bright'
    case 'STEADY': return 'text-ink/70'
    default: return 'text-ink/45'
  }
}

export function ExploreScreen({ onBack }: { onBack: () => void }) {
  const [meta, setMeta] = useState<AlmanacMeta | null>(null)
  const [league, setLeague] = useState<string | null>(null)
  const [from, setFrom] = useState<number | null>(null)
  const [sort, setSort] = useState<NonNullable<ClubQuery['sort']>>('strength')
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [clubs, setClubs] = useState<AlmanacClub[]>([])
  const [loading, setLoading] = useState(true)
  const [picked, setPicked] = useState<{ club: string; season: string } | null>(null)

  useEffect(() => { explore.meta().then(setMeta).catch(() => {}) }, [])
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q.trim()), 250)
    return () => clearTimeout(t)
  }, [q])

  useEffect(() => {
    let live = true
    setLoading(true)
    explore
      .clubs({ league: league ?? undefined, from: from ?? undefined, q: debouncedQ || undefined, sort, limit: 5000 })
      .then((c) => { if (live) { setClubs(c); setLoading(false) } })
      .catch(() => { if (live) setLoading(false) })
    return () => { live = false }
  }, [league, from, sort, debouncedQ])

  return (
    <div className="space-y-5">
      <div className="flex items-end justify-between gap-4">
        <div>
          <div className="eyebrow text-ink/45">Reference · every top-5 squad, FIFA 15 → EA FC 26</div>
          <h2 className="font-display text-2xl font-semibold text-ink-bright">The Almanac</h2>
        </div>
        <button
          onClick={onBack}
          className="eyebrow border border-edge px-3 py-1.5 text-ink/60 transition hover:border-edge-bright hover:text-ink-bright"
        >
          ← Back to setup
        </button>
      </div>

      <Panel label="Browse" className="p-5">
        <div className="space-y-4">
          <Row label="LEAGUE">
            <Chip active={league === null} onClick={() => setLeague(null)}>ALL</Chip>
            {meta?.leagues.map((l) => (
              <Chip key={l} active={league === l} onClick={() => setLeague(l)}>{l}</Chip>
            ))}
          </Row>
          <div className="grid gap-4 sm:grid-cols-[auto_auto_1fr]">
            <Row label="ERA">
              {ERAS.map((e) => (
                <Chip key={e.label} active={from === e.from} onClick={() => setFrom(e.from)}>{e.label}</Chip>
              ))}
            </Row>
            <Row label="SORT">
              {SORTS.map((s) => (
                <Chip key={s.value} active={sort === s.value} onClick={() => setSort(s.value)}>{s.label}</Chip>
              ))}
            </Row>
            <div className="sm:justify-self-end">
              <div className="mb-2 eyebrow text-ink/65">SEARCH</div>
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="club name…"
                className="w-full border border-edge bg-terminal/60 px-3 py-1.5 text-sm text-ink-bright placeholder:text-ink/30 focus:border-amber/60 focus:outline-none sm:w-56"
              />
            </div>
          </div>
        </div>
      </Panel>

      <div className="flex items-baseline justify-between">
        <div className="eyebrow text-ink/45">
          {sort === 'strength' && !league && !from && !debouncedQ ? 'Hall of Fame · strongest squads' : 'Results'}
        </div>
        <div className="eyebrow text-ink/35">{loading ? 'loading…' : `${clubs.length} squads`}</div>
      </div>

      {clubs.length === 0 && !loading ? (
        <div className="border border-edge bg-panel/40 p-8 text-center font-display italic text-ink/50">
          No squads match those filters.
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {clubs.map((c) => (
            <ClubCard key={c.club + c.season} c={c} onClick={() => setPicked({ club: c.club, season: c.season })} />
          ))}
        </div>
      )}

      <AnimatePresence>
        {picked && <SquadModal club={picked.club} season={picked.season} onClose={() => setPicked(null)} />}
      </AnimatePresence>
    </div>
  )
}

function ClubCard({ c, onClick }: { c: AlmanacClub; onClick: () => void }) {
  return (
    <motion.button
      whileHover={{ y: -2 }}
      onClick={onClick}
      className="group flex flex-col border border-edge bg-panel/40 p-3 text-left transition hover:border-edge-bright"
    >
      <div className="flex items-start justify-between gap-2">
        <span className={`font-display text-3xl font-black tabular-nums leading-none ${tierTone(c.tier)}`}>
          {c.strength}
        </span>
        <span className="eyebrow text-[9px] text-ink/40">{c.tier}</span>
      </div>
      <div className="mt-2 truncate font-semibold text-ink-bright group-hover:text-amber">{c.club}</div>
      <div className="eyebrow mt-0.5 text-[10px] text-ink/45">{c.season} · {c.league}</div>
      <div className="mt-2 truncate border-t border-edge pt-1.5 text-[11px] text-ink/55">
        ★ {c.topPlayer} <span className="tabular-nums text-ink/40">{c.topOverall}</span>
      </div>
    </motion.button>
  )
}

function SquadModal({ club, season, onClose }: { club: string; season: string; onClose: () => void }) {
  const [squad, setSquad] = useState<AlmanacSquad | null>(null)
  useEffect(() => {
    let live = true
    explore.squad(club, season).then((s) => { if (live) setSquad(s) }).catch(() => {})
    return () => { live = false }
  }, [club, season])

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-terminal/80 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.97, y: 10 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.97, y: 10 }}
        onClick={(e) => e.stopPropagation()}
        className="max-h-[90vh] w-full max-w-4xl overflow-y-auto border border-edge-bright bg-terminal p-5"
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-display text-2xl font-semibold text-ink-bright">{club}</h3>
              {squad && <Stamp text={squad.tier} tone={squad.tier === 'ICONIC' ? 'amber' : 'phosphor'} />}
            </div>
            <div className="eyebrow mt-1 text-ink/50">
              {season} · {squad?.league ?? ''}{squad ? ` · ${squad.formation}` : ''}
            </div>
          </div>
          <div className="text-right">
            {squad && <div className="font-display text-3xl font-black tabular-nums text-amber">{squad.strength}</div>}
            <button onClick={onClose} className="eyebrow mt-1 text-ink/40 transition hover:text-ink-bright">close ✕</button>
          </div>
        </div>

        {!squad ? (
          <div className="py-16 text-center font-display italic text-ink/40">Reading the dossier…</div>
        ) : (
          <div className="grid gap-5 md:grid-cols-[minmax(0,300px)_1fr]">
            <div className="h-[420px]">
              <TeamPitch formation={squad.formation} players={squad.xi} fill />
            </div>
            <div>
              <div className="mb-2 eyebrow text-ink/50">Full squad · {squad.roster.length}</div>
              <table className="w-full text-sm">
                <tbody>
                  {squad.roster.map((p, i) => (
                    <tr key={p.sofifaId + '-' + i} className="border-b border-edge/60">
                      <td className="py-1 pr-2 text-right tabular-nums text-ink/30">{i + 1}</td>
                      <td className="py-1 pr-2">
                        <span className="text-ink-bright">{p.name}</span>
                        <span className="ml-1.5 text-[10px] text-ink/35">{p.positions.join(' / ')}</span>
                      </td>
                      <td className="py-1 pl-2 text-right">
                        <span className={`font-bold tabular-nums ${lineClasses[p.line].text}`}>{p.overall}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </motion.div>
    </motion.div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 eyebrow text-ink/65">{label}</div>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`border px-3 py-1 text-xs font-semibold tracking-wide transition ${
        active ? 'border-amber/60 bg-amber/12 text-amber' : 'border-edge text-ink/55 hover:border-edge-bright hover:text-ink-bright'
      }`}
    >
      {children}
    </button>
  )
}
