// Small SVG charts. Every figure is an estimate; charts are for shape and comparison.
import { useState } from 'preact/hooks'
import { dayMonth, hourLabel, time } from './format'
const W = 320

/** Round axis values: 0 up to a "nice" top at or above [max], about [n] steps. */
export function niceTicks(max: number, n = 4): number[] {
  const m = max > 0 && isFinite(max) ? max : 1
  const raw = m / n
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  const step = [1, 2, 2.5, 5, 10].map((k) => k * mag).find((s) => s >= raw) ?? 10 * mag
  const out: number[] = []
  for (let v = 0; v < m + step * 0.999; v += step) out.push(Number(v.toFixed(6)))
  return out
}

/** Time-of-day ticks between t0 and t1 on whole hours: [position 0..1, label]. */
export function timeTicks(t0: number, t1: number, max = 5): [number, string][] {
  const H = 3600000
  const step = [1, 2, 3, 4, 6, 12, 24].find((h) => (t1 - t0) / (h * H) <= max) ?? 24
  const start = new Date(t0); start.setMinutes(0, 0, 0)
  const out: [number, string][] = []
  for (let t = start.getTime(); t <= t1; t += H) {
    if (t < t0 || new Date(t).getHours() % step) continue
    out.push([(t - t0) / Math.max(1, t1 - t0), hourLabel(t)])
  }
  return out
}

/** About [n] evenly spaced labels for index-based charts. [centred]: bars (slot centres). */
export function indexTicks(labels: string[], n = 4, centred = false): [number, string][] {
  const len = labels.length
  if (!len) return []
  const k = Math.min(n, len)
  const idx = Array.from(new Set(Array.from({ length: k }, (_, i) => Math.round((i * (len - 1)) / Math.max(1, k - 1)))))
  return idx.map((i) => [centred ? (i + 0.5) / len : i / Math.max(1, len - 1), labels[i]])
}

export const dateLabels = (isos: string[]) => isos.map(dayMonth)

/** Axis frame: y values down the left, x labels underneath. */
export function Frame({ ticks, fmt, height, xt, children, indent, readout }: { ticks?: number[]; fmt?: (v: number) => string; height: number; xt?: [number, string][]; children: any; indent?: number; readout?: (p: number) => string | null }) {
  const top = ticks?.length ? ticks[ticks.length - 1] : 1
  // Touch and drag to read any point; let go and it disappears.
  const [pos, setPos] = useState<number | null>(null)
  const at = (e: PointerEvent) => { const r = (e.currentTarget as HTMLElement).getBoundingClientRect(); setPos(Math.max(0, Math.min(1, (e.clientX - r.left) / r.width))) }
  const label = pos != null && readout ? readout(pos) : null
  return (
    <div class="chart-frame">
      <div class="chart">
        {ticks && fmt && <div class="yaxis" style={{ height }}>{ticks.map((t) => <span style={{ bottom: `${(t / top) * 100}%` }}>{fmt(t)}</span>)}</div>}
        <div class="plot" style={readout ? { touchAction: 'pan-y' } : undefined}
          onPointerDown={readout ? (e: any) => { (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId); at(e) } : undefined}
          onPointerMove={readout ? (e: any) => { if (pos != null) at(e) } : undefined}
          onPointerUp={readout ? () => setPos(null) : undefined} onPointerCancel={readout ? () => setPos(null) : undefined} onPointerLeave={readout ? () => setPos(null) : undefined}>
          {children}
          {label && <><i class="scrub-line" style={{ left: `${pos! * 100}%` }} /><span class="scrub-label" style={{ left: `${pos! * 100}%`, transform: `translateX(${pos! < 0.2 ? 0 : pos! > 0.8 ? -100 : -50}%)` }}>{label}</span></>}
        </div>
      </div>
      {xt && xt.length > 0 && <div class="xaxis" style={{ marginLeft: `${indent ?? (ticks && fmt ? 44 : 0)}px` }}>{xt.map(([p, l]) => <span style={{ left: `${p * 100}%`, transform: `translateX(${p < 0.04 ? 0 : p > 0.96 ? -100 : -50}%)` }}>{l}</span>)}</div>}
    </div>
  )
}

