export const pieces = (x: number) => (Math.abs(x) < 0.05 ? '0' : x < 10 ? x.toFixed(1) : x.toFixed(0))
export const piecesLabel = (x: number) => `≈ ${pieces(x)} ${Math.abs(x - 1) < 0.05 ? 'piece' : 'pieces'}`
export const mg = (x: number) => `${x.toFixed(1)} mg`
/** "system" follows the device; 12-hour always shows AM/PM. Set from Settings. */
export let timeFormat = 'system'
export const setTimeFormat = (f: string) => { timeFormat = f }
export const time = (ms: number) => {
  if (timeFormat === '24h') return new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false })
  if (timeFormat === '12h') return new Date(ms).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true })
  return new Date(ms).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}
/** Axis label for a whole hour: "6 AM" / "18:00", following the time-format setting. */
export const hourLabel = (ms: number) => {
  const d = new Date(ms)
  const use24 = timeFormat === '24h' || (timeFormat === 'system' && !/[AP]M/i.test(new Date(2000, 0, 1, 13).toLocaleTimeString([], { hour: 'numeric' })))
  if (use24) return `${String(d.getHours()).padStart(2, '0')}:00`
  const h = d.getHours() % 12 || 12
  return `${h} ${d.getHours() < 12 ? 'AM' : 'PM'}`
}
/** "8 Sep" from an ISO date. */
export const dayMonth = (iso: string) => new Date(iso + 'T12:00').toLocaleDateString([], { day: 'numeric', month: 'short' })
export const signedDuration = (m: number) => `${m >= 0 ? '+' : '−'}${duration(Math.abs(m) * 60000)}`
export const duration = (ms: number) => {
  const m = Math.max(0, Math.floor(ms / 60000)); const d = Math.floor(m / 1440); const h = Math.floor(m / 60) % 24
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m % 60}m` : `${m % 60}m`
}
export const ago = (ms: number) => (Date.now() - ms < 60000 ? 'just now' : `${duration(Date.now() - ms)} ago`)
export const dayTitle = (iso: string) => new Date(iso + 'T12:00').toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })
export const shortDate = (iso: string) => new Date(iso + 'T12:00').toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' })
export const minutesOfDay = (m: number) => time(new Date(2000, 0, 1, Math.floor(m / 60) % 24, m % 60).getTime())
import { palette } from '../theme'
const hexRgb = (h: string) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
export const cravingColor = (level: number) => {
  const f = (level - 1) / 9
  const a = hexRgb(palette.value?.cravingLow ?? '#5fa893'), b = hexRgb(palette.value?.cravingHigh ?? '#e0443a')
  return `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * f)).join(',')})`
}
export const TAGS = ['Coffee', 'After food', 'Driving', 'Work', 'Stress', 'Drinking', 'Boredom']
export const isoToday = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10) }

/** "2 days 6 hours of 3 days held toward Bonfire", from the step-down count. */
export function stepText(p: { held: number; needed: number; heldHours: number; neededHours: number; unlocked: boolean; next: { tier: string } }) {
  if (p.unlocked) return `${p.needed} of ${p.needed} days held: unlocked`
  const per = p.neededHours / Math.max(1, p.needed)
  const days = Math.floor(p.heldHours / per + 1e-9), hours = Math.round(p.heldHours - days * per)
  const d = `${days} ${days === 1 ? 'day' : 'days'}`
  return `${hours > 0 ? `${d} ${hours} ${hours === 1 ? 'hour' : 'hours'}` : d} of ${p.needed} days held toward ${p.next.tier}`
}
