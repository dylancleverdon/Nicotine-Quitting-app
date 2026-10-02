import { useMemo, useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { Barcode, Bars, DoseStrip, ForecastChart, Heatmap, KIND_COLORS, Line, Meter, ReceptorChart, StackedBars, Wave, dateLabels } from './Charts'
import { dayTitle, duration, pieces, shortDate, signedDuration, time } from './format'

const SECTIONS = ['Today', 'Cravings ahead', 'Receptors', 'Stretch & pull', 'Trends', 'Patterns', 'Going up', 'Going down', 'Mix', 'Forecasts', 'Milestones', 'Ladder']
const Card = ({ title, sub, children }: { title: string; sub?: string; children?: any }) => (
  <div class="card soft"><h3>{title}</h3>{sub && <div class="muted">{sub}</div>}{children}</div>
)
const Stat = ({ v, l }: { v: any; l: string }) => <div class="stat small"><b>{v}</b><span>{l}</span></div>

export function Insights() {
  const snap = S.snapshot.value!
  const ins = snap.insights
  const settings = S.settings.value
  const [sec, setSec] = useState('Today')
  const [recap, setRecap] = useState(0)
  const tick = Math.floor(S.tick.value / 300000)
  const out = useMemo(() => (sec === 'Cravings ahead' || sec === 'Receptors' ? Core.outlooks(S.json()) : null), [sec, S.json(), tick])
  // Opt-in "Detailed charts": range choices and a stepper on multi-day charts (42 days otherwise).
  const detailed = !!S.settings.value.detailedCharts
  const [range, setRange] = useState(42)
  const [endOff, setEndOff] = useState(0)
  const R = detailed ? range : 42
  const win = <T,>(a: T[]): T[] => (detailed ? a.slice(0, Math.max(1, a.length - endOff)) : a).slice(-R)
  const days = win(snap.days)
  const full = win(snap.days.slice(0, -1))
  // Day charts: choose which day to view (no overlay, no comparison).
  const [dayOff, setDayOff] = useState(0)
  if (!snap.days.length) return <main><h1>Insights</h1><div class="muted">Log a few doses and your graphs appear here.</div></main>
  const today = snap.days[snap.days.length - 1]
  const dl = (ds: { date: string }[]) => dateLabels(ds.map((d) => d.date))
  const h = (v: number) => `${v}h`
  const n = (v: number) => `${v}`
  const dayStart = new Date(snap.today + 'T00:00').getTime()
  return (
    <main>
      <h1>Insights</h1>
      <div class="scroll-x">{SECTIONS.map((s) => <button class={`chip ${sec === s ? 'on' : ''}`} onClick={() => setSec(s)}>{s}</button>)}</div>
      {detailed && !['Today', 'Cravings ahead', 'Receptors', 'Ladder'].includes(sec) && <div class="row wrap small">
        {[[7, '7 days'], [30, '30 days'], [90, '90 days'], [100000, 'All']].map(([v, l]) => <button class={`chip ${range === v ? 'on' : ''}`} onClick={() => { setRange(v as number); setEndOff(0) }}>{l}</button>)}
        {range < 100000 && <><button class="btn text" onClick={() => setEndOff(endOff + range)}>‹ Earlier</button>
          <button class="btn text" disabled={endOff === 0} onClick={() => setEndOff(Math.max(0, endOff - range))}>Later ›</button></>}
      </div>}
      {sec === 'Today' && (() => {
        const iso = new Date(new Date(snap.wakingToday + 'T12:00').getTime() - dayOff * 864e5).toISOString().slice(0, 10)
        const past = dayOff > 0 ? Core.day(S.json(), iso) : null
        const wave = past ? past.wave : snap.wave
        const wakeAt = past ? past.wakeAt : snap.wakeAt, sleepAt = past ? past.sleepAt : snap.sleepAt
        const found = snap.days.find((d) => d.date === iso)
        const detail = past ?? (dayOff === 0 ? null : Core.day(S.json(), iso))
        const stat = found ?? { ...today, pieces: detail?.pieces ?? 0, doses: detail?.doses.length ?? 0, clearHours: 0 }
        const stripDoses = dayOff === 0 ? snap.todayDoses : (past?.doses ?? [])
        const isoMidnight = new Date(iso + 'T00:00').getTime()
        const oldest = snap.days[0]?.date ?? iso
        const showVol = !!settings.showVolatility
        const vol = showVol ? (past ?? Core.day(S.json(), iso)).volatility : undefined
        const vd = full.map((d) => d.volatility)
        const vAvg = vd.map((_, i) => { const s = vd.slice(Math.max(0, i - 6), i + 1); return s.reduce((a, b) => a + b, 0) / s.length })
        const y = snap.yesterday
        return <>
          {dayOff === 0 && y && <Card title="Yesterday in review" sub="Facts only · every figure is an estimate">
            <div class="stats"><Stat v={`≈ ${pieces(y.pieces)}`} l="pieces" />{y.netMin != null && <Stat v={signedDuration(y.netMin)} l="net" />}<Stat v={`≈ ${y.volatility.toFixed(1)} mg/h`} l="volatility" /></div>
            <div class="small">
              {y.mix.length > 0 && <div>Mix: {y.mix.map((m) => `${Math.round(m.value * 100)}% ${m.label}`).join(', ')}</div>}
              {y.longestGapMin != null && <div>Longest gap between pieces: {duration(y.longestGapMin * 60000)}</div>}
              <div>Doses stacked while the last one was still peaking: {y.stacked}</div>
              {y.morningStretchMin != null && <div>Morning stretch: {duration(y.morningStretchMin * 60000)}</div>}
            </div>
            {y.tips.map((t) => <div class="small tip">💡 {t}</div>)}
          </Card>}
          <div class="day-stepper"><button class="btn text" disabled={iso <= oldest} onClick={() => setDayOff(dayOff + 1)}>‹</button>
            <b>{dayOff === 0 ? 'Today' : dayOff === 1 ? 'Yesterday' : dayTitle(iso)}</b>
            <button class="btn text" disabled={dayOff === 0} onClick={() => setDayOff(dayOff - 1)}>›</button></div>
          <Card title="Blood-level wave" sub={dayOff === 0 ? 'Each dose is a hill or spike. Sleep is shaded; the dashed line is your typical day.' : 'Each dose is a hill or spike. Sleep is shaded.'}>
            {wave.length > 1 ? <Wave points={wave} now={dayOff === 0 ? Date.now() : undefined} typical={dayOff === 0 ? snap.typical.map((v, i) => [dayStart + i * 1800000, v]).filter((p) => wave.length && p[0] >= wave[0][0] && p[0] <= wave[wave.length - 1][0]) : []}
              shade={[[wave[0]?.[0] ?? 0, wakeAt], [sleepAt, wave[wave.length - 1]?.[0] ?? 0]]} overlay={vol} /> : <div class="muted">Nothing logged that day.</div>}
            <label class="row small"><input type="checkbox" style={{ width: 'auto' }} checked={showVol} onChange={() => S.updateSettings({ showVolatility: !showVol })} /> Show volatility</label>
          </Card>
          <Card title="Dose strip" sub="That day's doses across 24 hours, sized by amount and coloured by type.">
            {stripDoses.length ? <DoseStrip doses={stripDoses} dayStart={isoMidnight} /> : <div class="muted">No doses that day.</div>}
            <div class="xaxis-plain small muted"><span>12 AM</span><span>6 AM</span><span>12 PM</span><span>6 PM</span><span>12 AM</span></div>
          </Card>
          {vd.length > 0 && <Card title="Nicotine volatility" sub="How fast and how much your nicotine level changes, each day (mg per hour), with a 7-day average line. Lower means steadier nicotine through the day.">
            <Bars values={vd} line={vAvg} fmt={(v) => `${v} mg/h`} x={dl(full)} />
          </Card>}
          <Card title={dayOff === 0 ? 'Today so far' : 'That day'}><div class="stats"><Stat v={`≈ ${pieces(stat.pieces)}`} l="pieces" /><Stat v={`${stat.clearHours.toFixed(1)} h`} l="clear hours" /><Stat v={stat.doses} l="doses" /></div></Card>
        </>
      })()}
      {sec === 'Cravings ahead' && out && (() => {
        const f = out.forecast
        const first = f.points[0]?.[0] ?? Date.now()
        return <Card title="Cravings ahead" sub="Chance of a craving over the next 24 hours, from your own logs and your estimated nicotine level. Dots show how strong one would probably be. Sleep is shaded. An estimate, not a promise.">
          <ForecastChart points={f.points} now={Date.now()} />
          <div class="next-craving"><b>{f.next ? `Next craving likely around ${time(f.next.peakAt)} (strength about ${Math.round(f.next.strength)})` : 'No clear craving peak ahead right now.'}</b></div>
          {f.windows.length > 1 && <div class="small">Most likely: {f.windows.map((w) => `${time(w.peakAt)} (about ${Math.round(w.strength)})`).join(', ')}{f.quietestAt ? `. Quietest: around ${time(f.quietestAt)}.` : '.'}</div>}
          {f.learning && <div class="muted">Still learning: based on {f.cravingsUsed} logged {f.cravingsUsed === 1 ? 'craving' : 'cravings'} so far, plus your nicotine curve. Log cravings with "Craving? Log it" and this sharpens up.</div>}
          {f.tested >= 3 && <div class="muted">Last 2 weeks: {f.hits} of {f.tested} cravings came during a predicted high window (the likeliest quarter of waking time).</div>}
        </Card>
      })()}
      {sec === 'Receptors' && out && (() => {
        const r = out.receptors
        if (!r) return <Card title="Receptors" sub="Appears after your first full day of logging." />
        const pct = (v: number) => `${Math.round(v * 100)}%`
        return <Card title="Receptors" sub="Estimated nicotine receptor load. 100% is typical of heavy regular use; the shaded band is the typical non-user range. Solid: your past. Dashed: if you keep following the program. Faint: if you stayed on your current rung.">
          <ReceptorChart history={r.history.map((p) => p.value)} plan={r.plan.map((p) => p.value)} stay={r.stay.map((p) => p.value)} typical={r.typical}
            dates={[...r.history.map((p) => p.label), ...r.plan.map((p) => p.label)]} />
          <div class="stats"><Stat v={`≈ ${pct(r.todayLoad)}`} l="load today" /><Stat v={r.clearAirOnPlan ? shortDate(r.clearAirOnPlan) : 'over a year'} l="Clear Air on plan" />
            <Stat v={r.typicalOnPlan ? shortDate(r.typicalOnPlan) : 'over a year'} l="typical range on plan" /></div>
          {!r.typicalIfStay && r.stay.length > 0 && <div class="small">Staying on your current rung keeps the load around {pct(r.stay[r.stay.length - 1].value)}. Each step down lets it fall further.</div>}
          {snap.relapse.on && <div class="muted">Relapse prevention mode is on. The dashed line shows what tapering looks like once you're ready.</div>}
          <div class="muted">An estimate from brain-imaging research averages and your logs, not a medical measurement. Everyone heals at their own pace.</div>
        </Card>
      })()}
      {sec === 'Stretch & pull' && (() => {
        const sd = win(snap.stretchDays)
        if (!sd.length) return <Card title="Stretch & pull" sub="Starts once you're working at a target rung." />
        const week = sd.filter((d) => d.date < snap.today).slice(-7).filter((d) => !d.paused)
        const paused = sd.filter((d) => d.paused).length
        const avg = (f: (d: any) => number) => (week.length ? week.reduce((a, d) => a + f(d), 0) / week.length : 0)
        return <Card title="Stretch & pull" sub="Stretch: time you held off after the battery was full. Pull: nicotine that came before the battery had room for it. Net = stretch − pull; positive means you're living below your target pace. Every day starts clean.">
          <div class="muted">Stretch (teal) and pull (grey), hours a day</div>
          <Line values={sd.map((d) => d.pullMin / 60)} second={sd.map((d) => d.stretchMin / 60)} color="var(--muted)" fmt={h} x={dl(sd)} />
          <div class="muted">Net, hours a day</div>
          <Bars values={sd.map((d) => Math.max(0, (d.stretchMin - d.pullMin) / 60))} height={80} fmt={h} x={dl(sd)} />
          {week.length > 0 && <div class="stats"><Stat v={duration(avg((d) => d.stretchMin) * 60000)} l="stretch, 7-day avg" /><Stat v={duration(avg((d) => d.pullMin) * 60000)} l="pull, 7-day avg" />
            <Stat v={signedDuration(avg((d) => d.stretchMin - d.pullMin))} l="net, 7-day avg" /></div>}
          {snap.stretchSummary && <div class="small">{snap.stretchSummary}</div>}
          {paused > 0 && <div class="muted">{paused} {paused === 1 ? 'day' : 'days'} in Relapse prevention mode: stretch and pull paused.</div>}
        </Card>
      })()}
      {sec === 'Trends' && <>
        {snap.recentStates.length > 0 && <Card title={`${snap.recentStates.filter((s) => s.extra === 'logged' || s.extra === 'clear').length} of ${snap.recentStates.length} days known`} sub="One dot per day: filled = logged, 🌿 = clear, ? = nothing logged, 👻 = left out. Only known days count in your figures.">
          <div class="strip">{snap.recentStates.map((s) => <span class={s.extra === 'logged' || s.extra === 'clear' ? 'on' : ''} title={s.label}>{s.extra === 'logged' ? '●' : s.extra === 'clear' ? '🌿' : s.extra === 'ghost' ? '👻' : '?'}</span>)}</div>
          {snap.measured && <div class="small">Your last 7 days measure {snap.measured.label}{snap.known7 < 7 ? ` (${snap.known7} of 7 days known)` : ''}</div>}
        </Card>}
        <Card title="Daily totals" sub="Pieces a day with the 7-day average line. Hatched = unknown doses (range); faded = estimated days.">
          <Bars values={days.map((d) => d.pieces)} highs={days.map((d) => (d.hasRange ? d.high : null))} faded={days.map((d) => d.estimated)} line={snap.sevenDayAverage.slice(-days.length)} fmt={n} x={dl(days)} />
        </Card>
        {ins.staircase.length > 1 && <Card title="Tier staircase" sub="Your measured tier week by week."><Line values={ins.staircase.map((s) => s.value)} stepped fmt={n} x={dateLabels(ins.staircase.map((s) => s.extra))} />
          <div class="muted">{ins.staircase.map((s) => s.label).join(' → ')}</div></Card>}
        {ins.weeklyGaps.length > 1 && <Card title="Gap between pieces" sub="Average time between doses, week by week. This one should climb."><Line values={ins.weeklyGaps.map((m) => m / 60)} color="var(--tertiary)" fmt={h} x={ins.weeklyGaps.map((_, i) => `Wk ${i + 1}`)} /></Card>}
      </>}
      {sec === 'Patterns' && <>
        <Card title="When it happens" sub="Hour of day across, Monday to Sunday down."><Heatmap grid={ins.heatmap} /></Card>
        <Card title="Wake to first piece" sub="Minutes from waking to the first dose. Longer is better."><Line values={snap.days.map((d) => d.wakeToFirstMin ?? 0)} color="var(--tertiary)" fmt={(v) => `${v}m`} x={dl(snap.days)} /></Card>
        {(S.settings.value.hideTimer || snap.checks.length > 0) && (() => {
          const ch = snap.checks
          const perPiece = full.slice(-14).reduce((a, d) => a + d.pieces, 0)
          const total14 = ch.slice(-14).reduce((a, d) => a + d.value, 0)
          return <Card title="Checking" sub="Taps on the hidden next-piece timer. Checks while it's still refilling are the 'wanting it' signal; fewer over time is progress.">
            {ch.length > 1 ? <Line values={ch.map((d) => d.value)} second={ch.map((d) => Number(d.extra))} color="var(--muted)" fmt={n} x={dateLabels(ch.map((d) => d.label))} />
              : <div class="muted">Your first days of checks appear here.</div>}
            <div class="muted">All checks (grey) and while refilling (teal), per day.</div>
            <div class="stats"><Stat v={snap.checksToday} l="checks today" />{perPiece > 0 && <Stat v={(total14 / perPiece).toFixed(1)} l="checks per piece (2 weeks)" />}</div>
          </Card>
        })()}
        <Card title="Triggers">{ins.triggers.length ? ins.triggers.map((t) => <div class="row small"><span class="grow">{t.label}</span>{t.value}</div>) : <div class="muted">Hold a product to tag what was going on.</div>}</Card>
        <Card title="Comparisons">{ins.comparisons.map((c) => <div class="small">{c.label}: ≈ {pieces(c.value)} vs {pieces(Number(c.extra))}</div>)}</Card>
      </>}
      {sec === 'Going up' && <>
        {snap.held.some((h) => Number(h[2]) >= 0) && (() => {
          const hs = snap.held.filter((h) => Number(h[2]) >= 0)
          const held = hs.filter((h) => Number(h[1]) <= Number(h[2]) + 0.25).length
          return <Card title="Holding steady" sub="Pieces a day against the rung you were working at (line). Every day at or under it is a win, whether or not you're tapering.">
            <Bars values={hs.map((h) => Number(h[1]))} line={hs.map((h) => Number(h[2]))} fmt={n} x={dateLabels(hs.map((h) => h[0]))}
              faded={hs.map((h) => Number(h[1]) > Number(h[2]) + 0.25)} />
            <div class="stats"><Stat v={held} l={`of the last ${hs.length} days held`} />{snap.heldDays > 0 && snap.target && <Stat v={snap.heldDays} l={`days held at ${snap.target.label}`} />}</div>
          </Card>
        })()}
        <Card title="Clear hours" sub="Hours each day your level sat near zero while awake."><Line values={full.map((d) => d.clearHours)} color="var(--tertiary)" fmt={h} x={dl(full)} /></Card>
        <Card title="Wins"><div class="stats"><Stat v={`≈ ${pieces(ins.avoidedPieces)}`} l="pieces avoided" /><Stat v={`${ins.avoidedMg.toFixed(1)} mg`} l="nicotine avoided" />
          <Stat v={ins.winRate != null ? `${Math.round(ins.winRate * 100)}%` : '–'} l="craving win rate" /></div></Card>
        <Card title="How cravings ended" sub="Last 2 weeks. Riding it out and waiting for the right time both count as wins. Worked out from your logs: a piece within 45 minutes is linked to the craving.">
          {snap.cravingEndings.length ? <div class="stats">{snap.cravingEndings.map((e) => <Stat v={e.value} l={(e.extra === 'win' ? '✓ ' : '') + e.label.toLowerCase()} />)}</div>
            : <div class="muted">Log cravings with "Craving? Log it" and this fills in.</div>}
        </Card>
        <Card title="Money saved"><h2>{S.settings.value.currency}{ins.money.toFixed(2)}</h2>
          {S.settings.value.rewardCost > 0 && <><Meter value={ins.money / S.settings.value.rewardCost} /><div class="muted">Toward {S.settings.value.rewardName || 'your reward'}</div></>}</Card>
        {ins.overnight.length > 1 && <Card title="Overnight gap"><Line values={ins.overnight.map((m) => m / 60)} color="var(--tertiary)" fmt={h} /></Card>}
        <Card title="Beaten triggers" sub="Of each trigger's last 10 appearances, how many passed without nicotine.">{ins.beaten.map((b) => <div class="row small"><span class="grow">{b.label}</span>{b.extra}</div>)}</Card>
      </>}
      {sec === 'Going down' && <>
        <Card title="Nicotine quality" sub="How you use nicotine, on a food scale. Gum, lozenges and patches are broccoli; smoke is burger and fries.">
          <Line values={full.map((d) => d.quality ?? 100)} color="var(--tertiary)" fmt={n} top={100} x={dl(full)} />
          {snap.qualityLabel && <b>Today: {snap.qualityLabel}</b>}{snap.swapTip && <div class="small">{snap.swapTip}</div>}
          <div class="muted">🥦 90+ · 🍎 75+ · 🥪 55+ · 🍕 35+ · 🍩 11+ · 🍔 0–10</div></Card>
        <Card title="Average dose size (mg)"><Line values={full.map((d) => (d.doses ? d.mg / d.doses : 0))} fmt={(v) => `${v} mg`} x={dl(full)} /></Card>
        <Card title="Spike share (%)"><Line values={full.map((d) => (d.mg > 0 ? (d.spikeMg / d.mg) * 100 : 0))} fmt={(v) => `${v}%`} top={100} x={dl(full)} /></Card>
        <Card title="Background level" sub="Modelled on cotinine; drifts down even through messy days."><Line values={win(ins.background)} fmt={(v) => `${v} mg`} x={dl(win(snap.days))} /></Card>
        <Card title="Cravings vs doses" sub="Doses orange, urges teal."><Line values={full.map((d) => d.doses)} second={full.map((d) => d.cravings)} fmt={n} x={dl(full)} /></Card>
        <Card title="What you can ride out"><div class="small">{ins.coachConfident ? `You reliably ride out cravings up to about ${ins.capacity} out of 10.` : 'Log a few more cravings (and whether they passed) to personalise this.'}</div>
          {ins.coachLevels.map((l) => <div class="row small"><span class="grow">{l.label}</span>{l.extra}</div>)}{ins.honest && <div class="muted">Honest level: {ins.honest}</div>}</Card>
        <Card title="Heaviness score" sub="From time-to-first-use and amount (0–6)."><b>{ins.heaviness?.toFixed(1) ?? '–'} of 6</b></Card>
        {snap.doubleUpsWeekly.length > 1 && <Card title="Double-ups" sub="Doses stacked while the last one was still peaking, per week."><Line values={snap.doubleUpsWeekly.map((d) => d.value)} fmt={n} x={dateLabels(snap.doubleUpsWeekly.map((d) => d.label))} /></Card>}
        {ins.checkIns.length >= 2 && <Card title="Daily check-in" sub="Craving strength (orange) and mood (teal), 1–5."><Line values={win(ins.checkIns).map((c) => c.value)} second={win(ins.checkIns).map((c) => Number(c.extra))} fmt={n} top={5} /></Card>}
      </>}
      {sec === 'Mix' && <>
        {(() => {
          const weeks: Record<string, number>[] = []
          for (let i = 0; i < days.length; i += 7) {
            const w: Record<string, number> = {}
            days.slice(i, i + 7).forEach((d) => Object.entries(d.kinds ?? {}).forEach(([k, v]) => { w[k] = (w[k] ?? 0) + v }))
            weeks.push(w)
          }
          const kinds = Object.keys(KIND_COLORS).filter((k) => weeks.some((w) => w[k]))
          return <Card title="Product mix" sub="Nicotine by delivery method, week by week.">
            <StackedBars columns={weeks} />
            <div class="row wrap small">{kinds.map((k) => <span><span style={{ display: 'inline-block', width: 10, height: 10, borderRadius: 2, background: KIND_COLORS[k], marginRight: 4 }} />{k.toLowerCase()}</span>)}</div>
          </Card>
        })()}
        <Card title="Label vs absorbed"><div class="stats"><Stat v={`${Math.round(snap.days.reduce((a, d) => a + d.labelMg, 0))} mg`} l="on the labels" /><Stat v={`≈ ${Math.round(snap.days.reduce((a, d) => a + d.mg, 0))} mg`} l="absorbed" /></div></Card>
        <Card title="Borrowed share"><b>{Math.round((snap.days.reduce((a, d) => a + d.borrowedPieces, 0) / Math.max(0.001, snap.days.reduce((a, d) => a + d.pieces, 0))) * 100)}%</b></Card>
      </>}
      {sec === 'Forecasts' && <>
        {snap.stepProgress && <Card title="Next step down" sub="Full days in a row at or under your level, since your last change. Days with nothing logged are skipped. Today counts once it's over. Staying where you are is a win too.">
          <b>{snap.stepProgress.offered ? `${snap.stepProgress.needed} of ${snap.stepProgress.needed} days held: ${snap.stepProgress.next.label} is offered on the Log tab` : `${snap.stepProgress.held} of ${snap.stepProgress.needed} days held`}</b>
          <Meter value={snap.stepProgress.held / snap.stepProgress.needed} /></Card>}
        {snap.taperSteps.length > 0 && <Card title="If you take each step" sub={`Stepping down each time it's offered (every ${S.settings.value.holdDays} days). Optional: staying steady is a win too. ${snap.taperBasis ?? ''}.`}>
          {snap.taperSteps.map((t) => <div class="row small"><span class="grow">{t.label}</span>around {shortDate(t.extra)}</div>)}
        </Card>}
        <Card title="Journey to Clear Air">{ins.journey != null ? <><h2>{Math.round(ins.journey * 100)}%</h2><Meter value={ins.journey} /></> : <div class="muted">Starts after your baseline week.</div>}</Card>
        <Card title="Taper speed"><b>{ins.taperText ?? 'Needs a week or two more data.'}</b></Card>
        <Card title="Arrival dates">{ins.arrivals.map((a) => <div class="small">{a.label}: {a.extra ? (a.extra <= snap.today ? 'reached' : shortDate(a.extra)) : 'not at this pace yet'}</div>)}</Card>
        {snap.nowCurve.length > 0 && <Card title="Then vs now" sub="Your average baseline day (grey) over your average day now."><Line values={snap.nowCurve} second={snap.thenCurve} fmt={(v) => `${v} mg`} x={snap.nowCurve.map((_, i) => (i % 12 === 0 ? `${i / 2}:00` : ''))} /></Card>}
      </>}
      {sec === 'Milestones' && <>
        <Card title="Records"><div class="stats"><Stat v={duration(ins.longestGapMin * 60000)} l="longest gap" /><Stat v={ins.lightestPieces != null ? `≈ ${pieces(ins.lightestPieces)}` : '–'} l="lightest day" />
          <Stat v={duration(ins.stretchMin * 60000)} l="total stretch" /><Stat v={ins.daysAtRung} l="days at this rung" /></div></Card>
        <Card title="Insights">{ins.cards.map((c) => <div class="small">• {c}</div>)}</Card>
        <Card title="Badges" sub="Never taken away.">{ins.badges.length ? ins.badges.slice().reverse().map((b) => <div class="small">🔥 {b.label} · {b.extra}</div>) : <div class="muted">Your first badges come with your first step down.</div>}</Card>
        {ins.recaps.length > 0 && <Card title="Recap"><div class="scroll-x">{ins.recaps.map((r, i) => <button class={`chip ${recap === i ? 'on' : ''}`} onClick={() => setRecap(i)}>{r.title}</button>)}</div>
          {(() => { const r = ins.recaps[recap]; return r && <div class="small">≈ {pieces(r.pieces)} pieces{r.drop ? ` · biggest weekly drop ${Math.round(r.drop)}%` : ''} · longest gap {duration(r.longestGapMin * 60000)} · {r.cravings} cravings ridden out{r.trigger ? ` · most-beaten trigger: ${r.trigger}` : ''}{r.rungs.length ? ` · rungs: ${r.rungs.join(', ')}` : ''}</div> })()}</Card>}
        <Card title="Day barcode" sub="Dark where nicotine was in your system, light where clear."><Barcode rows={snap.days.slice(-60).map((d) => d.barcode)} /></Card>
        <Card title="Silly conversions"><div class="small">≈ {Math.round(ins.pouches)} pouches skipped ({ins.pouchMetres.toFixed(1)} m end to end) · {ins.chewHours.toFixed(1)} hours of chewing avoided · {Math.round(ins.cigarettes)} cigarettes' worth not taken</div></Card>
        {ins.clearAirLast && Date.now() - ins.clearAirLast > 6 * 3600000 && <Card title="Clear Air countdown" sub="A research-based recovery timeline (approximate).">
          {ins.clearAirSteps.map((s) => <div class="small">{Date.now() >= s.value ? '✓' : '○'} {s.label}</div>)}</Card>}
      </>}
      {sec === 'Ladder' && snap.history.length > 0 && <Card title="Level history" sub="Newest first. Step ups keep their reason; practice pace shows its practice net.">
        {snap.history.map((h) => <div class="small">{h.extra === 'down' ? '▼' : h.extra === 'up' ? '▲' : h.extra === 'practice' ? '◇' : '•'} {h.label}</div>)}
      </Card>}
      {sec === 'Ladder' && <Card title="The ladder" sub="Each rung is one piece a day lighter.">
        {snap.ladder.map((r) => {
          const t = snap.target && Math.abs(snap.target.pieces - r.pieces) < 1e-6, me = snap.measured && Math.abs(snap.measured.pieces - r.pieces) < 1e-6
          return <div class="row small" style={{ fontWeight: t || me ? 700 : 400, color: t ? 'var(--primary)' : undefined }}><span class="grow">{r.label}</span>{t && 'target '}{me && 'you'}</div>
        })}
        <div class="muted">{snap.tiers.map((t) => `${t.label}: ${t.extra}`).join(' · ')}</div>
      </Card>}
      <div class="muted">≈ All nicotine figures are estimates.</div>
    </main>
  )
}