const Grid = ({ ticks, y }: { ticks?: number[]; y: (v: number) => number }) =>
  <>{ticks?.slice(1).map((t) => <line x1={0} x2={W} y1={y(t)} y2={y(t)} stroke="var(--line)" stroke-width={1} opacity={0.6} vector-effect="non-scaling-stroke" />)}</>

export function Wave({ points, typical = [], shade = [], now, height = 140, axes = true, overlay }: { points: number[][]; typical?: number[][]; shade?: number[][]; now?: number; height?: number; axes?: boolean; overlay?: number[][] }) {
  if (points.length < 2) return null
  const t0 = points[0][0], t1 = points[points.length - 1][0]
  const ticks = axes ? niceTicks(Math.max(0.5, ...points.map((p) => p[1]), ...typical.map((p) => p[1])), 3) : undefined
  const max = ticks ? ticks[ticks.length - 1] : Math.max(1, ...points.map((p) => p[1]), ...typical.map((p) => p[1])) * 1.1
  const x = (t: number) => ((t - t0) / Math.max(1, t1 - t0)) * W
  const y = (v: number) => height - (v / max) * height
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(p[1]).toFixed(1)}`).join('')
  // Volatility overlay: its own scale (right), read in the scrub label and the legend.
  const oTicks = overlay && overlay.length > 1 ? niceTicks(Math.max(1, ...overlay.map((p) => p[1])), 3) : undefined
  const oMax = oTicks ? oTicks[oTicks.length - 1] : 1
  const oLine = overlay && overlay.length > 1 ? overlay.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${(height - (p[1] / oMax) * height).toFixed(1)}`).join('') : ''
  const near = (arr: number[][], t: number) => arr.reduce((a, b) => (Math.abs(b[0] - t) < Math.abs(a[0] - t) ? b : a))
  return (<>
    <Frame ticks={ticks} fmt={(v) => `${v} mg`} height={height} xt={timeTicks(t0, t1, axes ? 5 : 4)}
      readout={(p) => { const t = t0 + p * (t1 - t0); const pt = near(points, t); return `${time(pt[0])} · ≈ ${pt[1].toFixed(1)} mg${oLine ? ` · volatility ≈ ${near(overlay!, t)[1].toFixed(1)} mg/h` : ''}` }}>
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <Grid ticks={ticks} y={y} />
      {shade.map(([a, b]) => b > a && <rect x={x(Math.max(a, t0))} y={0} width={Math.max(0, x(Math.min(b, t1)) - x(Math.max(a, t0)))} height={height} fill="var(--line)" opacity={0.35} />)}
      {typical.length > 1 && <path d={typical.map((p, i) => `${i ? 'L' : 'M'}${x(p[0])},${y(p[1])}`).join('')} fill="none" stroke="var(--muted)" stroke-dasharray="6 5" opacity={0.6} />}
      <path d={`${line}L${W},${height}L0,${height}Z`} fill="var(--primary)" opacity={0.25} />
      <path d={line} fill="none" stroke="var(--primary)" stroke-width={2.5} />
      {oLine && <path d={oLine} fill="none" stroke="var(--tertiary)" stroke-width={1.5} />}
      {now && now >= t0 && now <= t1 && <line x1={x(now)} x2={x(now)} y1={0} y2={height} stroke="var(--muted)" />}
    </svg>
    </Frame>
    {oLine && <div class="small muted" style={{ textAlign: 'right' }}><span style={{ color: 'var(--tertiary)' }}>━</span> Volatility (mg/h), right scale 0–{oMax}</div>}
  </>)
}

