import ts from 'typescript'

const CHECKED_ATTRS = new Set(['placeholder', 'title', 'aria-label', 'alt', 'label'])
const HAS_LETTER = /\p{L}/u

function collectIgnoredLines(code) {
  const ignored = new Set()
  code.split(/\r?\n/).forEach((line, i) => {
    if (line.includes('i18n-ignore')) {
      ignored.add(i + 1) // 1-based comment line
      ignored.add(i + 2) // line immediately below
    }
  })
  return ignored
}

export function findViolations(code, fileName = 'file.tsx') {
  const sf = ts.createSourceFile(fileName, code, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const ignoredLines = collectIgnoredLines(code)
  const violations = []
  const lineOf = (pos) => sf.getLineAndCharacterOfPosition(pos).line + 1

  function push(pos, text, kind) {
    const line = lineOf(pos)
    if (!ignoredLines.has(line)) violations.push({ line, text, kind })
  }

  function visit(node) {
    if (ts.isJsxText(node)) {
      if (HAS_LETTER.test(node.text)) push(node.getStart(sf), node.text.trim(), 'jsx-text')
    } else if (ts.isJsxAttribute(node) && node.name) {
      const name = node.name.getText(sf)
      if (CHECKED_ATTRS.has(name)) {
        const init = node.initializer
        let str = null
        if (init && ts.isStringLiteral(init)) str = init
        else if (init && ts.isJsxExpression(init) && init.expression && ts.isStringLiteral(init.expression)) str = init.expression
        if (str && HAS_LETTER.test(str.text)) push(str.getStart(sf), str.text, `attr:${name}`)
      }
    }
    ts.forEachChild(node, visit)
  }

  visit(sf)
  return violations
}
