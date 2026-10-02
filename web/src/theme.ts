// Colour themes: palettes come from the shared core (same colours as Android).
import { signal } from '@preact/signals'
import { Core } from './core'

export interface Palette {
  bg: string; surface: string; surface2: string; surface3: string; text: string; muted: string; line: string
  primary: string; onPrimary: string; primarySoft: string; onPrimarySoft: string; secondary: string; tertiary: string
  heat: string[]; onHeat: string[]; kinds: Record<string, string>; cravingLow: string; cravingHigh: string; dark: boolean
}

export const palette = signal<Palette | null>(null)
const media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null

/** Apply the chosen theme as CSS variables (and the browser's theme colour). */
export function applyTheme(s: any) {
  const p = Core.palette(s.theme ?? 'firewatch', s.themeMode ?? 'system', media?.matches ?? true, !!s.trueBlack, s.calmColours ?? true, !!s.colourBlindCharts)
  palette.value = p
  const r = document.documentElement.style
  const vars: Record<string, string> = {
    '--bg': p.bg, '--surface': p.surface, '--surface2': p.surface2, '--surface3': p.surface3, '--text': p.text, '--muted': p.muted,
    '--line': p.line, '--primary': p.primary, '--on-primary': p.onPrimary, '--primary-soft': p.primarySoft, '--on-primary-soft': p.onPrimarySoft,
    '--secondary': p.secondary, '--tertiary': p.tertiary,
  }
  Object.entries(vars).forEach(([k, v]) => r.setProperty(k, v))
  r.setProperty('color-scheme', p.dark ? 'dark' : 'light')
  document.documentElement.dataset.theme = s.theme ?? 'firewatch'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', p.bg)
}

export function onSystemThemeChange(f: () => void) { media?.addEventListener?.('change', f) }

export const kindColor = (k: string) => palette.value?.kinds[k] ?? '#b0a49c'
export const heatColor = (level: number) => palette.value?.heat[Math.max(0, Math.min(6, level))] ?? '#241b17'
export const onHeatColor = (level: number) => palette.value?.onHeat[Math.max(0, Math.min(6, level))] ?? 'var(--text)'
