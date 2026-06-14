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
      <Panel label="Draft Configuration" className="p-6">
        <h2 className="font-display text-2xl font-semibold text-ink-bright">Set your terms</h2>
        <div className="mt-1"><Prompt>choose your shape and intel, then begin the draft</Prompt></div>

        <div className="mt-6 space-y-5">
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

          <div className="flex flex-wrap gap-2">
            <span className="eyebrow border border-edge px-2 py-1 text-ink/55">Scope · World Draft</span>
            <span className="eyebrow border border-edge px-2 py-1 text-ink/55">Mode · Squad First</span>
            <span className="eyebrow border border-edge px-2 py-1 text-ink/30">Position First · soon</span>
            <span className="eyebrow border border-edge px-2 py-1 text-ink/30">Classic · soon</span>
          </div>
        </div>
      </Panel>

      {error && <div className="border border-danger/50 bg-danger/10 p-3 text-sm text-danger">! {error}</div>}

      <motion.button
        whileHover={{ scale: busy ? 1 : 1.01 }}
        whileTap={{ scale: busy ? 1 : 0.99 }}
        disabled={busy}
        onClick={initiate}
        className="w-full bg-amber py-4 text-base font-bold uppercase tracking-[0.18em] text-terminal transition hover:bg-ink-bright disabled:opacity-50"
      >
        {busy ? 'Dealing the cards…' : 'Begin the Draft'}
      </motion.button>
    </div>
  )
}

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 flex items-baseline gap-2">
        <span className="eyebrow text-ink/65">{label}</span>
        {hint && <span className="font-display text-[12px] italic text-ink/40">{hint}</span>}
      </div>
      {children}
    </div>
  )
}

function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`border px-3.5 py-1.5 text-sm font-semibold tracking-wide transition ${
        active
          ? 'border-amber/60 bg-amber/12 text-amber'
          : 'border-edge text-ink/55 hover:border-edge-bright hover:text-ink-bright'
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
    <div className="inline-flex divide-x divide-edge border border-edge">
      {options.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`px-4 py-1.5 text-sm font-semibold tracking-wide transition ${
            value === o.value ? 'bg-amber/12 text-amber' : 'text-ink/55 hover:text-ink-bright'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
