<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient, ApiError } from '../api/client'
import { useDaySchedule } from '../composables/useDaySchedule'
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

function shiftDate(days) {
  const d = new Date(`${props.date}T00:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

const canGoPrevious = computed(() => !horizon.value?.day1 || shiftDate(-1) >= horizon.value.day1)
const canGoNext = computed(() => !horizon.value?.forwardBound || shiftDate(1) <= horizon.value.forwardBound)

function goToDate(date) {
  router.push({ name: 'day', params: { date } })
}

const emptyForm = () => ({ type: 'CONSTRAINED', startTime: '', endTime: '', name: '' })
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
    <header class="day-view__nav">
      <button type="button" :disabled="!canGoPrevious" @click="goToDate(shiftDate(-1))">← Previous day</button>
      <h1>{{ date }}</h1>
      <button type="button" :disabled="!canGoNext" @click="goToDate(shiftDate(1))">Next day →</button>
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
      <label>
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
