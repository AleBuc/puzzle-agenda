// Pure time/layout helpers for the proportional day grid (DayGrid.vue,
// GridBlock.vue). toMinutes/formatMinutes/effectiveStart were extracted
// unchanged from the retired DayTimeline.vue's list-model computation.

export const MINUTES_PER_DAY = 24 * 60
const VERY_SHORT_THRESHOLD_MINUTES = 5

export function toMinutes(hhmm) {
  const [hours, minutes] = hhmm.split(':').map(Number)
  return hours * 60 + minutes
}

export function formatMinutes(minutes) {
  // No `% 24`: minutes only ever ranges 0..1440 here, and the end-of-day
  // boundary must display as "24:00", not wrap around to "00:00".
  const hours = String(Math.floor(minutes / 60)).padStart(2, '0')
  const mins = String(minutes % 60).padStart(2, '0')
  return `${hours}:${mins}`
}

// A spillover block (startsPreviousDay) starts before this day even began, so its
// effective start here is minute 0 — symmetric to how endsNextDay clamps the end to
// MINUTES_PER_DAY instead of the block's literal (next-day) endTime.
export function effectiveStart(block) {
  return block.startsPreviousDay ? 0 : toMinutes(block.startTime)
}

export function effectiveEnd(block) {
  return block.endsNextDay ? MINUTES_PER_DAY : toMinutes(block.endTime)
}

export function minutesToPercent(minutes) {
  return (minutes / MINUTES_PER_DAY) * 100
}

export function percentToMinutes(percent) {
  return (percent / 100) * MINUTES_PER_DAY
}

// Floors to the nearest 15-minute mark at or before `minutes` (creation-popup
// pre-fill snap, FR-008).
export function snapDownToQuarterHour(minutes) {
  return Math.floor(minutes / 15) * 15
}

// Rounds to the nearest 5-minute mark (popup adjustment / keyboard step
// granularity, FR-009 / FR-023).
export function snapToFiveMinutes(minutes) {
  return Math.round(minutes / 5) * 5
}

// Maps day.blocks into grid-ready positioned entries (FR-002). There is no
// "gap" entry here — free time is simply unrendered space (FR-007).
export function layoutBlocks(blocks) {
  return blocks.map((block) => {
    const start = effectiveStart(block)
    const end = effectiveEnd(block)
    return {
      block,
      topPercent: minutesToPercent(start),
      heightPercent: minutesToPercent(end) - minutesToPercent(start),
      isVeryShort: end - start <= VERY_SHORT_THRESHOLD_MINUTES,
      isContinuationOnly: !!block.startsPreviousDay,
    }
  })
}
