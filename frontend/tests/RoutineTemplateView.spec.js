import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import RoutineTemplateView from '../src/views/RoutineTemplateView.vue'

const entries = [{ id: 'e1', name: 'Sleep', startTime: '23:00', endTime: '07:00' }]

describe('RoutineTemplateView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a mapped error message and reloads the list when a delete fails', async () => {
    const fetchMock = vi.fn((url, options = {}) => {
      const method = options.method ?? 'GET'
      if (method === 'DELETE') {
        return Promise.resolve({
          status: 404,
          ok: false,
          json: () => Promise.resolve({ reason: 'ROUTINE_TEMPLATE_ENTRY_NOT_FOUND', message: 'RoutineTemplateEntry[id=e1] not found' }),
        })
      }
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(entries) })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(RoutineTemplateView)
    await flushPromises()

    await wrapper.find('.routine-entry-card__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.find('.routine-template-view__error').text()).toBe(
      'This routine entry no longer exists. The view has been refreshed.',
    )
    const getCallsAfterFailure = fetchMock.mock.calls.filter(([, opts]) => (opts?.method ?? 'GET') === 'GET')
    expect(getCallsAfterFailure.length).toBeGreaterThan(1) // initial load + reload after failure
  })

  it('falls back to the generic message for an unknown error code', async () => {
    const fetchMock = vi.fn((url, options = {}) => {
      const method = options.method ?? 'GET'
      if (method === 'DELETE') {
        return Promise.resolve({
          status: 500,
          ok: false,
          json: () => Promise.resolve({ reason: 'SOMETHING_UNEXPECTED', message: 'boom' }),
        })
      }
      return Promise.resolve({ status: 200, ok: true, json: () => Promise.resolve(entries) })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(RoutineTemplateView)
    await flushPromises()

    await wrapper.find('.routine-entry-card__actions button:last-child').trigger('click')
    await flushPromises()

    expect(wrapper.find('.routine-template-view__error').text()).toBe('Something went wrong. Please try again.')
  })
})
