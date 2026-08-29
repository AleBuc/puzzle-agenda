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

// Kept for handleDelete/confirmFragmentDelete below (User Story 3 replaces
// both with BlockPopup's own errorMessage prop and removes this).
const formError = ref(null)

// Creation popup (User Story 2): PopupState (data-model.md) drives BlockPopup;
// a same-day draft (data-model.md BlockDraft) survives a backdrop-click
// dismissal so a slot mis-click doesn't lose what was already filled in.
const popupState = ref(null)
const { draft, captureDraft, clearDraft } = useBlockDraft(dateRef)
const popupError = ref(null)

function openCreatePopup({ startTime }) {
  popupError.value = null
  popupState.value = { mode: 'create', startTime }
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

function handlePopupClosed({ reason, snapshot }) {
  if (reason === 'backdrop' && snapshot) {
    captureDraft(snapshot)
  } else {
    clearDraft()
  }
  popupState.value = null
}

// Deleting a fragment (US4): if it's the only fragment of its activity on
// this day, delete immediately; otherwise offer a choice between this
// fragment only and every fragment of that activity today (FR-014-FR-015).
const pendingFragmentDelete = ref(null)

function sameActivityFragmentCount(block) {
  if (block.type !== 'PLANNED_ACTIVITY') return 1
  return (day.value?.blocks ?? []).filter((b) => b.activityId === block.activityId).length
}

async function handleDelete(block) {
  if (sameActivityFragmentCount(block) > 1) {
    pendingFragmentDelete.value = block
    return
  }
  formError.value = null
  try {
    await deleteBlock(block.id)
    await loadDayActivities()
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
    await load()
    await loadDayActivities()
  }
}

async function confirmFragmentDelete(scope) {
  if (!pendingFragmentDelete.value) return
  formError.value = null
  try {
    await deleteBlock(pendingFragmentDelete.value.id, scope)
    pendingFragmentDelete.value = null
    await loadDayActivities()
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
    pendingFragmentDelete.value = null
    await load()
    await loadDayActivities()
  }
}

function cancelFragmentDelete() {
  pendingFragmentDelete.value = null
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
    <DayGrid v-else :date="date" :blocks="day?.blocks ?? []" @activate-slot="openCreatePopup" />

    <div v-if="pendingFragmentDelete" class="day-view__confirm">
      <p>
        "{{ pendingFragmentDelete.activityName }}" has more than one fragment today. Delete just
        this one, or every fragment of this activity today?
      </p>
      <button type="button" @click="confirmFragmentDelete('self')">Delete this fragment only</button>
      <button type="button" @click="confirmFragmentDelete('activityDay')">
        Delete all fragments of this activity today
      </button>
      <button type="button" @click="cancelFragmentDelete">Cancel</button>
    </div>

    <p v-if="formError" class="day-view__error">{{ formError }}</p>

    <BlockPopup
      :popup-state="popupState"
      :day-activities="dayActivities"
      :draft="draft"
      :error-message="popupError"
      @submit-create="handleSubmitCreate"
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

.day-view__error {
  color: #c33;
}

.day-view__confirm {
  margin-top: 1rem;
  padding: 0.75rem;
  border: 1px solid #d1555c;
  border-radius: 0.25rem;
  background: #fdecec;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}
</style>
