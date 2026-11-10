import { Fragment, useState, useSyncExternalStore, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import schemasJson from '../apiSchemas.json'

// Swagger-like rendering of the ```api blocks of content/<lang>/api/index.md. The request and response types
// are described by apiSchemas.json, generated from the shared DTOs of the data module.

type Field = {
  name: string
  type: string
  required: boolean
  nullable?: boolean
  default?: string
  enum?: string[]
}
const schemas = schemasJson as unknown as Record<string, Field[]>

export type Endpoint = {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  path: string
  roles: string[]
  summary: string
  rateLimited?: boolean
  body?: string
  response: { status: number; type?: string; list?: boolean; example?: unknown }
  notes?: string[]
  errors?: { status: number; when: string }[]
}

// ---------------------------------------------------------------- shared filter state (toolbar <-> sections)

type Filter = { query: string; role: string; open: number }
let filter: Filter = { query: '', role: '', open: 0 }
const listeners = new Set<() => void>()
const setFilter = (patch: Partial<Filter>) => {
  filter = { ...filter, ...patch }
  listeners.forEach((l) => l())
}
const useFilter = () =>
  useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => filter,
  )

const ROLES = ['PUBLIC', 'PLATFORM_ADMIN', 'OWNER', 'DEVICE']

const matches = (e: Endpoint, f: Filter) => {
  if (f.role && !e.roles.includes(f.role)) return false
  const q = f.query.trim().toLowerCase()
  if (!q) return true
  return [e.method, e.path, e.summary, e.body ?? '', e.response.type ?? ''].some((s) => s.toLowerCase().includes(q))
}

// ---------------------------------------------------------------- small building blocks

const METHOD_STYLE: Record<Endpoint['method'], string> = {
  GET: 'bg-sky-600 text-white dark:bg-sky-500 dark:text-sky-950',
  POST: 'bg-emerald-600 text-white dark:bg-emerald-500 dark:text-emerald-950',
  PUT: 'bg-amber-600 text-white dark:bg-amber-400 dark:text-amber-950',
  PATCH: 'bg-violet-600 text-white dark:bg-violet-400 dark:text-violet-950',
  DELETE: 'bg-rose-600 text-white dark:bg-rose-500 dark:text-rose-950',
}
const CARD_STYLE: Record<Endpoint['method'], string> = {
  GET: 'border-sky-600/30 dark:border-sky-400/25',
  POST: 'border-emerald-600/30 dark:border-emerald-400/25',
  PUT: 'border-amber-600/30 dark:border-amber-400/25',
  PATCH: 'border-violet-600/30 dark:border-violet-400/25',
  DELETE: 'border-rose-600/30 dark:border-rose-400/25',
}

const REASON: Record<number, string> = {
  200: 'OK',
  201: 'Created',
  204: 'No Content',
  400: 'Bad Request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not Found',
  409: 'Conflict',
}

function Status({ code }: { code: number }) {
  const ok = code < 300
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded px-1.5 py-0.5 font-mono text-xs font-semibold ${
        ok
          ? 'bg-emerald-600/10 text-emerald-700 dark:text-emerald-300'
          : 'bg-rose-600/10 text-rose-700 dark:text-rose-300'
      }`}
    >
      {code} {REASON[code] ?? ''}
    </span>
  )
}

function Role({ role }: { role: string }) {
  const { t } = useTranslation()
  return (
    <span
      className={`whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-medium ${
        role === 'PUBLIC' ? 'border-accent/40 text-accent' : 'border-line text-muted'
      }`}
    >
      {t(`api.roles.${role}`)}
    </span>
  )
}

// `code` spans inside the prose of summaries, notes and errors.
const inline = (text: string): ReactNode[] =>
  text.split(/(`[^`]+`)/).map((part, i) =>
    part.startsWith('`') ? (
      <code key={i} className="rounded bg-surface px-1 py-0.5 font-mono text-[0.85em]">
        {part.slice(1, -1)}
      </code>
    ) : (
      <Fragment key={i}>{part}</Fragment>
    ),
  )

