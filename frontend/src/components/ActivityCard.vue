<script setup>
defineProps({
  activity: { type: Object, required: true },
})

const emit = defineEmits(['edit', 'delete'])
</script>

<template>
  <div class="activity-card" :class="`activity-card--${activity.priority.toLowerCase()}`">
    <span class="activity-card__name">{{ activity.name }}</span>
    <span class="activity-card__meta">
      {{ activity.estimatedDurationMinutes }} min · {{ activity.priority }}
      <span v-if="activity.category"> · {{ activity.category }}</span>
    </span>
    <span v-if="activity.status === 'PLANNED'" class="activity-card__status">Planned</span>
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
}

.activity-card__actions {
  display: flex;
  gap: 0.25rem;
}
</style>
