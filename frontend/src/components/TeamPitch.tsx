import type { XiSlot } from '../api'
import { lineClasses, PITCH } from '../theme'

/** Read-only pitch: lays an XI out in its formation, showing each player's OVR. With `showStats`, also G/A/CS. */
export function TeamPitch({
  formation,
  players,
  showStats = false,
}: {
  formation: string
  players: XiSlot[]
  showStats?: boolean
}) {
  const coords = PITCH[formation] ?? PITCH['4-3-3']
  return (
    <div className="relative mx-auto aspect-[7/10] w-full max-w-sm overflow-hidden border border-edge bg-gradient-to-b from-[#1a1d16] to-[#12120d]">
      <div className="pointer-events-none absolute inset-0 opacity-50">
        <div className="absolute left-1/2 top-0 h-full w-px -translate-x-1/2 bg-ink/15" />
        <div className="absolute left-1/2 top-1/2 h-20 w-20 -translate-x-1/2 -translate-y-1/2 rounded-full border border-ink/15" />
        <div className="absolute left-1/2 top-0 h-12 w-24 -translate-x-1/2 border-x border-b border-ink/12" />
        <div className="absolute bottom-0 left-1/2 h-12 w-24 -translate-x-1/2 border-x border-t border-ink/12" />
      </div>
      {players.map((p, i) => {
        const c = coords[i] ?? { x: 50, y: 50 }
        const lc = lineClasses[p.line]
        const showCS = p.line === 'GK' || p.line === 'DEF'
        return (
          <div
            key={i}
            className="absolute flex w-16 -translate-x-1/2 -translate-y-1/2 flex-col items-center"
            style={{ left: `${c.x}%`, top: `${c.y}%` }}
            title={`${p.name} · ${p.position} · ${p.overall}`}
          >
            <span className={`flex h-8 w-8 items-center justify-center border ${lc.border} ${lc.glow} bg-terminal/85 text-[11px] font-bold tabular-nums ${lc.text}`}>
              {p.overall}
            </span>
            <span className="mt-0.5 max-w-16 truncate text-[8px] leading-tight text-ink/85">{p.name}</span>
            <span className={`text-[8px] font-bold leading-tight ${lc.text}`}>{p.position}</span>
            {showStats && (
              <span className="text-[8px] leading-tight text-amber">
                {p.goals ?? 0}G·{p.assists ?? 0}A{showCS ? `·${p.cleanSheets ?? 0}CS` : ''}
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}
