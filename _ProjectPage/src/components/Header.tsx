import { useTranslation } from 'react-i18next'
import { languages } from '../i18n'
import { useTheme } from '../theme'

const iconButton = 'rounded-md p-2 text-muted transition-colors hover:bg-surface hover:text-fg'

type Props = { open: boolean; wide: boolean; onToggleWidth: () => void }

export default function Header({ open, wide, onToggleWidth }: Props) {
  const { t, i18n } = useTranslation()
  const { theme, toggle } = useTheme()

  return (
    <header
      className={`fixed left-14 right-0 top-0 z-30 flex h-14 items-center gap-2 border-b border-line bg-bg/80 px-3 backdrop-blur transition-[left] duration-200 ${
        open ? 'md:left-64' : ''
      }`}
    >
      <p
        title={t('app.title')}
        className="line-clamp-2 min-w-0 flex-1 text-xs font-medium leading-snug text-muted max-sm:hidden"
      >
        {t('app.title')}
      </p>

      <div className="ml-auto flex shrink-0 items-center gap-2">
        <button
          onClick={onToggleWidth}
          aria-label={t('ui.toggleWidth')}
          aria-pressed={wide}
          title={t(wide ? 'ui.narrow' : 'ui.wide')}
          className={`${iconButton} hidden lg:block`}
        >
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
            {wide ? (
              <path d="M9 4v16M15 4v16M3 12h4M5 10l2 2-2 2M21 12h-4M19 10l-2 2 2 2" />
            ) : (
              <path d="M3 4v16M21 4v16M7 12h10M10 9l-3 3 3 3M14 9l3 3-3 3" />
            )}
          </svg>
        </button>
        <div
          role="group"
          aria-label={t('ui.language')}
          className="flex rounded-md border border-line bg-surface p-0.5 text-xs"
        >
          {languages.map((l) => (
            <button
              key={l}
              onClick={() => void i18n.changeLanguage(l)}
              aria-pressed={i18n.language === l}
              className={`rounded px-2 py-1 font-medium uppercase transition-colors ${
                i18n.language === l ? 'bg-bg text-accent shadow-sm' : 'text-muted hover:text-fg'
              }`}
            >
              {l}
            </button>
          ))}
        </div>
        <button onClick={toggle} aria-label={t('ui.toggleTheme')} className={iconButton}>
          {theme === 'dark' ? (
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" />
            </svg>
          )}
        </button>
      </div>
    </header>
  )
}
