import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import DayGrid from '../src/components/DayGrid.vue'

const TODAY = '2026-08-29'

const dayGridSource = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../src/components/DayGrid.vue'),
  'utf-8',
)

// jsdom does not apply component <style> blocks (no layout/CSSOM engine), so
// a real getComputedStyle assertion can't see the height/max-height conflict
// that caused the original bug. Reading the rule bodies out of the source
// directly is what actually catches a regression here: it fails the moment
// the two properties end up back on the same selector, which is exactly what
// happened before this fix (`.day-grid` carried both `height: 1440px` and
// `max-height: 70vh` — max-height always wins that conflict, so the box, and
// every block positioned as a percentage of it, was compressed to ~484px).
function cssRuleBody(selector) {
  const escaped = selector.replace(/[.]/g, '\\.')
  const match = dayGridSource.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`))
  return match ? match[1] : null
}

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
      vi.setSystemTime(new Date('2026-08-29T12:00:00'))
      const wrapper = mount(DayGrid, { props: { date: '2026-08-30', blocks: [] }, attachTo: document.body })
      const el = wrapper.element
      Object.defineProperty(el, 'scrollHeight', { value: 2400, configurable: true })
      Object.defineProperty(el, 'clientHeight', { value: 200, configurable: true })
      await wrapper.vm.$nextTick()
      expect(el.scrollTop).toBe(0)
      wrapper.unmount()
    })
  })

  describe('keyboard operation (US4)', () => {
    it('is itself a keyboard-focusable element, alongside existing blocks which are already tabbable', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [block()] } })
      expect(wrapper.attributes('tabindex')).toBe('0')
      expect(wrapper.findComponent({ name: 'GridBlock' }).attributes('tabindex')).toBe('0')
    })

    it('moves the keyboard cursor in 5-minute increments with ArrowDown/ArrowUp', async () => {
      vi.setSystemTime(new Date('2026-08-29T00:00:00'))
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })

      await wrapper.trigger('keydown', { key: 'ArrowDown' })
      await wrapper.trigger('keydown', { key: 'Enter' })
      expect(wrapper.emitted('activate-slot').at(-1)).toEqual([{ startTime: '00:05' }])

      await wrapper.trigger('keydown', { key: 'ArrowUp' })
      await wrapper.trigger('keydown', { key: 'Enter' })
      expect(wrapper.emitted('activate-slot').at(-1)).toEqual([{ startTime: '00:00' }])
    })

    it('does not move the cursor before 00:00 or past 23:55', async () => {
      vi.setSystemTime(new Date('2026-08-29T00:00:00'))
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })

      await wrapper.trigger('keydown', { key: 'ArrowUp' })
      await wrapper.trigger('keydown', { key: 'Enter' })
      expect(wrapper.emitted('activate-slot').at(-1)).toEqual([{ startTime: '00:00' }])
    })

    it('activates the focused slot on Space as well as Enter', async () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
      await wrapper.trigger('keydown', { key: ' ' })
      expect(wrapper.emitted('activate-slot')).toBeTruthy()
    })

    it('provides a persistent "Add block" control, reachable independently of the roving cursor', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
      const addBlock = wrapper.find('.day-grid__add-block')
      expect(addBlock.exists()).toBe(true)
      expect(addBlock.element.tagName).toBe('BUTTON')
    })

    it('the "Add block" control defaults to the current time (snapped to 15 min) on today\'s view', async () => {
      vi.setSystemTime(new Date('2026-08-29T09:22:00'))
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
      await wrapper.find('.day-grid__add-block').trigger('click')
      expect(wrapper.emitted('activate-slot')).toEqual([[{ startTime: '09:15' }]])
    })

    it('the "Add block" control defaults to the start of the day on a day that is not today', async () => {
      vi.setSystemTime(new Date('2026-08-29T09:22:00'))
      const wrapper = mount(DayGrid, { props: { date: '2026-08-30', blocks: [] } })
      await wrapper.find('.day-grid__add-block').trigger('click')
      expect(wrapper.emitted('activate-slot')).toEqual([[{ startTime: '00:00' }]])
    })
  })

  describe('scroll viewport vs. coordinate space (regression: height/max-height conflict)', () => {
    it('gives the day-grid__content element the fixed 1440px coordinate-space height', () => {
      expect(cssRuleBody('.day-grid__content')).toMatch(/height:\s*1440px/)
    })

    it('does not also cap that element with max-height (max-height always wins that conflict)', () => {
      expect(cssRuleBody('.day-grid__content')).not.toMatch(/max-height/)
    })

    it('keeps the fixed-height max-height off the scrolling viewport element', () => {
      expect(cssRuleBody('.day-grid')).not.toMatch(/height:\s*1440px/)
    })

    it('renders the scroll viewport and the coordinate space as two distinct, nested elements', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] } })
      const scrollEl = wrapper.find('.day-grid').element
      const contentEl = wrapper.find('.day-grid__content').element
      expect(contentEl).not.toBe(scrollEl)
      expect(scrollEl.contains(contentEl)).toBe(true)
    })
  })

  describe('click-to-create time computation (regression: scroll-position independence)', () => {
    // The click listener lives on `.day-grid__content`, not `.day-grid` (the
    // scrolling viewport around it) — see DayGrid.vue for the full reasoning.
    // `offsetY` is relative to the padding edge of whatever element the
    // listener is attached to, so attaching it to the fixed, non-scrolling
    // content element means the computed time never depends on how far the
    // viewport has been scrolled. The old code attached this same handler to
    // the scrolling element itself, so its rect height only reflected the
    // visible ~70vh window and every click after scrolling landed on the
    // wrong time. These tests fake `getBoundingClientRect` (jsdom has no
    // layout engine) and dispatch directly on `.day-grid__content`.
    function clickAt(contentEl, offsetY) {
      contentEl.getBoundingClientRect = () => ({ top: 0, left: 0, right: 100, bottom: 1440, width: 100, height: 1440 })
      const event = new MouseEvent('click', { bubbles: true, cancelable: true })
      Object.defineProperty(event, 'offsetY', { value: offsetY, configurable: true })
      contentEl.dispatchEvent(event)
    }

    it('computes the clicked time from offsetY on the content element, snapped down to the quarter hour', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      clickAt(wrapper.find('.day-grid__content').element, 320) // 05:20 -> floors to 05:15
      expect(wrapper.emitted('activate-slot')).toEqual([[{ startTime: '05:15' }]])
      wrapper.unmount()
    })

    it('produces the same time regardless of the scroll viewport\'s scrollTop', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      wrapper.find('.day-grid').element.scrollTop = 900
      clickAt(wrapper.find('.day-grid__content').element, 320)
      expect(wrapper.emitted('activate-slot')).toEqual([[{ startTime: '05:15' }]])
      wrapper.unmount()
    })

    it('does not react to a click dispatched on the scroll viewport itself', () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      clickAt(wrapper.find('.day-grid').element, 320)
      expect(wrapper.emitted('activate-slot')).toBeUndefined()
      wrapper.unmount()
    })

    it('parks the keyboard cursor on the clicked slot (regression: it used to stay on the current-time default)', async () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      clickAt(wrapper.find('.day-grid__content').element, 320) // -> 05:15
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.day-grid').attributes('aria-label')).toContain('05:15')
      wrapper.unmount()
    })
  })

  describe('scroll-to-cursor on focus (regression: pointer-click focus corrupting the click)', () => {
    // A re-test found that clicking `.day-grid__content` (not itself
    // focusable) moves native DOM focus to the nearest focusable ancestor,
    // `.day-grid` — and this happens on mousedown, BEFORE the click event
    // fires. The previous version scrolled the keyboard cursor into view on
    // every focus, including this one: with the grid scrolled to the end of
    // the day and the cursor parked on the current-time default, that scroll
    // raced ahead of the click and moved the content out from under the
    // pointer, so the click's own offsetY read the wrong (already-scrolled)
    // position — the popup opened pre-filled with roughly the current time
    // instead of the time actually clicked. `:focus-visible` is the
    // browser's own signal for "this focus came from the keyboard" (or
    // another explicit, non-pointer action), which is exactly the
    // distinction needed: scrolling to the keyboard cursor is a keyboard
    // behavior and must never be a side effect of a pointer-triggered focus.
    // jsdom has neither a real `:focus-visible` computation nor a
    // `scrollIntoView` implementation, so both are stubbed here.
    let originalScrollIntoView

    beforeEach(() => {
      originalScrollIntoView = Element.prototype.scrollIntoView
      Element.prototype.scrollIntoView = vi.fn()
    })

    afterEach(() => {
      Element.prototype.scrollIntoView = originalScrollIntoView
    })

    function stubFocusVisible(el, value) {
      el.matches = (selector) => (selector === ':focus-visible' ? value : false)
    }

    it('scrolls the cursor into view on a keyboard-driven focus (:focus-visible true)', async () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      stubFocusVisible(wrapper.element, true)
      await wrapper.trigger('focus')
      expect(Element.prototype.scrollIntoView).toHaveBeenCalled()
      wrapper.unmount()
    })

    it('does not scroll on a pointer-triggered focus (:focus-visible false)', async () => {
      const wrapper = mount(DayGrid, { props: { date: TODAY, blocks: [] }, attachTo: document.body })
      stubFocusVisible(wrapper.element, false)
      await wrapper.trigger('focus')
      expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled()
      wrapper.unmount()
    })
  })
})
