<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient, ApiError } from '../api/client'
import { resolveErrorMessage, GENERIC_ERROR_MESSAGE } from '../api/errorMessages'
import { useDaySchedule } from '../composables/useDaySchedule'
import { useBlockDraft } from '../composables/useBlockDraft'
import { shiftIsoDate } from '../date-utils'
import DayGrid from '../components/DayGrid.vue'
import BlockPopup from '../components/BlockPopup.vue'

const props = defineProps({
  date: { type: String, required: true },
})

const router = useRouter()
const dateRef = computed(() => props.date)
const { day, loading, error, load, createBlock, editBlock, deleteBlock } = useDaySchedule(dateRef)

// Bounds day-to-day navigation to the reachable range (FR-023).
const horizon = ref(null)
async function loadHorizon() {
  horizon.value = await apiClient.get('/horizon')
}
loadHorizon()
watch(() => props.date, loadHorizon)

// Backlog activities available to plan into this day, each carrying its own
// remaining time / status for exactly this day (FR-010-FR-011) — every
// activity stays listed, fully-planned ones just get a visual marker rather
// than being removed from the selector.
const dayActivities = ref([])
async function loadDayActivities() {
  dayActivities.value = await apiClient.get(`/activities?day=${props.date}`)
}
loadDayActivities()
watch(() => props.date, loadDayActivities)

const previousDate = computed(() => shiftIsoDate(props.date, -1))
const nextDate = computed(() => shiftIsoDate(props.date, 1))
const canGoPrevious = computed(() => !horizon.value?.day1 || previousDate.value >= horizon.value.day1)
const canGoNext = computed(() => !horizon.value?.forwardBound || nextDate.value <= horizon.value.forwardBound)

function goToDate(date) {
  router.push({ name: 'day', params: { date } })
}

// Left/Right arrow keys navigate days (bounded by the horizon, same as the
// buttons), as long as focus isn't inside a form control that itself uses
// arrow keys (text/time inputs, selects) — otherwise this would hijack
// normal editing of the add/edit-block form below.
function handleKeydown(event) {
  const target = event.target
  const isFormControl = target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)
  if (isFormControl) return

  if (event.key === 'ArrowLeft' && canGoPrevious.value) {
    event.preventDefault()
    goToDate(previousDate.value)
  } else if (event.key === 'ArrowRight' && canGoNext.value) {
    event.preventDefault()
    goToDate(nextDate.value)
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))

// Popup (creation from User Story 2, details/edit/delete from User Story 3):
// PopupState (data-model.md) drives BlockPopup as a single reactive value, so
// only one popup is ever open (FR-020). A same-day creation draft
// (data-model.md BlockDraft) survives a backdrop-click dismissal so a slot
// mis-click doesn't lose what was already filled in.
const popupState = ref(null)
const { draft, captureDraft, clearDraft } = useBlockDraft(dateRef)
const popupError = ref(null)

function openCreatePopup({ startTime }) {
  popupError.value = null
  popupState.value = { mode: 'create', startTime }
}

// A block is one of several same-day fragments of the same activity when
// more than one block on this day shares its activityId (FR-013); used both
// to open the details popup with the right sameDayFragmentCount and, before
// that, by DayGrid indirectly through the block data itself.
function sameActivityFragmentCount(block) {
  if (block.type !== 'PLANNED_ACTIVITY') return 1
  return (day.value?.blocks ?? []).filter((b) => b.activityId === block.activityId).length
}

function openDetailsPopup(block) {
  popupError.value = null
  popupState.value = {
    mode: 'details',
    block,
    readOnly: block.startsPreviousDay,
    sameDayFragmentCount: sameActivityFragmentCount(block),
  }
}

async function handleSubmitCreate(payload) {
  popupError.value = null
  try {
    await createBlock(payload)
    await loadDayActivities()
    popupState.value = null
    clearDraft()
  } catch (err) {
    popupError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
  }
}

async function handleSubmitEdit(payload) {
  popupError.value = null
  try {
    await editBlock(payload.id, { startTime: payload.startTime, endTime: payload.endTime, name: payload.name })
    await loadDayActivities()
    popupState.value = null
  } catch (err) {
    popupError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
  }
}

async function handleSubmitDelete({ id, scope }) {
  popupError.value = null
  try {
    await deleteBlock(id, scope)
    await loadDayActivities()
    popupState.value = null
  } catch (err) {
    // Mirrors the edit/create failure pattern: show the mapped error and keep
    // the popup open (FR-016) rather than closing it mid-error, which would
    // hide the very message just shown. The grid behind it still refreshes
    // to the real state (e.g. the block is already gone if this was a
    // stale-state 404), even while the popup keeps showing its last-known info.
    popupError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
    await load()
    await loadDayActivities()
  }
}

function handlePopupClosed({ reason, snapshot }) {
  if (reason === 'backdrop' && snapshot) {
    captureDraft(snapshot)
  } else {
    clearDraft()
  }
  popupState.value = null
  if (reason === 'navigate-to-start-day') {
    goToDate(shiftIsoDate(props.date, -1))
  }
}
</script>

<template>
  <section class="day-view">
    <header class="day-view__nav" role="navigation" aria-label="Day navigation">
      <button
        type="button"
        :disabled="!canGoPrevious"
        :aria-label="`Go to previous day, ${previousDate}`"
        title="Previous day (Left arrow)"
        @click="goToDate(previousDate)"
      >
        ← Previous day
      </button>
      <h1 aria-live="polite">{{ date }}</h1>
      <button
        type="button"
        :disabled="!canGoNext"
        :aria-label="`Go to next day, ${nextDate}`"
        title="Next day (Right arrow)"
        @click="goToDate(nextDate)"
      >
        Next day →
      </button>
    </header>

    <p v-if="loading">Loading…</p>
    <p v-else-if="error">Could not load this day.</p>
    <DayGrid
      v-else
      :date="date"
      :blocks="day?.blocks ?? []"
      @activate-slot="openCreatePopup"
      @activate-block="openDetailsPopup"
    />

    <BlockPopup
      :popup-state="popupState"
      :day-activities="dayActivities"
      :draft="draft"
      :error-message="popupError"
      :date="date"
      @submit-create="handleSubmitCreate"
      @submit-edit="handleSubmitEdit"
      @submit-delete="handleSubmitDelete"
      @closed="handlePopupClosed"
    />
  </section>
</template>

<style scoped>
.day-view__nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

</style>
