import { useEffect, useRef, useState } from 'preact/hooks'
import { Core } from '../core'
import { savedName, sendFeedback } from '../feedback'
import * as S from '../store'
import { Meter, Wave } from './Charts'
import { CheckInSheet, CravingSheet, DoseSheet, EditDoseSheet, PracticeDurationSheet, VapeSheet, doseLine } from './Sheets'
import { TAGS, cravingColor, dayTitle, duration, stepText, isoToday, mg, minutesOfDay, pieces, piecesLabel, signedDuration, time } from './format'

export function Home({ toast, go, backfill }: { toast: (msg: string, undo?: () => void) => void; go: (r: string) => void; backfill?: (days: string[]) => void }) {
  const snap = S.snapshot.value!
  const settings = S.settings.value
  const [steadyInfo, setSteadyInfo] = useState(false)
  const [practiceAsk, setPracticeAsk] = useState<any>(null)
  const [practiceHelp, setPracticeHelp] = useState(false)
  const [dontAsk, setDontAsk] = useState(false)
  const [editDose, setEditDose] = useState<string | null>(null)
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
  const moveTarget = async (pieces: number, reason: string, detail = '') => {
    await S.setTarget(pieces, reason, detail)
    const rung = snap.ladder.find((r) => Math.abs(r.pieces - pieces) < 1e-6)
    if (reason === 'down') setCelebrate(rung)
    else toast(reason === 'up' ? `Stepped up to ${rung?.label}. Your level is more accurate now.` : `Starting at ${rung?.label}.`)
  }
  const pr = snap.practice
  const sp = snap.target && snap.target.pieces > 0 ? snap.stepProgress : null
  const travel = Core.travelPrompt(S.json())
  // iPhone Safari, not yet added to the Home Screen: a one-time tip.
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent)
  const standalone = (navigator as any).standalone === true || (typeof matchMedia === 'function' && matchMedia('(display-mode: standalone)').matches)
  const showInstall = ios && !standalone && !settings.installCardSeen
  const ca = snap.clearAir
  const [hadSome, setHadSome] = useState(false)
  const nextStep = ca.active ? snap.insights.clearAirSteps.find((st) => st.value > Date.now()) : null
  const checkedIn = S.live('checkin').some((c) => new Date(c.at).toDateString() === new Date().toDateString())
  const battery = snap.battery

  return (
    <main>
      <div class="row between"><h1 class="brand">Firewatch<small>by Baastik Labs</small></h1><button class="btn text help-btn" aria-label="Help" onClick={() => go('help')}>?</button></div>
      {showInstall && <div class="card accent install-card">
        <h2>Add Firewatch to your Home Screen</h2>
        <div class="small">For the full app on iPhone: tap Share <span aria-hidden="true">⎋</span>, then Add to Home Screen. Your data stays in this browser either way.</div>
        <div><button class="btn outline" onClick={() => S.updateSettings({ installCardSeen: true })}>Got it</button></div>
      </div>}
      {rp.on && <div class="card soft relapse-on"><b>Relapse prevention mode is on</b>
        <div class="small">{rp.nextAt && !hidden ? `Next scheduled piece at ${time(rp.nextAt)}` : ''}{rp.productName ? ` · ${rp.productName}` : ''}</div>
        <div class="muted">Reminders are Android-only for now.</div></div>}
      {ca.active ? <div class="card clear-air">
        <p class="tier">Clear Air</p>
        <div class="big-num"><b>{ca.daysFree}</b> {ca.daysFree === 1 ? 'day' : 'days'} nicotine-free</div>
        {ca.healing != null && <><div class="small">Receptors heading back to typical: ≈ {Math.round(ca.healing * 100)}%</div><Meter value={ca.healing} /></>}
        {nextStep && <div class="small">Next on the recovery timeline: {nextStep.label} (in about {duration(nextStep.value - Date.now())})</div>}
        <div class="muted">A total that only goes up. Logging something just counts it.</div>
      </div> : <>
      <div class="card">
        {showTier ? <>
          {(snap.target ?? snap.measured) && <>
            <p class="tier">{(snap.target ?? snap.measured)!.tier}</p>
            <div class="small">{(snap.target ?? snap.measured)!.plain}</div>
            {pr.active && <div class="practice-row">
              <div class="row"><b class="grow">Practice pace · {pr.active.tier} {pr.active.label.split(' · ')[1]?.replace(' a day', '')}</b><button class="btn outline small-btn" aria-label="What is practice pace?" onClick={() => setPracticeHelp(!practiceHelp)}>?</button></div>
              {pr.netMin != null && <div class="small" style={{ color: pr.netMin >= 0 ? 'var(--tertiary)' : 'var(--muted)' }}>Practice net {signedDuration(pr.netMin)}{pr.day > 1 ? ` · day ${pr.day}` : ''}</div>}
              {practiceHelp && <div class="muted" style={{ whiteSpace: 'pre-line' }}>{pr.explainer}</div>}
            </div>}
            {snap.early && <div class="muted">Early estimate · firming up as you log your first week{snap.baselineState === 'progress' ? ` (day ${snap.baselineDay} of 7)` : ''}.</div>}
            {sp && !snap.early && <div class="step-progress">
              <Meter value={sp.fraction} label="Waking hours held toward the next step down" />
              <div class="small">{stepText(sp)}</div>
              {sp.unlocked ? <button class="btn" onClick={() => moveTarget(sp.next.pieces, 'down')}>Step down to {sp.next.label}</button>
                : sp.todayOver ? <div class="muted">Today won't count toward this one; the count starts again tomorrow.</div>
                : sp.unlocksAt ? <div class="muted">Unlocks around {new Date(sp.unlocksAt).toDateString() === new Date(Date.now() + 864e5).toDateString() ? 'tomorrow' : dayTitle(new Date(sp.unlocksAt).toISOString().slice(0, 10))}, {time(sp.unlocksAt)} if today stays at or under {snap.target?.label}</div> : null}
              {!sp.unlocked && sp.restartedOn && sp.restartedOn === new Date(Date.now() - 864e5).toISOString().slice(0, 10) && <div class="muted">Count started again on {dayTitle(sp.restartedOn)}.</div>}
            </div>}
            {snap.target && snap.measured && snap.measured.pieces !== snap.target.pieces &&
              <div class="muted">Working at {snap.target.label}. Your last 7 days measure {snap.measured.label}.</div>}
          </>}
          {battery && hidden && <div class="tap-reveal" onClick={() => reveal(battery.charge < 0.999)}>
            <b>Tap to see your next piece time</b>
            <Meter value={battery.charge} />
          </div>}
          {battery && !hidden && <div>
            <b>{rp.on && rp.nextAt ? `Next scheduled piece at ${time(rp.nextAt)}` : battery.state === 'CLEAR' ? 'Clear for one if you want it' : battery.state === 'CHARGING' ? `Next piece around ${battery.readyAt ? time(battery.readyAt) : 'later'}` :
              battery.state === 'FULL_AT_WAKE' ? (battery.fullAt ? `Full at ${time(battery.fullAt)} · or fresh when you wake up` : 'Full when you wake up') : battery.state === 'MORNING_DELAY' ? `First piece goal: ${battery.readyAt ? time(battery.readyAt) : ''}` :
              battery.state === 'WIND_DOWN' ? 'Winding down for bed' : 'Sleeping hours · Fresh start when you wake up'}</b>
            <Meter value={battery.charge} />
            {battery.closeToBed && <div class="small muted">Close to bedtime: nicotine can make it harder to fall asleep.</div>}
            {!rp.on && <div class="small stretch" style={{ color: battery.stretchMin - battery.pullMin >= 0 ? 'var(--tertiary)' : 'var(--muted)' }}>
              Stretch {duration(battery.stretchMin * 60000)} · Pull {duration(battery.pullMin * 60000)} · Net {signedDuration(battery.stretchMin - battery.pullMin)}</div>}
            {!rp.on && snap.morningStretch != null && snap.morningStretch >= 1 && <div class="small muted">Morning stretch: {duration(snap.morningStretch * 60000)}</div>}
            {!rp.on && !settings.netExplained && <div class="card accent small">
              <div>{snap.netExplainer}</div>
              <div class="row"><button class="btn text" onClick={() => S.updateSettings({ netExplained: true })}>Got it</button><button class="btn text" onClick={() => go('help')}>Learn more</button></div>
            </div>}
            {snap.tip && <div class="small tip">💡 {snap.tip.extra} <button class="btn text" onClick={() => S.updateSettings({ tipDismissedAt: { ...(settings.tipDismissedAt ?? {}), [snap.tip!.label]: Date.now() } })}>Hide for 2 weeks</button></div>}
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
        {(((settings.showSteadyDays ?? true) && snap.steadyDays > 0) || snap.heldTotal > 0 || snap.lighterThanStart != null || (snap.daysOffSmokeAndVape ?? 0) > 0 || snap.insights.journey != null) && <div class="wins small">
          {(settings.showSteadyDays ?? true) && snap.steadyDays > 0 && <div class="tappable" onClick={() => setSteadyInfo(!steadyInfo)}>✓ {snap.steadyDays} steady {snap.steadyDays === 1 ? 'day' : 'days'} <span class="muted">ⓘ</span>{steadyInfo && <div class="muted">{snap.steadyExplainer}</div>}</div>}
          {snap.heldTotal > 0 && snap.target && !snap.early && <div>✓ {snap.heldTotal} {snap.heldTotal === 1 ? 'day' : 'days'} held at {snap.target.tier} in total</div>}
          {snap.lighterThanStart != null && <div>✓ About {Math.round(snap.lighterThanStart * 100)}% lighter than when you started</div>}
          {(snap.daysOffSmokeAndVape ?? 0) > 0 && <div>✓ {snap.daysOffSmokeAndVape} days off cigarettes and vapes</div>}
          {snap.insights.journey != null && snap.insights.journey > 0 && <div>Journey to Clear Air: {Math.round(snap.insights.journey * 100)}%</div>}
        </div>}
        {showTier && snap.wave.length > 2 && <Wave points={snap.wave} height={64} axes={false} now={Date.now()} shade={[[snap.wave[0][0], snap.wakeAt], [snap.sleepAt, snap.wave[snap.wave.length - 1][0]]]} />}
        {showTier && snap.wave.length > 2 && <div class="now-mg"><b>≈ {snap.nowMg.toFixed(1)} mg</b> in your system now</div>}
      </div>
      </>}

      {snap.steadyMilestone && (settings.showSteadyDays ?? true) && <div class="card accent">
        <h2>{snap.steadyMilestone} steady days</h2>
        <div class="small">{snap.steadyMilestone} days at or under your pace with no cigarettes or vapes. That's real control.</div>
        <div><button class="btn" onClick={() => S.updateSettings({ steadyMilestoneSeen: snap.steadyMilestone })}>Nice</button></div>
      </div>}
      {snap.practiceFollowUp && <div class="card accent">
        <h2>How was {snap.practiceFollowUp.label} pace?</h2>
        <div class="small">Step down to it, or stay where you are. Either is fine.</div>
        <div class="row"><button class="btn" onClick={async () => { await S.updateSettings({ practiceAnswered: pr.followUpId ?? '' }); moveTarget(snap.practiceFollowUp!.pieces, 'down') }}>Step down</button>
          <button class="btn outline" onClick={() => S.updateSettings({ practiceAnswered: pr.followUpId ?? '', stepDownSnoozedAt: Date.now() })}>Stay here</button></div>
      </div>}
      {pr.lighterOffer && snap.target && <div class="card accent">
        <h2>{pr.lighterTitle}</h2>
        <div class="small" style={{ whiteSpace: 'pre-line' }}>{pr.lighterBody}</div>
        <div class="row wrap"><button class="btn" onClick={() => setPracticeAsk(pr.lighterOffer)}>Try {pr.lighterOffer.tier} pace</button>
          <button class="btn outline" onClick={() => S.updateSettings({ lighterSnoozedAt: Date.now(), ...(dontAsk ? { lighterOffers: false } : {}) })}>Stay here</button></div>
        <label class="row small"><input type="checkbox" style={{ width: 'auto' }} checked={dontAsk} onChange={() => setDontAsk(!dontAsk)} /> Don't ask me again</label>
      </div>}
      {pr.workFrom && snap.target && <div class="card accent">
        <h2>{pr.workFrom.tier} pace held</h2>
        <div class="small">You practiced {pr.workFrom.tier} pace for {pr.workFromHours} hours, practice net {signedDuration(pr.workFromNet)}. Work from {pr.workFrom.tier} from now on?</div>
        <div class="row wrap"><button class="btn" onClick={async () => { await S.stopPractice(); await S.updateSettings({ practiceAnswered: pr.lastSessionId ?? '' }); await S.setTarget(pr.workFrom!.pieces, 'measured', 'from measured level'); toast(`Working from ${pr.workFrom!.label}.`) }}>Work from {pr.workFrom.tier}</button>
          <button class="btn outline" onClick={() => S.updateSettings({ workFromSnoozedAt: Date.now() })}>Keep practicing</button>
          <button class="btn outline" onClick={async () => { await S.stopPractice(); await S.updateSettings({ practiceAnswered: pr.lastSessionId ?? '', lighterSnoozedAt: Date.now() }) }}>Back to {snap.target.tier} pace</button></div>
      </div>}
      {travel && <div class="card accent">
        <h2>Your time zone changed</h2>
        <div class="small">You're {travel.extra}. Use your usual day ({minutesOfDay(settings.wakeMinutes)}–{minutesOfDay(settings.sleepMinutes)}) in local time here? It starts from your next wake-up; past days don't move.</div>
        <div class="row wrap"><button class="btn" onClick={() => S.updateSettings(Core.travelAnswer(settings, 'local'))}>Yes, use local time</button>
          <button class="btn outline" onClick={() => S.updateSettings(Core.travelAnswer(settings, 'keep'))}>Keep my home times</button></div>
      </div>}
      {ca.offer && <div class="card accent">
        <h2>Your last 7 days were nicotine-free</h2>
        <div class="small">Switch to Clear Air? The Log tab becomes your days nicotine-free, with craving logging up front. You can step back up any time.</div>
        <div class="row"><button class="btn" onClick={() => moveTarget(0, 'down')}>Switch to Clear Air</button>
          <button class="btn outline" onClick={() => S.updateSettings({ clearAirOfferSnoozedAt: Date.now() })}>Not now</button></div>
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
          <button class="btn outline" onClick={() => setPracticeAsk(snap.stepDown)}>Try it for a day</button>
          <button class="btn outline" onClick={() => S.updateSettings({ stepDownSnoozedAt: Date.now() })}>Stay here</button></div>
      </div>}
      {snap.stepUp && snap.target && <div class="card accent">
        <h2>This rung is tough right now</h2>
        <div class="small">{snap.stepUpWhy} Stepping up to {snap.stepUp.label} makes your level more accurate. Step-downs are offered when you're ready.</div>
        <div class="row"><button class="btn" onClick={() => moveTarget(snap.stepUp!.pieces, 'up', (snap.stepUpWhy ?? '').replace(/\.$/, '').replace(/^./, (c) => c.toLowerCase()))}>Step up</button>
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
      {ca.active ? <button class="btn craving-big" onClick={() => setCraving(true)}>Craving? Log it</button> : <div class="grid2">
        <button class="btn outline" style={{ height: 56 }} onClick={() => setCraving(true)}>Craving? Log it</button>
        <button class="btn outline" style={{ height: 56 }} onClick={() => setVape(true)}>Friend's vape</button>
      </div>}
      {ca.active && !hadSome && <button class="btn text" onClick={() => setHadSome(true)}>I had some</button>}

      {(!ca.active || hadSome) && <><div><h2>Log a dose</h2><div class="muted">Tap to log it now · hold for time, amount and more</div></div>
      {ca.active && <button class="btn outline" onClick={() => setVape(true)}>Friend's vape</button>}
      <div class="grid2">
        {S.homeProducts.value.map((p) => (
          <button class="product" onPointerDown={() => down(p)} onPointerUp={() => up(p)} onPointerLeave={() => press.current && clearTimeout(press.current)} onContextMenu={(e) => e.preventDefault()}>
            <b>{p.name}</b>
            {!settings.hideDosePreview && snap.previews[p.id] != null && Math.abs(snap.previews[p.id]) >= 1 && <span class="preview">{snap.previews[p.id] > 0 ? `+${duration(snap.previews[p.id] * 60000)} stretch` : `+${duration(-snap.previews[p.id] * 60000)} pull`}</span>}
            <span>{piecesLabel(Core.piecesOf(p, S.json()))}</span>
          </button>
        ))}
      </div></>}
      <div class="grid2">
        {([['WAKE', 'Good morning'], ['SLEEP', 'Good night']] as ['WAKE' | 'SLEEP', string][]).map(([kind, label]) => (
          <button class="btn outline" onContextMenu={(e) => e.preventDefault()}
            onPointerDown={() => { longFired.current = false; press.current = window.setTimeout(() => {
              longFired.current = true
              const v = prompt(`${label}: what time? (e.g. 7:30)`, '')
              if (!v) return
              const [hh, mm] = v.split(':').map(Number)
              if (isNaN(hh)) return
              const d = new Date(); d.setHours(hh, mm || 0, 0, 0); if (d.getTime() > Date.now()) d.setDate(d.getDate() - 1)
              S.logSleep(kind, d.getTime()).then(() => toast(`${label} · ${time(d.getTime())}`))
            }, 500) }}
            onPointerUp={async () => { if (press.current) clearTimeout(press.current); if (!longFired.current) { await S.logSleep(kind); toast(`${label} · ${time(Date.now())}`) } }}
            onPointerLeave={() => press.current && clearTimeout(press.current)}>{label}</button>
        ))}
      </div>
      <div class="muted">Usual day {minutesOfDay(settings.wakeMinutes)}–{minutesOfDay(settings.sleepMinutes)} · hold to set a time</div>

      <h2>Today</h2>
      {snap.todayDoses.length === 0 && <div class="muted">Nothing logged yet today.</div>}
      <div class="list">{snap.todayDoses.map((d) => (
        <div class="item" onClick={() => setEditDose(d.id)}>
          <div>{d.name}<br /><span>{doseLine(d)}</span></div>
          {d.kind === 'POUCH' && !d.removedAt && !d.estimated && Date.now() - d.at >= 0 && Date.now() - d.at < 3600000 &&
            <button class="btn text" onClick={(e) => { e.stopPropagation(); const raw = S.live('dose').find((x) => x.id === d.id); if (raw) S.save('dose', { ...raw, removedAt: Date.now() }) }}>Took it out</button>}
          <b>{piecesLabel(d.pieces)}</b>
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
      {practiceAsk && <PracticeDurationSheet rung={practiceAsk} initial={settings.practiceUntilBedtime ?? true} stopNote={pr.stopNote} onClose={() => setPracticeAsk(null)}
        onStart={async (untilBedtime) => { const r = practiceAsk; setPracticeAsk(null); await S.startPractice(r.pieces, untilBedtime); toast(`Practice pace on: ${r.tier}. ${pr.stopNote}`) }} />}
      {editDose && <EditDoseSheet id={editDose} onClose={() => setEditDose(null)} toast={toast} />}
      {checkIn && <CheckInSheet onClose={() => setCheckIn(false)} onSave={(c, m, s) => { setCheckIn(false); S.save('checkin', { id: Core.newId(), at: Date.now(), craving: c, mood: m, sleep: s }) }} />}
      {celebrate && <div class="sheet-bg" onClick={() => setCelebrate(null)}><div class="sheet" style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 48 }}>🔥</div><h2>New rung: {celebrate.tier}</h2>
        <div>{celebrate.plain} You earned the {celebrate.label} badge. Badges are never taken away.</div>
        <button class="btn" onClick={() => setCelebrate(null)}>Onward</button></div></div>}
    </main>
  )
}

export const todayIso = isoToday
