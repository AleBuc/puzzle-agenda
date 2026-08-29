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
      date: '2026-08-29',
      ...props,
    },
  })
  await nextTick()
  return wrapper
}

function singleFragmentBlock() {
  return {
    id: 'b1',
    type: 'CONSTRAINED',
    startTime: '09:00',
    endTime: '10:00',
    name: 'Standup',
    activityId: null,
    activityName: null,
    startsPreviousDay: false,
    endsNextDay: false,
  }
}

function plannedActivityBlock() {
  return {
    id: 'b2',
    type: 'PLANNED_ACTIVITY',
    startTime: '07:00',
    endTime: '07:20',
    name: null,
    activityId: 'a2',
    activityName: 'Course a pied',
    startsPreviousDay: false,
    endsNextDay: false,
  }
}

function spilloverBlock() {
  return {
    id: 'b3',
    type: 'ROUTINE',
    startTime: '22:30',
    endTime: '07:00',
    name: 'Sleep',
    activityId: null,
    activityName: null,
    startsPreviousDay: true,
    endsNextDay: false,
  }
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

describe('BlockPopup (details mode)', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows the block type, time range, and name with edit/delete actions', async () => {
    const block = singleFragmentBlock()
    const wrapper = await mountPopup({ popupState: { mode: 'details', block, readOnly: false, sameDayFragmentCount: 1 } })

    const content = body().find('.block-popup__content')
    expect(content.text()).toContain('09:00–10:00')
    expect(content.text()).toContain('Standup')
    expect(body().find('.block-popup__edit').exists()).toBe(true)
    expect(body().find('.block-popup__delete').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows the linked activity name for a planned-activity block', async () => {
    const wrapper = await mountPopup({
      popupState: { mode: 'details', block: plannedActivityBlock(), readOnly: false, sameDayFragmentCount: 1 },
    })
    expect(body().find('.block-popup__content').text()).toContain('Course a pied')
    wrapper.unmount()
  })

  it('emits submit-edit with the updated time range on a valid edit', async () => {
    const block = singleFragmentBlock()
    const wrapper = await mountPopup({ popupState: { mode: 'details', block, readOnly: false, sameDayFragmentCount: 1 } })

    await body().find('.block-popup__edit').trigger('click')
    await body().find('input[type="time"]').setValue('11:00')
    await body().find('form').trigger('submit')

    expect(wrapper.emitted('submit-edit')).toBeTruthy()
    const [payload] = wrapper.emitted('submit-edit')[0]
    expect(payload).toMatchObject({ id: 'b1', startTime: '11:00', endTime: '10:00' })
    wrapper.unmount()
  })

  it('emits submit-delete with scope "self" immediately for a block that is its activity\'s only fragment today', async () => {
    const block = singleFragmentBlock()
    const wrapper = await mountPopup({ popupState: { mode: 'details', block, readOnly: false, sameDayFragmentCount: 1 } })

    await body().find('.block-popup__delete').trigger('click')

    expect(body().find('.block-popup__delete-confirm').exists()).toBe(false)
    expect(wrapper.emitted('submit-delete')).toBeTruthy()
    const [payload] = wrapper.emitted('submit-delete')[0]
    expect(payload).toEqual({ id: 'b1', scope: 'self' })
    wrapper.unmount()
  })

  it('shows the in-place fragment-scope choice before deleting when the activity has more than one fragment today', async () => {
    const block = plannedActivityBlock()
    const wrapper = await mountPopup({ popupState: { mode: 'details', block, readOnly: false, sameDayFragmentCount: 2 } })

    await body().find('.block-popup__delete').trigger('click')

    expect(wrapper.emitted('submit-delete')).toBeFalsy()
    const confirm = body().find('.block-popup__delete-confirm')
    expect(confirm.exists()).toBe(true)

    await confirm.find('.block-popup__delete-all').trigger('click')

    expect(wrapper.emitted('submit-delete')).toBeTruthy()
    const [payload] = wrapper.emitted('submit-delete')[0]
    expect(payload).toEqual({ id: 'b2', scope: 'activityDay' })
    wrapper.unmount()
  })

  it('shows a mapped error without losing the current details/edit view', async () => {
    const block = singleFragmentBlock()
    const wrapper = await mountPopup({
      popupState: { mode: 'details', block, readOnly: false, sameDayFragmentCount: 1 },
      errorMessage: 'This block no longer exists. The view has been refreshed.',
    })

    expect(body().find('.block-popup__error').text()).toBe('This block no longer exists. The view has been refreshed.')
    expect(body().find('.block-popup__content').exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders a read-only notice with no edit/delete actions for a spillover (continuation-only) block', async () => {
    const wrapper = await mountPopup({
      popupState: { mode: 'details', block: spilloverBlock(), readOnly: true, sameDayFragmentCount: 1 },
      date: '2026-08-29',
    })

    const content = body().find('.block-popup__content')
    expect(content.text()).toContain('22:30')
    expect(content.text()).toContain('2026-08-28')
    expect(body().find('.block-popup__edit').exists()).toBe(false)
    expect(body().find('.block-popup__delete').exists()).toBe(false)
    expect(body().find('.block-popup__go-to-start-day').exists()).toBe(true)
    wrapper.unmount()
  })

  it('emits closed with reason "navigate-to-start-day" when the read-only link is activated', async () => {
    const wrapper = await mountPopup({
      popupState: { mode: 'details', block: spilloverBlock(), readOnly: true, sameDayFragmentCount: 1 },
    })

    await body().find('.block-popup__go-to-start-day').trigger('click')

    expect(wrapper.emitted('closed')).toBeTruthy()
    const [payload] = wrapper.emitted('closed')[0]
    expect(payload).toEqual({ reason: 'navigate-to-start-day' })
    wrapper.unmount()
  })
})

// These exercise reka-ui's real Dialog behavior (no simulated .vm.$emit
// shortcuts) to the extent it is observable under jsdom + Vue Test Utils —
// see research.md §7's caveat: full native Tab-key traversal and focus
// timing are ultimately a browser guarantee from reka-ui itself (research.md
// §1), not something this project re-implements.
describe('BlockPopup (accessibility)', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('moves focus inside the dialog content when it opens', async () => {
    const wrapper = await mountPopup()
    await nextTick()

    expect(body().find('.block-popup__content').element.contains(document.activeElement)).toBe(true)
    wrapper.unmount()
  })

  it('keeps focus within the dialog content while Tab is pressed repeatedly', async () => {
    const wrapper = await mountPopup()
    await nextTick()

    const content = body().find('.block-popup__content').element
    for (let i = 0; i < 10; i += 1) {
      document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))
      await nextTick()
      expect(content.contains(document.activeElement)).toBe(true)
    }
    wrapper.unmount()
  })

  it('returns focus to the element that had it before the popup opened, once Escape closes it', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    expect(document.activeElement).toBe(trigger)

    // BlockPopup is fully controlled by `popupState` — reka-ui only restores
    // focus once that prop actually flips closed, so this simulates what
    // DayView.vue does in response to the `closed` emit: set popupState back
    // to null.
    const wrapper = await mountPopup({ popupState: null })
    await wrapper.setProps({ popupState: { mode: 'create', startTime: '09:15' } })
    await nextTick()
    expect(document.activeElement).not.toBe(trigger)

    document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    await wrapper.setProps({ popupState: null })
    await nextTick()

    expect(document.activeElement).toBe(trigger)
    wrapper.unmount()
    trigger.remove()
  })
})
