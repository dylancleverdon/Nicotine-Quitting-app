import { useRef, useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { Meter, Wave } from './Charts'
import { CheckInSheet, CravingSheet, DoseSheet, VapeSheet, doseLine } from './Sheets'
import { TAGS, cravingColor, duration, isoToday, mg, pieces, piecesLabel, signedDuration, time } from './format'

export function Home({ toast, go }: { toast: (msg: string, undo?: () => void) => void; go: (r: string) => void }) {
  const snap = S.snapshot.value!
  const settings = S.settings.value
  const [options, setOptions] = useState<any>(null)
  const [craving, setCraving] = useState(false)
  const [vape, setVape] = useState<any>(false)
  const [checkIn, setCheckIn] = useState(false)
  const [celebrate, setCelebrate] = useState<any>(null)
  const press = useRef<number | null>(null)
  const longFired = useRef(false)

  const logProduct = async (p: any, at?: number, opts?: any) => {
    const d = await S.logProduct(p, at, opts)
    const waited = Core.waitedForFull(S.json(), d.id)
    toast(waited ? `Logged ${p.name}. You waited for a full battery. Nice work!` : `Logged ${p.name}`, () => S.remove(d.id))
  }
  const tap = (p: any) => {
    if (p.kind === 'VAPE' && p.borrowedFrom) return setVape(p)
    logProduct(p)
  }
  const down = (p: any) => {
    longFired.current = false
    press.current = window.setTimeout(() => { longFired.current = true; setOptions(p) }, 500)
  }
  const up = (p: any) => {
    if (press.current) clearTimeout(press.current)
    if (!longFired.current) tap(p)
  }
  const moveTarget = async (pieces: number, reason: string) => {
    await S.setTarget(pieces, reason)
    const rung = snap.ladder.find((r) => Math.abs(r.pieces - pieces) < 1e-6)
    if (reason === 'down') setCelebrate(rung)
    else toast(reason === 'up' ? `Stepped back to ${rung?.label}. That's normal.` : `Starting at ${rung?.label}.`)
  }
  const checkedIn = S.live('checkin').some((c) => new Date(c.at).toDateString() === new Date().toDateString())
  const battery = snap.battery

  return (
    <main>
      <h1 class="brand">Firewatch<small>by Baastik Labs</small></h1>
      <div class="card">
        {snap.revealed ? <>
          {(snap.target ?? snap.measured) && <>
            <p class="tier">{(snap.target ?? snap.measured)!.tier}</p>
            <div class="small">{(snap.target ?? snap.measured)!.plain}</div>
            {snap.target && snap.measured && snap.measured.pieces !== snap.target.pieces &&
              <div class="muted">Working at {snap.target.label}. Your last 7 days measure {snap.measured.label}.</div>}
          </>}
          {battery && <div>
            <b>{battery.state === 'CLEAR' ? 'Clear for one if you want it' : battery.state === 'CHARGING' ? `Next piece around ${battery.readyAt ? time(battery.readyAt) : 'later'}` :
              battery.state === 'FULL_AT_WAKE' ? 'Full when you wake up' : battery.state === 'MORNING_DELAY' ? `First piece goal: ${battery.readyAt ? time(battery.readyAt) : ''}` :
              battery.state === 'WIND_DOWN' ? 'Winding down for bed' : 'Sleeping hours · Fresh start when you wake up'}</b>
            <Meter value={battery.charge} />
            {battery.fitsNow && <div class="small muted">A {battery.fitsNow} fits now</div>}
            <div class="small stretch" style={{ color: battery.stretchMin - battery.pullMin >= 0 ? 'var(--tertiary)' : 'var(--muted)' }}>
              Stretch {duration(battery.stretchMin * 60000)} · Pull {duration(battery.pullMin * 60000)} · Net {signedDuration(battery.stretchMin - battery.pullMin)}</div>
          </div>}
        </> : <>
          <div class="label">{snap.baselineState === 'progress' ? `Baseline week · day ${snap.baselineDay} of 7` : 'Baseline week'}</div>
          {snap.baselineState === 'progress' && <Meter value={snap.baselineDay / 7} />}
          <div class="small">{snap.baselineState === 'none' ? 'Starts with your first log. For 7 days Firewatch just watches, then it shows your starting tier.' : 'Just log as usual. Your starting tier appears after day 7.'}</div>
          <button class="btn outline" onClick={() => go('backfill')}>Estimate my last week instead</button>
        </>}
        <div class="stats">
          <div class="stat"><b>≈ {pieces(snap.todayPieces)}</b><span>pieces today</span></div>
          <div class="stat"><b>≈ {mg(snap.todayMg)}</b><span>absorbed today</span></div>
        </div>
        <div class="stats">
          <div class="stat small"><b>{snap.todayDoses.length}</b><span>doses</span></div>
          <div class="stat small"><b>{snap.lastDoseAt ? duration(Date.now() - snap.lastDoseAt) : '–'}</b><span>since last</span></div>
          <div class="stat small"><b>{snap.todayRodeOut}/{snap.todayCravings}</b><span>urges beaten</span></div>
          {snap.qualityLabel && <div class="stat small"><b>{snap.qualityLabel.split(' · ')[0].split(' ')[0]} {Math.round(snap.qualityScore!)}</b><span>quality</span></div>}
        </div>
        {snap.revealed && snap.wave.length > 2 && <Wave points={snap.wave} height={64} now={Date.now()} shade={[[snap.wave[0][0], snap.wakeAt], [snap.sleepAt, snap.wave[snap.wave.length - 1][0]]]} />}
      </div>

      {snap.revealed && !snap.target && snap.measured && <div class="card accent">
        <h2>Your starting point: {snap.measured.tier}</h2>
        <div class="small">{snap.measured.plain} Work from here?</div>
        <div><button class="btn" onClick={() => moveTarget(snap.measured!.pieces, 'start')}>Start here</button></div>
      </div>}
      {snap.stepDown && snap.target && <div class="card accent">
        <h2>Ready for {snap.stepDown.label}?</h2>
        <div class="small">You've held {snap.target.label} for {settings.holdDays} days. {snap.stepDownNote}</div>
        <div class="row"><button class="btn" onClick={() => moveTarget(snap.stepDown!.pieces, 'down')}>Step down</button>
          <button class="btn outline" onClick={() => S.updateSettings({ stepDownSnoozedAt: Date.now() })}>Not yet</button></div>
      </div>}
      {snap.stepUp && snap.target && <div class="card accent">
        <h2>This rung is tough right now</h2>
        <div class="small">Stepping up to {snap.stepUp.label} for a while is normal and keeps you on gum rather than something worse.</div>
        <div class="row"><button class="btn" onClick={() => moveTarget(snap.stepUp!.pieces, 'up')}>Step up</button>
          <button class="btn outline" onClick={() => S.updateSettings({ stepUpSnoozedAt: Date.now() })}>I'm OK</button></div>
      </div>}
      {snap.headsUps.length > 0 && <div class="card soft"><b>Heads-up</b>{snap.headsUps.map((h) => <div class="small">{h}</div>)}</div>}
      {snap.swapTip && <div class="muted">{snap.swapTip}</div>}
      {settings.dailyCheckIn && !checkedIn && <button class="btn outline" onClick={() => setCheckIn(true)}>Daily check-in (3 taps)</button>}

      {snap.activeCraving ? <div class="card soft">
        <div class="row"><div class="dot" style={{ background: cravingColor(snap.activeCraving.intensity) }}>{snap.activeCraving.intensity}</div>
          <div><b>Riding out a craving · {snap.activeCraving.name}</b><div class="muted">Started {duration(Date.now() - snap.activeCraving.at)} ago</div></div></div>
        <div class="small">Most cravings pass within a few minutes. What's going on? (optional)</div>
        <div class="row wrap">{TAGS.map((t) => {
          const on = snap.activeCraving!.tags.includes(t)
          return <button class={`chip ${on ? 'on' : ''}`} onClick={() => {
            const c = S.records.value.get(snap.activeCraving!.id)!.data
            S.save('craving', { ...c, tags: on ? c.tags.filter((x: string) => x !== t) : [...(c.tags ?? []), t] })
          }}>{t}</button>
        })}</div>
        <div class="grid2"><button class="btn" onClick={() => S.finishCraving(snap.activeCraving!, 'RODE_OUT')}>It passed</button>
          <button class="btn outline" onClick={() => S.finishCraving(snap.activeCraving!, 'USED')}>I used</button></div>
      </div> : <div class="grid2">
        <button class="btn outline" style={{ height: 56 }} onClick={() => setCraving(true)}>Craving? Log it</button>
        <button class="btn outline" style={{ height: 56 }} onClick={() => setVape(true)}>Friend's vape</button>
      </div>}

      <div><h2>Log a dose</h2><div class="muted">Tap to log it now · hold for time, amount and more</div></div>
      <div class="grid2">
        {S.homeProducts.value.map((p) => (
          <button class="product" onPointerDown={() => down(p)} onPointerUp={() => up(p)} onPointerLeave={() => press.current && clearTimeout(press.current)} onContextMenu={(e) => e.preventDefault()}>
            <b>{p.name}</b><span>{piecesLabel(Core.piecesOf(p, S.json()))}</span>
          </button>
        ))}
      </div>
      <div class="grid2">
        <button class="btn outline" onClick={async () => { await S.logSleep('WAKE'); toast(`Good morning · ${time(Date.now())}`) }}>Good morning</button>
        <button class="btn outline" onClick={async () => { await S.logSleep('SLEEP'); toast(`Good night · ${time(Date.now())}`) }}>Good night</button>
      </div>

      <h2>Today</h2>
      {snap.todayDoses.length === 0 && <div class="muted">Nothing logged yet today.</div>}
      <div class="list">{snap.todayDoses.map((d) => (
        <div class="item" onClick={() => { if (confirm(`Delete ${d.name} at ${time(d.at)}?`)) { S.remove(d.id); toast('Dose deleted', () => S.restore(d.id)) } }}>
          <div>{d.name}<br /><span>{doseLine(d)}</span></div><b>{piecesLabel(d.pieces)}</b>
        </div>
      ))}</div>
      <div class="muted">≈ All nicotine figures are estimates. Their real value is comparing your own numbers over time.</div>

      {options && <DoseSheet product={options} onClose={() => setOptions(null)} onLog={(at, opts) => { setOptions(null); logProduct(options, at, opts) }} />}
      {craving && <CravingSheet onClose={() => setCraving(false)} onPick={async (level) => {
        setCraving(false); const c = await S.startCraving(level); toast(`Craving logged · ${level}. You've got this.`, () => S.remove(c.id))
      }} />}
      {vape && <VapeSheet preset={vape === true ? undefined : vape} onClose={() => setVape(false)} onLog={async (dose, saveAs, strength) => {
        setVape(false)
        if (saveAs && strength != null) {
          const p = { id: Core.newId(), name: `${saveAs}'s vape`, kind: 'VAPE', labelMg: strength, absorption: 1, speed: 'SPIKE', onHome: true, order: 100, borrowedFrom: saveAs, createdAt: Date.now() }
          await S.save('product', p); dose.productId = p.id
        } else if (vape !== true) dose.productId = vape.id
        await S.save('dose', dose); toast('Logged as a range', () => S.remove(dose.id))
      }} />}
      {checkIn && <CheckInSheet onClose={() => setCheckIn(false)} onSave={(c, m, s) => { setCheckIn(false); S.save('checkin', { id: Core.newId(), at: Date.now(), craving: c, mood: m, sleep: s }) }} />}
      {celebrate && <div class="sheet-bg" onClick={() => setCelebrate(null)}><div class="sheet" style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 48 }}>🔥</div><h2>New rung: {celebrate.tier}</h2>
        <div>{celebrate.plain} You earned the {celebrate.label} badge. Badges are never taken away.</div>
        <button class="btn" onClick={() => setCelebrate(null)}>Onward</button></div></div>}
    </main>
  )
}

export const todayIso = isoToday
