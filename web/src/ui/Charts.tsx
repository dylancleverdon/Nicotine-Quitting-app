// Small SVG charts. Every figure is an estimate; charts are for shape and comparison.
const W = 320

export function Wave({ points, typical = [], shade = [], now, height = 140 }: { points: number[][]; typical?: number[][]; shade?: number[][]; now?: number; height?: number }) {
  if (points.length < 2) return null
  const t0 = points[0][0], t1 = points[points.length - 1][0]
  const max = Math.max(1, ...points.map((p) => p[1]), ...typical.map((p) => p[1])) * 1.1
  const x = (t: number) => ((t - t0) / Math.max(1, t1 - t0)) * W
  const y = (v: number) => height - (v / max) * height
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(p[0]).toFixed(1)},${y(p[1]).toFixed(1)}`).join('')
  return (
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      {shade.map(([a, b]) => b > a && <rect x={x(Math.max(a, t0))} y={0} width={Math.max(0, x(Math.min(b, t1)) - x(Math.max(a, t0)))} height={height} fill="var(--line)" opacity={0.35} />)}
      {typical.length > 1 && <path d={typical.map((p, i) => `${i ? 'L' : 'M'}${x(p[0])},${y(p[1])}`).join('')} fill="none" stroke="var(--muted)" stroke-dasharray="6 5" opacity={0.6} />}
      <path d={`${line}L${W},${height}L0,${height}Z`} fill="var(--primary)" opacity={0.25} />
      <path d={line} fill="none" stroke="var(--primary)" stroke-width={2.5} />
      {now && now >= t0 && now <= t1 && <line x1={x(now)} x2={x(now)} y1={0} y2={height} stroke="var(--muted)" />}
    </svg>
  )
}

export function Bars({ values, line, highs, faded, height = 140 }: { values: number[]; line?: number[]; highs?: (number | null)[]; faded?: boolean[]; height?: number }) {
  if (!values.length) return null
  const max = Math.max(1, ...values, ...(line ?? []), ...(highs ?? []).map((h) => h ?? 0)) * 1.1
  const slot = W / values.length, w = slot * 0.7
  const y = (v: number) => height - (v / max) * height
  return (
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <defs><pattern id="hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="2" height="6" fill="var(--primary)" /></pattern></defs>
      {values.map((v, i) => (
        <g>
          <rect x={i * slot + (slot - w) / 2} y={y(v)} width={w} height={height - y(v)} fill="var(--primary)" opacity={faded?.[i] ? 0.45 : 1} />
          {highs?.[i] != null && highs[i]! > v && <rect x={i * slot + (slot - w) / 2} y={y(highs[i]!)} width={w} height={y(v) - y(highs[i]!)} fill="url(#hatch)" />}
        </g>
      ))}
      {line && line.length > 1 && <path d={line.map((v, i) => `${i ? 'L' : 'M'}${i * slot + slot / 2},${y(v)}`).join('')} fill="none" stroke="var(--secondary)" stroke-width={2} />}
    </svg>
  )
}

export function Line({ values, second, stepped, color = 'var(--primary)', height = 110 }: { values: number[]; second?: number[]; stepped?: boolean; color?: string; height?: number }) {
  const all = [...values, ...(second ?? [])]
  if (all.length < 2) return null
  const max = Math.max(0.001, ...all) * 1.1
  const path = (vs: number[]) => {
    const step = W / Math.max(1, vs.length - 1)
    return vs.map((v, i) => {
      const px = i * step, py = height - (v / max) * height
      if (!i) return `M${px},${py}`
      return stepped ? `H${px}V${py}` : `L${px},${py}`
    }).join('')
  }
  return (
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      {second && second.length > 1 && <path d={path(second)} fill="none" stroke="var(--tertiary)" stroke-width={2} />}
      {values.length > 1 && <path d={path(values)} fill="none" stroke={color} stroke-width={2.5} />}
    </svg>
  )
}

export function Heatmap({ grid }: { grid: number[][] }) {
  const max = Math.max(0.001, ...grid.flat())
  const cw = W / 24, ch = 18
  return (
    <svg viewBox={`0 0 ${W} ${ch * 7}`} width="100%">
      {grid.map((row, d) => row.map((v, h) => (
        <rect x={h * cw + 0.5} y={d * ch + 0.5} width={cw - 1} height={ch - 1} rx={2} fill={v > 0 ? 'var(--primary)' : 'var(--line)'} opacity={v > 0 ? 0.15 + 0.85 * (v / max) : 0.3} />
      )))}
    </svg>
  )
}

export function Barcode({ rows }: { rows: boolean[][] }) {
  if (!rows.length) return null
  const h = 5
  return (
    <svg viewBox={`0 0 ${W} ${rows.length * h}`} width="100%" preserveAspectRatio="none">
      {rows.map((cells, r) => cells.map((on, c) => (
        <rect x={(c * W) / cells.length} y={r * h} width={W / cells.length + 0.3} height={h - 1} fill={on ? 'var(--primary)' : 'var(--tertiary)'} opacity={on ? 0.9 : 0.35} />
      )))}
    </svg>
  )
}

export const Meter = ({ value }: { value: number }) => <div class="bar"><i style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }} /></div>

/** 24-hour craving forecast: likelihood curve, hourly dots coloured by likely strength, sleep shaded, now marked. */
export function ForecastChart({ points, now, height = 130 }: { points: number[][]; now: number; height?: number }) {
  if (points.length < 2) return null
  const t0 = points[0][0], t1 = points[points.length - 1][0]
  const max = Math.max(0.2, ...points.map((p) => p[1])) * 1.15
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
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      {sleep.map(([a, b]) => <rect x={x(a)} y={0} width={Math.max(0, x(b) - x(a))} height={height} fill="var(--line)" opacity={0.35} />)}
      <path d={`${line}L${W},${height}L0,${height}Z`} fill="var(--primary)" opacity={0.2} />
      <path d={line} fill="none" stroke="var(--primary)" stroke-width={2.5} />
      {points.filter((p, i) => i % 4 === 0 && !p[3] && p[1] > 0.02).map((p) => <circle cx={x(p[0])} cy={y(p[1])} r={3.5} fill={dot(p[2])} />)}
      {now >= t0 && now <= t1 && <line x1={x(now)} x2={x(now)} y1={0} y2={height} stroke="var(--muted)" />}
    </svg>
  )
}

/** Receptor load: past solid, plan dashed, "stay here" faint, typical non-user range shaded, today marked. */
export function ReceptorChart({ history, plan, stay, typical, height = 150 }: { history: number[]; plan: number[]; stay: number[]; typical: number; height?: number }) {
  const total = history.length + Math.max(plan.length, stay.length)
  if (total < 2) return null
  const x = (i: number) => (i / Math.max(1, total - 1)) * W
  const y = (v: number) => height - (Math.max(0, Math.min(1.05, v)) / 1.05) * height
  const today = Math.max(0, history.length - 1)
  const path = (vs: number[], off: number) => vs.map((v, i) => `${i ? 'L' : 'M'}${x(off + i).toFixed(1)},${y(v).toFixed(1)}`).join('')
  return (
    <svg viewBox={`0 0 ${W} ${height}`} width="100%" height={height} preserveAspectRatio="none">
      <rect x={0} y={y(typical)} width={W} height={height - y(typical)} fill="var(--tertiary)" opacity={0.15} />
      {stay.length > 1 && <path d={path(stay, today)} fill="none" stroke="var(--muted)" stroke-width={2} stroke-dasharray="6 5" opacity={0.6} />}
      {plan.length > 1 && <path d={path(plan, today)} fill="none" stroke="var(--primary)" stroke-width={2.5} stroke-dasharray="6 5" />}
      {history.length > 1 && <path d={path(history, 0)} fill="none" stroke="var(--primary)" stroke-width={3} />}
      <line x1={x(today)} x2={x(today)} y1={0} y2={height} stroke="var(--muted)" />
    </svg>
  )
}
