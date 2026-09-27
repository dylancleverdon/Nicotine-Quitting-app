import { useState } from 'preact/hooks'
import { Core } from '../core'
import * as S from '../store'
import { minutesOfDay } from './format'

const KINDS = ['GUM', 'POUCH', 'LOZENGE', 'PATCH', 'VAPE', 'CIGARETTE', 'OTHER']
const DEFAULT_ABS: Record<string, number> = { GUM: 0.5, POUCH: 0.4, LOZENGE: 0.6, PATCH: 0.8, VAPE: 0.5, CIGARETTE: 0.1, OTHER: 0.5 }
const SPEED: Record<string, string> = { VAPE: 'SPIKE', CIGARETTE: 'SPIKE', PATCH: 'FLAT' }
const toMin = (v: string) => { const [h, m] = v.split(':').map(Number); return h * 60 + m }
const toTime = (m: number) => `${String(Math.floor(m / 60) % 24).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`

export function Settings({ toast, go, updateInfo }: { toast: (m: string) => void; go: (r: string) => void; updateInfo: string }) {
  const s = S.settings.value
  const snap = S.snapshot.value!
  const [editing, setEditing] = useState<any>(null)
  const exportNow = () => {
    const blob = new Blob([JSON.stringify(S.exportFile(), null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `firewatch-backup-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
  }
  const importFrom = async (file: File) => {
    try {
      const data = JSON.parse(await file.text())
      if (!Array.isArray(data.records)) throw new Error()
      const replace = confirm('Replace what is in this browser with the file? (Cancel = add to it)')
      const n = await S.importFile(data, replace)
      toast(replace ? 'Replaced with the file.' : `Added ${n} records.`)
    } catch { toast("That file isn't a Firewatch backup.") }
  }
  const target = snap.target
  return (
    <main>
      <h1>Settings</h1>
      <div class="card"><h3>Updates</h3><div class="small">Web version {__APP_VERSION__}</div><div class="muted">{updateInfo}</div>
        <div class="muted">Firewatch updates itself the next time you open it. Your data is never touched by an update.</div></div>

      <h3 class="label">Your plan</h3>
      <div class="small">{target ? `Working at ${target.label}` : 'Your target appears once your baseline is known.'}</div>
      {target && target.pieces > 0 && <button class="btn outline" onClick={() => {
        const i = snap.ladder.findIndex((r) => Math.abs(r.pieces - target.pieces) < 1e-6); const up = snap.ladder[Math.max(0, i - 1)]
        S.setTarget(up.pieces, 'up'); toast(`Stepped back to ${up.label}. That's normal.`)
      }}>Step back up a rung</button>}
      <div class="small">Hold each rung for</div>
      <div class="row wrap">{[[3, '3 days'], [7, '1 week'], [14, '2 weeks'], [21, '3 weeks']].map(([d, l]) =>
        <button class={`chip ${s.holdDays === d ? 'on' : ''}`} onClick={() => S.updateSettings({ holdDays: d })}>{l}</button>)}</div>
      <div class="small">Morning delay goal</div>
      <div class="row wrap">{[[0, 'Off'], [15, '15 min'], [30, '30 min'], [60, '1 hour'], [90, '1½ hours']].map(([m, l]) =>
        <button class={`chip ${s.morningDelayMinutes === m ? 'on' : ''}`} onClick={() => S.updateSettings({ morningDelayMinutes: m })}>{l}</button>)}</div>
      <label class="row small"><input type="checkbox" style={{ width: 'auto' }} checked={s.windDown} onChange={() => S.updateSettings({ windDown: !s.windDown })} /> Wind down: no "clear for one" in the last hour before bed</label>
      <label class="row small"><input type="checkbox" style={{ width: 'auto' }} checked={s.dailyCheckIn} onChange={() => S.updateSettings({ dailyCheckIn: !s.dailyCheckIn })} /> Daily check-in (cravings, mood, sleep)</label>
      {snap.baselineState !== 'complete' && <button class="btn outline" onClick={() => go('backfill')}>Back-date my baseline week</button>}

      <h3 class="label">Usual sleep schedule</h3>
      <div class="row"><span class="grow small">Wake up</span><input type="time" style={{ width: 130 }} value={toTime(s.wakeMinutes)} onChange={(e) => S.updateSettings({ wakeMinutes: toMin((e.target as HTMLInputElement).value) })} /></div>
      <div class="row"><span class="grow small">Go to bed</span><input type="time" style={{ width: 130 }} value={toTime(s.sleepMinutes)} onChange={(e) => S.updateSettings({ sleepMinutes: toMin((e.target as HTMLInputElement).value) })} /></div>

      <h3 class="label">Products</h3>
      <div class="list">{S.products.value.map((p) => (
        <div class="item" onClick={() => setEditing(p)}><div>{p.name}<br /><span>{p.kind.toLowerCase()} · {p.labelMg} mg · ~{Math.round(p.absorption * 100)}% absorbed</span></div>
          <input type="checkbox" style={{ width: 'auto' }} checked={p.onHome} onClick={(e) => e.stopPropagation()} onChange={() => S.save('product', { ...p, onHome: !p.onHome })} /></div>
      ))}</div>
      <button class="btn outline" onClick={() => setEditing({ id: '', name: '', kind: 'POUCH', labelMg: 0, absorption: 0.4, speed: 'BUILD', onHome: true })}>Add a product</button>
      <div class="row"><span class="grow small">One piece is</span>
        <select style={{ width: 200 }} value={s.referenceProductId} onChange={(e) => S.updateSettings({ referenceProductId: (e.target as HTMLSelectElement).value })}>
          {S.products.value.map((p) => <option value={p.id}>{p.name}</option>)}</select></div>

      <h3 class="label">Money</h3>
      <div class="row"><input style={{ width: 70 }} value={s.currency} onChange={(e) => S.updateSettings({ currency: (e.target as HTMLInputElement).value.slice(0, 3) })} />
        <input placeholder="Saving toward" value={s.rewardName} onChange={(e) => S.updateSettings({ rewardName: (e.target as HTMLInputElement).value })} />
        <input style={{ width: 110 }} placeholder="Costs" inputMode="decimal" value={s.rewardCost || ''} onChange={(e) => S.updateSettings({ rewardCost: Number((e.target as HTMLInputElement).value) || 0 })} /></div>

      <h3 class="label">Your data</h3>
      <div class="muted">Everything stays in this browser. Export now and then (to Files, iCloud Drive or email). The same file works in the Android app.</div>
      <div class="row"><button class="btn" onClick={exportNow}>Export</button>
        <label class="btn outline">Import<input type="file" accept="application/json,.json" style={{ display: 'none' }} onChange={(e) => { const f = (e.target as HTMLInputElement).files?.[0]; if (f) importFrom(f) }} /></label></div>

      <div class="footer">Firewatch by Baastik Labs · © 2026 Baastik Labs<br />≈ All nicotine figures are estimates.</div>
      {editing && <ProductEditor product={editing} onClose={() => setEditing(null)} />}
    </main>
  )
}

