import { motion } from 'framer-motion'
import type { SpineGame } from '../season'
import { shortClub, unbeatenPrefix } from '../season'

const FILL: Record<string, string> = { W: 'bg-phosphor', D: 'bg-ink/40', L: 'bg-danger' }

/**
 * The "spine of the season" — every matchday as a W/D/L block. The unbeaten run from matchday 1 is drawn as one
 * continuous gold thread along the top that visibly FRAYS at the defeat that finally ends it (or runs the whole
 * length, glowing, for an Invincible season). Used live in playback (grows as the season plays) and on the
 * results front page (the full 38).
 */
export function SeasonSpine({
  games,
  total = 38,
  animate = false,
  height = 'h-6',
}: {
  games: SpineGame[]
  total?: number
  animate?: boolean
  height?: string
}) {
  const unbeaten = unbeatenPrefix(games)
  const frayed = unbeaten < games.length // a defeat lands right after the run
  const invincible = games.length >= total && unbeaten >= total
  const threadPct = (unbeaten / total) * 100

  return (
    <div>
      <div className="relative">
        {/* the unbeaten thread */}
        {unbeaten > 0 && (
          <motion.div
            className="pointer-events-none absolute -top-[3px] left-0 z-10 h-[2px] bg-amber"
            style={{ boxShadow: `0 0 6px 1px rgba(200,151,63,${invincible ? 0.9 : 0.55})` }}
            initial={animate ? { width: 0 } : false}
            animate={{ width: `${threadPct}%` }}
            transition={{ type: 'spring', stiffness: 140, damping: 24 }}
          />
        )}
        {/* the fray — a small claret snap where the run breaks */}
        {frayed && (
          <motion.span
            className="pointer-events-none absolute -top-[7px] z-20 -translate-x-1/2 text-[9px] font-black leading-none text-danger"
            style={{ left: `${threadPct}%` }}
            initial={animate ? { scale: 0, opacity: 0 } : false}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 500, damping: 14, delay: animate ? 0.15 : 0 }}
          >
            ✕
          </motion.span>
        )}

        <div className={`flex gap-px ${height}`}>
          {Array.from({ length: total }).map((_, i) => {
            const g = games[i]
            if (!g)
              return <div key={i} className="flex-1 border border-edge/40 bg-panel-2/40" />
            const Block = animate ? motion.div : 'div'
            return (
              <Block
                key={i}
                title={`MD ${g.md} · ${g.home ? 'v' : '@'} ${shortClub(g.opp)} · ${g.gf}–${g.ga}`}
                className={`flex-1 ${FILL[g.r]} ${g.r === 'W' ? 'opacity-100' : g.r === 'D' ? 'opacity-90' : 'opacity-95'}`}
                {...(animate
                  ? { initial: { scaleY: 0.2, opacity: 0 }, animate: { scaleY: 1, opacity: 1 }, transition: { duration: 0.25 } }
                  : {})}
              />
            )
          })}
        </div>
      </div>

      <div className="mt-1 flex items-center justify-between text-[10px] text-ink/40">
        <span className="flex items-center gap-2.5">
          <Key c="bg-phosphor" t="W" />
          <Key c="bg-ink/40" t="D" />
          <Key c="bg-danger" t="L" />
        </span>
        <span className="eyebrow text-[9px]">
          {invincible ? (
            <span className="text-amber">Invincible · {total} unbeaten</span>
          ) : unbeaten > 0 ? (
            <>
              Unbeaten run <span className="tabular-nums text-amber">{unbeaten}</span>
              {frayed && <span className="text-ink/35"> · ended MD {games[unbeaten]?.md}</span>}
            </>
          ) : (
            <span className="text-ink/35">no unbeaten start</span>
          )}
        </span>
      </div>
    </div>
  )
}

function Key({ c, t }: { c: string; t: string }) {
  return (
    <span className="flex items-center gap-1">
      <i className={`inline-block h-2 w-2 ${c}`} />
      {t}
    </span>
  )
}
