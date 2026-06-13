import type { LineName, Rating } from './api'

/** Position → line (mirrors com.draft.footy.Line). */
const POS_LINE: Record<string, LineName> = {
  ST: 'ATT', CF: 'ATT', LW: 'ATT', RW: 'ATT', LF: 'ATT', RF: 'ATT',
  CAM: 'MID', CM: 'MID', CDM: 'MID', LM: 'MID', RM: 'MID',
  CB: 'DEF', LB: 'DEF', RB: 'DEF', LWB: 'DEF', RWB: 'DEF',
  GK: 'GK',
}
export const lineOf = (position: string): LineName => POS_LINE[position] ?? 'MID'

/** Literal Tailwind classes per line (kept literal so Tailwind keeps them in the build). */
export const lineClasses: Record<LineName, { text: string; border: string; dot: string; glow: string }> = {
  ATT: { text: 'text-att', border: 'border-att', dot: 'bg-att', glow: 'glow-att' },
  MID: { text: 'text-mid', border: 'border-mid', dot: 'bg-mid', glow: 'glow-mid' },
  DEF: { text: 'text-def', border: 'border-def', dot: 'bg-def', glow: 'glow-def' },
  GK: { text: 'text-gk', border: 'border-gk', dot: 'bg-gk', glow: 'glow-gk' },
}

export const LINE_LABEL: Record<LineName, string> = { ATT: 'ATTACK', MID: 'MIDFIELD', DEF: 'DEFENCE', GK: 'KEEPER' }

/** How to render a rating per Show-Ratings mode — the true overall is simply absent in SCOUT/OFF. */
export function renderRating(r: Rating | null): { kind: 'exact' | 'range' | 'hidden'; text: string } {
  if (!r || r.hidden) return { kind: 'hidden', text: '••' }
  if (r.overall != null) return { kind: 'exact', text: String(r.overall) }
  if (r.low != null && r.high != null) return { kind: 'range', text: `${r.low}–${r.high}` }
  return { kind: 'hidden', text: '••' }
}

export const FORMATIONS = ['4-3-3', '4-4-2', '4-2-3-1', '4-5-1', '3-4-3', '3-5-2', '5-4-1'] as const

export interface Pt { x: number; y: number } // pitch %; x left→right, y top(attack)→bottom(GK)

/** Pitch coordinates per formation, in the SAME slot order the backend emits (so slot.index maps directly). */
export const PITCH: Record<string, Pt[]> = {
  // LW,ST,RW, CM,CM,CDM, LB,CB,CB,RB, GK
  '4-3-3': [
    { x: 20, y: 20 }, { x: 50, y: 16 }, { x: 80, y: 20 },
    { x: 33, y: 49 }, { x: 67, y: 49 }, { x: 50, y: 62 },
    { x: 14, y: 76 }, { x: 38, y: 78 }, { x: 62, y: 78 }, { x: 86, y: 76 },
    { x: 50, y: 91 },
  ],
  // ST,ST, LM,CDM,CM,RM, LB,CB,CB,RB, GK
  '4-4-2': [
    { x: 38, y: 17 }, { x: 62, y: 17 },
    { x: 15, y: 50 }, { x: 40, y: 58 }, { x: 62, y: 50 }, { x: 85, y: 50 },
    { x: 14, y: 76 }, { x: 38, y: 78 }, { x: 62, y: 78 }, { x: 86, y: 76 },
    { x: 50, y: 91 },
  ],
  // ST,CAM,LM,RM,CDM,CDM, LB,CB,CB,RB, GK
  '4-2-3-1': [
    { x: 50, y: 15 }, { x: 50, y: 37 }, { x: 17, y: 41 }, { x: 83, y: 41 },
    { x: 38, y: 60 }, { x: 62, y: 60 },
    { x: 14, y: 76 }, { x: 38, y: 78 }, { x: 62, y: 78 }, { x: 86, y: 76 },
    { x: 50, y: 91 },
  ],
  // ST,CAM,LM,RM,CM,CDM, LB,CB,CB,RB, GK
  '4-5-1': [
    { x: 50, y: 15 }, { x: 50, y: 37 }, { x: 14, y: 50 }, { x: 86, y: 50 },
    { x: 37, y: 56 }, { x: 63, y: 58 },
    { x: 14, y: 76 }, { x: 38, y: 78 }, { x: 62, y: 78 }, { x: 86, y: 76 },
    { x: 50, y: 91 },
  ],
  // LW,ST,RW, LM,CM,CM,RM, CB,CB,CB, GK
  '3-4-3': [
    { x: 20, y: 20 }, { x: 50, y: 16 }, { x: 80, y: 20 },
    { x: 14, y: 52 }, { x: 39, y: 53 }, { x: 61, y: 53 }, { x: 86, y: 52 },
    { x: 28, y: 77 }, { x: 50, y: 78 }, { x: 72, y: 77 },
    { x: 50, y: 91 },
  ],
  // ST,ST, CAM,LM,RM,CM,CDM, CB,CB,CB, GK
  '3-5-2': [
    { x: 40, y: 17 }, { x: 60, y: 17 },
    { x: 50, y: 39 }, { x: 14, y: 52 }, { x: 86, y: 52 }, { x: 35, y: 57 }, { x: 63, y: 57 },
    { x: 28, y: 77 }, { x: 50, y: 78 }, { x: 72, y: 77 },
    { x: 50, y: 91 },
  ],
  // ST, LM,CM,CM,RM, LWB,CB,CB,CB,RWB, GK
  '5-4-1': [
    { x: 50, y: 17 },
    { x: 16, y: 50 }, { x: 40, y: 53 }, { x: 60, y: 53 }, { x: 84, y: 50 },
    { x: 9, y: 69 }, { x: 31, y: 78 }, { x: 50, y: 79 }, { x: 69, y: 78 }, { x: 91, y: 69 },
    { x: 50, y: 92 },
  ],
}
