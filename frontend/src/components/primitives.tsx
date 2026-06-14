import type { ReactNode } from 'react'

/** An editorial "leaf": a quiet card with a small-caps eyebrow header above a hairline rule. */
export function Panel({
  children,
  label,
  className = '',
  accent = 'edge',
}: {
  children: ReactNode
  label?: string
  className?: string
  accent?: 'edge' | 'amber' | 'phosphor'
}) {
  const accentText =
    accent === 'amber' ? 'text-amber' : accent === 'phosphor' ? 'text-phosphor' : 'text-ink/70'
  return (
    <div className={`relative border border-edge bg-panel/50 ${className}`}>
      {label && (
        <div className="mb-3 flex items-center gap-3 border-b border-edge pb-2">
          <span className={`eyebrow ${accentText}`}>{label}</span>
          <span className="h-px flex-1 bg-edge" />
        </div>
      )}
      {children}
    </div>
  )
}

/** A set-in small-caps tag (CLASSIFIED / SCOUTED / OVERPERFORMED …) — boxed, no rotation. */
export function Stamp({
  text,
  tone = 'amber',
  className = '',
}: {
  text: string
  tone?: 'amber' | 'phosphor' | 'danger'
  className?: string
}) {
  const tones = {
    amber: 'text-amber border-amber/50',
    phosphor: 'text-phosphor border-phosphor/50',
    danger: 'text-danger border-danger/50',
  }
  return (
    <span
      className={`inline-block border ${tones[tone]} px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.18em] ${className}`}
    >
      {text}
    </span>
  )
}

/** A refined lead-in line, e.g. "— acquire target". No blinking caret. */
export function Prompt({ children }: { children: ReactNode }) {
  return (
    <div className="font-display text-[15px] italic text-ink/75">
      <span className="not-italic text-amber">—</span> {children}
    </div>
  )
}
