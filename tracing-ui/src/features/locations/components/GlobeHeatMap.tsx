import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react'
import Globe, { type GlobeMethods } from 'react-globe.gl'
import { feature } from 'topojson-client'
import { scaleSequentialLog, interpolateOrRd } from 'd3'
import { MeshBasicMaterial } from 'three'
import countries110m from 'world-atlas/countries-110m.json'
import type { LocationCount } from '../types/locations'
import { normalizeCountryName, resolveCountryKey } from '../utils/countryAliases'
import LocationRecordsModal from './LocationRecordsModal'
import './GlobeHeatMap.css'

interface CountryFeature {
  type: 'Feature'
  geometry: unknown
  properties: { name?: string }
}

interface Props {
  counts: LocationCount[]
  chainRootId?: string
}

const EUROPE_VIEW = { lat: 52, lng: 15, altitude: 1.8 }
const BACKGROUND = '#faf8f3'
const OCEAN_COLOR = '#c8d6e2'
const ZERO_COUNT_COLOR = '#e8e0cf'
const STROKE_COLOR = '#6b7888'
const SIDE_COLOR = 'rgba(15, 23, 42, 0.08)'
const POLYGON_ALTITUDE = 0.01

// Parsed once per page load. The TopoJSON cannot change at runtime.
const COUNTRY_POLYGONS: CountryFeature[] = (() => {
  const topology = countries110m as unknown as Parameters<typeof feature>[0]
  const objects = (countries110m as unknown as { objects: Record<string, unknown> }).objects
  const countriesObject = objects.countries as Parameters<typeof feature>[1]
  const collection = feature(topology, countriesObject) as unknown as { features: CountryFeature[] }
  return collection.features
})()

// Module-scope so remounting the map doesn't reallocate the material.
const GLOBE_MATERIAL = new MeshBasicMaterial({ color: OCEAN_COLOR })

export default function GlobeHeatMap({ counts, chainRootId }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const globeRef = useRef<GlobeMethods | undefined>(undefined)
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  const [openCountry, setOpenCountry] = useState<{ name: string; key: string } | null>(null)

  const countByCountry = useMemo(() => {
    const map = new Map<string, number>()
    for (const entry of counts) {
      map.set(entry.country, entry.recordCount)
    }
    return map
  }, [counts])

  const maxCount = useMemo(
    () => counts.reduce((max, c) => Math.max(max, c.recordCount), 0),
    [counts],
  )

  const colorScale = useMemo(() => {
    const domainMax = Math.max(maxCount, 2)
    return scaleSequentialLog((t: number) => interpolateOrRd(0.2 + 0.65 * t)).domain([1, domainMax])
  }, [maxCount])

  useLayoutEffect(() => {
    const el = containerRef.current
    if (!el) return
    const updateSize = (width: number, height: number) => {
      if (width > 0 && height > 0) {
        setSize({ width, height })
      }
    }
    updateSize(el.clientWidth, el.clientHeight)
    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry) {
        updateSize(entry.contentRect.width, entry.contentRect.height)
      }
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const countForFeature = useCallback((feat: object): number => {
    const name = (feat as CountryFeature).properties?.name
    if (!name) return 0
    const key = resolveCountryKey(name, countByCountry)
    return key ? (countByCountry.get(key) ?? 0) : 0
  }, [countByCountry])

  const polygonCapColor = useCallback((p: object) => {
    const count = countForFeature(p)
    return count === 0 ? ZERO_COUNT_COLOR : colorScale(count)
  }, [countForFeature, colorScale])

  const polygonSideColor = useCallback(() => SIDE_COLOR, [])
  const polygonStrokeColor = useCallback(() => STROKE_COLOR, [])

  const polygonLabel = useCallback((p: object) => {
    const feat = p as CountryFeature
    const name = feat.properties?.name ?? 'Unknown'
    const count = countForFeature(p)
    return `<div class="globe-tip"><div class="globe-tip-name">${name}</div><div class="globe-tip-count">${count} record${count === 1 ? '' : 's'}</div></div>`
  }, [countForFeature])

  const onPolygonClick = useCallback((p: object) => {
    const feat = p as CountryFeature
    const displayName = feat.properties?.name
    if (!displayName) return
    const matchedKey = resolveCountryKey(displayName, countByCountry)
      ?? normalizeCountryName(displayName)
    if (!countByCountry.has(matchedKey)) return
    setOpenCountry({ name: displayName, key: matchedKey })
  }, [countByCountry])

  const onGlobeReady = useCallback(() => {
    globeRef.current?.pointOfView(EUROPE_VIEW, 0)
  }, [])

  return (
    <div className="globe-heatmap" ref={containerRef}>
      {size && (
        <Globe
          ref={globeRef}
          width={size.width}
          height={size.height}
          backgroundColor={BACKGROUND}
          globeImageUrl={null}
          globeMaterial={GLOBE_MATERIAL}
          showAtmosphere={false}
          showGraticules={false}
          polygonsData={COUNTRY_POLYGONS}
          polygonCapColor={polygonCapColor}
          polygonSideColor={polygonSideColor}
          polygonStrokeColor={polygonStrokeColor}
          polygonAltitude={POLYGON_ALTITUDE}
          polygonLabel={polygonLabel}
          polygonsTransitionDuration={400}
          onPolygonClick={onPolygonClick}
          onGlobeReady={onGlobeReady}
        />
      )}
      {openCountry && (
        <LocationRecordsModal
          countryName={openCountry.name}
          countryKey={openCountry.key}
          chainRootId={chainRootId}
          onClose={() => setOpenCountry(null)}
        />
      )}
    </div>
  )
}
