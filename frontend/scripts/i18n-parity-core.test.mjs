import test from 'node:test'
import assert from 'node:assert/strict'
import { flattenKeys, diffKeys } from './i18n-parity-core.mjs'

test('flattenKeys flattens nested objects to leaf paths', () => {
  const keys = [...flattenKeys({ a: { b: 'x' }, c: 'y' })].sort()
  assert.deepEqual(keys, ['a.b', 'c'])
})

test('diffKeys reports keys missing on each side', () => {
  const { missingInB, missingInA } = diffKeys({ a: '1', b: '2' }, { a: '1', c: '3' })
  assert.deepEqual(missingInB, ['b'])
  assert.deepEqual(missingInA, ['c'])
})

test('diffKeys is empty when key sets match regardless of values', () => {
  const { missingInB, missingInA } = diffKeys({ a: { b: '1' } }, { a: { b: '2' } })
  assert.equal(missingInB.length, 0)
  assert.equal(missingInA.length, 0)
})
