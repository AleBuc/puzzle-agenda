<script setup>
import { ref } from 'vue'

defineProps({
  activity: { type: Object, required: true },
})

const emit = defineEmits(['edit', 'delete'])

const expanded = ref(false)
</script>

<template>
  <div class="activity-card" :class="`activity-card--${activity.priority.toLowerCase()}`">
    <span class="activity-card__name">{{ activity.name }}</span>
    <span class="activity-card__meta">
      {{ activity.estimatedDurationMinutes }} min · {{ activity.priority }}
      <span v-if="activity.category"> · {{ activity.category }}</span>
    </span>
    <button
      v-if="activity.totalFragmentCount > 0"
      type="button"
      class="activity-card__status"
      @click="expanded = !expanded"
    >
      Planned on {{ activity.plannedDayCount }} day{{ activity.plannedDayCount === 1 ? '' : 's' }}
      ({{ activity.totalFragmentCount }} fragment{{ activity.totalFragmentCount === 1 ? '' : 's' }})
    </button>
    <ul v-if="expanded" class="activity-card__days">
      <li v-for="day in activity.days" :key="day.day">
        {{ day.day }}: {{ day.plannedMinutes }} min ({{ day.status }})
      </li>
    </ul>
    <span class="activity-card__actions">
      <button type="button" @click="emit('edit', activity)">Edit</button>
      <button type="button" @click="emit('delete', activity)">Delete</button>
    </span>
  </div>
</template>

<style scoped>
.activity-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.25rem;
  border-left: 4px solid;
}

.activity-card--low {
  border-color: #999;
  background: #f5f5f5;
}

.activity-card--medium {
  border-color: #d1a13a;
  background: #fbf3e2;
}

.activity-card--high {
  border-color: #d1555c;
  background: #fdecec;
}

.activity-card__name {
  font-weight: 600;
}

.activity-card__meta {
  color: #666;
  flex: 1;
}

.activity-card__status {
  font-size: 0.85em;
  color: #4d78ad;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}

.activity-card__days {
  flex-basis: 100%;
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.85em;
  color: #555;
}

.activity-card__actions {
  display: flex;
  gap: 0.25rem;
}
</style>
