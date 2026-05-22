import type { Sentinel2HashCheckResponse, Sentinel2HashCheckFileStatus } from '../types/provenance'

interface Props {
  result: Sentinel2HashCheckResponse | null
  error: string | null
}

function resolveVariant(result: Sentinel2HashCheckResponse | null, error: string | null): string {
  if (error) return 'trace-verification-error'
  if (!result || result.traceStatus !== 'OK') return 'trace-verification-error'
  const isMulti = result.fileResults.length > 1
  if (isMulti) return result.matchedFiles === result.totalFiles ? 'trace-verification-ok' : 'trace-verification-error'
  return result.fileResults[0]?.status === 'OK' ? 'trace-verification-ok' : 'trace-verification-error'
}

function FileCheckContent({ result, error }: Props) {
  const fileResult = result?.fileResults[0]
  const status: Sentinel2HashCheckFileStatus | undefined = fileResult?.status
  const isMulti = (result?.fileResults.length ?? 0) > 1
  const allMatched = result ? result.matchedFiles === result.totalFiles : false

  if (error) return (
    <>
      <h3 className="trace-verification-title">Could not check against Copernicus</h3>
      <p>The verification request did not complete: <code>{error}</code></p>
    </>
  )

  if (result?.traceStatus === 'TRACE_NOT_FOUND') return (
    <>
      <h3 className="trace-verification-title">✗ No matching record in the Copernicus registry</h3>
      <p>
        The Copernicus Data Space Ecosystem has no Sentinel-2 product matching{' '}
        <code>{result.imageId}</code>, so nothing can be checked.
      </p>
    </>
  )

  if (result?.traceStatus === 'SIGNATURE_ERROR') return (
    <>
      <h3 className="trace-verification-title">✗ Registry record found, but signature could not be verified</h3>
      <p>
        A Copernicus record exists for <code>{result.imageId}</code>, but its
        digital signature failed verification, so file hash comparisons were not performed.
      </p>
    </>
  )

  if (isMulti && result) return (
    <>
      <h3 className="trace-verification-title">
        {allMatched
          ? `✓ All ${result.totalFiles} files match the official Copernicus product`
          : `✗ Some files do not match the official Copernicus product`}
      </h3>
      <p>Of <strong>{result.totalFiles}</strong> files you supplied:</p>
      <ul className="trace-verification-checks">
        <li>
          <strong>{result.matchedFiles}</strong> matched the official hash in
          the Copernicus registry.
        </li>
        {result.mismatchedFiles > 0 && (
          <li>
            <strong>{result.mismatchedFiles}</strong> had the right name in the
            registry but the wrong content (hash mismatch — file was modified or corrupted).
          </li>
        )}
        {result.filesNotInTrace > 0 && (
          <li>
            <strong>{result.filesNotInTrace}</strong> were not in Copernicus's
            signed file list. Copernicus doesn't sign every file in a product, and the
            exact set varies by product type — so this isn't automatically a tampering
            signal.
          </li>
        )}
      </ul>
      <details className="trace-verification-details">
        <summary>Per-file results ({result.fileResults.length})</summary>
        <table className="trace-verification-table">
          <thead><tr><th>File</th><th>Status</th></tr></thead>
          <tbody>
            {result.fileResults.map(r => (
              <tr key={r.filename} className={`trace-verification-row-${r.status.toLowerCase()}`}>
                <td><code>{r.filename}</code></td>
                <td>
                  {r.status === 'OK' ? '✓ matched' :
                   r.status === 'HASH_MISMATCH' ? '✗ hash mismatch' :
                   '○ not in registry'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
      <p className="trace-verification-meta">
        Registry reference: <code>{result.traceId}</code>
        {result.signatureAlgorithm && (
          <> &middot; Signature: <code>{result.signatureAlgorithm}</code></>
        )}
      </p>
    </>
  )

  if (status === 'OK' && fileResult) return (
    <>
      <h3 className="trace-verification-title">✓ This file is the official Sentinel-2 product</h3>
      <p>
        The hash of <code>{fileResult.filename}</code> exactly matches what
        Copernicus has signed for this product. Combined with the signature on the
        Copernicus record (also verified), this proves the file you supplied is
        byte-for-byte identical to what Copernicus published.
      </p>
      <p className="trace-verification-meta">
        Hash matched: <code>{fileResult.providedHash}</code>
        <br />
        Registry reference: <code>{result?.traceId}</code>
        {result?.signatureAlgorithm && (
          <> &middot; Signature: <code>{result.signatureAlgorithm}</code></>
        )}
      </p>
    </>
  )

  if (status === 'HASH_MISMATCH' && fileResult) return (
    <>
      <h3 className="trace-verification-title">✗ File does not match the official product</h3>
      <p>
        A file named <code>{fileResult.filename}</code> exists in Copernicus's signed
        product, but its hash differs from what you supplied. This means the file
        you picked is <strong>not</strong> byte-for-byte identical to the official
        Sentinel-2 product, even though the names match.
      </p>
      <p className="trace-verification-meta">
        Your file's hash: <code>{fileResult.providedHash}</code>
        <br />
        Copernicus's signed hash: <code>{fileResult.expectedHash}</code>
      </p>
      <p className="trace-verification-hint">
        Likely reasons: the file was modified after download, you picked a different
        version of the same product, or the download is corrupted.
      </p>
    </>
  )

  if (status === 'FILE_NOT_IN_TRACE' && fileResult) return (
    <>
      <h3 className="trace-verification-title">File not found in Copernicus's signed file list</h3>
      <p>
        Copernicus has a record for <code>{result?.imageId}</code>, but no entry
        in its signed contents matches the file name <code>{fileResult.filename}</code>.
      </p>
      <p className="trace-verification-hint">
        Copernicus doesn't sign every file in a product, and the exact set varies by
        product type — so this isn't automatically a tampering signal. Try a different
        file from the product.
      </p>
    </>
  )

  return null
}

export default function Sentinel2FileCheckResult({ result, error }: Props) {
  return (
    <div className={`trace-verification ${resolveVariant(result, error)}`}>
      <FileCheckContent result={result} error={error} />
    </div>
  )
}
