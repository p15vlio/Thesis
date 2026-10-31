import { useCallback, useState } from 'react'

export type Theme = 'light' | 'dark'

// The initial class is applied by the inline script in index.html to avoid a flash.
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() =>
    document.documentElement.classList.contains('dark') ? 'dark' : 'light',
  )

  const toggle = useCallback(() => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    document.documentElement.classList.toggle('dark', next === 'dark')
    try {
      localStorage.setItem('theme', next)
    } catch {
      /* storage unavailable */
    }
    setTheme(next)
  }, [theme])

  return { theme, toggle }
}
