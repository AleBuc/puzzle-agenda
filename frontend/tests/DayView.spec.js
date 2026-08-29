import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { router } from '../src/router'
import DayView from '../src/views/DayView.vue'
import { shiftIsoDate } from '../src/date-utils'

const DATE = '2026-08-16'

const horizon = { day1: '2026-08-01', forwardBound: '2026-09-11' }

const dayActivities = [
  { id: 'a1', name: 'Write report', remainingMinutesForDay: 180, dayStatus: 'PARTIALLY_PLANNED' },
  { id: 'a2', name: 'Course a pied', remainingMinutesForDay: 0, dayStatus: 'PLANNED' },
]

function dayResponse(blocks) {
  return { date: DATE, materialized: true, blocks }
}

function mockFetch(blocksRef, { postResponse, putResponse, deleteResponse } = {}) {
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
    if (url.includes('/days/') && method === 'GET') {
      // Any other day (e.g. the previous day, for a "go to start day" navigation).
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(dayResponse([])) })
    }
    if (url.includes('/blocks') && method === 'POST') {
      return Promise.resolve(
        postResponse ?? { status: 201, ok: true, json: () => Promise.resolve(null) },
      )
    }
    if (url.includes('/blocks/') && method === 'PUT') {
      return Promise.resolve(putResponse ?? { status: 200, ok: true, json: () => Promise.resolve(null) })
    }
    if (url.includes('/blocks/') && method === 'DELETE') {
      return Promise.resolve(deleteResponse ?? { status: 204, ok: true, json: () => Promise.resolve(null) })
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

async function openDetailsPopup(wrapper, index = 0) {
  await wrapper.findAll('.grid-block')[index].trigger('click')
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

    wrapper.unmount()
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
      postResponse: {
        status: 409,
        ok: false,
        json: () => Promise.resolve({ reason: 'TIME_BLOCK_OVERLAP', message: 'TimeRange[...] overlaps existing range TimeRange[...]' }),
      },
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

  it('edits an existing block from the details popup', async () => {
    const block = { id: 'b1', type: 'CONSTRAINED', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: 'Standup', activityId: null, activityName: null }
    const blocksRef = { value: [block] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openDetailsPopup(wrapper)
    await body().find('.block-popup__edit').trigger('click')
    await body().find('input[type="time"]').setValue('11:00')
    await body().find('form').trigger('submit')
    await flushPromises()

    const putCall = fetchMock.mock.calls.find(([, opts]) => opts?.method === 'PUT')
    expect(putCall).toBeTruthy()
    expect(JSON.parse(putCall[1].body)).toMatchObject({ startTime: '11:00' })
    expect(body().find('.block-popup__content').exists()).toBe(false)

    wrapper.unmount()
  })

  it('deletes a single-fragment block immediately from the details popup', async () => {
    const block = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: null, activityId: 'a1', activityName: 'Write report' }
    const blocksRef = { value: [block] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openDetailsPopup(wrapper)
    await body().find('.block-popup__delete').trigger('click')
    await flushPromises()

    expect(body().find('.block-popup__delete-confirm').exists()).toBe(false)
    const deleteCall = fetchMock.mock.calls.find(([, opts]) => opts?.method === 'DELETE')
    expect(deleteCall[0]).toContain('scope=self')
    expect(body().find('.block-popup__content').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows the in-place fragment-scope choice and deletes with the chosen scope', async () => {
    const first = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '07:00', endTime: '07:20', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const second = { id: 'b2', type: 'PLANNED_ACTIVITY', startTime: '18:00', endTime: '18:25', endsNextDay: false, name: null, activityId: 'a2', activityName: 'Course a pied' }
    const blocksRef = { value: [first, second] }
    const fetchMock = mockFetch(blocksRef)
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openDetailsPopup(wrapper, 0)
    await body().find('.block-popup__delete').trigger('click')

    expect(body().find('.block-popup__delete-confirm').exists()).toBe(true)

    await body().find('.block-popup__delete-all').trigger('click')
    await flushPromises()

    const deleteCall = fetchMock.mock.calls.find(([, opts]) => opts?.method === 'DELETE')
    expect(deleteCall[0]).toContain('scope=activityDay')

    wrapper.unmount()
  })

  it('shows a mapped error and reloads the grid when a delete fails (stale state)', async () => {
    const block = { id: 'b1', type: 'PLANNED_ACTIVITY', startTime: '09:00', endTime: '10:00', endsNextDay: false, name: null, activityId: 'a1', activityName: 'Write report' }
    // A 404 on DELETE means the block is already gone server-side (e.g. deleted
    // from a second tab) — the reload right after must reflect that reality.
    const blocksRef = { value: [block] }
    const fetchMock = mockFetch(blocksRef, {
      deleteResponse: {
        status: 404,
        ok: false,
        json: () => {
          blocksRef.value = []
          return Promise.resolve({ reason: 'TIME_BLOCK_NOT_FOUND', message: 'TimeBlock[id=b1] not found' })
        },
      },
    })
    vi.stubGlobal('fetch', fetchMock)
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    const dayGetCallsBefore = fetchMock.mock.calls.filter(
      ([url, opts]) => url.includes(`/days/${DATE}`) && (opts?.method ?? 'GET') === 'GET',
    ).length

    await openDetailsPopup(wrapper)
    await body().find('.block-popup__delete').trigger('click')
    await flushPromises()

    expect(body().find('.block-popup__error').text()).toBe('This block no longer exists. The view has been refreshed.')

    const dayGetCallsAfter = fetchMock.mock.calls.filter(
      ([url, opts]) => url.includes(`/days/${DATE}`) && (opts?.method ?? 'GET') === 'GET',
    ).length
    expect(dayGetCallsAfter).toBeGreaterThan(dayGetCallsBefore)
    // The grid behind it refreshes to the real (now block-less) state, but the
    // popup itself stays open so the error message stays visible — same
    // pattern as a failed create/edit.
    expect(body().find('.block-popup__content').exists()).toBe(true)
    expect(wrapper.findAll('.grid-block')).toHaveLength(0)

    wrapper.unmount()
  })

  it('opens a read-only popup for a spillover block and navigates to its start day via the link', async () => {
    const spillover = { id: 'b1', type: 'ROUTINE', startTime: '22:30', endTime: '07:00', endsNextDay: false, startsPreviousDay: true, name: 'Sleep', activityId: null, activityName: null }
    const blocksRef = { value: [spillover] }
    vi.stubGlobal('fetch', mockFetch(blocksRef))
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openDetailsPopup(wrapper)

    expect(body().find('.block-popup__edit').exists()).toBe(false)
    expect(body().find('.block-popup__delete').exists()).toBe(false)
    expect(body().find('.block-popup__content').text()).toContain('22:30')

    await body().find('.block-popup__go-to-start-day').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.params.date).toBe(shiftIsoDate(DATE, -1))

    wrapper.unmount()
  })

  it('suspends the day-navigation arrow-key shortcut while a popup is open, and resumes once it closes', async () => {
    vi.stubGlobal('fetch', mockFetch({ value: [] }))
    router.push(`/days/${DATE}`)
    await router.isReady()
    const wrapper = mount(DayView, { props: { date: DATE }, global: { plugins: [router] }, attachTo: document.body })
    await flushPromises()

    await openCreatePopup(wrapper)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(router.currentRoute.value.params.date).toBe(DATE)

    // Escape closes the popup (no unsaved content in an otherwise-empty create form).
    const { DialogContent } = await import('reka-ui')
    wrapper.findComponent(DialogContent).vm.$emit('escapeKeyDown', new KeyboardEvent('keydown'))
    await flushPromises()
    expect(body().find('.block-popup__content').exists()).toBe(false)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    await flushPromises()
    expect(router.currentRoute.value.params.date).toBe(shiftIsoDate(DATE, 1))

    wrapper.unmount()
  })
})
