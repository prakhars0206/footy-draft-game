import { useState } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import type { RunState, SeasonReplay, SeasonView } from './api'
import { SetupScreen } from './screens/SetupScreen'
import { DraftScreen } from './screens/DraftScreen'
import { PlaybackScreen } from './screens/PlaybackScreen'
import { ResultsScreen } from './screens/ResultsScreen'

type View = 'setup' | 'draft' | 'playback' | 'results'

export default function App() {
  const [view, setView] = useState<View>('setup')
  const [run, setRun] = useState<RunState | null>(null)
  const [replay, setReplay] = useState<SeasonReplay | null>(null)
  const [season, setSeason] = useState<SeasonView | null>(null)

  return (
    <div className="min-h-screen bg-terminal text-ink">
      <div className="mx-auto max-w-6xl px-4 py-6">
        <Header />
        <AnimatePresence mode="wait">
          {view === 'setup' && (
            <Fade key="setup">
              <SetupScreen
                onCreated={(rs) => {
                  setRun(rs)
                  setView('draft')
                }}
              />
            </Fade>
          )}
          {view === 'draft' && run && (
            <Fade key="draft">
              <DraftScreen
                run={run}
                onRun={setRun}
                onSimulated={(r) => {
                  setReplay(r)
                  setView('playback')
                }}
              />
            </Fade>
          )}
          {view === 'playback' && replay && (
            <Fade key="playback">
              <PlaybackScreen
                replay={replay}
                onFinish={() => {
                  setSeason(replay.debrief)
                  setView('results')
                }}
              />
            </Fade>
          )}
          {view === 'results' && season && (
            <Fade key="results">
              <ResultsScreen
                season={season}
                onNewRun={() => {
                  setRun(null)
                  setReplay(null)
                  setSeason(null)
                  setView('setup')
                }}
              />
            </Fade>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

function Header() {
  return (
    <header className="mb-7">
      <div className="flex items-end justify-between gap-4">
        <div>
          <div className="eyebrow text-ink/45">The Season Almanac · Est. 2026</div>
          <h1 className="font-display text-3xl font-black leading-none tracking-tight text-ink-bright sm:text-4xl">
            Footy <span className="text-amber">Draft</span>
          </h1>
        </div>
        <div className="hidden text-right sm:block">
          <div className="font-display text-sm italic text-ink/60">spin · draft · chase the invincible</div>
          <div className="eyebrow mt-1 text-ink/40">Vol. I — No. 1</div>
        </div>
      </div>
      <div className="mt-3 border-t-2 border-double border-edge-bright" />
    </header>
  )
}

function Fade({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.25 }}
    >
      {children}
    </motion.div>
  )
}
