import { isValidElement, lazy, Suspense, useState, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import { Link } from 'react-router-dom'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import { common } from 'lowlight'
import rehypeHighlight from 'rehype-highlight'
import rehypeSlug from 'rehype-slug'
import remarkGfm from 'remark-gfm'
import { remarkCitations } from '../citations'
import { asset } from '../content'
import { ApiSection, ApiToolbar, type Endpoint } from './ApiReference'
import SlideViewer from './SlideViewer'

const Mermaid = lazy(() => import('./Mermaid'))

const VIDEO = /\.(mp4|webm|ogv)$/i

const text = (node: ReactNode): string =>
  typeof node === 'string' ? node : Array.isArray(node) ? node.map(text).join('') : ''

export default function Markdown({ source, citations = true }: { source: string; citations?: boolean }) {
  const [zoom, setZoom] = useState<{ src: string; alt: string } | null>(null)

  const components: Components = {
    // ![alt](/media/demo.mp4) renders a video; any other extension renders an image.
    img: ({ src = '', alt = '' }) =>
      VIDEO.test(src) ? (
        <video src={asset(src)} controls preload="metadata" aria-label={alt} className="w-full rounded-lg" />
      ) : (
        <img
          src={asset(src)}
          alt={alt}
          loading="lazy"
          onClick={() => setZoom({ src: asset(src), alt })}
          className="cursor-zoom-in rounded-lg"
        />
      ),
    a: ({ href = '', children }) => {
      if (href.startsWith('/') && !href.startsWith('/media/')) return <Link to={href}>{children}</Link>
      if (href.startsWith('#'))
        return (
          <a
            href={href}
            onClick={(e) => {
              e.preventDefault() // the hash is owned by the router
              document.getElementById(decodeURIComponent(href.slice(1)))?.scrollIntoView({ behavior: 'smooth' })
            }}
          >
            {children}
          </a>
        )
      if (href.startsWith('mailto:')) return <a href={href}>{children}</a>
      return (
        <a href={href.startsWith('/') ? asset(href) : href} target="_blank" rel="noreferrer">
          {children}
        </a>
      )
    },
    pre: ({ children }) => {
      if (isValidElement<{ className?: string; children?: ReactNode }>(children)) {
        const { className, children: code } = children.props
        if (className?.includes('language-mermaid'))
          return (
            <Suspense fallback={null}>
              <Mermaid code={text(code)} />
            </Suspense>
          )
        // ```api holds the endpoints of one API section as JSON; ```api-toolbar is the search and role filter above them.
        if (className?.includes('language-api-toolbar')) return <ApiToolbar />
        if (className?.includes('language-api')) return <ApiSection endpoints={JSON.parse(text(code)) as Endpoint[]} />
        if (className?.includes('language-slides')) return <SlideViewer slides={JSON.parse(text(code)).slides} />
        // A ```gallery block holds [{"src": "/media/...", "alt": "..."}]: a grid of images, each opens enlarged.
        if (className?.includes('language-gallery'))
          return (
            <div className="not-prose my-6 grid gap-x-4 gap-y-6 sm:grid-cols-2">
              {(JSON.parse(text(code)) as { src: string; alt: string }[]).map(({ src, alt }) => (
                <figure key={src} className="m-0">
                  <img
                    src={asset(src)}
                    alt={alt}
                    loading="lazy"
                    onClick={() => setZoom({ src: asset(src), alt })}
                    className="w-full cursor-zoom-in rounded-lg border border-line"
                  />
                  <figcaption className="mt-2 text-center text-sm text-muted">{alt}</figcaption>
                </figure>
              ))}
            </div>
          )
      }
      return <pre>{children}</pre>
    },
    // Bibliography entries ("[12] ...") get an id the citation links can scroll to.
    p: ({ node, children }) => {
      // A paragraph holding only an image is a figure; the alt text is its caption.
      const only = node?.children.length === 1 ? node.children[0] : null
      if (only?.type === 'element' && only.tagName === 'img')
        return (
          <figure className="my-6">
            {children}
            <figcaption className="text-center">{String(only.properties.alt ?? '')}</figcaption>
          </figure>
        )
      const first = Array.isArray(children) ? children[0] : children
      const ref = typeof first === 'string' ? first.match(/^\[(\d+)\]/) : null
      return <p id={ref ? `ref-${ref[1]}` : undefined}>{children}</p>
    },
    table: ({ children }) => (
      <div className="overflow-x-auto">
        <table>{children}</table>
      </div>
    ),
  }

  return (
    <>
      <div className="prose max-w-none break-words prose-headings:scroll-mt-20 prose-headings:tracking-tight prose-a:no-underline hover:prose-a:underline prose-code:rounded prose-code:bg-surface prose-code:px-1.5 prose-code:py-0.5 prose-code:font-normal prose-code:before:content-none prose-code:after:content-none prose-pre:border prose-pre:border-line prose-pre:bg-surface [&_pre_code]:bg-transparent [&_pre_code]:p-0">
        <ReactMarkdown
          remarkPlugins={citations ? [remarkGfm, remarkCitations] : [remarkGfm]}
          rehypePlugins={[rehypeSlug, [rehypeHighlight, { ignoreMissing: true, languages: { ...common, dockerfile } }]]}
          components={components}
        >
          {source}
        </ReactMarkdown>
      </div>
      {zoom && (
        <div
          onClick={() => setZoom(null)}
          className="fixed inset-0 z-50 grid cursor-zoom-out place-items-center bg-black/80 p-6"
        >
          <img src={zoom.src} alt={zoom.alt} className="max-h-full max-w-full rounded-lg" />
        </div>
      )}
    </>
  )
}
