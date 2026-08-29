<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient, ApiError } from '../api/client'
import { resolveErrorMessage, GENERIC_ERROR_MESSAGE } from '../api/errorMessages'
import { useDaySchedule } from '../composables/useDaySchedule'
import { shiftIsoDate } from '../date-utils'
import DayGrid from '../components/DayGrid.vue'

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

function activityOptionLabel(activity) {
  const remaining = `${activity.remainingMinutesForDay}min left`
  return activity.dayStatus === 'PLANNED' ? `${activity.name} (fully planned, ${remaining})` : `${activity.name} (${remaining})`
}

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

const emptyForm = () => ({ type: 'CONSTRAINED', startTime: '', endTime: '', name: '', activityId: '' })
const form = ref(emptyForm())
const formError = ref(null)
const editingBlockId = ref(null)

async function submitForm() {
  formError.value = null
  try {
    if (editingBlockId.value) {
      await editBlock(editingBlockId.value, {
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        name: form.value.name || null,
      })
      await loadDayActivities()
    } else if (form.value.type === 'PLANNED_ACTIVITY') {
      await createBlock({
        type: 'PLANNED_ACTIVITY',
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        activityId: form.value.activityId,
      })
      await loadDayActivities()
    } else {
      await createBlock({
        type: form.value.type,
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        name: form.value.name || null,
      })
    }
    editingBlockId.value = null
    form.value = emptyForm()
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
  }
}

function startEdit(block) {
  editingBlockId.value = block.id
  formError.value = null
  form.value = { type: block.type, startTime: block.startTime, endTime: block.endTime, name: block.name || '' }
}

function cancelEdit() {
  editingBlockId.value = null
  formError.value = null
  form.value = emptyForm()
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
    <DayGrid v-else :date="date" :blocks="day?.blocks ?? []" />

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

    <form class="day-view__form" @submit.prevent="submitForm">
      <h2>{{ editingBlockId ? 'Edit block' : 'Add a block' }}</h2>
      <label>
        Type
        <select
          v-model="form.type"
          :disabled="!!editingBlockId"
          :title="editingBlockId ? 'Type cannot be changed after creation' : undefined"
        >
          <option value="ROUTINE">Routine</option>
          <option value="CONSTRAINED">Constrained</option>
          <option value="PLANNED_ACTIVITY">Planned activity</option>
        </select>
      </label>
      <label>
        Start
        <input v-model="form.startTime" type="time" step="300" required />
      </label>
      <label>
        End
        <input v-model="form.endTime" type="time" step="300" required />
      </label>
      <label v-if="form.type === 'PLANNED_ACTIVITY' && !editingBlockId">
        Activity
        <select v-model="form.activityId" required>
          <option value="" disabled>Select a backlog activity…</option>
          <option v-for="activity in dayActivities" :key="activity.id" :value="activity.id">
            {{ activityOptionLabel(activity) }}
          </option>
        </select>
      </label>
      <label v-else>
        Name
        <input v-model="form.name" type="text" />
      </label>
      <div class="day-view__form-actions">
        <button type="submit">{{ editingBlockId ? 'Save' : 'Add block' }}</button>
        <button v-if="editingBlockId" type="button" @click="cancelEdit">Cancel</button>
      </div>
      <p v-if="formError" class="day-view__error">{{ formError }}</p>
    </form>
  </section>
</template>

<style scoped>
.day-view__nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.day-view__form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.5rem;
  max-width: 20rem;
}

.day-view__form-actions {
  display: flex;
  gap: 0.5rem;
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