// Path with its {parameters} highlighted.
const PathText = ({ path }: { path: string }) => (
  <>
    {path.split(/(\{[^}]+\})/).map((part, i) =>
      part.startsWith('{') ? (
        <span key={i} className="text-accent">
          {part}
        </span>
      ) : (
        <Fragment key={i}>{part}</Fragment>
      ),
    )}
  </>
)

// ---------------------------------------------------------------- JSON examples from the schemas

const inner = (type: string) => type.match(/^List<(.+)>$/)?.[1]

function example(type: string, depth = 0): unknown {
  const item = inner(type)
  if (item) return [example(item, depth + 1)]
  if (type.startsWith('Map<')) return { PENDING: 0 }
  if (schemas[type]) {
    if (depth > 3) return {}
    return Object.fromEntries(schemas[type].map((f) => [f.name, fieldExample(f, depth + 1)]))
  }
  return primitive(type)
}

function fieldExample(f: Field, depth: number): unknown {
  if (f.enum) return f.enum[0]
  if (f.default !== undefined && f.default !== 'null' && !schemas[f.type]) {
    const n = Number(f.default)
    return Number.isNaN(n) ? f.default : n
  }
  return example(f.type, depth)
}

function primitive(type: string): unknown {
  switch (type) {
    case 'Int':
    case 'Long':
      return 0
    case 'Double':
      return 0.0
    case 'Boolean':
      return true
    default:
      return 'string'
  }
}

// Minimal JSON highlighter that reuses the site's highlight.js colour classes.
function JsonBlock({ value }: { value: unknown }) {
  const json = JSON.stringify(value, null, 2)
  const parts = json.split(/("(?:[^"\\]|\\.)*"(?=\s*:)|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?|\btrue\b|\bfalse\b|\bnull\b)/)
  return (
    <pre className="m-0 max-h-96 overflow-auto rounded-md border border-line bg-surface p-3 font-mono text-[12.5px] leading-relaxed">
      <code>
        {parts.map((p, i) => {
          if (i % 2 === 0) return <Fragment key={i}>{p}</Fragment>
          const cls = /^"/.test(p)
            ? /:\s*$/.test(parts[i + 1] ?? '') || (parts[i + 1] ?? '').startsWith(':')
              ? 'hljs-attr'
              : 'hljs-string'
            : /^(true|false|null)$/.test(p)
              ? 'hljs-literal'
              : 'hljs-number'
          return (
            <span key={i} className={cls}>
              {p}
            </span>
          )
        })}
      </code>
    </pre>
  )
}

// ---------------------------------------------------------------- schema table

function TypeName({ type, onOpen }: { type: string; onOpen?: (t: string) => void }) {
  const item = inner(type)
  const named = item ?? type
  if (schemas[named] && onOpen)
    return (
      <>
        {item && 'List<'}
        <button onClick={() => onOpen(named)} className="font-mono text-accent hover:underline">
          {named}
        </button>
        {item && '>'}
      </>
    )
  return <span className="font-mono">{type}</span>
}

