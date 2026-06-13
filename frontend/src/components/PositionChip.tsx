import { lineClasses, lineOf } from '../theme'

/** A small position tag, coloured by its line (ATT/MID/DEF/GK). */
export function PositionChip({ position, dim = false }: { position: string; dim?: boolean }) {
  const lc = lineClasses[lineOf(position)]
  return (
    <span
      className={`inline-flex items-center gap-1 border ${lc.border} ${lc.text} px-1.5 py-0.5 text-[10px] font-bold tracking-wider ${dim ? 'opacity-40' : ''}`}
    >
      <span className={`h-1.5 w-1.5 ${lc.dot}`} />
      {position}
    </span>
  )
}
