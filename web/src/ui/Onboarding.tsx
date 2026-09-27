import { useEffect, useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { dayTitle, isoToday } from './format'

export function Onboarding() {
  const [step, setStep] = useState(0)
  const [backfill, setBackfill] = useState(false)
  const [filling, setFilling] = useState(false)
  const s = S.settings.value
  const finish = () => S.updateSettings({ onboardingDone: true })
  if (filling) return <Backfill onDone={finish} onCancel={() => setFilling(false)} />
  return (
    <main style={{ minHeight: '90vh' }}>
      {step === 0 && <div style={{ textAlign: 'center', paddingTop: 40 }}>
        <img src={`${import.meta.env.BASE_URL}icon.svg`} width={120} alt="" />
        <h1>Firewatch</h1><div class="muted">by Baastik Labs</div>
        <p>Log a dose in two seconds. Watch the numbers come down.</p>
        <p class="muted">Everything is counted in pieces: what one 4 mg nicotine gum delivers into your blood. Your data stays on this device.</p>
        <p class="muted">On iPhone: tap Share → Add to Home Screen so Firewatch opens like an app and keeps your data.</p>
      </div>}
      {step === 1 && <>
        <h2>Your usual day</h2><div class="small">Firewatch works out your pace from the hours you're awake.</div>
        <div class="row"><span class="grow">I usually wake up at</span><input type="time" style={{ width: 130 }} value={`${String(Math.floor(s.wakeMinutes / 60)).padStart(2, '0')}:${String(s.wakeMinutes % 60).padStart(2, '0')}`}
          onChange={(e) => { const [h, m] = (e.target as HTMLInputElement).value.split(':').map(Number); S.updateSettings({ wakeMinutes: h * 60 + m }) }} /></div>
        <div class="row"><span class="grow">I usually go to bed at</span><input type="time" style={{ width: 130 }} value={`${String(Math.floor(s.sleepMinutes / 60) % 24).padStart(2, '0')}:${String(s.sleepMinutes % 60).padStart(2, '0')}`}
          onChange={(e) => { const [h, m] = (e.target as HTMLInputElement).value.split(':').map(Number); S.updateSettings({ sleepMinutes: h * 60 + m }) }} /></div>
      </>}
      {step === 2 && <>
        <h2>What do you use?</h2><div class="small">Ticked products get a big one-tap button.</div>
        {S.products.value.map((p) => <label class="row"><input type="checkbox" style={{ width: 'auto' }} checked={p.onHome} onChange={() => S.save('product', { ...p, onHome: !p.onHome })} />{p.name}</label>)}
      </>}
      {step === 3 && <>
        <h2>How do you want to start?</h2>
        {[[false, 'Establish a baseline', 'Just log as usual for 7 days. Your tier appears on day 8.'], [true, 'Estimate my last week', 'Go through the last 7 days and tap roughly what you used each day. No times needed. Your tier shows up straight away.']].map(([v, t, b]) =>
          <div class={`card ${backfill === v ? 'accent' : 'soft'}`} onClick={() => setBackfill(v as boolean)} style={{ cursor: 'pointer' }}><b>{t}</b><div class="small">{b}</div></div>)}
      </>}
      <div class="row" style={{ marginTop: 'auto' }}>
        {step > 0 && <button class="btn text" onClick={() => setStep(step - 1)}>Back</button>}
        <span class="grow" />
        <button class="btn" onClick={() => (step < 3 ? setStep(step + 1) : backfill ? setFilling(true) : finish())}>{step < 3 ? 'Next' : backfill ? 'Estimate my week' : 'Start'}</button>
      </div>
    </main>
  )
}

const VAPES: [string, string][] = [['FEW', "Friend's vape · a couple of hits"], ['SESSION', "Friend's vape · a proper session"], ['ALL_NIGHT', "Friend's vape · on and off all night"]]

/** The back-dated week: a page per day, tap each product once per use. */
export function Backfill({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const realFirst = S.live('dose').filter((d) => !d.estimated).map((d) => new Date(d.at).toISOString().slice(0, 10)).sort()[0]
  const days = Core.backfillDays(isoToday()).filter((d) => !realFirst || d < realFirst)
  const [i, setI] = useState(0)
  const [entries, setEntries] = useState<Record<string, { counts: Record<string, number>; vapes: Record<string, number> }>>({})
  useEffect(() => { if (!days.length) onDone() }, [])
  if (!days.length) return null
  const day = days[i]
  const e = entries[day] ?? { counts: {}, vapes: {} }
  const set = (next: typeof e) => setEntries({ ...entries, [day]: next })
  const tile = (label: string, n: number, inc: () => void, dec: () => void) => (
    <div class={`card ${n ? 'accent' : 'soft'}`} style={{ flexDirection: 'row', alignItems: 'center', cursor: 'pointer', userSelect: 'none' }} onClick={inc}>
      <b class="grow">{label}</b>{n > 0 && <button class="btn outline" onClick={(ev) => { ev.stopPropagation(); dec() }}>−</button>}<b style={{ fontSize: 20 }}>{n ? `× ${n}` : '+'}</b>
    </div>
  )
  return (
    <main>
      <div class="label">Estimate your last week</div>
      <h1>{dayTitle(day)}</h1>
      <div class="bar"><i style={{ width: `${((i + 1) / days.length) * 100}%` }} /></div>
      <div class="muted">Tap each thing you used that day, once per use. Rough is fine.</div>
      {S.products.value.map((p) => tile(p.name, e.counts[p.id] ?? 0,
        () => set({ ...e, counts: { ...e.counts, [p.id]: (e.counts[p.id] ?? 0) + 1 } }),
        () => set({ ...e, counts: { ...e.counts, [p.id]: Math.max(0, (e.counts[p.id] ?? 0) - 1) } })))}
      {VAPES.map(([k, l]) => tile(l, e.vapes[k] ?? 0,
        () => set({ ...e, vapes: { ...e.vapes, [k]: (e.vapes[k] ?? 0) + 1 } }),
        () => set({ ...e, vapes: { ...e.vapes, [k]: Math.max(0, (e.vapes[k] ?? 0) - 1) } })))}
      {i > 0 && <button class="btn text" onClick={() => set(entries[days[i - 1]] ?? { counts: {}, vapes: {} })}>Same as the day before</button>}
      <div class="row">
        <button class="btn text" onClick={() => (i === 0 ? onCancel() : setI(i - 1))}>{i === 0 ? 'Cancel' : 'Back'}</button>
        <span class="grow" /><span class="muted">{i + 1} of {days.length}</span><span class="grow" />
        <button class="btn" onClick={async () => {
          if (i < days.length - 1) return setI(i + 1)
          const doses = days.flatMap((d) => { const en = entries[d] ?? { counts: {}, vapes: {} }; return Core.backfillDay(S.json(), d, en.counts, en.vapes) })
          if (doses.length) await S.saveMany('dose', doses)
          onDone()
        }}>{i < days.length - 1 ? 'Next' : 'Finish'}</button>
      </div>
    </main>
  )
}