function SchemaTable({ name, depth = 0 }: { name: string; depth?: number }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState<string | null>(null)
  const fields = schemas[name] ?? []
  return (
    <div className="overflow-x-auto rounded-md border border-line">
      <table className="m-0 w-full text-left text-[13px]">
        <thead className="bg-surface text-xs text-muted">
          <tr>
            <th className="px-3 py-1.5 font-medium">{t('api.field')}</th>
            <th className="px-3 py-1.5 font-medium">{t('api.type')}</th>
            <th className="px-3 py-1.5 font-medium">{t('api.details')}</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f) => (
            <tr key={f.name} className="border-t border-line align-top">
              <td className="px-3 py-1.5 font-mono">
                {f.name}
                {f.required && <span className="ml-0.5 text-rose-600 dark:text-rose-400">*</span>}
              </td>
              <td className="px-3 py-1.5 text-muted">
                <TypeName type={f.type} onOpen={depth < 2 ? (n) => setOpen(open === n ? null : n) : undefined} />
                {f.nullable && <span className="font-mono"> | null</span>}
              </td>
              <td className="px-3 py-1.5 text-muted">
                {[
                  f.required ? t('api.required') : t('api.optional'),
                  f.default !== undefined ? `${t('api.default')} ${f.default}` : null,
                ]
                  .filter(Boolean)
                  .join(' · ')}
                {f.enum && (
                  <div className="mt-0.5 flex flex-wrap gap-1">
                    {f.enum.map((v) => (
                      <code key={v} className="rounded bg-surface px-1 font-mono text-[11px] text-fg">
                        {v}
                      </code>
                    ))}
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {open && (
        <div className="border-t border-line p-2">
          <p className="mb-1.5 font-mono text-xs text-muted">{open}</p>
          <SchemaTable name={open} depth={depth + 1} />
        </div>
      )}
    </div>
  )
}

// A type shown as an example (default) or as its field table.
function TypeView({ type, list, exampleValue }: { type?: string; list?: boolean; exampleValue?: unknown }) {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'example' | 'schema'>('example')
  const value = exampleValue ?? (type ? (list ? [example(type)] : example(type)) : null)
  return (
    <div>
      <div className="mb-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        {type && (
          <span className="text-muted">
            {list ? `${t('api.arrayOf')} ` : ''}
            <span className="font-mono text-fg">{type}</span>
          </span>
        )}
        {type && (
          <span className="ml-auto inline-flex overflow-hidden rounded border border-line">
            {(['example', 'schema'] as const).map((k) => (
              <button
                key={k}
                onClick={() => setTab(k)}
                className={`px-2 py-0.5 ${tab === k ? 'bg-accent-soft text-accent' : 'text-muted hover:text-fg'}`}
              >
                {t(`api.${k}`)}
              </button>
            ))}
          </span>
        )}
      </div>
      {tab === 'schema' && type ? <SchemaTable name={type} /> : <JsonBlock value={value} />}
    </div>
  )
}

// ---------------------------------------------------------------- endpoint card

function params(path: string) {
  const [base, query = ''] = path.split('?')
  const inPath = [...base.matchAll(/\{([^}]+)\}/g)].map((m) => ({ name: m[1], where: 'path' }))
  const inQuery = query
    .split('&')
    .filter(Boolean)
    .map((q) => ({ name: q.split('=')[0], where: 'query' }))
  return [...inPath, ...inQuery]
}

const Label = ({ children }: { children: ReactNode }) => (
  <h4 className="mb-1.5 mt-4 text-xs font-semibold uppercase tracking-wider text-muted first:mt-0">{children}</h4>
)

