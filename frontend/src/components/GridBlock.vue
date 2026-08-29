<script setup>
// One proportionally-positioned block in the day grid (FR-002, FR-003).
// Emitted for every block, including a continuation-only (spillover)
// rendering — DayView.vue opens a read-only details popup for those.
const props = defineProps({
  positioned: { type: Object, required: true },
})

const emit = defineEmits(['activate'])

function label(block) {
  return block.name || block.activityName || block.type
}

function fullDetail(block) {
  return `${block.startTime}–${block.endTime} ${label(block)}`
}

function activate() {
  emit('activate', props.positioned.block)
}
</script>

<template>
  <div
    class="grid-block"
    :class="[
      `grid-block--${positioned.block.type.toLowerCase()}`,
      {
        'grid-block--continuation': positioned.isContinuationOnly,
        'grid-block--short': positioned.isVeryShort,
      },
    ]"
    :style="{ top: `${positioned.topPercent}%`, height: `${positioned.heightPercent}%` }"
    :title="positioned.isVeryShort ? fullDetail(positioned.block) : undefined"
    role="button"
    tabindex="0"
    @click="activate"
    @keydown.enter="activate"
    @keydown.space.prevent="activate"
  >
    <span v-if="positioned.isContinuationOnly" class="grid-block__continuation" aria-hidden="true">⋯</span>
    <span v-if="!positioned.isVeryShort" class="grid-block__time">
      {{ positioned.block.startTime }}–{{ positioned.block.endTime }}
    </span>
    <span class="grid-block__name">{{ label(positioned.block) }}</span>
  </div>
</template>

<style scoped>
.grid-block {
  position: absolute;
  left: 0.25rem;
  right: 0.25rem;
  overflow: hidden;
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  padding: 0 0.4rem;
  border-radius: 0.2rem;
  border-left: 3px solid;
  font-size: 0.85rem;
  cursor: pointer;
}

.grid-block--routine {
  border-color: #4a9d6e;
  background: #eef8f2;
}

.grid-block--constrained {
  border-color: #d1555c;
  background: #fdecec;
}

.grid-block--planned_activity {
  border-color: #4d78ad;
  background: #eef3fb;
}

.grid-block--continuation {
  border-style: dashed;
}

.grid-block--short {
  align-items: center;
  padding: 0 0.25rem;
  font-size: 0.7rem;
}

.grid-block__time {
  color: #555;
  white-space: nowrap;
}

.grid-block__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-block__continuation {
  flex-shrink: 0;
}
</style>
