import { useQuery } from '@tanstack/react-query'
import { useAuthFetch } from '@/core/auth/useAuthFetch'
import { fetchChainLocationCounts } from '../api/locationsApi'
import GlobeHeatMap from './GlobeHeatMap'
import './ProvenanceChainMap.css'

interface Props {
  rootId: string
}

export default function ProvenanceChainMap({ rootId }: Props) {
  const authFetch = useAuthFetch()
  const { data, isLoading, error } = useQuery({
    queryKey: ['chainLocationCounts', rootId],
    queryFn: () => fetchChainLocationCounts(authFetch, rootId),
  })

  return (
    <div className="chain-map">
      {isLoading && <div className="chain-map-status">Loading globe…</div>}
      {error && (
        <div className="chain-map-status chain-map-status-error">
          Error loading locations: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      )}
      {data && <GlobeHeatMap counts={data} chainRootId={rootId} />}
    </div>
  )
}
