import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BacklogView from '../src/views/BacklogView.vue'

const activities = [
  {
    id: '1',
    name: 'Grocery run',
    estimatedDurationMinutes: 30,
    priority: 'MEDIUM',
    category: 'errands',
    totalFragmentCount: 0,
    plannedDayCount: 0,
    days: [],
  },
  {
    id: '2',
    name: 'Doctor appointment',
    estimatedDurationMinutes: 60,
    priority: 'HIGH',
    category: null,
    totalFragmentCount: 3,
    plannedDayCount: 2,
    days: [
      { day: '2026-08-16', plannedMinutes: 60, status: 'PLANNED' },
      { day: '2026-08-18', plannedMinutes: 30, status: 'PARTIALLY_PLANNED' },
    ],
  },
]

describe('BacklogView', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve({
          status: 200,
          ok: true,
          json: () => Promise.resolve(activities),
        }),
      ),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the backlog list with each activity name', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const names = wrapper.findAll('.activity-card__name').map((n) => n.text())
    expect(names).toEqual(['Grocery run', 'Doctor appointment'])
  })

  it('shows aggregate fragment info only for an activity with fragments', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const statuses = wrapper.findAll('.activity-card__status')
    expect(statuses).toHaveLength(1)
    expect(statuses[0].text()).toContain('Planned on 2 days')
    expect(statuses[0].text()).toContain('3 fragments')
  })

  it('expands the per-day breakdown when the aggregate summary is clicked', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    await wrapper.find('.activity-card__status').trigger('click')

    expect(wrapper.text()).toContain('2026-08-16: 60 min (PLANNED)')
    expect(wrapper.text()).toContain('2026-08-18: 30 min (PARTIALLY_PLANNED)')
  })

  it('shows a confirm step stating the exact fragment count instead of deleting immediately', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const deleteButtons = wrapper.findAll('.activity-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[1].trigger('click') // the activity with fragments

    expect(wrapper.find('.backlog-view__confirm').exists()).toBe(true)
    const confirmText = wrapper.find('.backlog-view__confirm').text()
    expect(confirmText).toContain('Doctor appointment')
    expect(confirmText).toContain('3 planned fragment(s)')
    expect(confirmText).toContain('2 day(s)')
  })

  it('deletes an activity with no fragments immediately, without a confirm step', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const deleteButtons = wrapper.findAll('.activity-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[0].trigger('click') // the fragment-free activity

    expect(wrapper.find('.backlog-view__confirm').exists()).toBe(false)
  })

  it('shows a mapped error message and reloads the list when a delete fails', async () => {
    const fetchMock = vi.fn((url, options = {}) => {
      const method = options.method ?? 'GET'
      if (method === 'DELETE') {
        return Promise.resolve({
          status: 409,
          ok: false,
          json: () => Promise.resolve({ reason: 'ACTIVITY_HAS_PLANNED_FRAGMENTS', message: 'Activity has 5 planned fragments...' }),
        })
      }
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(activities) })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(BacklogView)
    await flushPromises()

    const deleteButtons = wrapper.findAll('.activity-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[0].trigger('click') // the fragment-free activity
    await flushPromises()

    expect(wrapper.find('.backlog-view__error').text()).toBe(
      'This activity still has planned fragments and could not be deleted.',
    )
    const getCallsAfterFailure = fetchMock.mock.calls.filter(([, opts]) => (opts?.method ?? 'GET') === 'GET')
    expect(getCallsAfterFailure.length).toBeGreaterThan(1) // initial load + reload after failure
  })

  it('shows an empty state when the backlog has no activities', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve([]) })),
    )
    const wrapper = mount(BacklogView)
    await flushPromises()

    expect(wrapper.find('.backlog-view__empty').text()).toBe('No activities yet. Create your first one below.')
  })
})
