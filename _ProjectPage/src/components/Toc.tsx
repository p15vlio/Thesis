import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

type Heading = { id: string; text: string; level: number }

// "On this page" list built from the rendered h2/h3 headings; highlights the one in view.
export default function Toc({ root, source }: { root: HTMLElement | null; source: string }) {
  const { t } = useTranslation()
  const [headings, setHeadings] = useState<Heading[]>([])
  const [active, setActive] = useState('')

  useEffect(() => {
    if (!root) return
    const els = Array.from(root.querySelectorAll<HTMLElement>('h2[id], h3[id]'))
    const frame = requestAnimationFrame(() =>
      setHeadings(els.map((e) => ({ id: e.id, text: e.textContent ?? '', level: Number(e.tagName[1]) }))),
    )

    const obs = new IntersectionObserver(
      (entries) => {
        const hit = entries.find((e) => e.isIntersecting)
        if (hit) setActive(hit.target.id)
      },
      { rootMargin: '-80px 0px -70% 0px' },
    )
    els.forEach((e) => obs.observe(e))
    return () => {
      cancelAnimationFrame(frame)
      obs.disconnect()
    }
  }, [root, source])

  useEffect(() => {
    if (active) document.querySelector(`[data-toc="${CSS.escape(active)}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [active])

  if (headings.length < 2) return null

  return (
    <nav aria-label={t('ui.onThisPage')} className="sticky top-20 max-h-[calc(100vh-6rem)] overflow-y-auto overflow-x-hidden text-sm">
      <p className="mb-2 text-xs font-medium uppercase tracking-wider text-muted">{t('ui.onThisPage')}</p>
      <ul className="space-y-1 border-l border-line">
        {headings.map((h) => (
          <li key={h.id}>
            <button
              data-toc={h.id}
              onClick={() => document.getElementById(h.id)?.scrollIntoView({ behavior: 'smooth' })}
              className={`-ml-px block w-full break-words border-l-2 py-0.5 text-left transition-colors ${
                h.level === 3 ? 'pl-6' : 'pl-3'
              } ${
                active === h.id
                  ? 'border-accent text-accent'
                  : 'border-transparent text-muted hover:text-fg'
              }`}
            >
              {h.text}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  )
}
