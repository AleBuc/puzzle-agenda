import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GridBlock from '../src/components/GridBlock.vue'

function positioned(overrides = {}) {
  return {
    block: {
      id: 'b1',
      type: 'CONSTRAINED',
      startTime: '09:00',
      endTime: '10:00',
      name: 'Standup',
      activityId: null,
      activityName: null,
      startsPreviousDay: false,
      endsNextDay: false,
    },
    topPercent: 37.5,
    heightPercent: 4.16,
    isVeryShort: false,
    isContinuationOnly: false,
    ...overrides,
  }
}

describe('GridBlock', () => {
  it('applies a distinct class per block type', () => {
    const wrapper = mount(GridBlock, { props: { positioned: positioned() } })
    expect(wrapper.classes()).toContain('grid-block--constrained')
  })

  it('positions itself proportionally via inline style', () => {
    const wrapper = mount(GridBlock, { props: { positioned: positioned({ topPercent: 25, heightPercent: 10 }) } })
    expect(wrapper.attributes('style')).toContain('top: 25%')
    expect(wrapper.attributes('style')).toContain('height: 10%')
  })

  it('shows a continuation indicator only for a continuation-only (spillover) block', () => {
    const normal = mount(GridBlock, { props: { positioned: positioned() } })
    expect(normal.find('.grid-block__continuation').exists()).toBe(false)

    const spillover = mount(GridBlock, { props: { positioned: positioned({ isContinuationOnly: true }) } })
    expect(spillover.find('.grid-block__continuation').exists()).toBe(true)
  })

  it('shows a full time+name label for a normal block, with no title tooltip', () => {
    const wrapper = mount(GridBlock, { props: { positioned: positioned() } })
    expect(wrapper.text()).toContain('09:00–10:00')
    expect(wrapper.text()).toContain('Standup')
    expect(wrapper.attributes('title')).toBeUndefined()
  })

  it('shows a compact label and a full-detail tooltip for a very short block', () => {
    const wrapper = mount(GridBlock, {
      props: { positioned: positioned({ isVeryShort: true, heightPercent: 0.35 }) },
    })
    expect(wrapper.attributes('title')).toContain('09:00–10:00')
    expect(wrapper.attributes('title')).toContain('Standup')
  })

  it('falls back to the activity name, then the type, when no name is set', () => {
    const withActivity = mount(GridBlock, {
      props: { positioned: positioned({ block: { ...positioned().block, name: null, activityName: 'Write report' } }) },
    })
    expect(withActivity.text()).toContain('Write report')

    const withNeither = mount(GridBlock, {
      props: { positioned: positioned({ block: { ...positioned().block, name: null, activityName: null } }) },
    })
    expect(withNeither.text()).toContain('CONSTRAINED')
  })

  it('emits activate with the underlying block on click, including for a continuation-only block', () => {
    const p = positioned({ isContinuationOnly: true })
    const wrapper = mount(GridBlock, { props: { positioned: p } })
    wrapper.trigger('click')
    expect(wrapper.emitted('activate')).toEqual([[p.block]])
  })

  it('emits activate on Enter and Space keydown, for keyboard activation', async () => {
    const p = positioned()
    const wrapper = mount(GridBlock, { props: { positioned: p } })
    await wrapper.trigger('keydown.enter')
    await wrapper.trigger('keydown.space')
    expect(wrapper.emitted('activate')).toHaveLength(2)
  })
})
