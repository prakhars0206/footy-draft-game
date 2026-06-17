import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import type { SpinView, SpinClub } from '../api'
import { Panel, Stamp } from './primitives'

type Style = {
  text: string; border: string; banner: string; tone: 'amber' | 'phosphor' | 'danger'
  epic: boolean; rgb: string; scanMs: number; tagline: string
}
const TIER_STYLE: Record<string, Style> = {
  ICONIC:   { text: 'text-amber',      border: 'border-amber',       banner: 'Iconic',   tone: 'amber',    epic: true,  rgb: '200,151,63',  scanMs: 2700, tagline: 'a colossus of the game' },
  ELITE:    { text: 'text-phosphor',   border: 'border-phosphor',    banner: 'Elite',    tone: 'phosphor', epic: true,  rgb: '111,158,126', scanMs: 2300, tagline: 'a genuine title threat' },
  PEDIGREE: { text: 'text-def',        border: 'border-def',         banner: 'Pedigree', tone: 'phosphor', epic: false, rgb: '91,134,179',  scanMs: 1800, tagline: 'European nights beckon' },
  STEADY:   { text: 'text-ink-bright', border: 'border-edge-bright', banner: 'Steady',   tone: 'amber',    epic: false, rgb: '239,231,214', scanMs: 1500, tagline: 'a solid, honest squad' },
  MINNOW:   { text: 'text-danger',     border: 'border-danger',      banner: 'Minnow',   tone: 'danger',   epic: false, rgb: '178,74,64',   scanMs: 1400, tagline: 'an underdog’s scrap' },
}
const REEL = ['ICONIC', 'ELITE', 'PEDIGREE', 'STEADY', 'MINNOW'] // the wheel the spin ticks through

export function SpinReveal({ spin, onPick }: { spin: SpinView; onPick: (club: SpinClub) => void }) {
  const [phase, setPhase] = useState<'scan' | 'reveal'>('scan')
  const [reel, setReel] = useState(0)
  const st = TIER_STYLE[spin.tier] ?? TIER_STYLE.STEADY
  const accent: 'edge' | 'amber' | 'phosphor' = st.epic && st.tone !== 'danger' ? st.tone : 'edge'

  // The wheel: tier names tick past, decelerating (slot-machine), then settle on the tier you landed.
  useEffect(() => {
    const target = Math.max(0, REEL.indexOf(spin.tier))
    const timers: ReturnType<typeof setTimeout>[] = []
    let alive = true, elapsed = 0, delay = 60, i = 0
    const tick = () => {
      if (!alive) return
      setReel(i % REEL.length)
      elapsed += delay
      if (elapsed / st.scanMs > 0.5) delay += st.epic ? 48 : 30 // ease off near the end
      if (elapsed >= st.scanMs) {
        setReel(target)                                          // land on the real tier, hold, then reveal
        timers.push(setTimeout(() => { if (alive) setPhase('reveal') }, 320))
        return
      }
      i++
      timers.push(setTimeout(tick, delay))
    }
    timers.push(setTimeout(tick, delay))
    return () => { alive = false; timers.forEach(clearTimeout) }
  }, [st, spin.tier])

  const bloom = (a: number) => ({ background: `radial-gradient(circle at 50% 42%, rgba(${st.rgb},${a}), transparent 62%)` })
  const reelSt = TIER_STYLE[REEL[reel]] ?? TIER_STYLE.STEADY

  return (
    <Panel accent={accent} className="relative flex min-h-0 flex-1 flex-col items-center justify-center overflow-hidden p-6 text-center">
      {phase === 'scan' ? (
        <div className="relative z-10 flex flex-col items-center">
          <div className="eyebrow text-ink/45">Spinning the wheel</div>
          {/* the reel window — tier names tick through, framed by hairline rails */}
          <div className="relative mt-5 h-16 w-72 overflow-hidden">
            <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-edge-bright" />
            <div className="pointer-events-none absolute inset-x-6 bottom-0 h-px bg-edge-bright" />
            <motion.div
              key={reel}
              initial={{ y: 30, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ duration: 0.11 }}
              className={`absolute inset-0 flex items-center justify-center font-display text-4xl font-black tracking-tight ${reelSt.text}`}
            >
              {reelSt.banner}
            </motion.div>
          </div>
          <div className="eyebrow mt-5 text-ink/30">which tier will you land?</div>
        </div>
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
              style={st.epic ? { textShadow: `0 0 24px rgba(${st.rgb},0.45)` } : undefined}
            >
              <Stamp text={st.banner} tone={st.tone} className="text-sm" />
            </motion.div>
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.25 }}
              className={`mt-3 font-display text-[13px] italic ${st.epic ? st.text : 'text-ink/55'}`}>
              {st.tagline}
            </motion.div>

            <div className="eyebrow mt-5 text-ink/45">Choose your club · this is final</div>
            <div className="mt-3 flex w-full flex-col gap-2.5">
              {spin.clubs.map((c, i) => (
                <motion.button
                  key={c.club + c.season}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: 0.12 + i * 0.08, duration: 0.32, ease: [0.33, 1, 0.68, 1] }}
                  whileHover={{ x: 3 }}
                  onClick={() => onPick(c)}
                  className={`group relative flex w-full items-center justify-between gap-3 overflow-hidden border ${st.border} bg-panel/60 px-4 py-3.5 text-left transition hover:bg-panel-2`}
                >
                  {/* a quiet tier-coloured edge */}
                  <span className="absolute inset-y-0 left-0 w-[3px]" style={{ background: `rgba(${st.rgb},0.7)` }} />
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
