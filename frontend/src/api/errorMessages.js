// Maps business error codes (ApiError#reason, contracts/api.md Error
// Conventions) to user-facing messages. The backend's raw `message` is
// sometimes a domain object dump (e.g. TimeRange[start=...] overlaps...)
// and must never reach the UI directly — always resolve through this
// dictionary instead.

export const ERROR_MESSAGES = {
  TIME_BLOCK_OVERLAP: 'This time slot overlaps an existing block.',
  TEMPLATE_ENTRY_OVERLAP: 'This entry overlaps an existing routine template entry.',
  DAY_BEYOND_FORWARD_HORIZON: 'This day is beyond the 2-week planning horizon.',
  DAY_NOT_REACHABLE: 'This day is not reachable.',
  INVALID_TIME_GRANULARITY: 'Start and end times must be 5-minute increments and form a valid range.',
  ACTIVITY_NOT_AVAILABLE: 'This activity is not available.',
  TIME_BLOCK_NOT_FOUND: 'This block no longer exists. The view has been refreshed.',
  ACTIVITY_NOT_FOUND: 'This activity no longer exists. The view has been refreshed.',
  ACTIVITY_HAS_PLANNED_FRAGMENTS: 'This activity still has planned fragments and could not be deleted.',
  PLANNED_ACTIVITY_SPANS_MIDNIGHT: "A planned activity block can't span across midnight.",
  ROUTINE_TEMPLATE_ENTRY_NOT_FOUND: 'This routine entry no longer exists. The view has been refreshed.',
  INVALID_REQUEST: 'This request is invalid. Please check the values and try again.',
}

export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.'

export function resolveErrorMessage(reason) {
  return ERROR_MESSAGES[reason] ?? GENERIC_ERROR_MESSAGE
}
