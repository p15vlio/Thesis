import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { asset } from '../content'

// A ```slides block holds {"slides": [{"src": "/media/..."}]}: the exported slides of the presentation,
// played here with buttons, the arrow keys and full screen.
export type Slide = { src: string }

export default function SlideViewer({ slides }: { slides: Slide[] }) {
  const { t } = useTranslation()
  const [i, setI] = useState(0)
  const box = useRef<HTMLDivElement>(null)
  const last = slides.length - 1
  const go = useCallback((d: number) => setI((n) => Math.min(last, Math.max(0, n + d))), [last])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!box.current) return
      const inside = box.current.contains(document.activeElement) || document.fullscreenElement === box.current
      if (!inside) return
      if (['ArrowRight', 'PageDown', ' '].includes(e.key)) { e.preventDefault(); go(1) }
      if (['ArrowLeft', 'PageUp'].includes(e.key)) { e.preventDefault(); go(-1) }
      if (e.key === 'Home') setI(0)
      if (e.key === 'End') setI(last)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [go, last])

  // preload the next slide so moving forward is instant
  useEffect(() => {
    if (i < last) new Image().src = asset(slides[i + 1].src)
  }, [i, last, slides])

  const fullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void box.current?.requestFullscreen()
  }
  const btn = 'rounded-md border border-line px-3 py-1.5 text-sm hover:bg-surface disabled:opacity-40'

  return (
    <div className="not-prose my-6">
      <div
        ref={box}
        tabIndex={0}
        className="group relative overflow-hidden rounded-lg border border-line bg-black outline-none focus-visible:ring-2 focus-visible:ring-accent [&:fullscreen]:flex [&:fullscreen]:items-center [&:fullscreen]:rounded-none [&:fullscreen]:border-0"
      >
        <img
          src={asset(slides[i].src)}
          alt={t('slides.slide', { n: i + 1, total: slides.length })}
          className="aspect-video w-full cursor-pointer object-contain"
          onClick={() => go(1)}
        />
        <button
          type="button"
          onClick={fullscreen}
          className="absolute right-2 top-2 rounded-md bg-black/60 px-2.5 py-1 text-xs text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
        >
          {t('slides.fullscreen')}
        </button>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button type="button" className={btn} onClick={() => go(-1)} disabled={i === 0}>
          ← {t('slides.previous')}
        </button>
        <button type="button" className={btn} onClick={() => go(1)} disabled={i === last}>
          {t('slides.next')} →
        </button>
        <span className="text-sm tabular-nums text-muted">
          {i + 1} / {slides.length}
        </span>
        <span className="flex-1" />
        <button type="button" className={btn} onClick={fullscreen}>
          {t('slides.fullscreen')}
        </button>
      </div>
    </div>
  )
}
