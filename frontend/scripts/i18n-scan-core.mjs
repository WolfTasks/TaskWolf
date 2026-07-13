import ts from 'typescript'

const CHECKED_ATTRS = new Set(['placeholder', 'title', 'aria-label', 'alt', 'label'])
const HAS_LETTER = /\p{L}/u

function templateHasLetter(node) {
  if (ts.isNoSubstitutionTemplateLiteral(node)) return HAS_LETTER.test(node.text)
  if (ts.isTemplateExpression(node)) {
    if (HAS_LETTER.test(node.head.text)) return true
    return node.templateSpans.some((span) => HAS_LETTER.test(span.literal.text))
  }
  return false
}

function templateText(node) {
  if (ts.isNoSubstitutionTemplateLiteral(node)) return node.text
  if (ts.isTemplateExpression(node)) {
    return node.head.text + node.templateSpans.map((span) => span.literal.text).join('')
  }
  return ''
}

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
    } else if (ts.isJsxExpression(node) && node.parent && (ts.isJsxElement(node.parent) || ts.isJsxFragment(node.parent))) {
      const expr = node.expression
      if (expr && ts.isStringLiteral(expr)) {
        if (HAS_LETTER.test(expr.text)) push(expr.getStart(sf), expr.text, 'jsx-expr-text')
      } else if (expr && (ts.isNoSubstitutionTemplateLiteral(expr) || ts.isTemplateExpression(expr))) {
        if (templateHasLetter(expr)) push(expr.getStart(sf), templateText(expr), 'jsx-expr-text')
      }
    } else if (ts.isJsxAttribute(node)) {
      const name = node.name.getText(sf)
      if (CHECKED_ATTRS.has(name)) {
        const init = node.initializer
        let str = null
        let tmpl = null
        if (init && ts.isStringLiteral(init)) str = init
        else if (init && ts.isJsxExpression(init) && init.expression) {
          const expr = init.expression
          if (ts.isStringLiteral(expr)) str = expr
          else if (ts.isNoSubstitutionTemplateLiteral(expr) || ts.isTemplateExpression(expr)) tmpl = expr
        }
        if (str && HAS_LETTER.test(str.text)) push(str.getStart(sf), str.text, `attr:${name}`)
        else if (tmpl && templateHasLetter(tmpl)) push(tmpl.getStart(sf), templateText(tmpl), `attr:${name}`)
      }
    }
    ts.forEachChild(node, visit)
  }

  visit(sf)
  return violations
}
