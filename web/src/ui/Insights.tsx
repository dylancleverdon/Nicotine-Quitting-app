import { useMemo, useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { Barcode, Bars, ForecastChart, Heatmap, Line, Meter, ReceptorChart, Wave, dateLabels } from './Charts'
import { duration, pieces, shortDate, signedDuration, time } from './format'

const SECTIONS = ['Today', 'Cravings ahead', 'Receptors', 'Stretch & pull', 'Trends', 'Patterns', 'Going up', 'Going down', 'Mix', 'Forecasts', 'Milestones', 'Ladder']
const Card = ({ title, sub, children }: { title: string; sub?: string; children?: any }) => (
  <div class="card soft"><h3>{title}</h3>{sub && <div class="muted">{sub}</div>}{children}</div>
)
const Stat = ({ v, l }: { v: any; l: string }) => <div class="stat small"><b>{v}</b><span>{l}</span></div>

export function Insights() {
  const snap = S.snapshot.value!
  const ins = snap.insights
  const [sec, setSec] = useState('Today')
  const [recap, setRecap] = useState(0)
  const tick = Math.floor(S.tick.value / 300000)
  const out = useMemo(() => (sec === 'Cravings ahead' || sec === 'Receptors' ? Core.outlooks(S.json()) : null), [sec, S.json(), tick])
  const days = snap.days.slice(-42)
  const full = snap.days.slice(0, -1).slice(-42)
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
      {sec === 'Today' && <>
        <Card title="Blood-level wave" sub="Each dose is a hill or spike. Sleep is shaded; the dashed line is your typical day.">
          <Wave points={snap.wave} now={Date.now()} typical={snap.typical.map((v, i) => [dayStart + i * 1800000, v]).filter((p) => snap.wave.length && p[0] >= snap.wave[0][0] && p[0] <= snap.wave[snap.wave.length - 1][0])}
            shade={[[snap.wave[0]?.[0] ?? 0, snap.wakeAt], [snap.sleepAt, snap.wave[snap.wave.length - 1]?.[0] ?? 0]]} />
        </Card>
        <Card title="Today so far"><div class="stats"><Stat v={`≈ ${pieces(today.pieces)}`} l="pieces" /><Stat v={`${today.clearHours.toFixed(1)} h`} l="clear hours" />
          <Stat v={duration(Math.max(0, today.awakeHours * 60 - today.mouthMin) * 60000)} l="mouth-free" /></div></Card>
      </>}
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
        const sd = snap.stretchDays.slice(-42)
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
        <Card title="Background level" sub="Modelled on cotinine; drifts down even through messy days."><Line values={ins.background.slice(-42)} fmt={(v) => `${v} mg`} x={dl(snap.days.slice(-42))} /></Card>
        <Card title="Cravings vs doses" sub="Doses orange, urges teal."><Line values={full.map((d) => d.doses)} second={full.map((d) => d.cravings)} fmt={n} x={dl(full)} /></Card>
        <Card title="What you can ride out"><div class="small">{ins.coachConfident ? `You reliably ride out cravings up to about ${ins.capacity} out of 10.` : 'Log a few more cravings (and whether they passed) to personalise this.'}</div>
          {ins.coachLevels.map((l) => <div class="row small"><span class="grow">{l.label}</span>{l.extra}</div>)}{ins.honest && <div class="muted">Honest level: {ins.honest}</div>}</Card>
        <Card title="Heaviness score" sub="From time-to-first-use and amount (0–6)."><b>{ins.heaviness?.toFixed(1) ?? '–'} of 6</b></Card>
      </>}
      {sec === 'Mix' && <>
        <Card title="Label vs absorbed"><div class="stats"><Stat v={`${Math.round(snap.days.reduce((a, d) => a + d.labelMg, 0))} mg`} l="on the labels" /><Stat v={`≈ ${Math.round(snap.days.reduce((a, d) => a + d.mg, 0))} mg`} l="absorbed" /></div></Card>
        <Card title="Borrowed share"><b>{Math.round((snap.days.reduce((a, d) => a + d.borrowedPieces, 0) / Math.max(0.001, snap.days.reduce((a, d) => a + d.pieces, 0))) * 100)}%</b></Card>
      </>}
      {sec === 'Forecasts' && <>
        <Card title="Journey to Clear Air">{ins.journey != null ? <><h2>{Math.round(ins.journey * 100)}%</h2><Meter value={ins.journey} /></> : <div class="muted">Starts after your baseline week.</div>}</Card>
        <Card title="Taper speed"><b>{ins.taperPct != null ? `${ins.taperPct.toFixed(1)}% lighter each week` : 'Needs a week or two more data.'}</b></Card>
        <Card title="Arrival dates">{ins.arrivals.map((a) => <div class="small">{a.label}: {a.extra ? (a.extra <= snap.today ? 'reached' : shortDate(a.extra)) : 'not at this pace yet'}</div>)}</Card>
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
