import test from 'node:test'
import assert from 'node:assert/strict'
import { findViolations } from './i18n-scan-core.mjs'

test('flags hardcoded JSX text', () => {
  const v = findViolations('const A = () => <button>Save changes</button>')
  assert.equal(v.length, 1)
  assert.equal(v[0].kind, 'jsx-text')
  assert.match(v[0].text, /Save changes/)
})

test('ignores translated t() calls', () => {
  const v = findViolations("const A = () => <button>{t('common:save')}</button>")
  assert.equal(v.length, 0)
})

test('flags hardcoded placeholder attribute', () => {
  const v = findViolations('const A = () => <input placeholder="Search issues" />')
  assert.equal(v.length, 1)
  assert.equal(v[0].kind, 'attr:placeholder')
})

test('ignores className and data-* attributes', () => {
  const v = findViolations('const A = () => <div className="flex gap-2" data-testid="Board" />')
  assert.equal(v.length, 0)
})

test('skips whitespace/punctuation-only JSX text', () => {
  const v = findViolations('const A = () => <div>  •  </div>')
  assert.equal(v.length, 0)
})

test('respects an i18n-ignore marker', () => {
  const code = [
    'const A = () => (',
    '  <div>',
    '    {/* i18n-ignore */}',
    '    <span>Raw literal</span>',
    '  </div>',
    ')',
  ].join('\n')
  assert.equal(findViolations(code).length, 0)
})
