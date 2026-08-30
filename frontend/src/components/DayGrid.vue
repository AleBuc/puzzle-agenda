<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import GridBlock from './GridBlock.vue'
import { todayIsoDate } from '../date-utils'
import {
  layoutBlocks,
  minutesToPercent,
  percentToMinutes,
  snapDownToQuarterHour,
  snapToFiveMinutes,
  formatMinutes,
  MINUTES_PER_DAY,
} from '../time-grid-utils'

const LAST_FIVE_MINUTE_SLOT = MINUTES_PER_DAY - 5 // 23:55, the last selectable slot start

const props = defineProps({
  date: { type: String, required: true },
  blocks: { type: Array, default: () => [] },
})

const emit = defineEmits(['activate-slot', 'activate-block'])

const gridEl = ref(null)
const contentEl = ref(null)
const cursorEl = ref(null)

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

// This listener lives on `.day-grid__content` — the fixed-height (1440px),
// non-scrolling coordinate space — rather than on `.day-grid`, the scrolling
// viewport around it. That placement is what makes the math below correct
// with no explicit scrollTop correction: `offsetY` is defined relative to the
// padding edge of `event.currentTarget`'s own box, which is a fixed frame of
// reference regardless of how far an ancestor has scrolled. Attaching this
// same handler to the scrolling element itself was the earlier bug — its
// `getBoundingClientRect().height` only ever reflected the visible ~70vh
// window, and `offsetY` was relative to that same scrolled visible area, so
// every click after scrolling computed a wrong time.
function handleBackgroundClick(event) {
  if (event.target !== event.currentTarget) return
  const rect = event.currentTarget.getBoundingClientRect()
  const percent = rect.height > 0 ? (event.offsetY / rect.height) * 100 : 0
  const minutes = snapDownToQuarterHour(percentToMinutes(percent))
  emit('activate-slot', { startTime: formatMinutes(minutes) })
}

// Scroll to "now" on today's view, or the start of the day otherwise (FR-021).
// The content element is always exactly MINUTES_PER_DAY pixels tall (1px per
// minute, enforced by the `.day-grid__content` height below), so a target
// time in minutes is already the target scroll offset in pixels — no need to
// measure or derive it from the DOM.
function scrollToInitialPosition() {
  const el = gridEl.value
  if (!el) return
  const targetMinutes = isToday.value ? nowMinutes.value : 0
  const centered = targetMinutes - el.clientHeight / 2
  el.scrollTop = Math.max(0, centered)
}

function scrollCursorIntoView() {
  cursorEl.value?.scrollIntoView?.({ block: 'nearest' })
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

// Keyboard operation (US4): a roving virtual cursor over the day's 5-minute
// slots (FR-023), moved with ArrowUp/ArrowDown (not Left/Right, which are
// reserved for day-to-day navigation even while the grid has focus) and
// activated with Enter/Space — existing blocks are separately reachable via
// Tab (GridBlock is already a tab stop). Initialized to the same
// today-vs-other-day default as the initial scroll position (FR-021).
function defaultFocusMinutes() {
  return isToday.value ? snapToFiveMinutes(nowMinutes.value) : 0
}

const focusedMinutes = ref(defaultFocusMinutes())

watch(
  () => props.date,
  () => {
    focusedMinutes.value = defaultFocusMinutes()
  },
)

async function handleGridKeydown(event) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    focusedMinutes.value = Math.min(LAST_FIVE_MINUTE_SLOT, focusedMinutes.value + 5)
    await nextTick()
    scrollCursorIntoView()
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    focusedMinutes.value = Math.max(0, focusedMinutes.value - 5)
    await nextTick()
    scrollCursorIntoView()
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    emit('activate-slot', { startTime: formatMinutes(focusedMinutes.value) })
  }
}

const hasGridFocus = ref(false)

async function handleGridFocus() {
  hasGridFocus.value = true
  await nextTick()
  scrollCursorIntoView()
}

// The persistent "Add block" control (FR-018): a normal, always-reachable
// tab stop independent of the roving cursor above, so a keyboard user is
// never limited to tabbing/arrowing through the grid slot-by-slot just to
// create a block.
function activateAddBlock() {
  const startMinutes = isToday.value ? snapDownToQuarterHour(nowMinutes.value) : 0
  emit('activate-slot', { startTime: formatMinutes(startMinutes) })
}
</script>

<template>
  <div
    ref="gridEl"
    class="day-grid"
    tabindex="0"
    :aria-label="`Day schedule grid. Use arrow keys to move the cursor at ${formatMinutes(focusedMinutes)}, Enter or Space to add a block there.`"
    @keydown="handleGridKeydown"
    @focus="handleGridFocus"
    @blur="hasGridFocus = false"
  >
    <button type="button" class="day-grid__add-block" @click="activateAddBlock">
      + Add block
    </button>

    <!--
      The scrollable viewport (`.day-grid`, above) and the day's coordinate
      space (`.day-grid__content`, here) are deliberately two different
      elements. `.day-grid` used to carry both `height: 1440px` and
      `max-height: 70vh` at once — max-height always wins that conflict, so
      the box (and every percentage computed against it, including every
      block's top/height) was actually ~484px tall on common screens instead
      of 1440px, a ~3x compression that made 15-minute blocks render as
      illegible ~5px slivers. Giving the fixed 1440px height to this inner,
      non-scrolling element instead means percentages inside it always
      resolve against the true 1440px, regardless of how short the outer
      viewport is.
    -->
    <div ref="contentEl" class="day-grid__content" @click="handleBackgroundClick">
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

      <div
        v-if="hasGridFocus"
        ref="cursorEl"
        class="day-grid__cursor"
        :style="{ top: `${minutesToPercent(focusedMinutes)}%` }"
      />
    </div>
  </div>
</template>

<style scoped>
.day-grid {
  overflow-y: auto;
  max-height: 70vh;
}

.day-grid__content {
  position: relative;
  height: 1440px;
  border-top: 1px solid #333;
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

.day-grid__add-block {
  position: sticky;
  top: 0.25rem;
  left: 0.25rem;
  z-index: 4;
  float: left;
}

.day-grid__cursor {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: #4d78ad;
  z-index: 2;
  pointer-events: none;
}
</style>
