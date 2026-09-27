import { useState } from 'preact/hooks'
import * as S from '../store'
import { Barcode, Bars, Heatmap, Line, Meter, Wave } from './Charts'
import { duration, pieces, shortDate, signedDuration } from './format'

const SECTIONS = ['Today', 'Stretch & pull', 'Trends', 'Patterns', 'Going up', 'Going down', 'Mix', 'Forecasts', 'Milestones', 'Ladder']
const Card = ({ title, sub, children }: { title: string; sub?: string; children?: any }) => (
  <div class="card soft"><h3>{title}</h3>{sub && <div class="muted">{sub}</div>}{children}</div>
)
const Stat = ({ v, l }: { v: any; l: string }) => <div class="stat small"><b>{v}</b><span>{l}</span></div>

export function Insights() {
  const snap = S.snapshot.value!
  const ins = snap.insights
  const [sec, setSec] = useState('Today')
  const [recap, setRecap] = useState(0)
  const days = snap.days.slice(-42)
  const full = snap.days.slice(0, -1).slice(-42)
  if (!snap.days.length) return <main><h1>Insights</h1><div class="muted">Log a few doses and your graphs appear here.</div></main>
  const today = snap.days[snap.days.length - 1]
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
      {sec === 'Stretch & pull' && (() => {
        const sd = snap.stretchDays.slice(-42)
        if (!sd.length) return <Card title="Stretch & pull" sub="Starts once you're working at a target rung." />
        const week = sd.filter((d) => d.date < snap.today).slice(-7)
        const avg = (f: (d: any) => number) => (week.length ? week.reduce((a, d) => a + f(d), 0) / week.length : 0)
        return <Card title="Stretch & pull" sub="Stretch: time you held off after the battery was full. Pull: how early a piece came before it was full. Net = stretch − pull; positive means you're living below your target pace. Every day starts clean.">
          <div class="muted">Stretch (teal) and pull (grey), hours a day</div>
          <Line values={sd.map((d) => d.pullMin / 60)} second={sd.map((d) => d.stretchMin / 60)} color="var(--muted)" />
          <div class="muted">Net, hours a day</div>
          <Bars values={sd.map((d) => Math.max(0, (d.stretchMin - d.pullMin) / 60))} height={80} />
          {week.length > 0 && <div class="stats"><Stat v={duration(avg((d) => d.stretchMin) * 60000)} l="stretch, 7-day avg" /><Stat v={duration(avg((d) => d.pullMin) * 60000)} l="pull, 7-day avg" />
            <Stat v={signedDuration(avg((d) => d.stretchMin - d.pullMin))} l="net, 7-day avg" /></div>}
          {snap.stretchSummary && <div class="small">{snap.stretchSummary}</div>}
        </Card>
      })()}
      {sec === 'Trends' && <>
        <Card title="Daily totals" sub="Pieces a day with the 7-day average line. Hatched = unknown doses (range); faded = estimated days.">
          <Bars values={days.map((d) => d.pieces)} highs={days.map((d) => (d.hasRange ? d.high : null))} faded={days.map((d) => d.estimated)} line={snap.sevenDayAverage.slice(-days.length)} />
          <div class="muted">{shortDate(days[0].date)} – {shortDate(days[days.length - 1].date)}</div>
        </Card>
        {ins.staircase.length > 1 && <Card title="Tier staircase" sub="Your measured tier week by week."><Line values={ins.staircase.map((s) => s.value)} stepped />
          <div class="muted">{ins.staircase.map((s) => s.label).join(' → ')}</div></Card>}
        {ins.weeklyGaps.length > 1 && <Card title="Gap between pieces" sub="Average time between doses, week by week. This one should climb."><Line values={ins.weeklyGaps} color="var(--tertiary)" /></Card>}
      </>}
      {sec === 'Patterns' && <>
        <Card title="When it happens" sub="Hour of day across, Monday to Sunday down."><Heatmap grid={ins.heatmap} /></Card>
        <Card title="Wake to first piece" sub="Minutes from waking to the first dose. Longer is better."><Line values={snap.days.map((d) => d.wakeToFirstMin ?? 0)} color="var(--tertiary)" /></Card>
        <Card title="Triggers">{ins.triggers.length ? ins.triggers.map((t) => <div class="row small"><span class="grow">{t.label}</span>{t.value}</div>) : <div class="muted">Hold a product to tag what was going on.</div>}</Card>
        <Card title="Comparisons">{ins.comparisons.map((c) => <div class="small">{c.label}: ≈ {pieces(c.value)} vs {pieces(Number(c.extra))}</div>)}</Card>
      </>}
      {sec === 'Going up' && <>
        <Card title="Clear hours" sub="Hours each day your level sat near zero while awake."><Line values={full.map((d) => d.clearHours)} color="var(--tertiary)" /></Card>
        <Card title="Wins"><div class="stats"><Stat v={`≈ ${pieces(ins.avoidedPieces)}`} l="pieces avoided" /><Stat v={`${ins.avoidedMg.toFixed(1)} mg`} l="nicotine avoided" />
          <Stat v={ins.winRate != null ? `${Math.round(ins.winRate * 100)}%` : '–'} l="craving win rate" /><Stat v={ins.cravingMinutes != null ? `${Math.round(ins.cravingMinutes)} min` : '–'} l="typical craving" /></div></Card>
        <Card title="Money saved"><h2>{S.settings.value.currency}{ins.money.toFixed(2)}</h2>
          {S.settings.value.rewardCost > 0 && <><Meter value={ins.money / S.settings.value.rewardCost} /><div class="muted">Toward {S.settings.value.rewardName || 'your reward'}</div></>}</Card>
        {ins.overnight.length > 1 && <Card title="Overnight gap"><Line values={ins.overnight} color="var(--tertiary)" /></Card>}
        <Card title="Beaten triggers" sub="Of each trigger's last 10 appearances, how many passed without nicotine.">{ins.beaten.map((b) => <div class="row small"><span class="grow">{b.label}</span>{b.extra}</div>)}</Card>
      </>}
      {sec === 'Going down' && <>
        <Card title="Nicotine quality" sub="How you use nicotine, on a food scale. Gum, lozenges and patches are broccoli; smoke is burger and fries.">
          <Line values={full.map((d) => d.quality ?? 100)} color="var(--tertiary)" />
          {snap.qualityLabel && <b>Today: {snap.qualityLabel}</b>}{snap.swapTip && <div class="small">{snap.swapTip}</div>}
          <div class="muted">🥦 90+ · 🍎 75+ · 🥪 55+ · 🍕 35+ · 🍩 11+ · 🍔 0–10</div></Card>
        <Card title="Average dose size (mg)"><Line values={full.map((d) => (d.doses ? d.mg / d.doses : 0))} /></Card>
        <Card title="Spike share (%)"><Line values={full.map((d) => (d.mg > 0 ? (d.spikeMg / d.mg) * 100 : 0))} /></Card>
        <Card title="Background level" sub="Modelled on cotinine; drifts down even through messy days."><Line values={ins.background.slice(-42)} /></Card>
        <Card title="Cravings vs doses" sub="Doses orange, urges teal."><Line values={full.map((d) => d.doses)} second={full.map((d) => d.cravings)} /></Card>
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
