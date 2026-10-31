// Markdown lives in <project>/content/<lang>/<section>/<page>.md (section landing: index.md).
const files = import.meta.glob('/content/**/*.md', { query: '?raw', import: 'default' }) as Record<
  string,
  () => Promise<string>
>

// True when a page has Markdown in at least one language.
export const hasContent = (key: string): boolean => Object.keys(files).some((path) => path.endsWith(`/${key}.md`))

export const loadContent =(lang: string, key: string): Promise<string | null> =>
  files[`/content/${lang}/${key}.md`]?.() ?? Promise.resolve(null)

// Resolves "/media/x.png" (or "media/x.png") against the site base so it works under /<repo>/.
export function asset(src: string): string {
  if (/^([a-z]+:)?\/\//i.test(src) || src.startsWith('data:')) return src
  return import.meta.env.BASE_URL + src.replace(/^\.?\//, '')
}
