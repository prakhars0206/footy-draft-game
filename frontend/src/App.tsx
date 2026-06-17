import { useState } from 'react'
import type { ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import type { RunState, SeasonReplay, SeasonView } from './api'
import type { SpineGame } from './season'
import { userSpine } from './season'
import { SetupScreen } from './screens/SetupScreen'
import { DraftScreen } from './screens/DraftScreen'
import { PlaybackScreen } from './screens/PlaybackScreen'
import { ResultsScreen } from './screens/ResultsScreen'
import { ExploreScreen } from './screens/ExploreScreen'

type View = 'setup' | 'draft' | 'playback' | 'results' | 'explore'

export default function App() {
  const [view, setView] = useState<View>('setup')
  const [run, setRun] = useState<RunState | null>(null)
  const [replay, setReplay] = useState<SeasonReplay | null>(null)
  const [season, setSeason] = useState<SeasonView | null>(null)
  const [spine, setSpine] = useState<SpineGame[] | null>(null)
  const [pundit, setPundit] = useState<{ correct: number; total: number } | null>(null)

  return (
    <div className="min-h-screen bg-terminal text-ink">
      <div className="mx-auto max-w-6xl px-4 py-6">
        <Header view={view} />
        <AnimatePresence mode="wait">
          {view === 'setup' && (
            <Page key="setup">
              <SetupScreen
                onCreated={(rs) => {
                  setRun(rs)
                  setView('draft')
                }}
                onExplore={() => setView('explore')}
              />
            </Page>
          )}
          {view === 'explore' && (
            <Page key="explore">
              <ExploreScreen onBack={() => setView('setup')} />
            </Page>
          )}
          {view === 'draft' && run && (
            <Page key="draft">
              <DraftScreen
                run={run}
                onRun={setRun}
                onSimulated={(r) => {
                  setReplay(r)
                  setView('playback')
                }}
              />
            </Page>
          )}
          {view === 'playback' && replay && (
            <Page key="playback">
              <PlaybackScreen
                replay={replay}
                onFinish={(p) => {
                  setPundit(p.total > 0 ? p : null)
                  setSpine(userSpine(replay))
                  setSeason(replay.debrief)
                  setView('results')
                }}
              />
            </Page>
          )}
          {view === 'results' && season && (
            <Page key="results">
              <ResultsScreen
                season={season}
                pundit={pundit}
                spine={spine ?? undefined}
                onNewRun={() => {
                  setRun(null)
                  setReplay(null)
                  setSeason(null)
                  setSpine(null)
                  setPundit(null)
                  setView('setup')
                }}
              />
            </Page>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}

const DATELINE: Record<View, { tag: string; issue: string }> = {
  setup: { tag: 'spin · draft · chase the invincible', issue: 'No. I — The Draft Room' },
  explore: { tag: 'every top-5 squad, on record', issue: 'Reference Section' },
  draft: { tag: 'assembling the eleven', issue: 'No. II — Team Selection' },
  playback: { tag: 'the season, matchday by matchday', issue: 'Live — Matchday Report' },
  results: { tag: 'the verdict is in', issue: 'Final Edition — The Debrief' },
}

function Header({ view }: { view: View }) {
  const d = DATELINE[view]
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
          <AnimatePresence mode="wait">
            <motion.div
              key={view}
              initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -4 }} transition={{ duration: 0.3 }}
            >
              <div className="font-display text-sm italic text-ink/60">{d.tag}</div>
              <div className="eyebrow mt-1 text-ink/40">{d.issue}</div>
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
      <div className="mt-3 border-t-2 border-double border-edge-bright" />
    </header>
  )
}

/**
 * A page-turn between screens: the next leaf slides in from the right as the last slips away left. Deliberately
 * transform-free at rest (no `perspective`/3D ancestor) so `position: fixed` modals inside a screen still anchor
 * to the viewport.
 */
function Page({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, x: 34 }}
      animate={{ opacity: 1, x: 0 }}
      exit={{ opacity: 0, x: -26 }}
      transition={{ duration: 0.34, ease: [0.33, 1, 0.68, 1] }}
    >
      {children}
    </motion.div>
  )
}
