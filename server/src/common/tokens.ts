import { randomBytes, randomUUID, timingSafeEqual } from 'node:crypto'

/** 128-bit random, URL-safe. Used for share links and driver tokens, so it must not be guessable. */
export function newToken() {
  return randomBytes(16).toString('base64url')
}

export function newId() {
  return randomUUID()
}

export function tokensEqual(a: string, b: string) {
  const x = Buffer.from(a)
  const y = Buffer.from(b)
  return x.length === y.length && timingSafeEqual(x, y)
}
