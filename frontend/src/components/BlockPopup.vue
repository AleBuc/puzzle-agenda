<script setup>
import { computed, ref, watch } from 'vue'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription, DialogClose } from 'reka-ui'
import { toMinutes, formatMinutes } from '../time-grid-utils'

const props = defineProps({
  popupState: { type: Object, default: null },
  dayActivities: { type: Array, default: () => [] },
  draft: { type: Object, default: null },
  errorMessage: { type: String, default: null },
})

const emit = defineEmits(['submit-create', 'submit-edit', 'submit-delete', 'closed'])

const isOpen = computed(() => props.popupState !== null)
const isCreate = computed(() => props.popupState?.mode === 'create')

function emptyForm() {
  return { type: 'CONSTRAINED', name: '', activityId: '', startTime: '', endTime: '' }
}

const form = ref(emptyForm())

// (Re)initialize the create form whenever a creation popup opens (including
// re-opening directly on a new slot per FR-020): apply the draft's
// type/name/activityId/duration if one exists for this day, but always take
// startTime from the freshly-activated slot (data-model.md BlockDraft).
watch(
  () => props.popupState,
  (state) => {
    if (!state || state.mode !== 'create') return
    const startMinutes = toMinutes(state.startTime)
    if (props.draft) {
      form.value = {
        type: props.draft.type,
        name: props.draft.name ?? '',
        activityId: props.draft.activityId ?? '',
        startTime: state.startTime,
        endTime: formatMinutes(startMinutes + props.draft.durationMinutes),
      }
    } else {
      form.value = {
        type: 'CONSTRAINED',
        name: '',
        activityId: '',
        startTime: state.startTime,
        endTime: formatMinutes(startMinutes + 60),
      }
    }
  },
  { immediate: true },
)

function activityOptionLabel(activity) {
  const remaining = `${activity.remainingMinutesForDay}min left`
  return activity.dayStatus === 'PLANNED' ? `${activity.name} (fully planned, ${remaining})` : `${activity.name} (${remaining})`
}

function submitCreate() {
  emit('submit-create', {
    type: form.value.type,
    startTime: form.value.startTime,
    endTime: form.value.endTime,
    name: form.value.type === 'PLANNED_ACTIVITY' ? null : form.value.name || null,
    activityId: form.value.type === 'PLANNED_ACTIVITY' ? form.value.activityId : null,
  })
}

function createSnapshot() {
  return {
    type: form.value.type,
    name: form.value.name || null,
    activityId: form.value.activityId || null,
    durationMinutes: toMinutes(form.value.endTime) - toMinutes(form.value.startTime),
  }
}

function handleEscape() {
  emit('closed', { reason: 'escape' })
}

function handleCloseButton() {
  emit('closed', { reason: 'close-button' })
}

function handleBackdrop() {
  if (isCreate.value) {
    emit('closed', { reason: 'backdrop', snapshot: createSnapshot() })
  } else {
    emit('closed', { reason: 'backdrop' })
  }
}
</script>

<template>
  <DialogRoot :open="isOpen">
    <DialogPortal>
      <DialogOverlay class="block-popup__overlay" />
      <DialogContent
        class="block-popup__content"
        @escape-key-down="handleEscape"
        @pointer-down-outside="handleBackdrop"
      >
        <DialogTitle>{{ isCreate ? 'Add a block' : 'Block details' }}</DialogTitle>
        <DialogDescription class="block-popup__visually-hidden">
          {{ isCreate ? 'Create a new time block for this day.' : 'View, edit, or delete this time block.' }}
        </DialogDescription>

        <form v-if="isCreate" @submit.prevent="submitCreate">
          <label>
            Type
            <select name="type" v-model="form.type">
              <option value="ROUTINE">Routine</option>
              <option value="CONSTRAINED">Constrained</option>
              <option value="PLANNED_ACTIVITY">Planned activity</option>
            </select>
          </label>
          <label>
            Start
            <input v-model="form.startTime" type="time" step="300" required />
          </label>
          <label>
            End
            <input v-model="form.endTime" type="time" step="300" required />
          </label>
          <label v-if="form.type === 'PLANNED_ACTIVITY'">
            Activity
            <select name="activity" v-model="form.activityId" required>
              <option value="" disabled>Select a backlog activity…</option>
              <option v-for="activity in dayActivities" :key="activity.id" :value="activity.id">
                {{ activityOptionLabel(activity) }}
              </option>
            </select>
          </label>
          <label v-else>
            Name
            <input v-model="form.name" name="name" type="text" />
          </label>
          <div class="block-popup__actions">
            <button type="submit">Add block</button>
            <DialogClose as-child>
              <button type="button" class="block-popup__cancel" @click="handleCloseButton">Cancel</button>
            </DialogClose>
          </div>
          <p v-if="errorMessage" class="block-popup__error">{{ errorMessage }}</p>
        </form>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<style scoped>
.block-popup__overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
}

.block-popup__content {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: #1b1b1f;
  border-radius: 0.4rem;
  padding: 1.25rem;
  min-width: 20rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.block-popup__content form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.block-popup__actions {
  display: flex;
  gap: 0.5rem;
}

.block-popup__error {
  color: #c33;
}

.block-popup__visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
</style>
