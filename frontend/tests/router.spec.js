import { describe, expect, it } from 'vitest'
import { router } from '../src/router'

describe('router', () => {
  it('resolves a /days/:date route with the date as a prop', async () => {
    const resolved = router.resolve('/days/2026-08-16')

    expect(resolved.name).toBe('day')
    expect(resolved.params.date).toBe('2026-08-16')
  })

  it('redirects the root path to today\'s day route', async () => {
    const resolved = router.resolve('/')

    expect(resolved.redirectedFrom ?? resolved.path).toBeDefined()
  })
})
