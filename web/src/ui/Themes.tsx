import { Core } from '../core'
import * as S from '../store'

/** Settings → Appearance → More themes: kept on its own screen so Settings stays uncluttered. */
export function Themes({ back }: { back: () => void }) {
  const s = S.settings.value
  const current = s.theme ?? 'firewatch'
  const mode = s.themeMode ?? 'system'
  const systemDark = typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
  const check = (key: string, label: string, sub: string, def = false) => (
    <label class="row small"><input type="checkbox" style={{ width: 'auto' }} checked={s[key] ?? def} onChange={() => S.updateSettings({ [key]: !(s[key] ?? def) })} />
      <span><b>{label}</b><br /><span class="muted">{sub}</span></span></label>
  )
  return (
    <main>
      <div class="row"><button class="btn text" onClick={back}>‹ Settings</button><h2>More themes</h2></div>
      <div class="small">Light or dark</div>
      <div class="row wrap">{[['system', 'Follow device'], ['light', 'Always light'], ['dark', 'Always dark']].map(([v, l]) =>
        <button class={`chip ${mode === v ? 'on' : ''}`} onClick={() => S.updateSettings({ themeMode: v })}>{l}</button>)}</div>
      <div class="theme-grid">{Core.themes().map((t) => {
        const p = Core.palette(t.id, mode, systemDark, !!s.trueBlack, s.calmColours ?? true, !!s.colourBlindCharts)
        return <button class={`theme-card ${current === t.id ? 'on' : ''}`} aria-pressed={current === t.id} aria-label={`${t.name} theme: ${t.feel}`}
          style={{ background: p.bg, color: p.text, borderColor: current === t.id ? p.primary : p.line }} onClick={() => S.updateSettings({ theme: t.id })}>
          <span class="swatches">{[p.primary, p.secondary, p.tertiary, p.heat[3]].map((c) => <i style={{ background: c }} />)}</span>
          <b>{t.name}</b><span style={{ color: p.muted }}>{t.feel}</span>
        </button>
      })}</div>
      <h3 class="label">Colour options</h3>
      {check('calmColours', 'Calmer colours, no red', 'Softer colours everywhere, and no red at all.', true)}
      {check('colourBlindCharts', 'Colour-blind-safe charts', 'Product colours that stay distinct for the common kinds of colour blindness.')}
      {check('trueBlack', 'True black', 'Pure black backgrounds in dark mode (saves battery on OLED screens).')}
      <div class="muted">Only colours change: tier names stay the same. Every theme is checked for readable contrast.</div>
    </main>
  )
}
