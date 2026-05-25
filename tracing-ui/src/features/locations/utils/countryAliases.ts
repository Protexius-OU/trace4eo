// Maps Natural Earth 110m polygon names (lower-cased) to common user-data
// aliases that may appear in metadata.attributes.location. All entries are
// stored lower-case + trimmed to match the canonical form returned by
// countByLocation. Order within an alias list doesn't matter; the first one
// found in the count map wins.
const POLYGON_NAME_ALIASES: Record<string, readonly string[]> = {
  'united states of america': ['usa', 'us', 'united states'],
  'united kingdom': ['uk', 'great britain', 'britain', 'england'],
  'russia': ['russian federation'],
  'czechia': ['czech republic'],
  'ivory coast': ["côte d'ivoire", "cote d'ivoire"],
  'myanmar': ['burma'],
  'south korea': ['republic of korea', 'korea (south)', 'korea'],
  'north korea': ['dprk', "democratic people's republic of korea", 'korea (north)'],
  'democratic republic of the congo': ['drc', 'congo (kinshasa)', 'congo-kinshasa'],
  'republic of the congo': ['congo', 'congo (brazzaville)', 'congo-brazzaville'],
  'united republic of tanzania': ['tanzania'],
  'bosnia and herzegovina': ['bosnia', 'bih'],
  'north macedonia': ['macedonia', 'fyrom', 'republic of macedonia'],
  'eswatini': ['swaziland'],
  'cabo verde': ['cape verde'],
  'east timor': ['timor-leste'],
  'palestine': ['state of palestine', 'palestinian territories'],
  'vatican': ['vatican city', 'holy see'],
  'the bahamas': ['bahamas'],
  'the gambia': ['gambia'],
}

export function normalizeCountryName(name: string): string {
  return name.trim().toLowerCase()
}

// Returns the count-map key that matches the given polygon name, or null if
// neither the polygon name nor any of its known aliases is present.
export function resolveCountryKey(
  polygonName: string,
  countByCountry: ReadonlyMap<string, number>,
): string | null {
  const direct = normalizeCountryName(polygonName)
  if (countByCountry.has(direct)) return direct
  const aliases = POLYGON_NAME_ALIASES[direct]
  if (aliases) {
    for (const alias of aliases) {
      if (countByCountry.has(alias)) return alias
    }
  }
  return null
}
