<script setup>
import { computed } from 'vue'
import TimeBlockCard from './TimeBlockCard.vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] },
})

const emit = defineEmits(['edit', 'delete'])

const MINUTES_PER_DAY = 24 * 60

function toMinutes(hhmm) {
  const [hours, minutes] = hhmm.split(':').map(Number)
  return hours * 60 + minutes
}

function formatMinutes(minutes) {
  // No `% 24`: minutes only ever ranges 0..1440 here, and the end-of-day
  // boundary must display as "24:00", not wrap around to "00:00".
  const hours = String(Math.floor(minutes / 60)).padStart(2, '0')
  const mins = String(minutes % 60).padStart(2, '0')
  return `${hours}:${mins}`
}

// Chronological blocks (FR-020) with visible free-time gaps between them (FR-022).
const timeline = computed(() => {
  const sorted = [...props.blocks].sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime))
  const items = []
  let cursor = 0

  for (const block of sorted) {
    const start = toMinutes(block.startTime)
    const end = block.endsNextDay ? MINUTES_PER_DAY : toMinutes(block.endTime)
    if (start > cursor) {
      items.push({ kind: 'gap', key: `gap-${cursor}`, startMinutes: cursor, endMinutes: start })
    }
    items.push({ kind: 'block', key: block.id, block })
    cursor = Math.max(cursor, end)
  }

  if (cursor < MINUTES_PER_DAY) {
    items.push({ kind: 'gap', key: `gap-${cursor}`, startMinutes: cursor, endMinutes: MINUTES_PER_DAY })
  }

  return items
})
</script>

<template>
  <ol class="day-timeline">
    <li v-for="item in timeline" :key="item.key">
      <TimeBlockCard
        v-if="item.kind === 'block'"
        :block="item.block"
        @edit="emit('edit', $event)"
        @delete="emit('delete', $event)"
      />
      <div v-else class="day-timeline__gap">
        Free {{ formatMinutes(item.startMinutes) }}–{{ formatMinutes(item.endMinutes) }}
      </div>
    </li>
  </ol>
</template>

<style scoped>
.day-timeline {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.day-timeline__gap {
  padding: 0.5rem 0.75rem;
  color: #888;
  font-style: italic;
}
</style>
