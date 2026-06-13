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
    <div className="scanlines crt min-h-screen bg-terminal text-ink">
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
    <header className="mb-6 flex items-center justify-between border-b border-edge pb-3">
      <div className="flex items-center gap-3">
        <span className="h-3 w-3 animate-pulse bg-phosphor" />
        <h1 className="text-lg font-extrabold tracking-[0.3em] text-ink-bright glow-phosphor">
          SCOUT<span className="text-amber">//</span>DRAFT
        </h1>
      </div>
      <span className="hidden text-[10px] tracking-mega text-ink/50 sm:block">
        CLASSIFIED · FIELD TERMINAL v2
      </span>
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
