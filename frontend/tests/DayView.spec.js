import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
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

function mockFetch(blocksRef, postResponse) {
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
    if (url.includes('/blocks') && method === 'POST') {
      return Promise.resolve(
        postResponse ?? { status: 201, ok: true, json: () => Promise.resolve(null) },
      )
    }
    if (url.includes('/blocks/') && method === 'DELETE') {
      return Promise.resolve({ status: 204, ok: true, json: () => Promise.resolve(null) })
    }
    return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(null) })
  })
}

// BlockPopup's DialogContent is teleported to document.body, outside the
// mounted wrapper's own root — DOM queries for it must go through the body.
function body() {
  return new DOMWrapper(document.body)
}

async function openCreatePopup(wrapper) {
  await wrapper.find('.day-grid').trigger('click')
  await nextTick()
  await flushPromises()
}

describe('DayView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

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

  it('opens the creation popup from an empty grid slot, listing every activity with its remaining time', async () => {
    vi.stubGlobal('fetch', mockFetch({ value: [] }))
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openCreatePopup(wrapper)

    await body().find('select[name="type"]').setValue('PLANNED_ACTIVITY')
    await nextTick()

    const activitySelect = body().find('select[name="activity"]')
    const options = activitySelect.findAll('option').map((o) => o.text())
    expect(options.some((o) => o.includes('Write report') && o.includes('180min left'))).toBe(true)
    expect(options.some((o) => o.includes('Course a pied') && o.includes('fully planned'))).toBe(true)

    wrapper.unmount()
  })

  it('creates a block from an empty slot and closes the popup on success', async () => {
    const blocksRef = { value: [] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openCreatePopup(wrapper)
    await body().find('form').trigger('submit')
    await flushPromises()

    const postCall = fetchMock.mock.calls.find(([, opts]) => opts?.method === 'POST')
    expect(postCall).toBeTruthy()
    expect(body().find('.block-popup__content').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows the mapped overlap error inside the popup without closing it when creation fails', async () => {
    const blocksRef = { value: [] }
    const fetchMock = mockFetch(blocksRef, {
      status: 409,
      ok: false,
      json: () => Promise.resolve({ reason: 'TIME_BLOCK_OVERLAP', message: 'TimeRange[...] overlaps existing range TimeRange[...]' }),
    })
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openCreatePopup(wrapper)
    await body().find('form').trigger('submit')
    await flushPromises()

    expect(body().find('.block-popup__error').text()).toBe('This time slot overlaps an existing block.')
    expect(body().find('.block-popup__content').exists()).toBe(true)

    wrapper.unmount()
  })
})
