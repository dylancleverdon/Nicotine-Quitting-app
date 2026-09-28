// Suggestions and bug reports go to the maker's Google Form (it only accepts new responses).
// If sending fails it's saved; it is never retried automatically. Settings → Unsent suggestions
// has "Send now" (one tap = one attempt) and "Delete".
import { Core } from './core'

export interface Unsent { id: string; at: number; type: string; text: string; body: string; error: string }

const KEY = 'fw_feedback_unsent'
export const unsent = (): Unsent[] => { try { return JSON.parse(localStorage.getItem(KEY) ?? '[]') } catch { return [] } }
const save = (q: Unsent[]) => { try { localStorage.setItem(KEY, JSON.stringify(q.slice(-50))) } catch { /* ignore */ } }

/** One attempt. The browser can't read the form's reply (no-cors), so only network errors show. */
async function post(body: string): Promise<string | null> {
  if (!navigator.onLine) return 'No connection'
  try {
    await fetch(Core.feedbackUrl(), { method: 'POST', mode: 'no-cors', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body })
    return null
  } catch { return "Couldn't reach the suggestions form" }
}

export const appInfo = () => `Web ${__APP_VERSION__} · ${navigator.userAgent.replace(/\s+/g, ' ').slice(0, 120)}`
export const savedName = () => { try { return localStorage.getItem('fw_feedback_name') ?? '' } catch { return '' } }

/** Sends now; on failure saves it for "Send now" in Settings and returns the reason. */
export async function sendFeedback(type: string, text: string, details: string, name: string): Promise<string | null> {
  try { localStorage.setItem('fw_feedback_name', name.trim()) } catch { /* ignore */ }
  const body = Core.feedbackBody(type, text, details, name, appInfo())
  const error = await post(body)
  if (error) save([...unsent(), { id: Core.newId(), at: Date.now(), type, text, body, error }])
  return error
}

export async function sendNow(id: string): Promise<string | null> {
  const item = unsent().find((u) => u.id === id)
  if (!item) return null
  const error = await post(item.body)
  save(error ? unsent().map((u) => (u.id === id ? { ...u, error } : u)) : unsent().filter((u) => u.id !== id))
  return error
}

export const deleteUnsent = (id: string) => save(unsent().filter((u) => u.id !== id))
