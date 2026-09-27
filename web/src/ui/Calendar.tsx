import { useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { doseLine } from './Sheets'
import { dayTitle, heat, isoToday, piecesLabel, time } from './format'

export function Calendar() {
  const snap = S.snapshot.value!
  const today = isoToday()
  const [month, setMonth] = useState(today.slice(0, 7))
  const [open, setOpen] = useState<string | null>(null)
  const byDate = new Map(snap.days.map((d) => [d.date, d]))
  const first = snap.days[0]?.date
  const [y, m] = month.split('-').map(Number)
  const start = new Date(y, m - 1, 1)
  const lead = (start.getDay() + 6) % 7
  const len = new Date(y, m, 0).getDate()
  const cells = Array.from({ length: Math.ceil((lead + len) / 7) * 7 }, (_, i) => i - lead + 1)
  const iso = (d: number) => `${month}-${String(d).padStart(2, '0')}`
  const shift = (n: number) => { const d = new Date(y, m - 1 + n, 1); setMonth(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`) }

  if (open) return <DayView iso={open} back={() => setOpen(null)} />
  return (
    <main>
      <div class="row"><button class="btn text" onClick={() => shift(-1)}>‹</button>
        <h2 class="grow" style={{ textAlign: 'center' }}>{start.toLocaleDateString([], { month: 'long', year: 'numeric' })}</h2>
        <button class="btn text" disabled={month >= today.slice(0, 7)} onClick={() => shift(1)}>›</button></div>
      <div class="cal">{['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d) => <b class="muted" style={{ textAlign: 'center' }}>{d}</b>)}</div>
      <div class="cal">{cells.map((d) => {
        if (d < 1 || d > len) return <div />
        const key = iso(d), day = byDate.get(key)
        const tracked = first && key >= first && key <= today
        const base = first && key >= first && (new Date(key).getTime() - new Date(first).getTime()) / 864e5 < 7
        return <div class={`${key === today ? 'today' : ''} ${base ? 'base' : ''}`} style={{ background: tracked ? heat[day?.level ?? 0] : 'var(--surface)', opacity: day?.estimated ? 0.7 : 1, color: key > today ? 'var(--line)' : undefined }}
          onClick={() => key <= today && setOpen(key)}>{d}</div>
      })}</div>
      <div class="row small">Clear {heat.map((c) => <span style={{ width: 16, height: 16, background: c, borderRadius: 4, display: 'inline-block' }} />)} Heavy</div>
      <div class="muted">Colour = pieces that day: none, up to 1, 3, 5, 8, 12, more. A dot marks your baseline week; faded days are estimates.</div>
    </main>
  )
}

function DayView({ iso, back }: { iso: string; back: () => void }) {
  S.snapshot.value
  const detail = Core.day(S.json(), iso)
  const [adding, setAdding] = useState(false)
  return (
    <main>
      <div class="row"><button class="btn text" onClick={back}>‹ Back</button><h2>{dayTitle(iso)}</h2></div>
      <div class="list">
        {detail.doses.map((d) => <div class="item" onClick={() => confirm(`Delete ${d.name}?`) && S.remove(d.id)}><div>{d.name}<br /><span>{doseLine(d)}</span></div><b>{piecesLabel(d.pieces)}</b></div>)}
        {detail.cravings.map((c) => <div class="item" onClick={() => confirm('Delete this craving?') && S.remove(c.id)}><div>Craving · {c.intensity} {c.name}<br /><span>{time(c.at)} · {c.outcome === 'RODE_OUT' ? 'rode it out' : c.outcome === 'USED' ? 'used' : 'in progress'}</span></div></div>)}
        {!detail.doses.length && !detail.cravings.length && <div class="muted">Nothing logged this day.</div>}
      </div>
      {adding ? <div class="card soft"><b>Which product? (logged at noon)</b>
        {S.products.value.map((p) => <button class="btn outline" onClick={() => { S.logProduct(p, new Date(iso + 'T12:00').getTime()); setAdding(false) }}>{p.name}</button>)}</div>
        : <button class="btn outline" onClick={() => setAdding(true)}>Add a dose I forgot to log</button>}
    </main>
  )
}
