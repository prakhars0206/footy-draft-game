import type { ReactNode } from 'react'

/** A bordered terminal panel with optional header label and decorative corner ticks. */
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
  const border =
    accent === 'amber' ? 'border-amber/40' : accent === 'phosphor' ? 'border-phosphor/40' : 'border-edge'
  return (
    <div className={`relative border ${border} bg-panel/70 ${className}`}>
      <Ticks />
      {label && (
        <div className="absolute -top-2 left-3 bg-terminal px-2 text-[10px] tracking-mega text-ink/70">
          {label}
        </div>
      )}
      {children}
    </div>
  )
}

function Ticks() {
  const c = 'absolute h-2 w-2 border-edge-bright'
  return (
    <>
      <span className={`${c} left-0 top-0 border-l border-t`} />
      <span className={`${c} right-0 top-0 border-r border-t`} />
      <span className={`${c} bottom-0 left-0 border-b border-l`} />
      <span className={`${c} bottom-0 right-0 border-b border-r`} />
    </>
  )
}

/** A rotated rubber-stamp label (CLASSIFIED / SCOUTED / OVERPERFORMED …). */
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
    amber: 'text-amber border-amber',
    phosphor: 'text-phosphor border-phosphor',
    danger: 'text-danger border-danger',
  }
  return (
    <span
      className={`inline-block -rotate-6 border-2 ${tones[tone]} px-2 py-0.5 text-xs font-extrabold tracking-[0.2em] opacity-90 ${className}`}
    >
      {text}
    </span>
  )
}

/** A blinking-caret prompt line, e.g. "> acquire target". */
export function Prompt({ children }: { children: ReactNode }) {
  return (
    <div className="caret text-sm text-phosphor/80">
      <span className="text-ink/50">&gt;</span> {children}
    </div>
  )
}
