<script setup>
import { onMounted, ref } from 'vue'
import { useBacklog } from '../composables/useBacklog'
import ActivityCard from '../components/ActivityCard.vue'
import { ApiError } from '../api/client'
import { resolveErrorMessage, GENERIC_ERROR_MESSAGE } from '../api/errorMessages'

const { activities, loading, error, load, createActivity, editActivity, deleteActivity } = useBacklog()
onMounted(() => load())

const emptyForm = () => ({ name: '', estimatedDurationMinutes: 30, priority: 'MEDIUM', category: '' })
const form = ref(emptyForm())
const formError = ref(null)
const editingId = ref(null)

// Confirm-delete flow for an activity that has one or more planned fragments
// (FR-016): deleting it requires an explicit second step, since it also
// cascades to every one of its fragments, across every day.
const pendingDelete = ref(null)

async function submitForm() {
  formError.value = null
  try {
    const payload = {
      name: form.value.name,
      estimatedDurationMinutes: Number(form.value.estimatedDurationMinutes),
      priority: form.value.priority,
      category: form.value.category || null,
    }
    if (editingId.value) {
      await editActivity(editingId.value, payload)
    } else {
      await createActivity(payload)
    }
    editingId.value = null
    form.value = emptyForm()
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
  }
}

function startEdit(activity) {
  editingId.value = activity.id
  formError.value = null
  form.value = {
    name: activity.name,
    estimatedDurationMinutes: activity.estimatedDurationMinutes,
    priority: activity.priority,
    category: activity.category || '',
  }
}

function cancelEdit() {
  editingId.value = null
  formError.value = null
  form.value = emptyForm()
}

async function handleDelete(activity) {
  if (activity.totalFragmentCount > 0) {
    pendingDelete.value = activity
    return
  }
  formError.value = null
  try {
    await deleteActivity(activity.id)
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
    await load()
  }
}

async function confirmDelete() {
  if (!pendingDelete.value) return
  formError.value = null
  try {
    await deleteActivity(pendingDelete.value.id, true)
    pendingDelete.value = null
  } catch (err) {
    formError.value = err instanceof ApiError ? resolveErrorMessage(err.reason) : GENERIC_ERROR_MESSAGE
    pendingDelete.value = null
    await load()
  }
}

function cancelDelete() {
  pendingDelete.value = null
}
</script>

<template>
  <section class="backlog-view">
    <h1>Backlog</h1>

    <p v-if="loading">Loading…</p>
    <p v-else-if="error">Could not load the backlog.</p>
    <p v-else-if="activities.length === 0" class="backlog-view__empty">
      No activities yet. Create your first one below.
    </p>
    <ul v-else class="backlog-view__list">
      <li v-for="activity in activities" :key="activity.id">
        <ActivityCard :activity="activity" @edit="startEdit" @delete="handleDelete" />
      </li>
    </ul>

    <div v-if="pendingDelete" class="backlog-view__confirm">
      <p>
        "{{ pendingDelete.name }}" has {{ pendingDelete.totalFragmentCount }} planned fragment(s)
        across {{ pendingDelete.plannedDayCount }} day(s). Deleting it will remove all of them.
        Delete anyway?
      </p>
      <button type="button" @click="confirmDelete">Delete anyway</button>
      <button type="button" @click="cancelDelete">Cancel</button>
    </div>

    <form class="backlog-view__form" @submit.prevent="submitForm">
      <h2>{{ editingId ? 'Edit activity' : 'Add an activity' }}</h2>
      <label>
        Name
        <input v-model="form.name" type="text" required />
      </label>
      <label>
        Estimated duration (minutes)
        <input v-model="form.estimatedDurationMinutes" type="number" min="1" required />
      </label>
      <label>
        Priority
        <select v-model="form.priority">
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
        </select>
      </label>
      <label>
        Category
        <input v-model="form.category" type="text" placeholder="optional" />
      </label>
      <div class="backlog-view__form-actions">
        <button type="submit">{{ editingId ? 'Save' : 'Add activity' }}</button>
        <button v-if="editingId" type="button" @click="cancelEdit">Cancel</button>
      </div>
      <p v-if="formError" class="backlog-view__error">{{ formError }}</p>
    </form>
  </section>
</template>

<style scoped>
.backlog-view__list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.backlog-view__confirm {
  margin-top: 1rem;
  padding: 0.75rem;
  border: 1px solid #d1555c;
  border-radius: 0.25rem;
  background: #fdecec;
}

.backlog-view__form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.5rem;
  max-width: 20rem;
}

.backlog-view__form-actions {
  display: flex;
  gap: 0.5rem;
}

.backlog-view__error {
  color: #c33;
}
</style>
