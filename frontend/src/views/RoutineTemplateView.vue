<script setup>
import { onMounted, ref } from 'vue'
import { useRoutineTemplate } from '../composables/useRoutineTemplate'
import RoutineEntryForm from '../components/RoutineEntryForm.vue'
import { ApiError } from '../api/client'

const { entries, loading, error, load, createEntry, editEntry, deleteEntry } = useRoutineTemplate()
onMounted(() => load())

const editingEntry = ref(null)
const formError = ref(null)

async function handleSubmit(payload) {
  formError.value = null
  try {
    if (editingEntry.value) {
      await editEntry(editingEntry.value.id, payload)
      editingEntry.value = null
    } else {
      await createEntry(payload)
    }
  } catch (err) {
    formError.value = err instanceof ApiError ? (err.message || err.reason) : 'Something went wrong.'
  }
}

function startEdit(entry) {
  formError.value = null
  editingEntry.value = entry
}

function cancelEdit() {
  formError.value = null
  editingEntry.value = null
}

async function handleDelete(entry) {
  await deleteEntry(entry.id)
}
</script>

<template>
  <section class="routine-template-view">
    <h1>Routine template</h1>
    <p class="routine-template-view__hint">
      Entries here pre-fill every newly visited day within the horizon. Editing an entry only
      affects days materialized after the edit — already-materialized days keep their original
      blocks.
    </p>

    <p v-if="loading">Loading…</p>
    <p v-else-if="error">Could not load the routine template.</p>
    <ul v-else class="routine-template-view__list">
      <li v-for="entry in entries" :key="entry.id">
        <div class="routine-entry-card">
          <span class="routine-entry-card__time">{{ entry.startTime }}&ndash;{{ entry.endTime }}</span>
          <span class="routine-entry-card__name">{{ entry.name }}</span>
          <span class="routine-entry-card__actions">
            <button type="button" @click="startEdit(entry)">Edit</button>
            <button type="button" @click="handleDelete(entry)">Delete</button>
          </span>
        </div>
      </li>
    </ul>

    <RoutineEntryForm :entry="editingEntry" @submit="handleSubmit" @cancel="cancelEdit" />
    <p v-if="formError" class="routine-template-view__error">{{ formError }}</p>
  </section>
</template>

<style scoped>
.routine-template-view__hint {
  color: #666;
  max-width: 32rem;
}

.routine-template-view__list {
  list-style: none;
  padding: 0;
  margin: 0 0 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.routine-entry-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.25rem;
  border-left: 4px solid #4a9d6e;
  background: #eef8f2;
}

.routine-entry-card__name {
  flex: 1;
}

.routine-entry-card__actions {
  display: flex;
  gap: 0.25rem;
}

.routine-template-view__error {
  color: #c33;
}
</style>
