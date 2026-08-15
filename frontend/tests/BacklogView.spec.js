import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BacklogView from '../src/views/BacklogView.vue'

const activities = [
  { id: '1', name: 'Grocery run', estimatedDurationMinutes: 30, priority: 'MEDIUM', category: 'errands', status: 'UNPLANNED' },
  { id: '2', name: 'Doctor appointment', estimatedDurationMinutes: 60, priority: 'HIGH', category: null, status: 'PLANNED' },
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

  it('marks a planned activity distinctly from an unplanned one', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const statuses = wrapper.findAll('.activity-card__status')
    expect(statuses).toHaveLength(1)
    expect(statuses[0].text()).toBe('Planned')
  })

  it('shows a confirm step instead of deleting immediately when the activity is planned', async () => {
    const wrapper = mount(BacklogView)
    await flushPromises()

    const deleteButtons = wrapper.findAll('.activity-card__actions button').filter((b) => b.text() === 'Delete')
    await deleteButtons[1].trigger('click') // the PLANNED activity's delete button

    expect(wrapper.find('.backlog-view__confirm').exists()).toBe(true)
    expect(wrapper.text()).toContain('Doctor appointment')
  })
})
