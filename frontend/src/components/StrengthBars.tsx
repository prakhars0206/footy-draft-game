import { motion } from 'framer-motion'
import type { Strength } from '../api'

const ROWS: { label: string; key: keyof Strength; cls: string }[] = [
  { label: 'ATT', key: 'attack', cls: 'bg-att' },
  { label: 'MID', key: 'midfield', cls: 'bg-mid' },
  { label: 'DEF', key: 'defence', cls: 'bg-def' },
  { label: 'GK', key: 'gk', cls: 'bg-gk' },
]

/** Live team strength. Null (Scout/Off mode) renders as redacted — the aggregate can't leak hidden ratings. */
export function StrengthBars({ strength }: { strength: Strength | null }) {
  const hidden = strength == null
  return (
    <div className="space-y-2">
      <div className="flex items-baseline justify-between">
        <span className="eyebrow text-ink/60">Team Strength</span>
        {hidden ? (
          <span className="redacted px-2 text-lg font-extrabold">00</span>
        ) : (
          <span className="font-display text-2xl font-semibold tabular-nums text-ink-bright">
            {strength.overall ?? '—'}
          </span>
        )}
      </div>
      {ROWS.map((row) => {
        const v = hidden ? null : (strength[row.key] as number | null)
        return (
          <div key={row.label} className="flex items-center gap-2">
            <span className="eyebrow w-8 text-ink/60">{row.label}</span>
            <div className="relative h-2 flex-1 overflow-hidden bg-panel-2">
              {hidden ? (
                <div className="redacted absolute inset-0" />
              ) : (
                <motion.div
                  className={`absolute inset-y-0 left-0 ${row.cls}`}
                  initial={{ width: 0 }}
                  animate={{ width: `${((v ?? 0) / 99) * 100}%` }}
                  transition={{ type: 'spring', stiffness: 120, damping: 18 }}
                />
              )}
            </div>
            <span className="w-7 text-right text-xs tabular-nums text-ink/70">
              {hidden ? '··' : (v ?? '—')}
            </span>
          </div>
        )
      })}
    </div>
  )
}
