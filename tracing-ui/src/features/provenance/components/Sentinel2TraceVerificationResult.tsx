import type { Sentinel2VerificationResponse } from '../types/provenance'

interface Props {
  result: Sentinel2VerificationResponse | null
  error: string | null
}

function resolveVariant(result: Sentinel2VerificationResponse | null, error: string | null): string {
  if (error || result?.status !== 'OK') return 'trace-verification-error'
  return 'trace-verification-ok'
}

function TraceVerificationContent({ result, error }: Props) {
  if (error) return (
    <>
      <h3 className="trace-verification-title">Could not check the Copernicus registry</h3>
      <p>The verification request did not complete: <code>{error}</code></p>
      <p className="trace-verification-hint">
        This usually means the Copernicus Traceability service is temporarily unavailable
        or the backend could not reach it. Try again in a few minutes.
      </p>
    </>
  )

  if (result?.status === 'OK') return (
    <>
      <h3 className="trace-verification-title">Product name found in the Copernicus registry</h3>
      <p>
        The data ID in this provenance record matches a real Sentinel-2 product registered
        with the{' '}
        <a
          href="https://documentation.dataspace.copernicus.eu/APIs/Traceability.html"
          target="_blank"
          rel="noreferrer"
        >Copernicus Data Space Ecosystem</a>
        , and Copernicus's signed record of that product is intact.
      </p>
      <p className="trace-verification-warning">
        <strong>This does not prove the file behind this record is the real product.</strong>{' '}
        The data ID is a string supplied at signing time and isn't compared against any
        file content. To prove the file matches, hash the original product file and check
        it against the registry below, or run the verification CLI against the file on
        disk.
      </p>
      <p className="trace-verification-meta">
        Registry reference: <code>{result.traceId}</code>
        {result.signatureAlgorithm && (
          <> &middot; Signature algorithm: <code>{result.signatureAlgorithm}</code></>
        )}
        {result.hashAlgorithm && (
          <> &middot; File hash algorithm used by Copernicus: <code>{result.hashAlgorithm}</code></>
        )}
      </p>
    </>
  )

  if (result?.status === 'TRACE_NOT_FOUND') return (
    <>
      <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
      <p>
        The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
        <code>{result.imageId}</code>.
      </p>
      <p className="trace-verification-hint">
        Common reasons: the data identifier doesn't exactly match a real Sentinel-2 product
        name (a typo when the record was signed), the product predates the Copernicus
        traceability service (records began in mid-April 2024 for newly-ingested products),
        or this provenance record was created from data that isn't actually a Sentinel-2
        product.
      </p>
    </>
  )

  return (
    <>
      <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
      <p>
        A Copernicus registry record exists for{' '}
        <code>{result?.imageId}</code>, but its digital signature failed verification.
      </p>
      <p className="trace-verification-hint">
        This is unusual. It can indicate a temporary problem at the Copernicus service, a
        certificate rotation that this client doesn't yet understand, or &mdash; least
        likely &mdash; that the registry entry has been tampered with.
      </p>
    </>
  )
}

export default function Sentinel2TraceVerificationResult({ result, error }: Props) {
  return (
    <div className={`trace-verification ${resolveVariant(result, error)}`}>
      <TraceVerificationContent result={result} error={error} />
    </div>
  )
}
