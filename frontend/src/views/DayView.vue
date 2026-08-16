<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient, ApiError } from '../api/client'
import { useDaySchedule } from '../composables/useDaySchedule'
import { shiftIsoDate } from '../date-utils'
import DayTimeline from '../components/DayTimeline.vue'

const props = defineProps({
  date: { type: String, required: true },
})

const router = useRouter()
const dateRef = computed(() => props.date)
const { day, loading, error, createBlock, editBlock, deleteBlock } = useDaySchedule(dateRef)

// Bounds day-to-day navigation to the reachable range (FR-023).
const horizon = ref(null)
async function loadHorizon() {
  horizon.value = await apiClient.get('/horizon')
}
loadHorizon()
watch(() => props.date, loadHorizon)

// Backlog activities available to plan into a slot (US3).
const unplannedActivities = ref([])
async function loadUnplannedActivities() {
  unplannedActivities.value = await apiClient.get('/activities?status=unplanned')
}
loadUnplannedActivities()

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
    } else if (form.value.type === 'PLANNED_ACTIVITY') {
      await createBlock({
        type: 'PLANNED_ACTIVITY',
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        activityId: form.value.activityId,
      })
      await loadUnplannedActivities()
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
    formError.value = err instanceof ApiError ? (err.message || err.reason) : 'Something went wrong.'
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

async function handleDelete(block) {
  await deleteBlock(block.id)
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
    <DayTimeline v-else :blocks="day?.blocks ?? []" @edit="startEdit" @delete="handleDelete" />

    <form class="day-view__form" @submit.prevent="submitForm">
      <h2>{{ editingBlockId ? 'Edit block' : 'Add a block' }}</h2>
      <label>
        Type
        <select v-model="form.type" :disabled="!!editingBlockId">
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
          <option v-for="activity in unplannedActivities" :key="activity.id" :value="activity.id">
            {{ activity.name }}
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
</style>
