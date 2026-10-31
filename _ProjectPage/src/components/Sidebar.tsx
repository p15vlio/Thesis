import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import pkg from '../../package.json'
import { childPath, nav, sectionPath } from '../nav'

const svg = (d: ReactNode) => (
  <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] shrink-0" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    {d}
  </svg>
)

const icons: Record<string, ReactNode> = {
  overview: svg(
    <>
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </>,
  ),
  gettingStarted: svg(
    <>
      <path d="M4 17l6-6-6-6" />
      <path d="M12 19h8" />
    </>,
  ),
  report: svg(
    <>
      <path d="M14 3H7a2 2 0 00-2 2v14a2 2 0 002 2h10a2 2 0 002-2V8z" />
      <path d="M14 3v5h5M9 13h6M9 17h6" />
    </>,
  ),
  api: svg(<path d="M8 8l-4 4 4 4M16 8l4 4-4 4M14 5l-4 14" />),
  appendix: svg(
    <>
      <path d="M5 4h11a3 3 0 013 3v13H8a3 3 0 01-3-3z" />
      <path d="M5 17a3 3 0 013-3h11" />
    </>,
  ),
  about: svg(
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0116 0" />
    </>,
  ),
}

const sectionClass = (active: boolean, inSection: boolean) =>
  `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
    active
      ? 'bg-accent-soft font-medium text-accent'
      : inSection
        ? 'font-medium text-fg hover:bg-surface'
        : 'text-muted hover:bg-surface hover:text-fg'
  }`

const childClass = (active: boolean) =>
  `-ml-px block rounded-r-md border-l-2 px-3 py-1.5 text-[13px] transition-colors ${
    active
      ? 'border-accent bg-accent-soft font-medium text-accent'
      : 'border-transparent text-muted hover:bg-surface hover:text-fg'
  }`

export default function Sidebar({
  open,
  onToggle,
  onNavigate,
}: {
  open: boolean
  onToggle: () => void
  onNavigate: () => void
}) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const version = pkg.version.split('.').slice(0, 2).join('.')

  const inSectionOf = (id: string) => pathname === sectionPath(id) || pathname.startsWith(`${sectionPath(id)}/`)

  // Arriving in a section (link, prev/next, citation, address bar) opens it, even if it was
  // collapsed by hand earlier. Collapsing it again by hand sticks until the section changes.
  const currentSection = nav.find(({ id }) => inSectionOf(id))?.id
  const [lastSection, setLastSection] = useState(currentSection)
  if (currentSection !== lastSection) {
    setLastSection(currentSection)
    if (currentSection) setExpanded((s) => ({ ...s, [currentSection]: true }))
  }

  // Keep the current page visible in the (scrollable) list.
  useEffect(() => {
    document.querySelector('aside a[aria-current="page"]')?.scrollIntoView({ block: 'nearest' })
  }, [pathname, open])

  return (
    <aside
      className={`fixed inset-y-0 left-0 z-40 flex flex-col overflow-hidden border-r border-line bg-bg transition-[width] duration-200 ${
        open ? 'w-64' : 'w-14'
      }`}
    >
      <div
        className={`flex h-14 shrink-0 items-center border-b border-line ${open ? 'gap-2.5 pl-4 pr-2' : 'justify-center'}`}
      >
        {open && (
          <>
            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-accent text-sm font-bold text-bg">
              I
            </span>
            <span className="flex-1 whitespace-nowrap font-semibold tracking-tight">Ionio Thesis</span>
          </>
        )}
        <button
          onClick={onToggle}
          aria-label={t('ui.toggleSidebar')}
          aria-expanded={open}
          className="rounded-md p-2 text-muted transition-colors hover:bg-surface hover:text-fg"
        >
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <path d="M9 4v16" />
          </svg>
        </button>
      </div>

      {open ? (
        <nav className="flex-1 space-y-1 overflow-y-auto p-3">
          {nav.map(({ id, children }) => {
            const inSection = inSectionOf(id)
            const isOpen = expanded[id] ?? inSection
            return (
              <div key={id}>
                <div className="flex items-center">
                  <NavLink
                    to={sectionPath(id)}
                    end
                    onClick={onNavigate}
                    className={({ isActive }) => `${sectionClass(isActive, inSection)} flex-1`}
                  >
                    <span className={inSection ? 'text-accent' : ''}>{icons[id]}</span>
                    {t(`nav.${id}.title`)}
                  </NavLink>
                  {children.length > 0 && (
                    <button
                      onClick={() => setExpanded((s) => ({ ...s, [id]: !isOpen }))}
                      aria-expanded={isOpen}
                      className="ml-0.5 rounded-md p-1.5 text-muted transition-colors hover:bg-surface hover:text-fg"
                    >
                      <svg
                        viewBox="0 0 20 20"
                        className={`h-4 w-4 transition-transform ${isOpen ? 'rotate-90' : ''}`}
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <path d="M7 5l6 5-6 5" />
                      </svg>
                    </button>
                  )}
                </div>
                {isOpen && children.length > 0 && (
                  <div className="my-1 ml-[22px] space-y-0.5 border-l border-line">
                    {children.map((child) => (
                      <NavLink
                        key={child}
                        to={childPath(id, child)}
                        onClick={onNavigate}
                        className={({ isActive }) => childClass(isActive)}
                      >
                        {t(`nav.${id}.items.${child}`)}
                      </NavLink>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </nav>
      ) : (
        <nav className="flex flex-1 flex-col items-center gap-1 py-3">
          {nav.map(({ id }) => (
            <NavLink
              key={id}
              to={sectionPath(id)}
              title={t(`nav.${id}.title`)}
              aria-label={t(`nav.${id}.title`)}
              onClick={onNavigate}
              className={`grid h-10 w-10 place-items-center rounded-lg transition-colors ${
                inSectionOf(id) ? 'bg-accent-soft text-accent' : 'text-muted hover:bg-surface hover:text-fg'
              }`}
            >
              {icons[id]}
            </NavLink>
          ))}
        </nav>
      )}

      {open && (
        <footer className="shrink-0 space-y-0.5 border-t border-line p-4 text-xs text-muted">
          <p className="font-medium text-fg">{t('footer.author')}</p>
          <p>{t('footer.university')}</p>
          <p>{t('footer.department')}</p>
          <p className="flex justify-between gap-2 pt-1 text-[11px]">
            <span>© 2026 · {t('footer.rights')}</span>
            <span className="shrink-0">v{version}</span>
          </p>
        </footer>
      )}
    </aside>
  )
}
