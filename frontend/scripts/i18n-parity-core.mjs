export function flattenKeys(obj, prefix = '', acc = new Set()) {
  for (const [k, val] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (val && typeof val === 'object' && !Array.isArray(val)) flattenKeys(val, key, acc)
    else acc.add(key)
  }
  return acc
}

export function diffKeys(a, b) {
  const ka = flattenKeys(a)
  const kb = flattenKeys(b)
  return {
    missingInB: [...ka].filter((k) => !kb.has(k)).sort(),
    missingInA: [...kb].filter((k) => !ka.has(k)).sort(),
  }
}
