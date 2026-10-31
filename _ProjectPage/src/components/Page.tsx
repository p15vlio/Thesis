import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { loadContent } from '../content'
import { pages, type NavPage } from '../nav'
import Markdown from './Markdown'
import Toc from './Toc'

export default function Page({ page, wide }: { page: NavPage; wide: boolean }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const id = `${lang}:${page.contentKey}`
  const [loaded, setLoaded] = useState<{ id: string; text: string | null } | null>(null)
  const [root, setRoot] = useState<HTMLElement | null>(null)

  useEffect(() => {
    let cancelled = false
    void loadContent(lang, page.contentKey).then((text) => {
      if (!cancelled) setLoaded({ id, text })
    })
    return () => {
      cancelled = true
    }
  }, [lang, page.contentKey, id])

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [page.path])

  const source = loaded?.id === id ? loaded.text : null

  // ?ref=N (from a citation link) scrolls to and briefly highlights that bibliography entry.
  const ref = new URLSearchParams(useLocation().search).get('ref')
  useEffect(() => {
    const el = ref && source ? document.getElementById(`ref-${ref}`) : null
    if (!el) return
    el.scrollIntoView({ block: 'center' })
    el.classList.add('ref-flash')
    const timer = setTimeout(() => el.classList.remove('ref-flash'), 2000)
    return () => {
      clearTimeout(timer)
      el.classList.remove('ref-flash')
    }
  }, [ref, source])
  const index = pages.findIndex((p) => p.path === page.path)
  const prev = pages[index - 1]
  const next = pages[index + 1]

  return (
    <div className="grid gap-10 xl:grid-cols-[minmax(0,1fr)_13rem]">
      <article ref={setRoot} className={wide ? '' : 'max-w-3xl'}>
        {page.parentKey && (
          <p className="mb-2 text-xs font-medium uppercase tracking-wider text-accent">{t(page.parentKey)}</p>
        )}
        <h1 className="text-3xl font-semibold tracking-tight">{t(page.titleKey)}</h1>
        <div className="mt-4 h-px bg-line" />

        {source && (
          <div className="mt-6">
            <Markdown source={source} citations={page.contentKey !== 'report/bibliography'} />
          </div>
        )}

        <div className={`grid grid-cols-2 gap-4 text-sm ${source ? 'mt-12 border-t border-line pt-6' : 'mt-8'}`}>
          {prev ? (
            <Link to={prev.path} className="rounded-lg border border-line p-3 transition-colors hover:bg-surface">
              <span className="block text-xs text-muted">{t('ui.previous')}</span>
              <span className="font-medium">{t(prev.titleKey)}</span>
            </Link>
          ) : (
            <span />
          )}
          {next && (
            <Link
              to={next.path}
              className="rounded-lg border border-line p-3 text-right transition-colors hover:bg-surface"
            >
              <span className="block text-xs text-muted">{t('ui.next')}</span>
              <span className="font-medium">{t(next.titleKey)}</span>
            </Link>
          )}
        </div>
      </article>

      <aside className="hidden xl:block">{source && <Toc root={root} source={source} />}</aside>
    </div>
  )
}
