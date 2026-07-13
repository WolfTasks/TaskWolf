import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { findViolations } from './i18n-scan-core.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const FRONTEND_ROOT = join(HERE, '..')
const SRC_DIR = join(FRONTEND_ROOT, 'src')
const ALLOWLIST_PATH = join(HERE, 'i18n-allowlist.json')
const isInit = process.argv.includes('--init')

function walk(dir, acc = []) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry)
    if (statSync(p).isDirectory()) walk(p, acc)
    else if (p.endsWith('.tsx')) acc.push(p)
  }
  return acc
}

const toRel = (abs) => relative(FRONTEND_ROOT, abs).split(sep).join('/')

const allowlist = new Set(JSON.parse(readFileSync(ALLOWLIST_PATH, 'utf8')))
const offenders = []
const report = []
let total = 0

for (const abs of walk(SRC_DIR)) {
  const rel = toRel(abs)
  if (!isInit && allowlist.has(rel)) continue
  const violations = findViolations(readFileSync(abs, 'utf8'), abs)
  if (!violations.length) continue
  offenders.push(rel)
  if (!isInit) {
    total += violations.length
    for (const v of violations) report.push(`${rel}:${v.line}  [${v.kind}]  ${v.text}`)
  }
}

if (isInit) {
  const sorted = [...new Set(offenders)].sort()
  writeFileSync(ALLOWLIST_PATH, JSON.stringify(sorted, null, 2) + '\n')
  console.log(`i18n-scan --init: wrote ${sorted.length} file(s) to the allowlist`)
  process.exit(0)
}

if (total) {
  console.error(`i18n-scan: ${total} hardcoded string(s) in ${offenders.length} non-allowlisted file(s):\n`)
  console.error(report.join('\n'))
  console.error('\nLocalize with t(), or add an `i18n-ignore` marker for genuine non-UI strings. Do NOT re-add files to the allowlist.')
  process.exit(1)
}
console.log(`i18n-scan: OK — 0 hardcoded strings outside the allowlist (${allowlist.size} file(s) still allowlisted).`)
