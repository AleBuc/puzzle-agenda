<script setup>
// Visually distinguishes ROUTINE/CONSTRAINED/PLANNED_ACTIVITY blocks (FR-021).
defineProps({
  block: { type: Object, required: true },
})

const emit = defineEmits(['edit', 'delete'])
</script>

<template>
  <div class="time-block-card" :class="`time-block-card--${block.type.toLowerCase()}`">
    <span class="time-block-card__time">
      <span v-if="block.startsPreviousDay">(-1) </span>{{ block.startTime }}–{{ block.endTime
      }}<span v-if="block.endsNextDay"> (+1)</span>
    </span>
    <span class="time-block-card__name">{{ block.name || block.activityName || block.type }}</span>
    <span v-if="!block.startsPreviousDay" class="time-block-card__actions">
      <button type="button" @click="emit('edit', block)">Edit</button>
      <button type="button" @click="emit('delete', block)">Delete</button>
    </span>
  </div>
</template>

<style scoped>
.time-block-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.25rem;
  border-left: 4px solid;
}

.time-block-card--routine {
  border-color: #4a9d6e;
  background: #eef8f2;
}

.time-block-card--constrained {
  border-color: #d1555c;
  background: #fdecec;
}

.time-block-card--planned_activity {
  border-color: #4d78ad;
  background: #eef3fb;
}

.time-block-card__name {
  flex: 1;
}

.time-block-card__actions {
  display: flex;
  gap: 0.25rem;
}
</style>
