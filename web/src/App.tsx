import { useEffect, useState } from 'preact/hooks'
import { useRegisterSW } from 'virtual:pwa-register/preact'
import * as S from './store'
import { persist } from './db'
import { markActivity } from './core'
import { setTimeFormat } from './ui/format'
import { Home } from './ui/Home'
import { Calendar } from './ui/Calendar'
import { Insights } from './ui/Insights'
import { Settings } from './ui/Settings'
import { Backfill, Onboarding } from './ui/Onboarding'
import changelog from '../CHANGELOG.md?raw'

const TABS = [['home', 'Log'], ['insights', 'Insights'], ['calendar', 'Calendar'], ['settings', 'Settings']]

export function App() {
  const [route, setRoute] = useState('home')
  const [toast, setToast] = useState<{ msg: string; undo?: () => void } | null>(null)
  const [whatsNew, setWhatsNew] = useState<string | null>(null)
  const [checked, setChecked] = useState('Checking for updates on each open')
  // Service worker: new versions install themselves and take over on the next open.
  useRegisterSW({ immediate: true, onRegisteredSW: () => setChecked(`Last checked ${new Date().toLocaleTimeString()}`) })

  useEffect(() => {
    S.load(); persist(); markActivity()
    const onVisible = () => document.visibilityState === 'visible' && (markActivity(), (S.tick.value = Date.now()))
    document.addEventListener('visibilitychange', onVisible)
    const t = setInterval(() => (S.tick.value = Date.now()), 15000)
    const seen = localStorage.getItem('fw_seen_version')
    if (seen && seen !== __APP_VERSION__) setWhatsNew(changelog.split('\n## ')[1] ?? null)
    localStorage.setItem('fw_seen_version', __APP_VERSION__)
    return () => clearInterval(t)
  }, [])

  const show = (msg: string, undo?: () => void) => { setToast({ msg, undo }); setTimeout(() => setToast((t) => (t?.msg === msg ? null : t)), 5000) }
  if (!S.loaded.value || !S.snapshot.value) return <main><div class="muted">Loading…</div></main>
  setTimeFormat(S.settings.value.timeFormat ?? 'system')
  if (!S.settings.value.onboardingDone) return <Onboarding />
  return (
    <>
      {route === 'home' && <Home toast={show} go={setRoute} />}
      {route === 'insights' && <Insights />}
      {route === 'calendar' && <Calendar />}
      {route === 'settings' && <Settings toast={show} go={setRoute} updateInfo={checked} />}
      {route === 'backfill' && <Backfill onDone={() => setRoute('home')} onCancel={() => setRoute('home')} />}
      {route !== 'backfill' && <nav class="tabs">{TABS.map(([r, l]) => <button class={route === r ? 'on' : ''} onClick={() => setRoute(r)}>{l}</button>)}</nav>}
      {toast && <div class="toast">{toast.msg}{toast.undo && <button class="btn text" onClick={() => { toast.undo!(); setToast(null) }}>Undo</button>}</div>}
      {whatsNew && <div class="sheet-bg" onClick={() => setWhatsNew(null)}><div class="sheet"><h2>What's new</h2>
        <div class="small" style={{ whiteSpace: 'pre-wrap' }}>{'Version ' + whatsNew}</div><button class="btn" onClick={() => setWhatsNew(null)}>Got it</button></div></div>}
    </>
  )
}
