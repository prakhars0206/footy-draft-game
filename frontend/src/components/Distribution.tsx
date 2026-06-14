import type { MonteCarlo } from '../api'

/**
 * The Monte-Carlo points cloud: a histogram of how many of the simulated seasons finished on each points band.
 * The median bin is muted; if `actual` is given (debrief) its bin lights up gold — your season in the cloud.
 */
export function Distribution({ mc, actual, height = 'h-20' }: { mc: MonteCarlo; actual?: number | null; height?: string }) {
  const maxC = Math.max(1, ...mc.histogram)
  const binOf = (pts: number) =>
    Math.min(mc.histogram.length - 1, Math.max(0, Math.floor((pts - mc.histMin) / mc.histBinWidth)))
  const actualBin = actual != null ? binOf(actual) : -1
  const medianBin = binOf(mc.median)

  return (
    <div>
      <div className={`flex items-end gap-px ${height}`}>
        {mc.histogram.map((c, i) => {
          const isAct = i === actualBin
          const isMed = i === medianBin && !isAct
          // Bars are DIRECT flex children so the % height resolves against the row's definite height.
          return (
            <div
              key={i}
              title={`${mc.histMin + i * mc.histBinWidth}–${mc.histMin + (i + 1) * mc.histBinWidth - 1} pts · ${c}`}
              className={`flex-1 ${isAct ? 'bg-amber' : isMed ? 'bg-ink/60' : 'bg-ink/25'}`}
              style={{ height: `${Math.max(c > 0 ? 6 : 0, (c / maxC) * 100)}%` }}
            />
          )
        })}
      </div>
      <div className="mt-1 flex justify-between text-[10px] tabular-nums text-ink/35">
        <span>{mc.min}</span>
        <span className="text-ink/50">median {mc.median}</span>
        <span>{mc.max}</span>
      </div>
    </div>
  )
}
