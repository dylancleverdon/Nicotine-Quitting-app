// Suggestions and bug reports go to the maker's Google Form (it only accepts new responses).
// The browser can't read the form's reply (no-cors), so a failed request (offline) is queued.
import { Core } from './core'

const QUEUE = 'fw_feedback_queue'
const read = (): string[] => { try { return JSON.parse(localStorage.getItem(QUEUE) ?? '[]') } catch { return [] } }
const write = (q: string[]) => { try { localStorage.setItem(QUEUE, JSON.stringify(q.slice(-50))) } catch { /* ignore */ } }

async function post(body: string): Promise<boolean> {
  try {
    await fetch(Core.feedbackUrl(), { method: 'POST', mode: 'no-cors', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body })
    return true
  } catch { return false }
}

export const appInfo = () => `Web ${__APP_VERSION__} · ${navigator.userAgent.replace(/\s+/g, ' ').slice(0, 120)}`
export const savedName = () => { try { return localStorage.getItem('fw_feedback_name') ?? '' } catch { return '' } }

export async function sendFeedback(type: string, suggestion: string, details: string, name: string): Promise<boolean> {
  try { localStorage.setItem('fw_feedback_name', name.trim()) } catch { /* ignore */ }
  const body = Core.feedbackBody(type, suggestion, details, name, appInfo())
  if (await post(body)) return true
  write([...read(), body])
  return false
}

export async function flushFeedback() {
  const q = read()
  if (!q.length) return
  const left: string[] = []
  for (const b of q) if (!(await post(b))) left.push(b)
  write(left)
}
