import { useMemo, useState } from 'preact/hooks'
import { Core } from '../core'

const help = Core.help()

/** First-launch welcome tour (shared text with Android). */
export function Tour({ onDone, onWhy }: { onDone: () => void; onWhy: () => void }) {
  const [i, setI] = useState(0)
  const page = help.tour[i]
  const last = i === help.tour.length - 1
  return (
    <main class="tour" style={{ minHeight: '90vh' }}>
      <div class="row between"><span /><button class="btn text" onClick={onDone}>Skip</button></div>
      <h1 style={{ marginTop: 40 }}>{page.title}</h1>
      <p class="help-body">{page.body}</p>
      {last && <button class="btn text" onClick={onWhy}>Why Firewatch works this way</button>}
      <div class="dots">{help.tour.map((_, j) => <span class={j === i ? 'on' : ''} />)}</div>
      <div class="row" style={{ marginTop: 'auto' }}>
        {i > 0 && <button class="btn text" onClick={() => setI(i - 1)}>Back</button>}
        <span class="grow" />
        <button class="btn" onClick={() => (last ? onDone() : setI(i + 1))}>{last ? "Let's go" : 'Next'}</button>
      </div>
    </main>
  )
}

export function Why({ onBack }: { onBack: () => void }) {
  return (
    <main>
      <button class="btn text" onClick={onBack}>‹ Back</button>
      <h1>Why Firewatch works this way</h1>
      {help.why.map((p) => <div class="card soft"><b>{p.title}</b><div class="small help-body">{p.body}</div></div>)}
    </main>
  )
}

export function Help({ go }: { go: (r: string) => void }) {
  const [q, setQ] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const list = useMemo(() => {
    const t = q.trim().toLowerCase()
    return help.articles.filter((a) => !t || a.title.toLowerCase().includes(t) || a.body.toLowerCase().includes(t))
  }, [q])
  const sections = [...new Set(help.articles.map((a) => a.section))]
  return (
    <main>
      <h1>Help</h1>
      <input class="search" type="search" placeholder="Search help" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} />
      <div class="row wrap">
        <button class="btn outline" onClick={() => go('tour')}>Welcome tour</button>
        <button class="btn outline" onClick={() => go('why')}>Why Firewatch works this way</button>
      </div>
      {sections.map((s) => {
        const items = list.filter((a) => a.section === s)
        if (!items.length) return null
        return <>
          <h3 class="label">{s}</h3>
          <div class="list">{items.map((a) => (
            <div class="item" style={{ flexDirection: 'column' }} onClick={() => setOpen(open === a.id ? null : a.id)}>
              <b>{a.title}</b>
              {open === a.id && <div class="small help-body">{a.body}</div>}
            </div>
          ))}</div>
        </>
      })}
      {list.length === 0 && <div class="muted">Nothing matches "{q}".</div>}
    </main>
  )
}
