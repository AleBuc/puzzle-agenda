import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { DOMWrapper, mount } from '@vue/test-utils'
import { DialogContent } from 'reka-ui'
import BlockPopup from '../src/components/BlockPopup.vue'

const dayActivities = [
  { id: 'a1', name: 'Write report', remainingMinutesForDay: 180, dayStatus: 'PARTIALLY_PLANNED' },
  { id: 'a2', name: 'Course a pied', remainingMinutesForDay: 0, dayStatus: 'PLANNED' },
]

// DialogPortal teleports its content to document.body, outside the mounted
// wrapper's own root element — DOM queries for it must go through the body,
// not `wrapper.find`.
function body() {
  return new DOMWrapper(document.body)
}

// DialogPortal only teleports its content once its own onMounted fires
// (it uses vueuse's useMounted() internally), which needs an extra tick
// after mount() before the teleported DOM actually appears in document.body.
async function mountPopup(props = {}) {
  const wrapper = mount(BlockPopup, {
    attachTo: document.body,
    props: {
      popupState: { mode: 'create', startTime: '09:15' },
      dayActivities,
      draft: null,
      errorMessage: null,
      ...props,
    },
  })
  await nextTick()
  return wrapper
}

describe('BlockPopup (creation mode)', () => {
  afterEach(() => {
    // DialogPortal teleports into document.body; VTU's unmount does not
    // reliably clear that teleported subtree, so without this, a stale form
    // from a previous test can still be the first DOM match in the next one.
    document.body.innerHTML = ''
  })

  it('is closed when popupState is null', async () => {
    const wrapper = await mountPopup({ popupState: null })
    expect(body().find('.block-popup__content').exists()).toBe(false)
    wrapper.unmount()
  })

  it('opens with the given start time pre-filled and a default 1-hour end time', async () => {
    const wrapper = await mountPopup()
    const [startInput, endInput] = body().findAll('input[type="time"]')
    expect(startInput.element.value).toBe('09:15')
    expect(endInput.element.value).toBe('10:15')
    wrapper.unmount()
  })

  it('restricts start/end adjustment to 5-minute increments via the native time input step', async () => {
    const wrapper = await mountPopup()
    const [startInput, endInput] = body().findAll('input[type="time"]')
    expect(startInput.attributes('step')).toBe('300')
    expect(endInput.attributes('step')).toBe('300')
    wrapper.unmount()
  })

  it('shows the activity selector with remaining time only for the planned-activity type', async () => {
    const wrapper = await mountPopup()
    expect(body().find('select[name="activity"]').exists()).toBe(false)

    await body().find('select[name="type"]').setValue('PLANNED_ACTIVITY')

    const activitySelect = body().find('select[name="activity"]')
    expect(activitySelect.exists()).toBe(true)
    const options = activitySelect.findAll('option').map((o) => o.text())
    expect(options.some((o) => o.includes('Write report') && o.includes('180min left'))).toBe(true)
    expect(options.some((o) => o.includes('Course a pied') && o.includes('fully planned'))).toBe(true)
    wrapper.unmount()
  })

  it('emits submit-create with the assembled payload on confirm', async () => {
    const wrapper = await mountPopup()
    await body().find('input[type="time"]').setValue('09:15')
    await body().find('form').trigger('submit')

    expect(wrapper.emitted('submit-create')).toBeTruthy()
    const [payload] = wrapper.emitted('submit-create')[0]
    expect(payload).toMatchObject({ type: 'CONSTRAINED', startTime: '09:15', endTime: '10:15' })
    wrapper.unmount()
  })

  it('shows a mapped error message inside the popup without closing it', async () => {
    const wrapper = await mountPopup({ errorMessage: 'This time slot overlaps an existing block.' })
    expect(body().find('.block-popup__error').text()).toBe('This time slot overlaps an existing block.')
    expect(body().find('.block-popup__content').exists()).toBe(true)
    wrapper.unmount()
  })

  it('emits closed with reason "close-button" and no snapshot when the close control is used', async () => {
    const wrapper = await mountPopup()
    await body().find('.block-popup__cancel').trigger('click')

    expect(wrapper.emitted('closed')).toBeTruthy()
    const [payload] = wrapper.emitted('closed')[0]
    expect(payload).toEqual({ reason: 'close-button' })
    wrapper.unmount()
  })

  it('emits closed with reason "escape" and no snapshot on Escape', async () => {
    const wrapper = await mountPopup()
    wrapper.findComponent(DialogContent).vm.$emit('escapeKeyDown', new KeyboardEvent('keydown'))

    expect(wrapper.emitted('closed')).toBeTruthy()
    const [payload] = wrapper.emitted('closed')[0]
    expect(payload).toEqual({ reason: 'escape' })
    wrapper.unmount()
  })

  it('emits closed with reason "backdrop" and a snapshot of the current fields on an outside interaction, without submitting', async () => {
    const wrapper = await mountPopup()
    await body().find('input[name="name"]').setValue('Errand')

    wrapper.findComponent(DialogContent).vm.$emit('pointerDownOutside', new Event('pointerdown'))

    expect(wrapper.emitted('submit-create')).toBeFalsy()
    expect(wrapper.emitted('closed')).toBeTruthy()
    const [payload] = wrapper.emitted('closed')[0]
    expect(payload.reason).toBe('backdrop')
    expect(payload.snapshot).toMatchObject({ type: 'CONSTRAINED', name: 'Errand', activityId: null, durationMinutes: 60 })
    wrapper.unmount()
  })

  it('pre-fills type/name/activity/duration from a given draft, but takes startTime from popupState', async () => {
    const wrapper = await mountPopup({
      popupState: { mode: 'create', startTime: '14:00' },
      draft: { day: '2026-08-29', type: 'PLANNED_ACTIVITY', name: null, activityId: 'a1', durationMinutes: 30 },
    })

    const [startInput, endInput] = body().findAll('input[type="time"]')
    expect(startInput.element.value).toBe('14:00')
    expect(endInput.element.value).toBe('14:30')
    expect(body().find('select[name="type"]').element.value).toBe('PLANNED_ACTIVITY')
    expect(body().find('select[name="activity"]').element.value).toBe('a1')
    wrapper.unmount()
  })
})
