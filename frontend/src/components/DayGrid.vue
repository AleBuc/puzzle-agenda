<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import GridBlock from './GridBlock.vue'
import { todayIsoDate } from '../date-utils'
import {
  layoutBlocks,
  minutesToPercent,
  percentToMinutes,
  snapDownToQuarterHour,
  formatMinutes,
  toMinutes,
} from '../time-grid-utils'

const props = defineProps({
  date: { type: String, required: true },
  blocks: { type: Array, default: () => [] },
})

const emit = defineEmits(['activate-slot', 'activate-block'])

const gridEl = ref(null)

const isToday = computed(() => props.date === todayIsoDate())

const now = ref(new Date())
const nowMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())
const nowPercent = computed(() => minutesToPercent(nowMinutes.value))

const hours = Array.from({ length: 24 }, (_, hour) => ({
  hour,
  topPercent: minutesToPercent(hour * 60),
  label: `${String(hour).padStart(2, '0')}:00`,
}))

const positionedBlocks = computed(() => layoutBlocks(props.blocks))

function relayActivate(block) {
  emit('activate-block', block)
}

function handleBackgroundClick(event) {
  if (event.target !== event.currentTarget) return
  const rect = event.currentTarget.getBoundingClientRect()
  const percent = rect.height > 0 ? (event.offsetY / rect.height) * 100 : 0
  const minutes = snapDownToQuarterHour(percentToMinutes(percent))
  emit('activate-slot', { startTime: formatMinutes(minutes) })
}

// Scroll to "now" on today's view, or the start of the day otherwise (FR-021).
function scrollToInitialPosition() {
  const el = gridEl.value
  if (!el) return
  const targetMinutes = isToday.value ? nowMinutes.value : 0
  const centered = (targetMinutes / 1440) * el.scrollHeight - el.clientHeight / 2
  el.scrollTop = Math.max(0, centered)
}

onMounted(async () => {
  await nextTick()
  scrollToInitialPosition()
})

watch(
  () => props.date,
  async () => {
    await nextTick()
    scrollToInitialPosition()
  },
)
</script>

<template>
  <div ref="gridEl" class="day-grid" @click="handleBackgroundClick">
    <div
      v-for="h in hours"
      :key="h.hour"
      class="day-grid__hour-row"
      :style="{ top: `${h.topPercent}%` }"
    >
      <span class="day-grid__hour-label">{{ h.label }}</span>
    </div>

    <GridBlock
      v-for="positioned in positionedBlocks"
      :key="positioned.block.id"
      :positioned="positioned"
      @activate="relayActivate"
    />

    <div v-if="isToday" class="day-grid__now-line" :style="{ top: `${nowPercent}%` }" />
  </div>
</template>

<style scoped>
.day-grid {
  position: relative;
  height: 1440px;
  border-top: 1px solid #333;
  overflow-y: auto;
  max-height: 70vh;
}

.day-grid__hour-row {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 1px solid #2a2a2a;
}

.day-grid__hour-label {
  position: absolute;
  top: 0.15rem;
  left: 0.25rem;
  font-size: 0.7rem;
  color: #888;
}

.day-grid__now-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: #d1555c;
  z-index: 2;
}
</style>
