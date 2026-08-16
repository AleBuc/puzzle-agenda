import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { shiftIsoDate, todayIsoDate } from '../src/date-utils'

describe('date-utils', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  describe('shiftIsoDate', () => {
    it('moves forward by one day', () => {
      expect(shiftIsoDate('2026-08-15', 1)).toBe('2026-08-16')
    })

    it('moves backward by one day', () => {
      expect(shiftIsoDate('2026-08-15', -1)).toBe('2026-08-14')
    })

    it('crosses a month boundary', () => {
      expect(shiftIsoDate('2026-08-31', 1)).toBe('2026-09-01')
    })

    it('crosses a year boundary', () => {
      expect(shiftIsoDate('2026-12-31', 1)).toBe('2027-01-01')
    })

    // Regression test: a naive `new Date(dateStr).toISOString()` round-trip silently
    // shifts by a day in any timezone ahead of UTC (e.g. UTC+2) — local midnight's UTC
    // representation still falls on the previous UTC calendar day. This must hold
    // regardless of which timezone the test runner's machine is in.
    it('is correct in a timezone ahead of UTC (would misbehave with local Date arithmetic)', () => {
      vi.stubEnv('TZ', 'Europe/Paris') // UTC+1 or UTC+2 depending on DST
      expect(shiftIsoDate('2026-08-15', 1)).toBe('2026-08-16')
      expect(shiftIsoDate('2026-08-15', -1)).toBe('2026-08-14')
    })

    it('is correct in a timezone behind UTC', () => {
      vi.stubEnv('TZ', 'America/Los_Angeles') // UTC-7 or UTC-8 depending on DST
      expect(shiftIsoDate('2026-08-15', 1)).toBe('2026-08-16')
      expect(shiftIsoDate('2026-08-15', -1)).toBe('2026-08-14')
    })
  })

  describe('todayIsoDate', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('returns the local calendar date, not the UTC one', () => {
      // Pin the test machine's own timezone to UTC+2 (Paris, in August) so the
      // assertion holds regardless of the CI host's actual default timezone —
      // both the "current instant" below and todayIsoDate()'s local getters
      // must resolve through the same zone for this test to mean anything.
      vi.stubEnv('TZ', 'Europe/Paris')
      // 2026-08-16T01:00 Paris time is still 2026-08-15T23:00 in UTC.
      vi.setSystemTime(new Date('2026-08-16T01:00:00+02:00'))

      expect(todayIsoDate()).toBe('2026-08-16')
    })
  })
})
