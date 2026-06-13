import type { LineName, Rating } from '../api'
import { lineClasses, renderRating } from '../theme'

const SIZES = {
  sm: 'h-7 min-w-7 text-xs px-1',
  md: 'h-10 min-w-10 text-base px-1.5',
  lg: 'h-12 min-w-12 text-lg px-2',
}

/** Square rating badge, coloured by the player's line. Shows an exact number, a Scout range, or a redaction bar. */
export function RatingBadge({
  rating,
  line,
  size = 'md',
}: {
  rating: Rating | null
  line: LineName
  size?: keyof typeof SIZES
}) {
  const r = renderRating(rating)
  const lc = lineClasses[line]
  if (r.kind === 'hidden') {
    return (
      <span
        className={`redacted inline-flex items-center justify-center border ${lc.border} ${SIZES[size]} font-bold`}
        title="rating hidden"
      >
        ░░
      </span>
    )
  }
  const isRange = r.kind === 'range'
  return (
    <span
      className={`inline-flex items-center justify-center border ${lc.border} ${lc.text} ${lc.glow} bg-black/40 ${SIZES[size]} font-bold tabular-nums ${isRange ? 'text-[0.62em] leading-none' : ''}`}
    >
      {r.text}
    </span>
  )
}