export function Bars({ values, line, highs, faded, height = 140, fmt, x }: { values: number[]; line?: number[]; highs?: (number | null)[]; faded?: boolean[]; height?: number; fmt?: (v: number) => string; x?: string[] }) {
  if (!values.length) return null
  const dataMax = Math.max(0.001, ...values, ...(line ?? []), ...(highs ?? []).map((h) => h ?? 0))
  const ticks = fmt ? niceTicks(dataMax, 3) : undefined
  const max = ticks ? ticks[ticks.length - 1] : Math.max(1, dataMax) * 1.1
  const slot = W / values.length, w = slot * 0.7
  const y = (v: number) => height - (v / max) * height
  return (
    <Frame ticks={ticks} fmt={fmt} height={height} xt={x ? indexTicks(x, 4, true) : undefined}
      readout={(p) => { const i = Math.min(values.length - 1, Math.floor(p * values.length)); return `${x?.[i] ? x[i] + ' · ' : ''}${fmt ? fmt(Number(values[i].toFixed(1))) : values[i].toFixed(1)}${line?.[i] != null ? ` (line ${line[i].toFixed(1)})` : ''}` }}>
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <Grid ticks={ticks} y={y} />
      <defs><pattern id="hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="2" height="6" fill="var(--primary)" /></pattern></defs>
      {values.map((v, i) => (
        <g>
          <rect x={i * slot + (slot - w) / 2} y={y(v)} width={w} height={height - y(v)} fill="var(--primary)" opacity={faded?.[i] ? 0.45 : 1} />
          {highs?.[i] != null && highs[i]! > v && <rect x={i * slot + (slot - w) / 2} y={y(highs[i]!)} width={w} height={y(v) - y(highs[i]!)} fill="url(#hatch)" />}
        </g>
      ))}
      {line && line.length > 1 && <path d={line.map((v, i) => `${i ? 'L' : 'M'}${i * slot + slot / 2},${y(v)}`).join('')} fill="none" stroke="var(--secondary)" stroke-width={2} />}
    </svg>
    </Frame>
  )
}

export function Line({ values, second, stepped, color = 'var(--primary)', height = 110, fmt, x, top }: { values: number[]; second?: number[]; stepped?: boolean; color?: string; height?: number; fmt?: (v: number) => string; x?: string[]; top?: number }) {
  const all = [...values, ...(second ?? [])]
  if (all.length < 2) return null
  const ticks = fmt ? niceTicks(top ?? Math.max(0.001, ...all), 3) : undefined
  const max = ticks ? ticks[ticks.length - 1] : Math.max(0.001, ...all) * 1.1
  const path = (vs: number[]) => {
    const step = W / Math.max(1, vs.length - 1)
    return vs.map((v, i) => {
      const px = i * step, py = height - (v / max) * height
      if (!i) return `M${px},${py}`
      return stepped ? `H${px}V${py}` : `L${px},${py}`
    }).join('')
  }
  return (
    <Frame ticks={ticks} fmt={fmt} height={height} xt={x ? indexTicks(x) : undefined}
      readout={(p) => { const n = Math.max(values.length, second?.length ?? 0); const i = Math.round(p * (n - 1)); const f = (v?: number) => (v == null ? '–' : fmt ? fmt(Number(v.toFixed(1))) : v.toFixed(1)); return `${x?.[i] ? x[i] + ' · ' : ''}${f(values[i])}${second ? ` / ${f(second[i])}` : ''}` }}>
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <Grid ticks={ticks} y={(v) => height - (v / max) * height} />
      {second && second.length > 1 && <path d={path(second)} fill="none" stroke="var(--tertiary)" stroke-width={2} />}
      {values.length > 1 && <path d={path(values)} fill="none" stroke={color} stroke-width={2.5} />}
    </svg>
    </Frame>
  )
}

