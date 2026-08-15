<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  // When set, the form is in "edit" mode and pre-filled from this entry.
  entry: { type: Object, default: null },
})

const emit = defineEmits(['submit', 'cancel'])

const emptyForm = () => ({ name: '', startTime: '', endTime: '' })
const form = ref(props.entry ? { ...props.entry } : emptyForm())

watch(
  () => props.entry,
  (entry) => {
    form.value = entry ? { ...entry } : emptyForm()
  },
)

function submit() {
  emit('submit', { name: form.value.name, startTime: form.value.startTime, endTime: form.value.endTime })
  if (!props.entry) {
    form.value = emptyForm()
  }
}
</script>

<template>
  <form class="routine-entry-form" @submit.prevent="submit">
    <h2>{{ entry ? 'Edit routine entry' : 'Add a routine entry' }}</h2>
    <label>
      Name
      <input v-model="form.name" type="text" required />
    </label>
    <label>
      Start
      <input v-model="form.startTime" type="time" step="300" required />
    </label>
    <label>
      End
      <input v-model="form.endTime" type="time" step="300" required />
    </label>
    <p class="routine-entry-form__hint">
      An end time at or before the start time means the entry spans midnight (e.g. sleep
      23:00&ndash;07:00).
    </p>
    <div class="routine-entry-form__actions">
      <button type="submit">{{ entry ? 'Save' : 'Add entry' }}</button>
      <button v-if="entry" type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.routine-entry-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 20rem;
}

.routine-entry-form__hint {
  font-size: 0.85em;
  color: #666;
  margin: 0;
}

.routine-entry-form__actions {
  display: flex;
  gap: 0.5rem;
}
</style>
