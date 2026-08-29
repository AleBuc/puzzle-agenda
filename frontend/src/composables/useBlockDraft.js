import { ref, watch } from 'vue'

// Single per-viewed-day creation-popup draft (data-model.md BlockDraft), a
// plain ref — not a store (Constitution Principle V). Captured when the
// creation popup is dismissed via backdrop click (FR-024); cleared on
// Escape, the popup's own close control, a successful create, or a day
// change.
export function useBlockDraft(dateRef) {
  const draft = ref(null)

  function captureDraft(fields) {
    draft.value = { day: dateRef.value, ...fields }
  }

  function clearDraft() {
    draft.value = null
  }

  watch(dateRef, clearDraft)

  return { draft, captureDraft, clearDraft }
}
