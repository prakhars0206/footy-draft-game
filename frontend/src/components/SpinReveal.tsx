import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import type { SpinView } from '../api'
import { Panel, Stamp } from './primitives'

// Fake names that flicker past during the "locking on" scan — the suspense before you see what you landed on.
const FLICKER = [
  'Real Madrid', 'Bayern', 'Liverpool', 'Juventus', 'Ajax', 'Sevilla', 'Napoli', 'Lyon', 'Roma', 'Valencia',
  'Benfica', 'Dortmund', 'Inter', 'Milan', 'Arsenal', 'Chelsea', 'Porto', 'Lazio', 'Monaco', 'Leeds',
]

type Style = { text: string; border: string; banner: string; tone: 'amber' | 'phosphor' | 'danger'; epic: boolean }
const TIER_STYLE: Record<string, Style> = {
  JUGGERNAUT: { text: 'text-amber', border: 'border-amber', banner: 'Juggernaut', tone: 'amber', epic: true },
  'TITLE CONTENDER': { text: 'text-phosphor', border: 'border-phosphor', banner: 'Title Contender', tone: 'phosphor', epic: true },
  'EUROPEAN CHASER': { text: 'text-def', border: 'border-def', banner: 'European Chaser', tone: 'phosphor', epic: false },
  'MID-TABLE': { text: 'text-ink-bright', border: 'border-edge-bright', banner: 'Mid-Table', tone: 'amber', epic: false },
  'RELEGATION SCRAPPER': { text: 'text-danger', border: 'border-danger', banner: 'Relegation Scrapper', tone: 'danger', epic: false },
}

export function SpinReveal({ spin, onAccess }: { spin: SpinView; onAccess: () => void }) {
  const [phase, setPhase] = useState<'scan' | 'reveal'>('scan')
  const [flick, setFlick] = useState(FLICKER[0])
  const st = TIER_STYLE[spin.tier] ?? TIER_STYLE['MID-TABLE']
  const accent: 'edge' | 'amber' | 'phosphor' = st.epic && st.tone !== 'danger' ? st.tone : 'edge'

  useEffect(() => {
    const iv = setInterval(() => setFlick(FLICKER[Math.floor(Math.random() * FLICKER.length)]), 70)
    const to = setTimeout(() => { clearInterval(iv); setPhase('reveal') }, 1100)
    return () => { clearInterval(iv); clearTimeout(to) }
  }, [])

  return (
    <Panel
      accent={accent}
      className="flex min-h-0 flex-1 flex-col items-center justify-center p-6 text-center"
    >
      {phase === 'scan' ? (
        <>
          <div className="eyebrow text-ink/50">Drawing your club</div>
          <div className="mt-4 font-display text-3xl italic text-ink/35">{flick}…</div>
          <div className="mt-6 h-1 w-52 overflow-hidden bg-panel-2">
            <motion.div className="h-full w-1/3 bg-amber" animate={{ x: ['-120%', '360%'] }} transition={{ repeat: Infinity, duration: 0.6, ease: 'linear' }} />
          </div>
        </>
      ) : (
        <motion.div
          initial={{ scale: st.epic ? 0.4 : 0.85, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', stiffness: st.epic ? 320 : 220, damping: 14 }}
          className="flex flex-col items-center"
        >
          <Stamp text={st.banner} tone={st.tone} className="mb-3" />
          <motion.div
            className={`font-display text-4xl font-black leading-none ${st.text}`}
            animate={st.epic ? { scale: [1, 1.03, 1] } : {}}
            transition={{ repeat: Infinity, duration: 1.8 }}
          >
            {spin.club}
          </motion.div>
          <div className="mt-2 font-display text-sm italic text-ink/60">{spin.season}{spin.league ? ` · ${spin.league}` : ''}</div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="eyebrow text-ink/50">Squad Rating</span>
            <span className={`font-display text-3xl font-semibold tabular-nums ${st.text}`}>{spin.strength}</span>
          </div>
          {st.epic && <div className={`mt-2 font-display text-[13px] italic ${st.text}`}>an elite squad lands</div>}
          <motion.button
            initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.5 }}
            onClick={onAccess}
            className={`mt-6 border ${st.border} ${st.text} px-6 py-2.5 text-sm font-semibold uppercase tracking-[0.18em] transition hover:bg-panel-2`}
          >
            Access Squad →
          </motion.button>
        </motion.div>
      )}
    </Panel>
  )
}
