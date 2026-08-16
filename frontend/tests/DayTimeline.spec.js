import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DayTimeline from '../src/components/DayTimeline.vue'

const blocks = [
  { id: '2', type: 'CONSTRAINED', startTime: '10:30', endTime: '11:00', endsNextDay: false, name: 'Adjacent', activityId: null },
  { id: '1', type: 'ROUTINE', startTime: '09:00', endTime: '10:30', endsNextDay: false, name: 'Standup', activityId: null },
]

describe('DayTimeline', () => {
  it('renders blocks in chronological order regardless of input order', () => {
    const wrapper = mount(DayTimeline, { props: { blocks } })

    const names = wrapper.findAll('.time-block-card__name').map((n) => n.text())
    expect(names).toEqual(['Standup', 'Adjacent'])
  })

  it('shows visible gaps before, between, and after blocks (none between adjacent blocks)', () => {
    const wrapper = mount(DayTimeline, { props: { blocks } })

    const gaps = wrapper.findAll('.day-timeline__gap').map((g) => g.text())
    expect(gaps).toEqual(['Free 00:00–09:00', 'Free 11:00–24:00'])
  })

  it('applies a distinct class per block type', () => {
    const wrapper = mount(DayTimeline, { props: { blocks } })

    expect(wrapper.find('.time-block-card--routine').exists()).toBe(true)
    expect(wrapper.find('.time-block-card--constrained').exists()).toBe(true)
  })

  it('treats a midnight-spanning block as occupying the rest of the day (no trailing gap)', () => {
    const wrapper = mount(DayTimeline, {
      props: {
        blocks: [
          { id: '1', type: 'ROUTINE', startTime: '23:00', endTime: '07:00', endsNextDay: true, name: 'Sleep', activityId: null },
        ],
      },
    })

    const gaps = wrapper.findAll('.day-timeline__gap').map((g) => g.text())
    expect(gaps).toEqual(['Free 00:00–23:00'])
  })

  it('treats a spillover block (startsPreviousDay) as occupying from midnight (no leading gap)', () => {
    const wrapper = mount(DayTimeline, {
      props: {
        blocks: [
          {
            id: '1',
            type: 'ROUTINE',
            startTime: '23:00',
            endTime: '07:00',
            endsNextDay: false,
            startsPreviousDay: true,
            name: 'Sleep',
            activityId: null,
          },
        ],
      },
    })

    const gaps = wrapper.findAll('.day-timeline__gap').map((g) => g.text())
    expect(gaps).toEqual(['Free 07:00–24:00'])
  })

  it('hides edit/delete actions for a spillover block', () => {
    const wrapper = mount(DayTimeline, {
      props: {
        blocks: [
          {
            id: '1',
            type: 'ROUTINE',
            startTime: '23:00',
            endTime: '07:00',
            endsNextDay: false,
            startsPreviousDay: true,
            name: 'Sleep',
            activityId: null,
          },
        ],
      },
    })

    expect(wrapper.find('.time-block-card__actions').exists()).toBe(false)
  })

  it('emits edit and delete events from the underlying block card', async () => {
    const wrapper = mount(DayTimeline, { props: { blocks } })

    const buttons = wrapper.findAll('.time-block-card__actions button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('edit')).toBeTruthy()
    expect(wrapper.emitted('delete')).toBeTruthy()
  })
})
