import { useEffect, useId, useState } from 'react'

const isDark = () => document.documentElement.classList.contains('dark')

// Re-renders the diagram when the theme class on <html> changes.
function useDark() {
  const [dark, setDark] = useState(isDark)
  useEffect(() => {
    const obs = new MutationObserver(() => setDark(isDark()))
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => obs.disconnect()
  }, [])
  return dark
}

export default function Mermaid({ code }: { code: string }) {
  const id = 'mmd' + useId().replace(/:/g, '')
  const dark = useDark()
  const [svg, setSvg] = useState('')

  useEffect(() => {
    let cancelled = false
    void import('mermaid').then(async ({ default: mermaid }) => {
      mermaid.initialize({ startOnLoad: false, theme: dark ? 'dark' : 'neutral', securityLevel: 'strict' })
      try {
        const { svg } = await mermaid.render(id, code)
        if (!cancelled) setSvg(svg)
      } catch {
        if (!cancelled) setSvg('')
      }
    })
    return () => {
      cancelled = true
    }
  }, [code, dark, id])

  return <div className="my-6 flex justify-center overflow-x-auto" dangerouslySetInnerHTML={{ __html: svg }} />
}