function EndpointCard({ e, openAll }: { e: Endpoint; openAll: number }) {
  const { t } = useTranslation()
  const [state, setState] = useState<{ open: boolean; stamp: number }>({ open: false, stamp: 0 })
  // "Expand all" / "Collapse all" bump a counter; its sign gives the new state for every card.
  const open = Math.abs(openAll) > state.stamp ? openAll > 0 : state.open
  const toggle = () => setState({ open: !open, stamp: Math.abs(openAll) })
  const ps = params(e.path)

  return (
    <div className={`overflow-hidden rounded-lg border ${CARD_STYLE[e.method]} bg-bg`}>
      <button
        onClick={toggle}
        aria-expanded={open}
        className="flex w-full flex-wrap items-center gap-x-3 gap-y-1.5 px-3 py-2 text-left hover:bg-surface"
      >
        <span className={`w-16 shrink-0 rounded py-0.5 text-center font-mono text-xs font-bold ${METHOD_STYLE[e.method]}`}>
          {e.method}
        </span>
        <span className="min-w-0 break-all font-mono text-[13.5px] font-medium">
          <PathText path={e.path} />
        </span>
        <span className="hidden min-w-0 flex-1 truncate text-sm text-muted md:block">{e.summary}</span>
        <span className="ml-auto flex flex-wrap gap-1">
          {e.rateLimited && (
            <span title={t('api.rateLimitedHint')} className="rounded-full border border-amber-600/40 px-2 py-0.5 text-[11px] text-amber-700 dark:text-amber-300">
              {t('api.rateLimited')}
            </span>
          )}
          {e.roles.map((r) => (
            <Role key={r} role={r} />
          ))}
        </span>
        <svg
          viewBox="0 0 16 16"
          className={`h-3.5 w-3.5 shrink-0 text-muted transition-transform ${open ? 'rotate-90' : ''}`}
          aria-hidden="true"
        >
          <path d="M6 3l5 5-5 5" fill="none" stroke="currentColor" strokeWidth="1.8" />
        </svg>
      </button>

      {open && (
        <div className="border-t border-line px-4 py-3 text-sm">
          <p className="m-0">{inline(e.summary)}</p>
          {e.notes && (
            <ul className="my-2 list-disc space-y-1 pl-5 text-muted">
              {e.notes.map((n, i) => (
                <li key={i}>{inline(n)}</li>
              ))}
            </ul>
          )}

          {ps.length > 0 && (
            <>
              <Label>{t('api.parameters')}</Label>
              <div className="flex flex-wrap gap-2">
                {ps.map((p) => (
                  <span key={p.name} className="rounded border border-line px-2 py-0.5 text-xs">
                    <span className="font-mono">{p.name}</span>
                    <span className="ml-1.5 text-muted">{t(`api.in.${p.where}`)}</span>
                  </span>
                ))}
              </div>
            </>
          )}

          {e.body && (
            <>
              <Label>{t('api.requestBody')}</Label>
              <TypeView type={e.body} />
            </>
          )}

          <Label>{t('api.responses')}</Label>
          <div className="space-y-3">
            <div>
              <div className="mb-1.5">
                <Status code={e.response.status} />
              </div>
              {e.response.status !== 204 && (
                <TypeView type={e.response.type} list={e.response.list} exampleValue={e.response.example} />
              )}
            </div>
            {e.errors?.map((err, i) => (
              <div key={i} className="flex flex-wrap items-start gap-2">
                <Status code={err.status} />
                <span className="min-w-0 flex-1 text-muted">{inline(err.when)}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- exported blocks

export function ApiSection({ endpoints }: { endpoints: Endpoint[] }) {
  const { t } = useTranslation()
  const f = useFilter()
  const shown = endpoints.filter((e) => matches(e, f))
  return (
    <div className="not-prose my-4 space-y-2">
      {shown.map((e) => (
        <EndpointCard key={e.method + e.path} e={e} openAll={f.open} />
      ))}
      {shown.length === 0 && <p className="text-sm text-muted">{t('api.noMatch')}</p>}
    </div>
  )
}

export function ApiToolbar() {
  const { t } = useTranslation()
  const f = useFilter()
  const chip = (active: boolean) =>
    `rounded-full border px-2.5 py-1 text-xs transition-colors ${
      active ? 'border-accent bg-accent-soft text-accent' : 'border-line text-muted hover:text-fg'
    }`
  return (
    <div className="not-prose sticky top-14 z-10 my-6 flex flex-wrap items-center gap-2 rounded-lg border border-line bg-bg/95 p-2 backdrop-blur">
      <input
        type="search"
        value={f.query}
        onChange={(ev) => setFilter({ query: ev.target.value })}
        placeholder={t('api.search')}
        className="min-w-0 flex-1 basis-48 rounded-md border border-line bg-surface px-2.5 py-1 text-sm outline-none focus:border-accent"
      />
      <div className="flex flex-wrap gap-1">
        <button className={chip(f.role === '')} onClick={() => setFilter({ role: '' })}>
          {t('api.allRoles')}
        </button>
        {ROLES.map((r) => (
          <button key={r} className={chip(f.role === r)} onClick={() => setFilter({ role: f.role === r ? '' : r })}>
            {t(`api.roles.${r}`)}
          </button>
        ))}
      </div>
      <div className="flex gap-1">
        <button className={chip(false)} onClick={() => setFilter({ open: Math.abs(f.open) + 1 })}>
          {t('api.expandAll')}
        </button>
        <button className={chip(false)} onClick={() => setFilter({ open: -(Math.abs(f.open) + 1) })}>
          {t('api.collapseAll')}
        </button>
      </div>
    </div>
  )
}
