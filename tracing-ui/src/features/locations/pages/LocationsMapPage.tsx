import { useQuery } from '@tanstack/react-query'
import { fetchLocationCounts } from '../api/locationsApi'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import GlobeHeatMap from '../components/GlobeHeatMap'
import './LocationsMapPage.css'

export default function LocationsMapPage() {
  const authFetch = useAuthFetch()
  const { data, isLoading, error } = useQuery({
    queryKey: ['locationCounts'],
    queryFn: () => fetchLocationCounts(authFetch),
  })

  return (
    <div className="locations-page">
      {isLoading && <div className="locations-status">Loading globe…</div>}
      {error && (
        <div className="locations-status locations-status-error">
          Error loading locations: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      )}
      {data && <GlobeHeatMap counts={data} />}
    </div>
  )
}
