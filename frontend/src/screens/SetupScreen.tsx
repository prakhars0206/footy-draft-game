import { useState } from 'react'
import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import { api } from '../api'
import type { Difficulty, PlayerRatings, RunState, ShowRatings } from '../api'
import { FORMATIONS } from '../theme'
import { Panel, Prompt } from '../components/primitives'

const ERAS: { label: string; from: number | null }[] = [
  { label: 'ALL-TIME', from: null },
  { label: '2010s+', from: 2010 },
  { label: 'MODERN ’16+', from: 2016 },
]

export function SetupScreen({ onCreated }: { onCreated: (run: RunState) => void }) {
  const [formation, setFormation] = useState<string>('4-3-3')
  const [showRatings, setShowRatings] = useState<ShowRatings>('SCOUT')
  const [difficulty, setDifficulty] = useState<Difficulty>('NORMAL')
  const [playerRatings, setPlayerRatings] = useState<PlayerRatings>('CAREER')
  const [eraFrom, setEraFrom] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function initiate() {
    setBusy(true)
    setError(null)
    try {
      const run = await api.createRun({
        formation,
        showRatings,
        difficulty,
        playerRatings,
        draftMode: 'SQUAD_FIRST',
        leagueScope: 'WORLD',
        eraFrom,
      })
      onCreated(run)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'failed to create run')
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Panel label="MISSION CONFIG" className="p-5">
        <Prompt>configure scouting parameters, then initiate the draft</Prompt>

        <div className="mt-5 space-y-5">
          <Field label="FORMATION">
            <div className="flex flex-wrap gap-2">
              {FORMATIONS.map((f) => (
                <Chip key={f} active={formation === f} onClick={() => setFormation(f)}>
                  {f}
                </Chip>
              ))}
            </div>
          </Field>

          <Field label="SHOW RATINGS" hint="Scout = fuzzy intel ranges · the headline mode">
            <Segmented
              options={[
                { value: 'ON', label: 'FULL' },
                { value: 'SCOUT', label: 'SCOUT' },
                { value: 'OFF', label: 'BLIND' },
              ]}
              value={showRatings}
              onChange={setShowRatings}
            />
          </Field>

          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="DIFFICULTY" hint="reroll budget: 3 / 1 / 0">
              <Segmented
                options={[
                  { value: 'EASY', label: 'EASY' },
                  { value: 'NORMAL', label: 'NORMAL' },
                  { value: 'HARD', label: 'HARD' },
                ]}
                value={difficulty}
                onChange={setDifficulty}
              />
            </Field>
            <Field label="PLAYER RATINGS" hint="career season vs career-best">
              <Segmented
                options={[
                  { value: 'CAREER', label: 'CAREER' },
                  { value: 'PRIME', label: 'PRIME' },
                ]}
                value={playerRatings}
                onChange={setPlayerRatings}
              />
            </Field>
          </div>

          <Field label="ERA">
            <div className="flex flex-wrap gap-2">
              {ERAS.map((e) => (
                <Chip key={e.label} active={eraFrom === e.from} onClick={() => setEraFrom(e.from)}>
                  {e.label}
                </Chip>
              ))}
            </div>
          </Field>

          <div className="flex flex-wrap gap-2 text-[10px] text-ink/40">
            <span className="border border-edge px-2 py-1">SCOPE: WORLD DRAFT</span>
            <span className="border border-edge px-2 py-1">MODE: SQUAD FIRST</span>
            <span className="border border-edge px-2 py-1 opacity-50">POSITION FIRST · soon</span>
            <span className="border border-edge px-2 py-1 opacity-50">CLASSIC · soon</span>
          </div>
        </div>
      </Panel>

      {error && <div className="border border-danger/50 bg-danger/10 p-3 text-sm text-danger">! {error}</div>}

      <motion.button
        whileHover={{ scale: busy ? 1 : 1.01 }}
        whileTap={{ scale: busy ? 1 : 0.99 }}
        disabled={busy}
        onClick={initiate}
        className="w-full border-2 border-phosphor bg-phosphor/10 py-4 text-lg font-extrabold tracking-[0.3em] text-phosphor glow-phosphor disabled:opacity-50"
      >
        {busy ? 'DEPLOYING…' : '▸ INITIATE DRAFT'}
      </motion.button>
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 flex items-baseline gap-2">
        <span className="text-[11px] tracking-mega text-ink/70">{label}</span>
        {hint && <span className="text-[10px] text-ink/35">{hint}</span>}
      </div>
      {children}
    </div>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`border px-3 py-1.5 text-sm font-bold tracking-wide transition ${
        active
          ? 'border-amber bg-amber/10 text-amber glow-amber'
          : 'border-edge text-ink/60 hover:border-edge-bright hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div className="inline-flex border border-edge">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`px-4 py-1.5 text-sm font-bold tracking-wide transition ${
            value === o.value ? 'bg-amber/15 text-amber glow-amber' : 'text-ink/55 hover:text-ink'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
