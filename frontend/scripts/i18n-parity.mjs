import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { diffKeys } from './i18n-parity-core.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const LOCALES = join(HERE, '..', 'src', 'i18n', 'locales')
const EN = join(LOCALES, 'en')
const DE = join(LOCALES, 'de')

const jsonFiles = (dir) => readdirSync(dir).filter((f) => f.endsWith('.json'))
const load = (dir, ns) => JSON.parse(readFileSync(join(dir, ns), 'utf8'))

const enFiles = jsonFiles(EN)
const deFiles = jsonFiles(DE)
let problems = 0

for (const f of enFiles.filter((f) => !deFiles.includes(f))) { console.error(`Namespace missing in de/: ${f}`); problems++ }
for (const f of deFiles.filter((f) => !enFiles.includes(f))) { console.error(`Namespace missing in en/: ${f}`); problems++ }

for (const ns of enFiles.filter((f) => deFiles.includes(f))) {
  const { missingInB, missingInA } = diffKeys(load(EN, ns), load(DE, ns))
  for (const k of missingInB) { console.error(`[${ns}] missing in de: ${k}`); problems++ }
  for (const k of missingInA) { console.error(`[${ns}] missing in en: ${k}`); problems++ }
}

if (problems) { console.error(`\ni18n-parity: ${problems} mismatch(es).`); process.exit(1) }
console.log('i18n-parity: OK — en/de namespaces and keys match.')