function ProductEditor({ product, onClose }: { product: any; onClose: () => void }) {
  const [p, setP] = useState({ ...product, absPct: Math.round(product.absorption * 100) })
  const valid = p.name.trim() && Number(p.labelMg) > 0 && p.absPct > 0 && p.absPct <= 100
  return (
    <div class="sheet-bg" onClick={onClose}><div class="sheet" onClick={(e) => e.stopPropagation()}>
      <h2>{product.id ? 'Edit product' : 'Add a product'}</h2>
      <input placeholder="Name" value={p.name} onInput={(e) => setP({ ...p, name: (e.target as HTMLInputElement).value })} />
      <div class="row wrap">{KINDS.map((k) => <button class={`chip ${p.kind === k ? 'on' : ''}`} onClick={() => setP({ ...p, kind: k, speed: SPEED[k] ?? 'BUILD', absPct: product.id ? p.absPct : Math.round(DEFAULT_ABS[k] * 100) })}>{k.toLowerCase()}</button>)}</div>
      <label class="small">Strength on the packaging (mg)<input inputMode="decimal" value={p.labelMg || ''} onInput={(e) => setP({ ...p, labelMg: Number((e.target as HTMLInputElement).value) })} /></label>
      <label class="small">Estimated % absorbed<input inputMode="numeric" value={p.absPct} onInput={(e) => setP({ ...p, absPct: Number((e.target as HTMLInputElement).value) })} /></label>
      <label class="small">Price of one (optional)<input inputMode="decimal" value={p.unitPrice || ''} onInput={(e) => setP({ ...p, unitPrice: Number((e.target as HTMLInputElement).value) || 0 })} /></label>
      <div class="row"><button class="btn" disabled={!valid} onClick={() => {
        const { absPct, ...rest } = p
        S.save('product', { ...rest, id: rest.id || Core.newId(), createdAt: rest.createdAt || Date.now(), absorption: absPct / 100 }); onClose()
      }}>Save</button>{product.id && <button class="btn text" onClick={() => { S.save('product', { ...product, archived: true, onHome: false }); onClose() }}>Remove</button>}</div>
    </div></div>
  )
}

export { minutesOfDay }