export function Heatmap({ grid }: { grid: number[][] }) {
  const max = Math.max(0.001, ...grid.flat())
  const cw = W / 24, ch = 18
  return (
    <Frame height={ch * 7} indent={20} xt={[0, 6, 12, 18].map((h) => [h / 24, hourLabel(new Date(2000, 0, 1, h).getTime())] as [number, string])}>
    <div class="chart"><div class="yaxis days">{['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d) => <span>{d}</span>)}</div><div class="plot">
    <svg viewBox={`0 0 ${W} ${ch * 7}`} width="100%">
      {grid.map((row, d) => row.map((v, h) => (
        <rect x={h * cw + 0.5} y={d * ch + 0.5} width={cw - 1} height={ch - 1} rx={2} fill={v > 0 ? 'var(--primary)' : 'var(--line)'} opacity={v > 0 ? 0.15 + 0.85 * (v / max) : 0.3} />
      )))}
    </svg>
    </div></div>
    </Frame>
  )
}

export function Barcode({ rows }: { rows: boolean[][] }) {
  if (!rows.length) return null
  const h = 5
  return (
    <Frame height={rows.length * h} xt={[0, 6, 12, 18].map((hr) => [hr / 24, hourLabel(new Date(2000, 0, 1, hr).getTime())] as [number, string])}>
    <svg viewBox={`0 0 ${W} ${rows.length * h}`} width="100%" preserveAspectRatio="none">
      {rows.map((cells, r) => cells.map((on, c) => (
        <rect x={(c * W) / cells.length} y={r * h} width={W / cells.length + 0.3} height={h - 1} fill={on ? 'var(--primary)' : 'var(--tertiary)'} opacity={on ? 0.9 : 0.35} />
      )))}
    </svg>
    </Frame>
  )
}

export const Meter = ({ value }: { value: number }) => <div class="bar"><i style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} /></div>

/** 24-hour craving forecast: likelihood curve, hourly dots coloured by likely strength, sleep shaded, now marked. */
export function ForecastChart({ points, now, height = 130 }: { points: number[][]; now: number; height?: number }) {
  if (points.length < 2) return null
  const t0 = points[0][0], t1 = points[points.length - 1][0]
  const ticks = niceTicks(Math.max(0.1, ...points.map((p) => p[1])), 3)
  const max = ticks[ticks.length - 1]
  const x = (t: number) => ((t - t0) / Math.max(1, t1 - t0)) * W
  const y = (v: number) => height - (v / max) * height
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(p[1]).toFixed(1)}`).join('')
  const sleep: number[][] = []
  points.forEach((p, i) => {
    if (p[3] && (i === 0 || !points[i - 1][3])) sleep.push([p[0], p[0]])
    if (p[3] && sleep.length) sleep[sleep.length - 1][1] = points[Math.min(i + 1, points.length - 1)][0]
  })
  const dot = (s: number) => `hsl(${160 - ((Math.min(10, Math.max(1, s)) - 1) / 9) * 160}, 45%, 55%)`
  return (
    <Frame ticks={ticks} fmt={(v) => `${Math.round(v * 100)}%`} height={height} xt={timeTicks(t0, t1)}
      readout={(p) => { const t = t0 + p * (t1 - t0); const pt = points.reduce((a, b) => (Math.abs(b[0] - t) < Math.abs(a[0] - t) ? b : a)); return pt[3] ? `${time(pt[0])} · asleep` : `${time(pt[0])} · ${Math.round(pt[1] * 100)}% · strength ≈ ${Math.round(pt[2])}` }}>
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <Grid ticks={ticks} y={y} />
      {sleep.map(([a, b]) => <rect x={x(a)} y={0} width={Math.max(0, x(b) - x(a))} height={height} fill="var(--line)" opacity={0.35} />)}
      <path d={`${line}L${W},${height}L0,${height}Z`} fill="var(--primary)" opacity={0.2} />
      <path d={line} fill="none" stroke="var(--primary)" stroke-width={2.5} />
      {points.filter((p, i) => i % 4 === 0 && !p[3] && p[1] > 0.02).map((p) => <circle cx={x(p[0])} cy={y(p[1])} r={3.5} fill={dot(p[2])} />)}
      {now >= t0 && now <= t1 && <line x1={x(now)} x2={x(now)} y1={0} y2={height} stroke="var(--muted)" />}
    </svg>
    </Frame>
  )
}

/** Receptor load: past solid, plan dashed, "stay here" faint, typical non-user range shaded, today marked. */
export function ReceptorChart({ history, plan, stay, typical, height = 150, dates = [] }: { history: number[]; plan: number[]; stay: number[]; typical: number; height?: number; dates?: string[] }) {
  const total = history.length + Math.max(plan.length, stay.length)
  if (total < 2) return null
  const x = (i: number) => (i / Math.max(1, total - 1)) * W
  const y = (v: number) => height - (Math.max(0, Math.min(1, v)) / 1) * height
  const ticks = [0, 0.25, 0.5, 0.75, 1]
  const today = Math.max(0, history.length - 1)
  const path = (vs: number[], off: number) => vs.map((v, i) => `${i ? 'L' : 'M'}${x(off + i).toFixed(1)},${y(v).toFixed(1)}`).join('')
  return (
    <Frame ticks={ticks} fmt={(v) => `${Math.round(v * 100)}%`} height={height} xt={dates.length ? indexTicks(dateLabels(dates), 4) : undefined}
      readout={(p) => { const i = Math.round(p * (total - 1)); const v = i < history.length ? history[i] : plan[i - today] ?? stay[i - today]; return `${dates[i] ? dayMonth(dates[i]) + ' · ' : ''}≈ ${Math.round((v ?? 0) * 100)}%${i >= history.length ? ' (plan)' : ''}` }}>
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <Grid ticks={ticks} y={y} />
      <rect x={0} y={y(typical)} width={W} height={height - y(typical)} fill="var(--tertiary)" opacity={0.15} />
      {stay.length > 1 && <path d={path(stay, today)} fill="none" stroke="var(--muted)" stroke-width={2} stroke-dasharray="6 5" opacity={0.6} />}
      {plan.length > 1 && <path d={path(plan, today)} fill="none" stroke="var(--primary)" stroke-width={2.5} stroke-dasharray="6 5" />}
      {history.length > 1 && <path d={path(history, 0)} fill="none" stroke="var(--primary)" stroke-width={3} />}
      <line x1={x(today)} x2={x(today)} y1={0} y2={height} stroke="var(--muted)" />
    </svg>
    </Frame>
  )
}

export const KIND_COLORS: Record<string, string> = { GUM: '#8fc7b8', POUCH: '#ffb35c', LOZENGE: '#b8d98f', PATCH: '#9fb3e0', VAPE: '#e0443a', CIGARETTE: '#8a6a5a', OTHER: '#b0a49c' }

/** A day's doses across 24 hours: one dot per dose, sized by pieces, coloured by type. */
export function DoseStrip({ doses, dayStart }: { doses: { at: number; pieces: number; kind: string }[]; dayStart: number }) {
  return (
    <svg viewBox={`0 0 ${W} 40`} width="100%" height={40} preserveAspectRatio="none">
      <line x1={0} x2={W} y1={20} y2={20} stroke="var(--line)" />
      {[6, 12, 18].map((h) => <line x1={(h / 24) * W} x2={(h / 24) * W} y1={12} y2={28} stroke="var(--line)" />)}
      {doses.map((d) => { const x = ((((d.at - dayStart) / 3600000) % 24 + 24) % 24) / 24 * W; return <circle cx={x} cy={20} r={4 + Math.min(10, d.pieces * 4)} fill={KIND_COLORS[d.kind] ?? 'var(--primary)'} opacity={0.85} /> })}
    </svg>
  )
}

/** Weekly stacked bars of pieces by delivery method. */
export function StackedBars({ columns, height = 120 }: { columns: Record<string, number>[]; height?: number }) {
  const totals = columns.map((c) => Object.values(c).reduce((a, b) => a + b, 0))
  const max = Math.max(1, ...totals) * 1.1
  const slot = W / Math.max(1, columns.length), w = slot * 0.7
  return (
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      {columns.map((c, i) => { let y = height; return Object.entries(c).map(([k, v]) => { const h = (v / max) * height; y -= h; return <rect x={i * slot + (slot - w) / 2} y={y} width={w} height={h} fill={KIND_COLORS[k] ?? 'var(--primary)'} /> }) })}
    </svg>
  )
}
