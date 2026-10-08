import { useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { TAGS, cravingColor, pieces, time } from './format'

export function Sheet({ onClose, children }: { onClose: () => void; children: any }) {
  return (
    <div class="sheet-bg" onClick={onClose}>
      <div class="sheet" onClick={(e) => e.stopPropagation()}>{children}</div>
    </div>
  )
}

const OFFSETS: [number, string][] = [[0, 'Now'], [5, '5 min ago'], [10, '10 min ago'], [20, '20 min ago'], [30, '30 min ago'], [60, '1 hour ago'], [120, '2 hours ago'], [180, '3 hours ago']]
const AMOUNTS: [number, string][] = [[0.5, '½'], [1, '1'], [1.5, '1½'], [2, '2']]
const ORAL = ['GUM', 'POUCH', 'LOZENGE']

/** Long-press options: when, how much, how long it stayed in, coffee/soda, what was going on. */
export function DoseSheet({ product, onClose, onLog }: { product: any; onClose: () => void; onLog: (at: number, opts: any) => void }) {
  const [offset, setOffset] = useState(0)
  const [custom, setCustom] = useState('')
  const [mult, setMult] = useState(1)
  const [duration, setDuration] = useState('FULL')
  const [outAt, setOutAt] = useState('')
  const [acidic, setAcidic] = useState(false)
  const [tags, setTags] = useState<string[]>([])
  const at = () => {
    if (custom) {
      const [h, m] = custom.split(':').map(Number)
      const d = new Date(); d.setHours(h, m, 0, 0)
      if (d.getTime() > Date.now()) d.setDate(d.getDate() - 1)
      return d.getTime()
    }
    return Date.now() - offset * 60000
  }
  return (
    <Sheet onClose={onClose}>
      <h2>{product.name}</h2>
      <h3>When</h3>
      <div class="row wrap">
        {OFFSETS.map(([m, l]) => <button class={`chip ${!custom && offset === m ? 'on' : ''}`} onClick={() => { setCustom(''); setOffset(m) }}>{l}</button>)}
        <input type="time" value={custom} onInput={(e) => setCustom((e.target as HTMLInputElement).value)} style={{ width: 130 }} />
      </div>
      <h3>How much</h3>
      <div class="row wrap">{AMOUNTS.map(([v, l]) => <button class={`chip ${mult === v ? 'on' : ''}`} onClick={() => setMult(v)}>{l}</button>)}</div>
      {ORAL.includes(product.kind) && <>
        <h3>How long it stayed in</h3>
        <div class="row wrap">{[['FULL', 'Full', '30+ min'], ['HALF', 'About half', '~15 min'], ['QUICK', 'Quick', '~5 min']].map(([v, l, m]) => <button class={`chip ${duration === v && !outAt ? 'on' : ''}`} onClick={() => { setDuration(v); setOutAt('') }}>{l} <span class="small muted">{m}</span></button>)}</div>
      </>}
      {product.kind === 'POUCH' && <label class="row small">Took it out at <input type="time" value={outAt} onInput={(e) => setOutAt((e.target as HTMLInputElement).value)} style={{ width: 130 }} /></label>}
      {product.kind === 'GUM' && <label class="row"><input type="checkbox" checked={acidic} onChange={() => setAcidic(!acidic)} style={{ width: 'auto' }} /> Coffee or soda around it (cuts absorption)</label>}
      <h3>What was going on (optional)</h3>
      <div class="row wrap">{TAGS.map((t) => <button class={`chip ${tags.includes(t) ? 'on' : ''}`} onClick={() => setTags(tags.includes(t) ? tags.filter((x) => x !== t) : [...tags, t])}>{t}</button>)}</div>
      <button class="btn" onClick={() => { const t = at(); onLog(t, { multiplier: mult, duration, acidic, tags, removedAt: outTime(t, outAt) }) }}>Log it</button>
    </Sheet>
  )
}

/** One tap logs a craving at the level that matches how it feels. */
export function CravingSheet({ onClose, onPick }: { onClose: () => void; onPick: (level: number) => void }) {
  const levels = Core.cravingScale()
  return (
    <Sheet onClose={onClose}>
      <h2>How strong is the craving?</h2>
      <p class="muted">Tap the one that fits right now. This isn't a dose; it's an urge you're riding out.</p>
      {levels.map((l) => (
        <div class="level" onClick={() => onPick(l.value)}>
          <div class="dot" style={{ background: cravingColor(l.value) }}>{l.value}</div>
          <div><b>{l.label}</b><div class="muted">{l.extra}</div></div>
        </div>
      ))}
    </Sheet>
  )
}

const STRENGTHS: [number | null, string][] = [[null, 'No idea'], [20, '2% · 20 mg'], [30, '3% · 30 mg'], [50, '5% · 50 mg']]
const VAPE_AMOUNTS: [string, string, number, number][] = [['FEW', 'A couple of hits', 2, 5], ['SESSION', 'A proper session', 10, 20], ['ALL_NIGHT', 'On and off all night', 30, 80]]

/** A friend's vape in three taps, logged as a range. */
export function VapeSheet({ onClose, onLog, preset }: { onClose: () => void; onLog: (dose: any, saveAs: string | null, strength: number | null) => void; preset?: any }) {
  const [strength, setStrength] = useState<number | null | undefined>(preset ? preset.labelMg : undefined)
  const [amount, setAmount] = useState<number | null>(null)
  const [puffs, setPuffs] = useState(0)
  const [save, setSave] = useState(false)
  const [name, setName] = useState('')
  const range = puffs > 0 ? [puffs, puffs] : amount != null ? [VAPE_AMOUNTS[amount][2], VAPE_AMOUNTS[amount][3]] : null
  const ready = range && strength !== undefined
  return (
    <Sheet onClose={onClose}>
      <h2>{preset?.name ?? "Friend's vape"}</h2>
      <p class="muted">Unknown doses are logged as a range. Timing uses the top of the range, so it never says you're clear too soon.</p>
      {!preset && <>
        <h3>1. Strength</h3>
        <div class="row wrap">{STRENGTHS.map(([v, l]) => <button class={`chip ${strength === v ? 'on' : ''}`} onClick={() => setStrength(v)}>{l}</button>)}</div>
      </>}
      <h3>2. How much</h3>
      <div class="row wrap">{VAPE_AMOUNTS.map((a, i) => <button class={`chip ${puffs === 0 && amount === i ? 'on' : ''}`} onClick={() => { setAmount(i); setPuffs(0) }}>{a[1]}</button>)}</div>
      <div class="row">Or count puffs: <button class="btn outline" onClick={() => setPuffs(Math.max(0, puffs - 1))}>−</button><b>{puffs}</b><button class="btn outline" onClick={() => { setPuffs(puffs + 1); setAmount(null) }}>+1 puff</button></div>
      {!preset && strength != null && <label class="row"><input type="checkbox" checked={save} onChange={() => setSave(!save)} style={{ width: 'auto' }} /> Save it for next time</label>}
      {save && <input placeholder="Friend's name" value={name} onInput={(e) => setName((e.target as HTMLInputElement).value)} />}
      <button class="btn" disabled={!ready} onClick={() => {
        const what = puffs > 0 ? `${puffs} puffs` : VAPE_AMOUNTS[amount!][1].toLowerCase()
        const label = (save && name ? `${name}'s vape` : preset?.name ?? "Friend's vape") + ` (${what})`
        onLog(Core.vapeDose(Date.now(), strength ?? null, range![0], range![1], label), save && name ? name.trim() : null, strength ?? null)
      }}>Log it</button>
    </Sheet>
  )
}

export function CheckInSheet({ onClose, onSave }: { onClose: () => void; onSave: (c: number, m: number, s: number) => void }) {
  const [c, setC] = useState(3), [m, setM] = useState(3), [s, setS] = useState(3)
  const row = (label: string, opts: string[], v: number, set: (n: number) => void) => (
    <div><h3>{label}</h3><div class="row wrap">{opts.map((o, i) => <button class={`chip ${v === i + 1 ? 'on' : ''}`} onClick={() => set(i + 1)}>{o}</button>)}</div></div>
  )
  return (
    <Sheet onClose={onClose}>
      <h2>Daily check-in</h2>
      {row('Cravings today', ['None', 'Mild', 'Some', 'Strong', 'Intense'], c, setC)}
      {row('Mood', ['Awful', 'Low', 'OK', 'Good', 'Great'], m, setM)}
      {row("Last night's sleep", ['Awful', 'Poor', 'OK', 'Good', 'Great'], s, setS)}
      <button class="btn" onClick={() => onSave(c, m, s)}>Save</button>
    </Sheet>
  )
}

/** "Took it out at" (HH:MM) → a time after [at] (just after midnight if earlier); '' = not set. */
export function outTime(at: number, hm: string): number | null {
  if (!hm) return null
  const [h, m] = hm.split(':').map(Number)
  const d = new Date(at); d.setHours(h, m, 0, 0)
  if (d.getTime() <= at) d.setDate(d.getDate() + 1)
  return d.getTime()
}
const hhmm = (t: number) => { const d = new Date(t); return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

export const doseLine = (d: any) => `${time(d.at)} · ≈ ${pieces(d.pieces)} pc${d.removedAt && d.removedAt > d.at ? ` · in for ${Math.round((d.removedAt - d.at) / 60000)} min` : ''}${d.estimated ? ' · estimated' : ''}${d.tags?.length ? ' · ' + d.tags.join(', ').toLowerCase() : ''}`

const pad = (n: number) => String(n).padStart(2, '0')
const localInput = (t: number) => { const d = new Date(t); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}` }

/** Change a logged dose's time or amount, or delete it (with Undo). */
export function EditDoseSheet({ id, onClose, toast }: { id: string; onClose: () => void; toast: (m: string, undo?: () => void) => void }) {
  const raw = S.live('dose').find((d) => d.id === id)
  const [when, setWhen] = useState(raw ? localInput(raw.at) : '')
  const [mult, setMult] = useState(raw?.multiplier ?? 1)
  const [duration, setDuration] = useState(raw?.duration ?? 'FULL')
  const [outAt, setOutAt] = useState(raw?.removedAt ? hhmm(raw.removedAt) : '')
  if (!raw) return null
  return (
    <Sheet onClose={onClose}>
      <h2>{raw.productName || 'Dose'}</h2>
      <h3>When</h3>
      <input type="datetime-local" value={when} max={localInput(Date.now())} onInput={(e) => setWhen((e.target as HTMLInputElement).value)} />
      {!raw.rangeLowMg && <><h3>How much</h3>
        <div class="row wrap">{AMOUNTS.map(([v, l]) => <button class={`chip ${mult === v ? 'on' : ''}`} onClick={() => setMult(v)}>{l}</button>)}</div></>}
      {ORAL.includes(raw.kind) && <>
        <h3>How long it stayed in</h3>
        <div class="row wrap">{[['FULL', 'Full', '30+ min'], ['HALF', 'About half', '~15 min'], ['QUICK', 'Quick', '~5 min']].map(([v, l, m]) => <button class={`chip ${duration === v && !outAt ? 'on' : ''}`} onClick={() => { setDuration(v); setOutAt('') }}>{l} <span class="small muted">{m}</span></button>)}</div>
        {raw.kind === 'POUCH' && <label class="row small">Took it out at <input type="time" value={outAt} onInput={(e) => setOutAt((e.target as HTMLInputElement).value)} style={{ width: 130 }} /></label>}
      </>}
      <div class="row"><button class="btn" disabled={!when} onClick={async () => {
        const t = new Date(when).getTime()
        await S.save('dose', { ...raw, at: t, multiplier: mult, duration, removedAt: raw.kind === 'POUCH' ? outTime(t, outAt) : raw.removedAt ?? null }); onClose(); toast('Saved')
      }}>Save</button>
        <button class="btn text" onClick={async () => { onClose(); await S.remove(raw.id); toast('Dose deleted', () => S.restore(raw.id)) }}>Delete</button></div>
    </Sheet>
  )
}

/** After accepting a practice offer: how long practice pace should run. */
export function PracticeDurationSheet({ rung, initial, stopNote, onStart, onClose }: { rung: any; initial: boolean; stopNote: string; onStart: (untilBedtime: boolean) => void; onClose: () => void }) {
  const [untilBedtime, setUntilBedtime] = useState(initial)
  return (
    <Sheet onClose={onClose}>
      <h2>How long should practice pace run?</h2>
      <div class="small"><b>{rung.tier} pace</b></div>
      <DurationChoice value={untilBedtime} onChange={setUntilBedtime} />
      <div class="muted">{stopNote}</div>
      <div class="row"><button class="btn" onClick={() => onStart(untilBedtime)}>Start</button><button class="btn outline" onClick={onClose}>Cancel</button></div>
    </Sheet>
  )
}

export function DurationChoice({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return <div>{([[true, 'Turn off at bedtime (just today)'], [false, 'Leave it on until I turn it off']] as [boolean, string][]).map(([v, l]) =>
    <label class="row small"><input type="radio" name="practice-duration" style={{ width: 'auto' }} checked={value === v} onChange={() => onChange(v)} /> {l}</label>)}</div>
}
