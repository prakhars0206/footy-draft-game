import type { LineName, Rating } from '../api'
import { lineClasses } from '../theme'

/**
 * A scouting reading of a player's rating, styled as classified intel:
 *  - ON   → the exact overall, a clean badge.
 *  - SCOUT→ a smudged ink confidence band spanning the estimated range (you see roughly how good, and how
 *           uncertain the read is) with the range printed over it.
 *  - OFF  → a redaction bar — no intel at all.
 */
export function ScoutRating({ rating, line }: { rating: Rating | null; line: LineName }) {
  const lc = lineClasses[line]
  if (!rating || rating.hidden) {
    return <span className={`redacted inline-flex h-8 w-12 shrink-0 items-center justify-center border ${lc.border}`} title="no intel">░░</span>
  }
  if (rating.overall != null) {
    return (
      <span className={`inline-flex h-8 w-10 shrink-0 items-center justify-center border ${lc.border} ${lc.text} bg-terminal/60 text-sm font-bold tabular-nums`}>
        {rating.overall}
      </span>
    )
  }
  // SCOUT range → smudged confidence band on a 48–99 scale
  const lo = rating.low ?? 50, hi = rating.high ?? 80
  const D0 = 48, D1 = 99
  const px = (v: number) => Math.max(0, Math.min(100, ((v - D0) / (D1 - D0)) * 100))
  return (
    <div className={`w-16 shrink-0 ${lc.text}`} title={`scouted ${lo}–${hi}`}>
      <div className="relative h-8 w-full overflow-hidden border border-edge bg-panel-2">
        {/* the ink smudge — its width is the uncertainty, its position the calibre */}
        <div
          className="absolute inset-y-0"
          style={{ left: `${px(lo)}%`, width: `${Math.max(10, px(hi) - px(lo))}%`, background: 'currentColor', opacity: 0.45, filter: 'blur(1.4px)' }}
        />
        <div className="absolute inset-0 flex items-center justify-center font-mono text-[10px] font-bold tabular-nums text-ink-bright">
          {lo}–{hi}
        </div>
      </div>
    </div>
  )
}
