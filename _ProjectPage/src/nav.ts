import { hasContent } from './content'

// Sidebar structure. Labels live in the locale files under nav.<section>.title
// and nav.<section>.items.<child>.
export type NavSection = { id: string; children: string[] }

export const nav: NavSection[] = [
  { id: 'overview', children: ['introduction', 'architecture', 'technologies'] },
  { id: 'gettingStarted', children: ['installation', 'flows'] },
  { id: 'showcase', children: ['presentation', 'demo', 'screenshots'] },
  {
    id: 'report',
    children: ['abstract', 'aiStatement', 'contents', 'ch1', 'ch2', 'ch3', 'ch4', 'ch5', 'ch6', 'ch7', 'ch8', 'ch9', 'ch10', 'bibliography'],
  },
  { id: 'api', children: [] },
  { id: 'appendix', children: [] },
  { id: 'about', children: [] },
]

export const sectionPath = (id: string) => `/${id}`
export const childPath = (id: string, child: string) => `/${id}/${child}`

// Flat, ordered list of every page (drives routes, prev/next and content lookup).
// contentKey maps to content/<lang>/<contentKey>.md
export type NavPage = { path: string; titleKey: string; parentKey?: string; contentKey: string }

// A section is a page of its own when it has no subpages or has a content/<lang>/<id>/index.md;
// otherwise opening it redirects to its first subpage (see `redirects`).
const ownPage = ({ id, children }: NavSection) => children.length === 0 || hasContent(`${id}/index`)

export const pages: NavPage[] = nav.flatMap((section) => [
  ...(ownPage(section)
    ? [{ path: sectionPath(section.id), titleKey: `nav.${section.id}.title`, contentKey: `${section.id}/index` }]
    : []),
  ...section.children.map((child) => ({
    path: childPath(section.id, child),
    titleKey: `nav.${section.id}.items.${child}`,
    parentKey: `nav.${section.id}.title`,
    contentKey: `${section.id}/${child}`,
  })),
])

export const redirects = nav
  .filter((section) => !ownPage(section))
  .map((section) => ({ from: sectionPath(section.id), to: childPath(section.id, section.children[0]) }))
