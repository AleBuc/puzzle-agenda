import { describe, expect, it } from 'vitest'
import {
  toMinutes,
  formatMinutes,
  effectiveStart,
  effectiveEnd,
  minutesToPercent,
  percentToMinutes,
  snapDownToQuarterHour,
  snapToFiveMinutes,
  layoutBlocks,
} from '../src/time-grid-utils'

describe('toMinutes', () => {
  it('converts an HH:mm string to minutes since midnight', () => {
    expect(toMinutes('00:00')).toBe(0)
    expect(toMinutes('09:30')).toBe(570)
    expect(toMinutes('23:59')).toBe(1439)
  })
})

describe('formatMinutes', () => {
  it('formats minutes back to HH:mm', () => {
    expect(formatMinutes(0)).toBe('00:00')
    expect(formatMinutes(570)).toBe('09:30')
  })

  it('formats the end-of-day boundary as 24:00, not 00:00', () => {
    expect(formatMinutes(1440)).toBe('24:00')
  })
})

describe('effectiveStart', () => {
  it('returns the block start time in minutes for a normal block', () => {
    expect(effectiveStart({ startTime: '09:00', startsPreviousDay: false })).toBe(540)
  })

  it('returns 0 for a block spilling over from the previous day', () => {
    expect(effectiveStart({ startTime: '23:00', startsPreviousDay: true })).toBe(0)
  })
})

describe('effectiveEnd', () => {
  it('returns the block end time in minutes for a normal block', () => {
    expect(effectiveEnd({ endTime: '10:30', endsNextDay: false })).toBe(630)
  })

  it('returns 1440 for a block continuing into the next day', () => {
    expect(effectiveEnd({ endTime: '07:00', endsNextDay: true })).toBe(1440)
  })
})

describe('minutesToPercent', () => {
  it('converts minutes since midnight to a percentage of the 24h day', () => {
    expect(minutesToPercent(0)).toBe(0)
    expect(minutesToPercent(720)).toBe(50)
    expect(minutesToPercent(1440)).toBe(100)
  })
})

describe('percentToMinutes', () => {
  it('is the inverse of minutesToPercent', () => {
    expect(percentToMinutes(0)).toBe(0)
    expect(percentToMinutes(50)).toBe(720)
    expect(percentToMinutes(100)).toBe(1440)
  })
})

describe('snapDownToQuarterHour', () => {
  it('floors to the nearest 15-minute mark at or before the given minutes', () => {
    expect(snapDownToQuarterHour(0)).toBe(0)
    expect(snapDownToQuarterHour(14)).toBe(0)
    expect(snapDownToQuarterHour(15)).toBe(15)
    expect(snapDownToQuarterHour(29)).toBe(15)
    expect(snapDownToQuarterHour(59)).toBe(45)
  })
})

describe('snapToFiveMinutes', () => {
  it('rounds to the nearest 5-minute mark', () => {
    expect(snapToFiveMinutes(0)).toBe(0)
    expect(snapToFiveMinutes(2)).toBe(0)
    expect(snapToFiveMinutes(3)).toBe(5)
    expect(snapToFiveMinutes(57)).toBe(55)
    expect(snapToFiveMinutes(58)).toBe(60)
  })
})

describe('layoutBlocks', () => {
  it('positions a normal block proportionally by start time and duration', () => {
    const blocks = [{ id: '1', startTime: '06:00', endTime: '18:00', startsPreviousDay: false, endsNextDay: false }]
    const [positioned] = layoutBlocks(blocks)

    expect(positioned.block).toBe(blocks[0])
    expect(positioned.topPercent).toBe(25)
    expect(positioned.heightPercent).toBe(50)
  })

  it('clamps a midnight-spanning block to the day edge it spills into', () => {
    const spillover = { id: '2', startTime: '22:00', endTime: '07:00', startsPreviousDay: true, endsNextDay: false }
    const [positioned] = layoutBlocks([spillover])

    expect(positioned.topPercent).toBe(0)
    expect(positioned.heightPercent).toBeCloseTo(minutesToPercent(toMinutes('07:00')), 5)
  })

  it('clamps a block continuing into the next day to the bottom edge', () => {
    const overnight = { id: '3', startTime: '23:00', endTime: '07:00', startsPreviousDay: false, endsNextDay: true }
    const [positioned] = layoutBlocks([overnight])

    expect(positioned.topPercent).toBeCloseTo(minutesToPercent(toMinutes('23:00')), 5)
    expect(positioned.heightPercent).toBeCloseTo(100 - minutesToPercent(toMinutes('23:00')), 5)
  })

  it('marks a block at or below 5 minutes as very short', () => {
    const short = { id: '4', startTime: '09:00', endTime: '09:05', startsPreviousDay: false, endsNextDay: false }
    const notShort = { id: '5', startTime: '10:00', endTime: '10:10', startsPreviousDay: false, endsNextDay: false }
    const [shortPositioned, notShortPositioned] = layoutBlocks([short, notShort])

    expect(shortPositioned.isVeryShort).toBe(true)
    expect(notShortPositioned.isVeryShort).toBe(false)
  })

  it('marks a spillover block as continuation-only, not interactive', () => {
    const spillover = { id: '6', startTime: '22:00', endTime: '07:00', startsPreviousDay: true, endsNextDay: false }
    const normal = { id: '7', startTime: '09:00', endTime: '10:00', startsPreviousDay: false, endsNextDay: false }
    const [spilloverPositioned, normalPositioned] = layoutBlocks([spillover, normal])

    expect(spilloverPositioned.isContinuationOnly).toBe(true)
    expect(normalPositioned.isContinuationOnly).toBe(false)
  })
})
