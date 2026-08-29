import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DayGrid from '../src/components/DayGrid.vue'

const TODAY = '2026-08-29'

function block(overrides = {}) {
  return {
    id: 'b1',
    type: 'CONSTRAINED',
    startTime: '06:00',
    endTime: '18:00',
    name: 'Long block',
    activityId: null,
    activityName: null,
    startsPreviousDay: false,
    endsNextDay: false,
    ...overrides,
  }
}

describe('DayGrid', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('renders 24 hour gridlines with HH:00 labels', () => {
    const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
    const labels = wrapper.findAll('.day-grid__hour-label').map((l) => l.text())
    expect(labels).toHaveLength(24)
    expect(labels[0]).toBe('00:00')
    expect(labels[23]).toBe('23:00')
  })

  it('positions each block proportionally via GridBlock', () => {
    const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [block()] } })
    const gridBlock = wrapper.findComponent({ name: 'GridBlock' })
    expect(gridBlock.exists()).toBe(true)
    expect(gridBlock.props('positioned').topPercent).toBeCloseTo(25, 5)
    expect(gridBlock.props('positioned').heightPercent).toBeCloseTo(50, 5)
  })

  it('shows no "Free" text anywhere — free time is just unrendered space', () => {
    const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [block()] } })
    expect(wrapper.text()).not.toContain('Free')
  })

  it('shows the current-time indicator only when the viewed date is today', () => {
    vi.setSystemTime(new Date('2026-08-29T12:00:00'))

    const todayWrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
    expect(todayWrapper.find('.day-grid__now-line').exists()).toBe(true)

    const otherDayWrapper = mount(DayGrid, { props: { date: '2026-08-30', blocks: [] } })
    expect(otherDayWrapper.find('.day-grid__now-line').exists()).toBe(false)
  })

  it('positions the current-time indicator proportionally to the current time', () => {
    vi.setSystemTime(new Date('2026-08-29T12:00:00'))
    const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
    const nowLine = wrapper.find('.day-grid__now-line')
    expect(nowLine.attributes('style')).toContain('top: 50%')
  })

  it("relays a GridBlock's activate event as its own activate-block event", () => {
    const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [block()] } })
    wrapper.findComponent({ name: 'GridBlock' }).vm.$emit('activate', block())
    expect(wrapper.emitted('activate-block')).toEqual([[block()]])
  })

  describe('initial scroll position (FR-021)', () => {
    it('scrolls to the current time when viewing today', async () => {
      vi.setSystemTime(new Date('2026-08-29T12:00:00'))
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      const el = wrapper.element
      Object.defineProperty(el, 'scrollHeight', { value: 2400, configurable: true })
      Object.defineProperty(el, 'clientHeight', { value: 200, configurable: true })
      await wrapper.vm.$nextTick()
      // Scroll target should be roughly proportional to noon (50% of the day).
      expect(el.scrollTop).toBeGreaterThan(0)
      wrapper.unmount()
    })

    it('scrolls to the start of the day when viewing a day that is not today', async () => {
      const wrapper = mount(DayGrid, { props: { date: '2026-08-30', blocks: [] }, attachTo: document.body })
      const el = wrapper.element
      Object.defineProperty(el, 'scrollHeight', { value: 2400, configurable: true })
      Object.defineProperty(el, 'clientHeight', { value: 200, configurable: true })
      await wrapper.vm.$nextTick()
      expect(el.scrollTop).toBe(0)
      wrapper.unmount()
    })
  })
})
