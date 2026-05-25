export function commonPrefix(strings: string[]): string {
  if (strings.length < 2) return ''
  let prefix = strings[0] ?? ''
  for (let i = 1; i < strings.length; i++) {
    const s = strings[i] ?? ''
    while (!s.startsWith(prefix)) {
      prefix = prefix.slice(0, -1)
      if (prefix === '') return ''
    }
  }
  return prefix
}

export function meaningfulPrefix(strings: string[]): string {
  const filtered = strings.filter((s): s is string => Boolean(s))
  if (filtered.length < 2) return ''
  const cp = commonPrefix(filtered)
  if (filtered.some(s => s === cp)) return ''
  const m = cp.match(/^(.+[-_./:])/)
  const trimmed = m?.[1] ?? ''
  return trimmed.length >= 4 ? trimmed : ''
}

export function stripPrefix(str: string | null | undefined, prefix: string): string {
  if (!str) return ''
  return prefix && str.startsWith(prefix) ? str.slice(prefix.length) : str
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleDateString(undefined, { dateStyle: 'medium' })
}

export function sortTypesByMinDepth(
  nodes: Array<{ dataType: string; depth: number }>,
): string[] {
  const typeMinDepth = new Map<string, number>()
  for (const node of nodes) {
    const current = typeMinDepth.get(node.dataType)
    if (current === undefined || node.depth < current) {
      typeMinDepth.set(node.dataType, node.depth)
    }
  }
  return [...typeMinDepth.entries()]
    .sort((a, b) => a[1] - b[1])
    .map(([type]) => type)
}
