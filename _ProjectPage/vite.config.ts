import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, type Plugin } from 'vite'

// The screenshots and diagrams live in <repo>/_Docs/Images and are served as /media/<file>; the demo
// videos live in <repo>/_Docs/Videos and are served as /media/videos/<path>. Both are served by the dev
// server and copied into dist/media at build time, so they exist only once in the repository.
const DIRS: [string, string][] = [
  ['videos/', resolve(__dirname, '../_Docs/Videos')],
  ['', resolve(__dirname, '../_Docs/Images')],
]
const TYPES: Record<string, string> = { '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.mp4': 'video/mp4' }

// Relative paths of every servable file under dir, subfolders included
const walk = (dir: string, rel = ''): string[] =>
  readdirSync(join(dir, rel), { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(dir, `${rel}${e.name}/`) : extname(e.name).toLowerCase() in TYPES ? [`${rel}${e.name}`] : [],
  )

function docsMedia(base: string): Plugin {
  return {
    name: 'docs-media',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = decodeURIComponent((req.url ?? '').split('?')[0])
        const prefix = `${base}media/`.replace('//', '/')
        if (!url.startsWith(prefix)) return next()
        const path = url.slice(prefix.length)
        const match = DIRS.find(([sub]) => path.startsWith(sub))
        if (!match) return next()
        const [sub, dir] = match
        const file = join(dir, path.slice(sub.length))
        if (!file.startsWith(dir) || !existsSync(file)) return next()
        res.setHeader('Content-Type', TYPES[extname(file).toLowerCase()] ?? 'application/octet-stream')
        res.end(readFileSync(file))
      })
    },
    generateBundle() {
      for (const [sub, dir] of DIRS) {
        if (!existsSync(dir)) continue
        for (const rel of walk(dir)) {
          this.emitFile({ type: 'asset', fileName: `media/${sub}${rel}`, source: readFileSync(join(dir, rel)) })
        }
      }
    },
  }
}

// BASE_PATH is set by the deploy workflow (e.g. "/Thesis/"); "/" locally.
const base = process.env.BASE_PATH ?? '/'

export default defineConfig({
  base,
  plugins: [react(), tailwindcss(), docsMedia(base)],
})
