// Pure calendar-date (YYYY-MM-DD) helpers. The app's dates are timezone-naive
// wall-clock dates (research.md §1) — never mix a local-time `Date` with
// `toISOString()`'s UTC output. That mismatch silently shifts the result by
// a day depending on the browser's local UTC offset: for any zone ahead of
// UTC (e.g. UTC+2), local midnight's UTC representation still falls on the
// *previous* UTC calendar day, so `new Date(...).toISOString().slice(0, 10)`
// after adding/subtracting local days returns the wrong date.

/** The browser's local "today", read purely from local getters — never touches UTC. */
export function todayIsoDate() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** `isoDate` shifted by `days` calendar days. Stays anchored to UTC throughout (input and
 * output), so the local browser timezone can never contaminate the arithmetic. */
export function shiftIsoDate(isoDate, days) {
  const d = new Date(`${isoDate}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}
