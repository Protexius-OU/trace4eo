import { sha256, sha384, sha512 } from '@noble/hashes/sha2.js'
import { blake3 } from '@noble/hashes/blake3.js'

function createHasher(algorithm: string) {
  const normalised = algorithm.toUpperCase()
  if (normalised === 'SHA384') return sha384.create()
  if (normalised === 'SHA512') return sha512.create()
  return sha256.create()
}

export async function hashFile(file: File, algorithm: string): Promise<string> {
  const hasher = createHasher(algorithm)
  const reader = file.stream().getReader()

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    hasher.update(value)
  }

  const digest = hasher.digest()
  return btoa(String.fromCharCode(...digest))
}

export async function hashFileBlake3Hex(file: File): Promise<string> {
  const hasher = blake3.create({})
  const reader = file.stream().getReader()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    hasher.update(value)
  }
  return Array.from(hasher.digest(), b => b.toString(16).padStart(2, '0')).join('')
}
