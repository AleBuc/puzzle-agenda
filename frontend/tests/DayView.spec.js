import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { router } from '../src/router'
import DayView from '../src/views/DayView.vue'

const DATE = '2026-08-16'

const horizon = { day1: '2026-08-01', forwardBound: '2026-09-11' }

const dayActivities = [
  { id: 'a1', name: 'Write report', remainingMinutesForDay: 180, dayStatus: 'PARTIALLY_PLANNED' },
  { id: 'a2', name: 'Course a pied', remainingMinutesForDay: 0, dayStatus: 'PLANNED' },
]

function dayResponse(blocks) {
  return { date: DATE, materialized: true, blocks }
}

function mockFetch(blocksRef, deleteResponse) {
  return vi.fn((url, options = {}) => {
    const method = options.method ?? 'GET'
    if (url.includes('/horizon')) {
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(horizon) })
    }
    if (url.includes('/activities')) {
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(dayActivities) })
    }
    if (url.includes(`/days/${DATE}`) && method === 'GET') {
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(dayResponse(blocksRef.value)) })
    }
    if (url.includes('/blocks/') && method === 'DELETE') {
      return Promise.resolve(
        deleteResponse ?? { status: 204, ok: true, json: () => Promise.resolve(null) },
      )
    }
    return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(null) })
  })
}

describe('DayView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists every activity in the selector with its remaining time for this day, marking fully-planned ones', async () => {
    vi.stubGlobal('fetch', mockFetch({ value: [] }))
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    // The activity selector only renders once "Planned activity" is chosen as the block type.
    await wrapper.find('select').setValue('PLANNED_ACTIVITY')
    await flushPromises()

    const activitySelect = wrapper.findAll('select')[1]
    const options = activitySelect.findAll('option').map((o) => o.text())

    expect(options.some((o) => o.includes('Write report') && o.includes('180min left'))).toBe(true)
    expect(options.some((o) => o.includes('Course a pied') && o.includes('fully planned'))).toBe(true)
  })

  // NOTE: the delete / multi-fragment-scope-confirmation / error-mapping tests that
  // used to live here (against `.time-block-card__actions` buttons rendered by the
  // now-retired DayTimeline/TimeBlockCard list) are intentionally removed as part of
  // 003-calendar-day-view's User Story 1: GridBlock has no per-row buttons — the whole
  // block is clickable and emits `activate`, which DayView does not yet wire to
  // anything. That coverage is rebuilt against the new BlockPopup-driven flow in
  // User Story 3 (see tasks.md T021).

  it('renders one grid block per day block', async () => {
    const first = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: null, activityId: 'a1', activityName: 'Write report' }
    const second = { id: 'b2', type: 'CONSTRAINED', startTime: '14:00', endTime: '15:00', endsNextDay: false, name: 'Meeting', activityId: null, activityName: null }
    const blocksRef = { value: [first, second] }
    vi.stubGlobal('fetch', mockFetch(blocksRef))
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.findAll('.grid-block')).toHaveLength(2)
    expect(wrapper.find('.time-block-card').exists()).toBe(false)
  })
})
