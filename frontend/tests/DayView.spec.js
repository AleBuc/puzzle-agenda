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

  it('deletes the only fragment of an activity on a day immediately, without a scope prompt', async () => {
    const block = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: null, activityId: 'a1', activityName: 'Write report' }
    const blocksRef = { value: [block] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    await wrapper.find('.time-block-card__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.find('.day-view__confirm').exists()).toBe(false)
    const deleteCall = fetchMock.mock.calls.find(([url, opts]) => opts?.method === 'DELETE')
    expect(deleteCall[0]).toContain('scope=self')
  })

  it('prompts for a scope choice when the activity has more than one fragment that day', async () => {
    const first = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '07:00', endTime: '07:20', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const second = { id: 'b2', type: 'PLANNED_ACTIVITY', startTime: '18:00', endTime: '18:25', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const blocksRef = { value: [first, second] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    const deleteButtons = wrapper.findAll('.time-block-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[0].trigger('click')

    expect(wrapper.find('.day-view__confirm').exists()).toBe(true)

    await wrapper.findAll('.day-view__confirm button')[1].trigger('click') // "Delete all fragments..."
    await flushPromises()

    const deleteCall = fetchMock.mock.calls.find(([url, opts]) => opts?.method === 'DELETE')
    expect(deleteCall[0]).toContain('scope=activityDay')
  })

  it('shows a mapped error message and reloads the day when a single-fragment delete fails', async () => {
    const block = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: null, activityId: 'a1', activityName: 'Write report' }
    const blocksRef = { value: [block] }
    const fetchMock = mockFetch(blocksRef, {
      status: 404,
      ok: false,
      json: () => Promise.resolve({ reason: 'TIME_BLOCK_NOT_FOUND', message: 'TimeBlock[id=b1] not found' }),
    })
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    const dayGetCallsBefore = fetchMock.mock.calls.filter(
      ([url, opts]) => url.includes(`/days/${DATE}`) && (opts?.method ?? 'GET') === 'GET',
    ).length

    await wrapper.find('.time-block-card__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.find('.day-view__error').text()).toBe('This block no longer exists. The view has been refreshed.')
    expect(wrapper.find('.day-view__error').text()).not.toContain('TimeBlock[id=b1]')

    const dayGetCallsAfter = fetchMock.mock.calls.filter(
      ([url, opts]) => url.includes(`/days/${DATE}`) && (opts?.method ?? 'GET') === 'GET',
    ).length
    expect(dayGetCallsAfter).toBeGreaterThan(dayGetCallsBefore)
  })

  it('falls back to the generic message when a multi-fragment delete fails with an unknown code', async () => {
    const first = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '07:00', endTime: '07:20', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const second = { id: 'b2', type: 'PLANNED_ACTIVITY', startTime: '18:00', endTime: '18:25', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const blocksRef = { value: [first, second] }
    const fetchMock = mockFetch(blocksRef, {
      status: 500,
      ok: false,
      json: () => Promise.resolve({ reason: 'SOMETHING_UNEXPECTED', message: 'boom' }),
    })
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] } })
    await flushPromises()

    const deleteButtons = wrapper.findAll('.time-block-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[0].trigger('click')
    await wrapper.findAll('.day-view__confirm button')[1].trigger('click') // "Delete all fragments..."
    await flushPromises()

    expect(wrapper.find('.day-view__confirm').exists()).toBe(false)
    expect(wrapper.find('.day-view__error').text()).toBe('Something went wrong. Please try again.')
  })
})
