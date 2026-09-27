// IndexedDB: the same record envelopes as the Android app's database and backup files.
export interface RecordEnvelope { id: string; type: string; ts: number | null; updatedAt: number; deleted: boolean; data: any }

const DB = 'firewatch'
const STORE = 'records'

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: 'id' })
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function loadAll(): Promise<RecordEnvelope[]> {
  const db = await open()
  return new Promise((resolve, reject) => {
    const req = db.transaction(STORE).objectStore(STORE).getAll()
    req.onsuccess = () => resolve(req.result as RecordEnvelope[])
    req.onerror = () => reject(req.error)
  })
}

export async function putAll(records: RecordEnvelope[]): Promise<void> {
  if (!records.length) return
  const db = await open()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    const store = tx.objectStore(STORE)
    records.forEach((r) => store.put(r))
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/** Ask the browser not to clear our data under storage pressure. */
export async function persist(): Promise<boolean> {
  try { return (await navigator.storage?.persist?.()) ?? false } catch { return false }
}
