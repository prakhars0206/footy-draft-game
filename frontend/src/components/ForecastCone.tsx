import { motion } from 'framer-motion'
import type { MonteCarlo } from '../api'

/**
 * The Monte-Carlo projection as a forecast fan (think election/weather forecast cone): the points axis with
 * nested probability bands — the outer p5–p95 fades into the tails, the inner p25–p75 is solid, a median tick
 * marks the centre. If `actual` is given (debrief) the season is dropped in as a pin, so over/under-performance
 * reads at a glance.
 */
export function ForecastCone({
  mc,
  actual,
  height = 56,
}: {
  mc: MonteCarlo
  actual?: number | null
  height?: number
}) {
  const lo = mc.min
  const span = Math.max(1, mc.max - lo)
  const x = (v: number) => Math.max(0, Math.min(100, ((v - lo) / span) * 100))
  const p5 = x(mc.p5), p25 = x(mc.p25), med = x(mc.median), p75 = x(mc.p75), p95 = x(mc.p95)
  const ax = actual != null ? x(actual) : null
  const grow = { initial: { scaleX: 0 }, animate: { scaleX: 1 }, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] as const } }

  return (
    <div>
      <div className="relative w-full" style={{ height }}>
        {/* baseline */}
        <div className="absolute inset-x-0 top-1/2 h-px -translate-y-1/2 bg-edge" />

        {/* outer fan p5–p95 — fades into the tails */}
        <motion.div
          {...grow}
          className="absolute top-1/2 -translate-y-1/2"
          style={{
            left: `${p5}%`, width: `${p95 - p5}%`, height: height * 0.62, transformOrigin: `${med}% 50%`,
            background:
              'linear-gradient(90deg, transparent, rgba(200,151,63,0.10) 18%, rgba(200,151,63,0.20) 50%, rgba(200,151,63,0.10) 82%, transparent)',
          }}
        />
        {/* inner likely band p25–p75 */}
        <motion.div
          {...grow}
          className="absolute top-1/2 -translate-y-1/2 border-x border-amber/40 bg-amber/20"
          style={{ left: `${p25}%`, width: `${p75 - p25}%`, height: height * 0.42, transformOrigin: `${med}% 50%` }}
        />
        {/* median tick */}
        <motion.div
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
          className="absolute top-1/2 w-px -translate-y-1/2 bg-amber/80"
          style={{ left: `${med}%`, height: height * 0.58 }}
        />

        {/* the actual season, dropped in as a pin */}
        {ax != null && (
          <motion.div
            className="absolute inset-y-0 z-10 -translate-x-1/2"
            style={{ left: `${ax}%` }}
            initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5, type: 'spring', stiffness: 300, damping: 16 }}
          >
            <div className="mx-auto h-full w-px bg-ink-bright" />
            <div className="absolute -top-1 left-1/2 h-2.5 w-2.5 -translate-x-1/2 rotate-45 border border-ink-bright bg-amber" />
            <div className="absolute -bottom-0.5 left-1/2 -translate-x-1/2 whitespace-nowrap font-display text-[11px] font-bold tabular-nums text-ink-bright">
              {actual}
            </div>
          </motion.div>
        )}
      </div>

      <div className="mt-1 flex justify-between text-[10px] tabular-nums text-ink/35">
        <span>{mc.min}</span>
        <span className="text-amber/70">median {mc.median}</span>
        <span>{mc.max}</span>
      </div>
    </div>
  )
}
