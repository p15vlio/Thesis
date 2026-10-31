import { useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Header from './components/Header'
import Page from './components/Page'
import Sidebar from './components/Sidebar'
import { useReadingWidth } from './layout'
import { pages, redirects } from './nav'

const isDesktop = () => window.matchMedia('(min-width: 768px)').matches

export default function App() {
  const [open, setOpen] = useState(isDesktop)
  const { wide, toggle: toggleWidth } = useReadingWidth()

  return (
    <>
      <Header open={open} wide={wide} onToggleWidth={toggleWidth} />
      {open && (
        <div onClick={() => setOpen(false)} className="fixed inset-0 z-30 bg-black/40 md:hidden" aria-hidden />
      )}
      <Sidebar open={open} onToggle={() => setOpen((o) => !o)} onNavigate={() => !isDesktop() && setOpen(false)} />
      <main className={`pb-10 pl-20 pr-6 pt-24 transition-[padding] duration-200 ${open ? 'md:pl-72' : ''}`}>
        <div className={`mx-auto ${wide ? 'max-w-7xl' : 'max-w-5xl'}`}>
          <Routes>
            <Route path="/" element={<Navigate to={pages[0].path} replace />} />
            {redirects.map(({ from, to }) => (
              <Route key={from} path={from} element={<Navigate to={to} replace />} />
            ))}
            {pages.map((page) => (
              <Route key={page.path} path={page.path} element={<Page page={page} wide={wide} />} />
            ))}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </main>
    </>
  )
}
