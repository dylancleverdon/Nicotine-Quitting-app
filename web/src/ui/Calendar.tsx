import { Backfill } from './Onboarding'
import { useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { heatColor, onHeatColor } from '../theme'
import { EditDoseSheet, doseLine } from './Sheets'
import { dayTitle, isoToday, mg, pieces, piecesLabel, time } from './format'

export function Calendar({ toast }: { toast: (m: string, undo?: () => void) => void }) {
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

  if (open) return <DayView iso={open} back={() => setOpen(null)} toast={toast} />
  const states = snap.dayStates
  // This month's figures: "?" and ghost days left out; clear days count as 0.
  const monthDays = Array.from({ length: len }, (_, i) => iso(i + 1)).filter((k) => states[k] && states[k] !== 'unknown' && states[k] !== 'ghost' && k <= today)
  const monthPieces = monthDays.reduce((a, k) => a + (byDate.get(k)?.pieces ?? 0), 0)
  const clearDays = monthDays.filter((k) => states[k] === 'clear').length
  const lightest = monthDays.reduce<string | null>((a, k) => (a == null || (byDate.get(k)?.pieces ?? 0) < (byDate.get(a)?.pieces ?? 0) ? k : a), null)
  const anyUnknown = Array.from({ length: len }, (_, i) => iso(i + 1)).some((k) => states[k] === 'unknown')
  const modeDays = new Set(S.snapshot.value!.relapse.modeDays)
  return (
    <main>
      <div class="row"><button class="btn text" onClick={() => shift(-1)}>‹</button>
        <h2 class="grow" style={{ textAlign: 'center' }}>{start.toLocaleDateString([], { month: 'long', year: 'numeric' })}</h2>
        <button class="btn text" disabled={month >= today.slice(0, 7)} onClick={() => shift(1)}>›</button></div>
      <div class="cal">{['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d) => <b class="muted" style={{ textAlign: 'center' }}>{d}</b>)}</div>
      <div class="cal">{cells.map((d) => {
        if (d < 1 || d > len) return <div />
        const key = iso(d), day = byDate.get(key), st = states[key]
        const leftOut = st === 'unknown' || st === 'ghost'
        const tracked = first && key >= first && key <= today && !leftOut
        const base = first && key >= first && (new Date(key).getTime() - new Date(first).getTime()) / 864e5 < 7
        return <div class={`${key === today ? 'today' : ''} ${base ? 'base' : ''} ${modeDays.has(key) ? 'mode' : ''}`} style={{ background: tracked ? heatColor(day?.level ?? 0) : 'var(--surface)', opacity: day?.estimated ? 0.7 : 1, color: key > today ? 'var(--line)' : tracked ? onHeatColor(day?.level ?? 0) : undefined }}
          role="button" aria-label={`${dayTitle(key)}${st === 'unknown' ? ', nothing logged' : st === 'clear' ? ', clear day' : st === 'ghost' ? ', left out' : day ? `, about ${pieces(day.pieces)} pieces` : ''}`}
          onClick={() => key <= today && setOpen(key)}>{d}
          {st === 'unknown' && <span class="cal-badge">?</span>}{st === 'ghost' && <span class="cal-badge" style={{ opacity: 0.6 }}>👻</span>}{st === 'clear' && <span class="cal-badge">🌿</span>}
          {snap.levelMarks[key] && <span class="cal-mark">{snap.levelMarks[key] === 'down' ? '▼' : '▲'}</span>}</div>
      })}</div>
      <div class="row small">Clear {[0, 1, 2, 3, 4, 5, 6].map(heatColor).map((c) => <span style={{ width: 16, height: 16, background: c, borderRadius: 4, display: 'inline-block' }} />)} Heavy</div>
      {anyUnknown && <div class="small">{snap.unknownNote}</div>}
      <div class="muted">Colour = pieces that day: none, up to 1, 3, 5, 8, 12, more. 🌿 a clear day, ? nothing logged, 👻 left out. ▲ ▼ your level changed. A dot marks your baseline week; faded days are estimates; a ring marks Relapse prevention mode days.</div>
      {monthDays.length > 0 && <div class="card soft"><h3>This month</h3>
        <div class="stats"><div class="stat small"><b>≈ {pieces(monthPieces)}</b><span>pieces</span></div><div class="stat small"><b>≈ {pieces(monthPieces / monthDays.length)}</b><span>a day on average</span></div>
          <div class="stat small"><b>{clearDays}</b><span>{clearDays === 1 ? 'clear day' : 'clear days'}</span></div></div>
        {lightest && <div class="small">Lightest day: {dayTitle(lightest)} (≈ {pieces(byDate.get(lightest)?.pieces ?? 0)} pieces)</div>}</div>}
    </main>
  )
}

function DayView({ iso, back, toast }: { iso: string; back: () => void; toast: (m: string, undo?: () => void) => void }) {
  S.snapshot.value
  const detail = Core.day(S.json(), iso)
  const [adding, setAdding] = useState(false)
  const [filling, setFilling] = useState(false)
  const [editing, setEditing] = useState<string | null>(null)
  if (filling) return <Backfill only={[iso]} title="Add what you had" onDone={() => setFilling(false)} onCancel={() => setFilling(false)} />
  const st = detail.state
  return (
    <main>
      <div class="row"><button class="btn text" onClick={back}>‹ Back</button><h2>{dayTitle(iso)}</h2></div>
      <div class="stats"><div class="stat"><b>≈ {pieces(detail.pieces)}</b><span>pieces</span></div><div class="stat"><b>≈ {mg(detail.mg)}</b><span>absorbed</span></div>
        <div class="stat small"><b>{detail.doses.length}</b><span>doses</span></div><div class="stat small"><b>{detail.cravings.filter((c) => c.outcome === 'RODE_OUT').length} of {detail.cravings.length}</b><span>cravings ridden out</span></div></div>
      {detail.levels.map((l) => <div class="small" style={{ color: 'var(--primary)' }}>Level: {l}</div>)}
      <div class="list">
        {detail.doses.map((d) => <div class="item" onClick={() => setEditing(d.id)}><div>{d.name}<br /><span>{doseLine(d)}</span></div><b>{piecesLabel(d.pieces)}</b></div>)}
        {detail.cravings.map((c) => <div class="item" onClick={() => { if (confirm('Delete this craving?')) { S.remove(c.id); toast('Craving deleted', () => S.restore(c.id)) } }}><div>Craving · {c.intensity} {c.name}<br /><span>{time(c.at)} · {c.result.toLowerCase()}</span></div></div>)}
        {detail.sleeps.map((e) => <div class="item" onClick={() => { if (confirm(`Delete "${e.label}"?`)) { S.remove(e.extra); toast('Deleted', () => S.restore(e.extra)) } }}><div>{e.label}<br /><span>{time(e.value)}</span></div></div>)}
      </div>
      {!detail.doses.length && <div class="small">{st === 'clear' ? '🌿 A clear day: no nicotine.' : st === 'ghost' ? '👻 Left out of your figures.' : st === 'unknown' ? "? Nothing logged this day. It's left out of your figures until you say what happened." : 'Nothing logged this day.'}</div>}
      {!detail.doses.length && ['unknown', 'clear', 'ghost'].includes(st) && <div class="row wrap">
        {st !== 'clear' && <button class="btn outline" onClick={() => S.markDay(iso, 'clear')}>🌿 I had none</button>}
        {st !== 'ghost' && <button class="btn outline" onClick={() => S.markDay(iso, 'ghost')}>👻 Don't log this day</button>}
        {st !== 'unknown' && <button class="btn text" onClick={() => S.markDay(iso, null)}>Undo: back to ?</button>}
      </div>}
      {editing && <EditDoseSheet id={editing} onClose={() => setEditing(null)} toast={toast} />}
      <button class="btn" onClick={() => setFilling(true)}>Add what you had (no times needed)</button>
      {adding ? <div class="card soft"><b>Which product? Pick the time after.</b>
        {S.products.value.map((p) => <button class="btn outline" onClick={() => {
          const t = prompt('What time? (e.g. 14:30)', '12:00') ?? '12:00'
          const [h, m] = t.split(':').map(Number)
          S.logProduct(p, new Date(`${iso}T${String(h || 12).padStart(2, '0')}:${String(m || 0).padStart(2, '0')}`).getTime()); setAdding(false)
        }}>{p.name}</button>)}</div>
        : <button class="btn text" onClick={() => setAdding(true)}>Add one at an exact time</button>}
    </main>
  )
}
