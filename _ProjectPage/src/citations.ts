// Remark plugin: turns "[12]" in running text into a link to the bibliography entry.
// The entry is found by Page.tsx via the ?ref= query (the URL hash belongs to the router).
type Node = { type: string; value?: string; children?: Node[] }

const SKIP = new Set(['link', 'linkReference', 'heading', 'inlineCode', 'code'])
const CITATION = /\[(\d+)\]/g

export const bibliographyPath = '/report/bibliography'

function split(value: string): Node[] {
  const out: Node[] = []
  let last = 0
  for (const m of value.matchAll(CITATION)) {
    if (m.index > last) out.push({ type: 'text', value: value.slice(last, m.index) })
    out.push({
      type: 'link',
      url: `${bibliographyPath}?ref=${m[1]}`,
      children: [{ type: 'text', value: m[0] }],
    } as Node)
    last = m.index + m[0].length
  }
  if (last < value.length) out.push({ type: 'text', value: value.slice(last) })
  return out
}

function walk(node: Node) {
  if (!node.children || SKIP.has(node.type)) return
  node.children = node.children.flatMap((child) => {
    if (child.type === 'text' && child.value) return split(child.value)
    walk(child)
    return [child]
  })
}

export function remarkCitations() {
  return (tree: Node) => walk(tree)
}
