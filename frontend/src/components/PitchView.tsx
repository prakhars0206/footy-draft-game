import { motion } from 'framer-motion'
import type { Slot } from '../api'
import { lineClasses, lineOf, PITCH, renderRating } from '../theme'

function code(name: string): string {
  const surname = name.trim().split(/\s+/).pop() ?? name
  return surname.slice(0, 3).toUpperCase()
}

/** The pitch: dashed markers for empty slots, badges for filled ones. Eligible/selected slots highlight + click. */
export function PitchView({
  formation,
  slots,
  interactive,
  selectedIndex,
  onSlotClick,
}: {
  formation: string
  slots: Slot[]
  interactive?: Set<number>
  selectedIndex?: number | null
  onSlotClick?: (slot: Slot) => void
}) {
  const coords = PITCH[formation] ?? PITCH['4-3-3']
  return (
    <div className="relative mx-auto aspect-[7/10] h-full max-h-full overflow-hidden border border-edge bg-gradient-to-b from-[#0b1a12] to-[#06100b]">
      {/* pitch markings */}
      <div className="pointer-events-none absolute inset-0 opacity-30">
        <div className="absolute left-1/2 top-0 h-full w-px -translate-x-1/2 bg-mid/30" />
        <div className="absolute left-1/2 top-1/2 h-24 w-24 -translate-x-1/2 -translate-y-1/2 rounded-full border border-mid/30" />
        <div className="absolute left-1/2 top-0 h-16 w-32 -translate-x-1/2 border-x border-b border-mid/25" />
        <div className="absolute bottom-0 left-1/2 h-16 w-32 -translate-x-1/2 border-x border-t border-mid/25" />
      </div>

      {slots.map((slot) => {
        const c = coords[slot.index] ?? { x: 50, y: 50 }
        const lc = lineClasses[lineOf(slot.position)]
        const isInteractive = interactive?.has(slot.index)
        const isSelected = selectedIndex === slot.index
        const clickable = !!onSlotClick && (isInteractive || (slot.filled && selectedIndex == null) || isSelected)
        return (
          <div
            key={slot.index}
            className="absolute -translate-x-1/2 -translate-y-1/2"
            style={{ left: `${c.x}%`, top: `${c.y}%` }}
          >
            {slot.filled ? (
              <motion.button
                layoutId={slot.rating ? `badge-${slot.name}` : undefined}
                initial={{ scale: 0.4, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 260, damping: 20 }}
                onClick={clickable ? () => onSlotClick?.(slot) : undefined}
                className={`flex w-14 flex-col items-center ${clickable ? 'cursor-pointer' : 'cursor-default'}`}
              >
                <span
                  className={`flex h-8 w-8 items-center justify-center border ${lc.border} ${lc.glow} bg-black/70 text-[10px] font-extrabold ${lc.text} ${isSelected ? 'ring-2 ring-amber' : ''}`}
                >
                  {code(slot.name ?? '')}
                </span>
                <span className="mt-0.5 max-w-14 truncate text-[8px] leading-tight text-ink/80">{slot.name}</span>
                <span className={`text-[8px] font-bold leading-tight ${lc.text}`}>{renderRating(slot.rating).text}</span>
              </motion.button>
            ) : (
              <button
                onClick={isInteractive ? () => onSlotClick?.(slot) : undefined}
                className={`flex h-8 w-8 items-center justify-center border border-dashed text-[9px] font-bold tracking-wider ${
                  isInteractive
                    ? `${lc.border} ${lc.text} ${lc.glow} animate-pulse cursor-pointer`
                    : 'border-edge-bright text-ink/40'
                }`}
              >
                {slot.position}
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
}
