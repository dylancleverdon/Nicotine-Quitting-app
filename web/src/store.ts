// App state: every record in memory, written through to IndexedDB. Mirrors the Android Repository:
// writes merge into the stored JSON (so newer fields survive), deletes are tombstones.
import { signal, computed } from '@preact/signals'
import { Core, type Snapshot } from './core'
import { loadAll, putAll, type RecordEnvelope } from './db'

export const records = signal<Map<string, RecordEnvelope>>(new Map())
export const loaded = signal(false)
export const tick = signal(Date.now())

const recordsJson = computed(() => JSON.stringify([...records.value.values()]))
export const snapshot = computed<Snapshot | null>(() => {
  tick.value
  if (!loaded.value) return null
  return Core.compute(recordsJson.value)
})
export function json() { return recordsJson.value }

export function live(type: string): any[] {
  return [...records.value.values()].filter((r) => r.type === type && !r.deleted).map((r) => r.data)
}
export const settings = computed<any>(() => ({
  onboardingDone: false, referenceProductId: 'gum-4', wakeMinutes: 420, sleepMinutes: 1380, holdDays: 7, windDown: true,
  morningDelayMinutes: 0, morningDelayClock: -1, timeFormat: 'system', currency: '$', rewardName: '', rewardCost: 0, dailyCheckIn: false,
  ...(records.value.get('settings')?.data ?? {}),
}))
export const products = computed<any[]>(() => live('product').filter((p) => !p.archived).sort((a, b) => (a.order ?? 0) - (b.order ?? 0)))
export const homeProducts = computed(() => products.value.filter((p) => p.onHome))

const tsOf = (type: string, data: any): number | null =>
  type === 'settings' ? null : type === 'product' ? data.createdAt ?? 0 : data.at ?? null

async function write(list: { type: string; data: any; deleted?: boolean }[]) {
  const now = Date.now()
  const next = new Map(records.value)
  const out: RecordEnvelope[] = list.map(({ type, data, deleted }) => {
    const id = type === 'settings' ? 'settings' : data.id
    const old = next.get(id)
    const env: RecordEnvelope = { id, type, ts: tsOf(type, data), updatedAt: now, deleted: !!deleted, data: { ...(old?.data ?? {}), ...data } }
    next.set(id, env)
    return env
  })
  records.value = next
  await putAll(out)
}

export async function load() {
  const all = await loadAll()
  const map = new Map(all.map((r) => [r.id, r]))
  records.value = map
  // Default products this install has never had.
  const missing = Core.defaultProducts().filter((p) => !map.has(p.id)).map((p) => ({ type: 'product', data: { ...p, createdAt: Date.now() } }))
  if (missing.length) await write(missing)
  loaded.value = true
}

export const save = (type: string, data: any) => write([{ type, data }])
export const saveMany = (type: string, list: any[]) => write(list.map((data) => ({ type, data })))
export async function remove(id: string) {
  const r = records.value.get(id)
  if (r) await write([{ type: r.type, data: r.data, deleted: true }])
}
export async function restore(id: string) {
  const r = records.value.get(id)
  if (r) await write([{ type: r.type, data: r.data, deleted: false }])
}
export const updateSettings = (patch: any) => save('settings', { ...settings.value, ...patch })

export async function logProduct(product: any, at = Date.now(), opts = {}) {
  const dose = Core.doseFor(product, at, opts)
  await save('dose', dose)
  return dose
}
export async function startCraving(intensity: number) {
  const c = { id: Core.newId(), at: Date.now(), intensity, outcome: 'OPEN', tags: [] }
  await save('craving', c)
  return c
}
export async function finishCraving(c: any, outcome: 'RODE_OUT' | 'USED') {
  const stored = records.value.get(c.id)?.data ?? c
  await save('craving', { ...stored, outcome, endedAt: stored.endedAt ?? Date.now() })
}
export const logSleep = (kind: 'WAKE' | 'SLEEP', at = Date.now()) => save('sleep', { id: Core.newId(), at, kind })
export const setTarget = (pieces: number, reason: string) => save('rung', { id: Core.newId(), at: Date.now(), pieces, reason })

// --- Backup files: identical format to the Android app. ---
export function exportFile(reason = 'export') {
  return {
    app: 'Firewatch by Baastik Labs', format: 1, exportedAt: Date.now(), appVersion: `web ${__APP_VERSION__}`, reason,
    records: [...records.value.values()],
  }
}
export async function importFile(file: any, replace: boolean): Promise<number> {
  const incoming: RecordEnvelope[] = (file.records ?? []).map((r: any) => ({ ...r, data: r.data ?? {} }))
  const now = Date.now()
  const next = new Map(records.value)
  let toWrite: RecordEnvelope[]
  if (replace) {
    const keep = new Set(incoming.map((r) => r.id))
    const tomb = [...next.values()].filter((r) => !r.deleted && !keep.has(r.id) && r.type !== 'settings').map((r) => ({ ...r, deleted: true, updatedAt: now }))
    toWrite = [...tomb, ...incoming.map((r) => ({ ...r, updatedAt: now }))]
  } else {
    toWrite = incoming.filter((r) => !next.has(r.id) || r.updatedAt > next.get(r.id)!.updatedAt)
  }
  toWrite.forEach((r) => next.set(r.id, r))
  records.value = next
  await putAll(toWrite)
  return toWrite.length
}

declare global { const __APP_VERSION__: string }
