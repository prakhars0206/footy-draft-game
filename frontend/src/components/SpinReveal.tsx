import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import type { SpinView, SpinClub } from '../api'
import { Panel, Stamp } from './primitives'

// Fake names that flicker past during the "locking on" scan — the suspense before you see what you landed on.
const FLICKER = [
  'Real Madrid', 'Bayern', 'Liverpool', 'Juventus', 'Ajax', 'Sevilla', 'Napoli', 'Lyon', 'Roma', 'Valencia',
  'Benfica', 'Dortmund', 'Inter', 'Milan', 'Arsenal', 'Chelsea', 'Porto', 'Lazio', 'Monaco', 'Leeds',
]

type Style = {
  text: string; border: string; banner: string; tone: 'amber' | 'phosphor' | 'danger'
  epic: boolean; rgb: string; scanMs: number; tagline: string
}
const TIER_STYLE: Record<string, Style> = {
  JUGGERNAUT:            { text: 'text-amber',      border: 'border-amber',       banner: 'Juggernaut',          tone: 'amber',    epic: true,  rgb: '200,151,63',  scanMs: 2700, tagline: 'a colossus of the game lands' },
  'TITLE CONTENDER':     { text: 'text-phosphor',   border: 'border-phosphor',    banner: 'Title Contender',     tone: 'phosphor', epic: true,  rgb: '111,158,126', scanMs: 2300, tagline: 'genuine silverware pedigree' },
  'EUROPEAN CHASER':     { text: 'text-def',        border: 'border-def',         banner: 'European Chaser',     tone: 'phosphor', epic: false, rgb: '91,134,179',  scanMs: 1800, tagline: 'European nights beckon' },
  'MID-TABLE':           { text: 'text-ink-bright', border: 'border-edge-bright', banner: 'Mid-Table',           tone: 'amber',    epic: false, rgb: '239,231,214', scanMs: 1500, tagline: 'a solid, workmanlike squad' },
  'RELEGATION SCRAPPER': { text: 'text-danger',     border: 'border-danger',      banner: 'Relegation Scrapper', tone: 'danger',   epic: false, rgb: '178,74,64',   scanMs: 1400, tagline: 'a proper scrap on your hands' },
}

export function SpinReveal({ spin, onPick }: { spin: SpinView; onPick: (club: SpinClub) => void }) {
  const [phase, setPhase] = useState<'scan' | 'reveal'>('scan')
  const [flick, setFlick] = useState(FLICKER[0])
  const st = TIER_STYLE[spin.tier] ?? TIER_STYLE['MID-TABLE']
  const accent: 'edge' | 'amber' | 'phosphor' = st.epic && st.tone !== 'danger' ? st.tone : 'edge'

  // Decelerating "roulette" flicker — names whip past, then slow as the spin settles on its club.
  useEffect(() => {
    let alive = true
    let elapsed = 0
    let delay = 55
    const tick = () => {
      if (!alive) return
      setFlick(FLICKER[Math.floor(Math.random() * FLICKER.length)])
      elapsed += delay
      if (elapsed / st.scanMs > 0.5) delay += st.epic ? 42 : 24 // ease off near the end
      if (elapsed >= st.scanMs) { setPhase('reveal'); return }
      id = setTimeout(tick, delay)
    }
    let id = setTimeout(tick, delay)
    return () => { alive = false; clearTimeout(id) }
  }, [st])

  const bloom = (a: number) => ({ background: `radial-gradient(circle at 50% 42%, rgba(${st.rgb},${a}), transparent 62%)` })

  return (
    <Panel
      accent={accent}
      className="relative flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden p-6 text-center"
    >
      {phase === 'scan' ? (
        <>
          {/* a glow that builds as the spin charges — stronger for elite tiers */}
          <motion.div className="pointer-events-none absolute inset-0" style={bloom(st.epic ? 0.18 : 0.08)}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: st.scanMs / 1000 }} />
          <div className="relative z-10 flex flex-col items-center">
            <div className="eyebrow text-ink/50">{st.epic ? 'The wheel is slowing' : 'Drawing your club'}</div>
            <motion.div
              key={flick}
              initial={{ opacity: 0.3, y: 4 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.12 }}
              className="mt-4 font-display text-3xl italic text-ink/35"
            >
              {flick}…
            </motion.div>
            <div className="mt-6 h-1 w-52 overflow-hidden bg-panel-2">
              <motion.div className={`h-full ${st.epic ? 'bg-amber' : 'bg-edge-bright'}`}
                initial={{ width: '0%' }} animate={{ width: '100%' }} transition={{ duration: st.scanMs / 1000, ease: 'easeOut' }} />
            </div>
          </div>
        </>
      ) : (
        <>
          {/* ambience: a colour bloom (pulsing for elite tiers) + a one-off shimmer sweep */}
          <motion.div className="pointer-events-none absolute inset-0" style={bloom(st.epic ? 0.24 : 0.1)}
            initial={{ opacity: 0, scale: 0.7 }}
            animate={st.epic ? { opacity: [0, 1, 0.78, 1], scale: 1 } : { opacity: 1, scale: 1 }}
            transition={st.epic ? { duration: 2.6, repeat: Infinity, repeatType: 'reverse' } : { duration: 0.7 }} />
          {st.epic && (
            <motion.div className="pointer-events-none absolute inset-0"
              style={{ background: 'linear-gradient(110deg, transparent 35%, rgba(255,255,255,0.07) 50%, transparent 65%)' }}
              initial={{ x: '-120%' }} animate={{ x: '120%' }} transition={{ duration: 1.1, delay: 0.15, ease: 'easeInOut' }} />
          )}

          <motion.div
            initial={{ scale: st.epic ? 0.4 : 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: st.epic ? 300 : 220, damping: st.epic ? 12 : 16 }}
            className="relative z-10 flex w-full max-w-md flex-col items-center"
          >
            <motion.div
              initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}
              className={`font-display text-3xl font-black leading-none ${st.text}`}
              style={st.epic ? { textShadow: `0 0 24px rgba(${st.rgb},0.45)` } : undefined}
            >
              <Stamp text={st.banner} tone={st.tone} className="text-sm" />
            </motion.div>
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.25 }}
              className={`mt-3 font-display text-[13px] italic ${st.epic ? st.text : 'text-ink/55'}`}>
              {st.tagline}
            </motion.div>

            <div className="eyebrow mt-5 text-ink/45">Choose your club</div>
            <div className="mt-2 w-full space-y-2">
              {spin.clubs.map((c, i) => (
                <motion.button
                  key={c.club + c.season}
                  initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 + i * 0.09 }}
                  onClick={() => onPick(c)}
                  className={`flex w-full items-center justify-between gap-3 border ${st.border} px-4 py-3 text-left transition hover:bg-panel-2`}
                >
                  <div className="min-w-0">
                    <div className={`truncate font-display text-xl font-semibold ${st.text}`}>{c.club}</div>
                    <div className="truncate text-[11px] text-ink/50">{c.season}{c.league ? ` · ${c.league}` : ''}</div>
                  </div>
                  <div className="shrink-0 text-right">
                    <div className={`font-display text-2xl font-bold leading-none tabular-nums ${st.text}`}>{c.strength}</div>
                    <div className="eyebrow text-ink/35">squad</div>
                  </div>
                </motion.button>
              ))}
            </div>
          </motion.div>
        </>
      )}
    </Panel>
  )
}
