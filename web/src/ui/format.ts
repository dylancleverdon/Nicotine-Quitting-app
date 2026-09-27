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
export const signedDuration = (m: number) => `${m >= 0 ? '+' : '−'}${duration(Math.abs(m) * 60000)}`
export const duration = (ms: number) => {
  const m = Math.max(0, Math.floor(ms / 60000)); const d = Math.floor(m / 1440); const h = Math.floor(m / 60) % 24
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m % 60}m` : `${m % 60}m`
}
export const ago = (ms: number) => (Date.now() - ms < 60000 ? 'just now' : `${duration(Date.now() - ms)} ago`)
export const dayTitle = (iso: string) => new Date(iso + 'T12:00').toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })
export const shortDate = (iso: string) => new Date(iso + 'T12:00').toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' })
export const minutesOfDay = (m: number) => time(new Date(2000, 0, 1, Math.floor(m / 60) % 24, m % 60).getTime())
export const cravingColor = (level: number) => {
  const f = (level - 1) / 9
  const a = [0x5f, 0xa8, 0x93], b = [0xe0, 0x44, 0x3a]
  return `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * f)).join(',')})`
}
export const heat = ['#241b17', '#4a3526', '#6e3f1f', '#9a4a1c', '#c8581c', '#e86a24', '#ff8f3a']
export const TAGS = ['Coffee', 'After food', 'Driving', 'Work', 'Stress', 'Drinking', 'Boredom']
export const isoToday = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10) }
