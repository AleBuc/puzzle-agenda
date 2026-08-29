import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { useBlockDraft } from '../src/composables/useBlockDraft'

describe('useBlockDraft', () => {
  it('starts with no draft', () => {
    const { draft } = useBlockDraft(ref('2026-08-29'))
    expect(draft.value).toBeNull()
  })

  it('captureDraft stores the given fields scoped to the current day', () => {
    const dateRef = ref('2026-08-29')
    const { draft, captureDraft } = useBlockDraft(dateRef)

    captureDraft({ type: 'PLANNED_ACTIVITY', name: null, activityId: 'a1', durationMinutes: 60 })

    expect(draft.value).toEqual({
      day: '2026-08-29',
      type: 'PLANNED_ACTIVITY',
      name: null,
      activityId: 'a1',
      durationMinutes: 60,
    })
  })

  it('clearDraft resets the draft to null', () => {
    const dateRef = ref('2026-08-29')
    const { draft, captureDraft, clearDraft } = useBlockDraft(dateRef)

    captureDraft({ type: 'CONSTRAINED', name: 'Errand', activityId: null, durationMinutes: 30 })
    expect(draft.value).not.toBeNull()

    clearDraft()
    expect(draft.value).toBeNull()
  })

  it('automatically clears the draft when the watched date changes', async () => {
    const dateRef = ref('2026-08-29')
    const { draft, captureDraft } = useBlockDraft(dateRef)

    captureDraft({ type: 'ROUTINE', name: 'Sleep', activityId: null, durationMinutes: 60 })
    expect(draft.value).not.toBeNull()

    dateRef.value = '2026-08-30'
    await nextTick()

    expect(draft.value).toBeNull()
  })
})
