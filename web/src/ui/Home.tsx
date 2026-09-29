import { useEffect, useRef, useState } from 'preact/hooks'
import { Core } from '../core'
import { savedName, sendFeedback } from '../feedback'
import * as S from '../store'
import { Meter, Wave } from './Charts'
import { CheckInSheet, CravingSheet, DoseSheet, VapeSheet, doseLine } from './Sheets'
import { TAGS, cravingColor, duration, isoToday, mg, pieces, piecesLabel, signedDuration, time } from './format'

export function Home({ toast, go, backfill }: { toast: (msg: string, undo?: () => void) => void; go: (r: string) => void; backfill?: (days: string[]) => void }) {
  const snap = S.snapshot.value!
  const settings = S.settings.value
  const [options, setOptions] = useState<any>(null)
  const [craving, setCraving] = useState(false)
  const [vape, setVape] = useState<any>(false)
  const [checkIn, setCheckIn] = useState(false)
  const [celebrate, setCelebrate] = useState<any>(null)
  const [relapseSheet, setRelapseSheet] = useState(false)
  const [revealUntil, setRevealUntil] = useState(0)
  const hidden = !!settings.hideTimer && Date.now() > revealUntil
  const reveal = (charging: boolean) => {
    S.logTimerCheck(charging)
    setRevealUntil(Date.now() + 30000)
    setTimeout(() => setRevealUntil(0), 30500)
  }
  const [fb, setFb] = useState<{ type: string; text: string; details: string; name: string } | null>(null)
  const rp = snap.relapse
  // First week: the early target (8 a day) firms up as days are logged.
  useEffect(() => { if (snap.earlyUpdate != null) S.setTarget(snap.earlyUpdate, 'early') }, [snap.earlyUpdate])
  const showTier = snap.revealed || snap.early
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
      <div class="row between"><h1 class="brand">Firewatch<small>by Baastik Labs</small></h1><button class="btn text help-btn" aria-label="Help" onClick={() => go('help')}>?</button></div>
      {rp.on && <div class="card soft relapse-on"><b>Relapse prevention mode is on</b>
        <div class="small">{rp.nextAt && !hidden ? `Next scheduled piece at ${time(rp.nextAt)}` : ''}{rp.productName ? ` · ${rp.productName}` : ''}</div>
        <div class="muted">Reminders are Android-only for now.</div></div>}
      <div class="card">
        {showTier ? <>
          {(snap.target ?? snap.measured) && <>
            <p class="tier">{(snap.target ?? snap.measured)!.tier}</p>
            <div class="small">{(snap.target ?? snap.measured)!.plain}</div>
            {snap.practicing && <div class="muted">Practice day: {snap.ladder.find((r) => Math.abs(r.pieces - (settings.practicePieces ?? 0)) < 1e-6)?.label ?? ''} pace</div>}
            {snap.early && <div class="muted">Early estimate · firming up as you log your first week{snap.baselineState === 'progress' ? ` (day ${snap.baselineDay} of 7)` : ''}.</div>}
            {snap.target && snap.measured && snap.measured.pieces !== snap.target.pieces &&
              <div class="muted">Working at {snap.target.label}. Your last 7 days measure {snap.measured.label}.</div>}
          </>}
          {battery && hidden && <div class="tap-reveal" onClick={() => reveal(battery.charge < 0.999)}>
            <b>Tap to see your next piece time</b>
            <Meter value={battery.charge} />
          </div>}
          {battery && !hidden && <div>
            <b>{rp.on && rp.nextAt ? `Next scheduled piece at ${time(rp.nextAt)}` : battery.state === 'CLEAR' ? 'Clear for one if you want it' : battery.state === 'CHARGING' ? `Next piece around ${battery.readyAt ? time(battery.readyAt) : 'later'}` :
              battery.state === 'FULL_AT_WAKE' ? 'Full when you wake up' : battery.state === 'MORNING_DELAY' ? `First piece goal: ${battery.readyAt ? time(battery.readyAt) : ''}` :
              battery.state === 'WIND_DOWN' ? 'Winding down for bed' : 'Sleeping hours · Fresh start when you wake up'}</b>
            <Meter value={battery.charge} />
            {battery.closeToBed && <div class="small muted">Close to bedtime: nicotine can make it harder to fall asleep.</div>}
            {!rp.on && <div class="small stretch" style={{ color: battery.stretchMin - battery.pullMin >= 0 ? 'var(--tertiary)' : 'var(--muted)' }}>
              Stretch {duration(battery.stretchMin * 60000)} · Pull {duration(battery.pullMin * 60000)} · Net {signedDuration(battery.stretchMin - battery.pullMin)}</div>}
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
        {(((settings.showSteadyDays ?? true) && snap.steadyDays > 0) || snap.heldDays > 0 || snap.lighterThanStart != null || (snap.daysOffSmokeAndVape ?? 0) > 0 || snap.insights.journey != null) && <div class="wins small">
          {(settings.showSteadyDays ?? true) && snap.steadyDays > 0 && <div>✓ {snap.steadyDays} steady {snap.steadyDays === 1 ? 'day' : 'days'}</div>}
          {snap.heldDays > 0 && snap.target && <div>✓ Held {snap.target.label} for {snap.heldDays} {snap.heldDays === 1 ? 'day' : 'days'}</div>}
          {snap.lighterThanStart != null && <div>✓ About {Math.round(snap.lighterThanStart * 100)}% lighter than when you started</div>}
          {(snap.daysOffSmokeAndVape ?? 0) > 0 && <div>✓ {snap.daysOffSmokeAndVape} days off cigarettes and vapes</div>}
          {snap.insights.journey != null && snap.insights.journey > 0 && <div>Journey to Clear Air: {Math.round(snap.insights.journey * 100)}%</div>}
        </div>}
        {showTier && snap.wave.length > 2 && <Wave points={snap.wave} height={64} axes={false} now={Date.now()} shade={[[snap.wave[0][0], snap.wakeAt], [snap.sleepAt, snap.wave[snap.wave.length - 1][0]]]} />}
        {showTier && snap.wave.length > 2 && <div class="now-mg"><b>≈ {snap.nowMg.toFixed(1)} mg</b> in your system now</div>}
      </div>

      {snap.steadyMilestone && (settings.showSteadyDays ?? true) && <div class="card accent">
        <h2>{snap.steadyMilestone} steady days</h2>
        <div class="small">{snap.steadyMilestone} days at or under your pace with no cigarettes or vapes. That's real control.</div>
        <div><button class="btn" onClick={() => S.updateSettings({ steadyMilestoneSeen: snap.steadyMilestone })}>Nice</button></div>
      </div>}
      {snap.practiceFollowUp && <div class="card accent">
        <h2>How was {snap.practiceFollowUp.label} pace?</h2>
        <div class="small">Step down to it, or stay where you are. Either is fine.</div>
        <div class="row"><button class="btn" onClick={async () => { await S.updateSettings({ practiceDate: '', practicePieces: 0 }); moveTarget(snap.practiceFollowUp!.pieces, 'down') }}>Step down</button>
          <button class="btn outline" onClick={() => S.updateSettings({ practiceDate: '', practicePieces: 0, stepDownSnoozedAt: Date.now() })}>Stay here</button></div>
      </div>}
      {snap.welcomeBack && <div class="card accent">
        <h2>Welcome back</h2>
        <div class="small">Want to add what you had while you were away? Rough counts per day, no times needed.</div>
        <div class="row"><button class="btn" onClick={() => backfill?.(snap.welcomeBack!)}>Add those days</button>
          <button class="btn outline" onClick={() => S.updateSettings({ welcomeBackDismissedAt: Date.now() })}>Not now</button></div>
      </div>}
      {snap.revealed && (!snap.target || snap.early) && snap.measured && <div class="card accent">
        <h2>Your starting point: {snap.measured.tier}</h2>
        <div class="small">{snap.measured.plain} Work from here?</div>
        <div><button class="btn" onClick={() => moveTarget(snap.measured!.pieces, 'start')}>Start here</button></div>
      </div>}
      {snap.stepDown && snap.target && <div class="card accent">
        <h2>Ready for {snap.stepDown.label}?</h2>
        <div class="small">You've held {snap.target.label} for {settings.holdDays} days. {snap.stepDownNote} Or stay here, that's fine too.</div>
        <div class="row wrap"><button class="btn" onClick={() => moveTarget(snap.stepDown!.pieces, 'down')}>Step down</button>
          <button class="btn outline" onClick={() => { S.updateSettings({ practiceDate: snap.wakingToday, practicePieces: snap.stepDown!.pieces }); toast(`Practice day: ${snap.stepDown!.label} pace for today`) }}>Try it for a day</button>
          <button class="btn outline" onClick={() => S.updateSettings({ stepDownSnoozedAt: Date.now() })}>Stay here</button></div>
      </div>}
      {snap.stepUp && snap.target && <div class="card accent">
        <h2>This rung is tough right now</h2>
        <div class="small">{snap.stepUpWhy} Stepping up to {snap.stepUp.label} for a while is normal, and it keeps you on gum instead of something worse.</div>
        <div class="row"><button class="btn" onClick={() => moveTarget(snap.stepUp!.pieces, 'up')}>Step up</button>
          <button class="btn outline" onClick={() => S.updateSettings({ stepUpSnoozedAt: Date.now() })}>I'm OK</button></div>
      </div>}
      {rp.recommend && <div class="card accent">
        <h2>Try Relapse prevention mode?</h2>
        <div class="small">{rp.recommend} Chewing on a steady schedule early on keeps you ahead of cravings.</div>
        <div class="row"><button class="btn" onClick={() => setRelapseSheet(true)}>Tell me more</button>
          <button class="btn outline" onClick={() => S.updateSettings({ relapseCardDismissedAt: Date.now() })}>Not now</button></div>
      </div>}
      {rp.movingOn && <div class="card accent">
        <h2>You've been steady for 4 weeks</h2>
        <div class="small">Ready to switch to tapering? Relapse prevention mode turns off, and Firewatch helps you step down at your own pace.</div>
        <div class="row"><button class="btn" onClick={async () => { await S.setRelapse(false); toast('Relapse prevention mode is off. On to tapering.') }}>Switch to tapering</button>
          <button class="btn outline" onClick={() => S.updateSettings({ movingOnDismissedAt: Date.now() })}>Not yet</button></div>
      </div>}
      {snap.headsUps.length > 0 && <div class="card soft"><b>Heads-up</b>{snap.headsUps.map((h) => <div class="small">{h}</div>)}</div>}
      {snap.swapTip && <div class="muted">{snap.swapTip}</div>}
      {settings.dailyCheckIn && !checkedIn && <button class="btn outline" onClick={() => setCheckIn(true)}>Daily check-in (3 taps)</button>}

      {snap.activeCraving ? <div class="muted craving-line">Craving logged at {time(snap.activeCraving.at)}. You've got this.</div> : null}
      {<div class="grid2">
        <button class="btn outline" style={{ height: 56 }} onClick={() => setCraving(true)}>Craving? Log it</button>
        <button class="btn outline" style={{ height: 56 }} onClick={() => setVape(true)}>Friend's vape</button>
      </div>}

      <div><h2>Log a dose</h2><div class="muted">Tap to log it now · hold for time, amount and more</div></div>
      <div class="grid2">
        {S.homeProducts.value.map((p) => (
          <button class="product" onPointerDown={() => down(p)} onPointerUp={() => up(p)} onPointerLeave={() => press.current && clearTimeout(press.current)} onContextMenu={(e) => e.preventDefault()}>
            <b>{p.name}</b>
            {!settings.hideDosePreview && snap.previews[p.id] != null && Math.abs(snap.previews[p.id]) >= 1 && <span class="preview">{snap.previews[p.id] > 0 ? `+${duration(snap.previews[p.id] * 60000)} stretch` : `+${duration(-snap.previews[p.id] * 60000)} pull`}</span>}
            <span>{piecesLabel(Core.piecesOf(p, S.json()))}</span>
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
      <button class="btn outline" onClick={() => setRelapseSheet(true)}>{rp.on ? 'Relapse prevention mode: on' : 'Relapse prevention mode'}</button>
      <div class="muted">≈ All nicotine figures are estimates. Their real value is comparing your own numbers over time.</div>
      <button class="btn text" onClick={() => setFb({ type: 'Idea', text: '', details: '', name: savedName() })}>Suggest something / report a bug</button>

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
      {fb && <div class="sheet-bg" onClick={() => setFb(null)}><div class="sheet" onClick={(e) => e.stopPropagation()}>
        <h2>Suggest something</h2>
        <div class="row wrap">{['Idea', 'Bug', 'Other'].map((t) => <button class={`chip ${fb.type === t ? 'on' : ''}`} onClick={() => setFb({ ...fb, type: t })}>{t}</button>)}</div>
        <textarea rows={4} placeholder={fb.type === 'Bug' ? 'What went wrong?' : 'Your suggestion'} value={fb.text} onInput={(e) => setFb({ ...fb, text: (e.target as HTMLTextAreaElement).value })} />
        <textarea rows={2} placeholder={fb.type === 'Bug' ? 'What were you doing? (optional)' : 'Details (optional)'} value={fb.details} onInput={(e) => setFb({ ...fb, details: (e.target as HTMLTextAreaElement).value })} />
        <input placeholder="Your name (optional)" value={fb.name} onInput={(e) => setFb({ ...fb, name: (e.target as HTMLInputElement).value })} />
        <div class="muted">{Core.feedbackPrivacy()}</div>
        <div class="row"><button class="btn" disabled={!fb.text.trim()} onClick={async () => {
          const f = fb; setFb(null)
          const err = await sendFeedback(f.type, f.text, f.details, f.name)
          toast(err ? `${err}. Saved: send it later from Settings → Unsent suggestions.` : 'Thanks, sent!')
        }}>Send</button><button class="btn outline" onClick={() => setFb(null)}>Cancel</button></div>
      </div></div>}
      {relapseSheet && <div class="sheet-bg" onClick={() => setRelapseSheet(false)}><div class="sheet" onClick={(e) => e.stopPropagation()}>
        <h2>Relapse prevention mode</h2>
        <div class="small"><b>What it is:</b> a reminder to chew a piece at a steady gap: your tier's gap, or every 2 hours before you have a tier.</div>
        <div class="small"><b>Why:</b> early on, staying ahead of cravings makes going back to smoking or vaping much less likely. It sounds backwards for an app about cutting down, and that's intentional for now. Your tiers and figures stay just as honest.</div>
        <div class="small"><b>Turning it off:</b> this same button, any time.</div>
        <div class="muted">On the web app, reminders are Android-only for now; the "Next scheduled piece" time still shows here.</div>
        {rp.on ? <div class="row"><button class="btn" onClick={async () => { setRelapseSheet(false); await S.setRelapse(false); toast('Relapse prevention mode turned off') }}>Turn off</button>
          <button class="btn outline" onClick={() => setRelapseSheet(false)}>Keep it on</button></div>
          : <div class="row"><button class="btn" onClick={async () => { setRelapseSheet(false); await S.setRelapse(true); toast('Relapse prevention mode turned on') }}>Turn on</button>
          <button class="btn outline" onClick={() => { setRelapseSheet(false); if (rp.recommend) S.updateSettings({ relapseCardDismissedAt: Date.now() }) }}>Not now</button></div>}
      </div></div>}
      {checkIn && <CheckInSheet onClose={() => setCheckIn(false)} onSave={(c, m, s) => { setCheckIn(false); S.save('checkin', { id: Core.newId(), at: Date.now(), craving: c, mood: m, sleep: s }) }} />}
      {celebrate && <div class="sheet-bg" onClick={() => setCelebrate(null)}><div class="sheet" style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 48 }}>🔥</div><h2>New rung: {celebrate.tier}</h2>
        <div>{celebrate.plain} You earned the {celebrate.label} badge. Badges are never taken away.</div>
        <button class="btn" onClick={() => setCelebrate(null)}>Onward</button></div></div>}
    </main>
  )
}

export const todayIso = isoToday
