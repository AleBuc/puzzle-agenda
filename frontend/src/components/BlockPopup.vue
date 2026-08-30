<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription, DialogClose } from 'reka-ui'
import { toMinutes, formatMinutes } from '../time-grid-utils'
import { shiftIsoDate } from '../date-utils'

const props = defineProps({
  popupState: { type: Object, default: null },
  dayActivities: { type: Array, default: () => [] },
  draft: { type: Object, default: null },
  errorMessage: { type: String, default: null },
  date: { type: String, default: null },
})

const emit = defineEmits(['submit-create', 'submit-edit', 'submit-delete', 'closed'])

const isOpen = computed(() => props.popupState !== null)
const isCreate = computed(() => props.popupState?.mode === 'create')
const isDetails = computed(() => props.popupState?.mode === 'details')
const startDayDate = computed(() => (props.date ? shiftIsoDate(props.date, -1) : null))

// FR-017 (focus returns to the triggering element): this Dialog is fully
// controlled by `popupState`, not opened via a <DialogTrigger>, so reka-ui's
// own auto-focus-on-close only ever calls `.focus()` on its internal
// `rootContext.triggerElement`, which is exclusively populated by
// <DialogTrigger> on mount — something this component doesn't use, and
// which isn't reachable from outside the DialogRoot's own subtree. So this
// restores focus independently: capture whatever had focus at the moment
// the popup opens, and hand it focus back once it closes (after reka-ui's
// own — here, no-op — close handling has run).
let triggerElement = null

watch(isOpen, async (open, wasOpen) => {
  if (open) {
    triggerElement = document.activeElement
  } else if (wasOpen && triggerElement) {
    await nextTick()
    triggerElement.focus()
    triggerElement = null
  }
})

function label(block) {
  return block.name || block.activityName || block.type
}

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

// Details mode (User Story 3): view/edit/delete an existing block, including
// the in-place multi-fragment delete choice (FR-013) as local view state —
// never a second, nested dialog.
const isEditing = ref(false)
const isConfirmingDelete = ref(false)
const editForm = ref({ startTime: '', endTime: '', name: '' })

watch(
  () => props.popupState,
  (state) => {
    isEditing.value = false
    isConfirmingDelete.value = false
    if (state?.mode === 'details') {
      editForm.value = { startTime: state.block.startTime, endTime: state.block.endTime, name: state.block.name || '' }
    }
  },
  { immediate: true },
)

function startEdit() {
  isEditing.value = true
}

function cancelEdit() {
  isEditing.value = false
}

function submitEdit() {
  emit('submit-edit', {
    id: props.popupState.block.id,
    startTime: editForm.value.startTime,
    endTime: editForm.value.endTime,
    name: editForm.value.name || null,
  })
}

function startDelete() {
  if (props.popupState.sameDayFragmentCount > 1) {
    isConfirmingDelete.value = true
  } else {
    emit('submit-delete', { id: props.popupState.block.id, scope: 'self' })
  }
}

function confirmDelete(scope) {
  emit('submit-delete', { id: props.popupState.block.id, scope })
}

function cancelDelete() {
  isConfirmingDelete.value = false
}

function goToStartDay() {
  emit('closed', { reason: 'navigate-to-start-day' })
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

        <template v-else-if="isDetails && popupState.readOnly">
          <p class="block-popup__readonly-notice">
            Starts {{ popupState.block.startTime }} on {{ startDayDate }} — edit it from that day.
          </p>
          <div class="block-popup__actions">
            <button type="button" class="block-popup__go-to-start-day" @click="goToStartDay">
              Go to start day
            </button>
            <DialogClose as-child>
              <button type="button" class="block-popup__cancel" @click="handleCloseButton">Close</button>
            </DialogClose>
          </div>
        </template>

        <div v-else-if="isDetails && !isEditing && !isConfirmingDelete">
          <p class="block-popup__detail-time">{{ popupState.block.startTime }}–{{ popupState.block.endTime }}</p>
          <p class="block-popup__detail-name">{{ label(popupState.block) }}</p>
          <div class="block-popup__actions">
            <button type="button" class="block-popup__edit" @click="startEdit">Edit</button>
            <button type="button" class="block-popup__delete" @click="startDelete">Delete</button>
            <DialogClose as-child>
              <button type="button" class="block-popup__cancel" @click="handleCloseButton">Close</button>
            </DialogClose>
          </div>
          <p v-if="errorMessage" class="block-popup__error">{{ errorMessage }}</p>
        </div>

        <form v-else-if="isDetails && isEditing" @submit.prevent="submitEdit">
          <label>
            Start
            <input v-model="editForm.startTime" type="time" step="300" required />
          </label>
          <label>
            End
            <input v-model="editForm.endTime" type="time" step="300" required />
          </label>
          <label v-if="popupState.block.type !== 'PLANNED_ACTIVITY'">
            Name
            <input v-model="editForm.name" type="text" />
          </label>
          <div class="block-popup__actions">
            <button type="submit">Save</button>
            <button type="button" @click="cancelEdit">Cancel</button>
          </div>
          <p v-if="errorMessage" class="block-popup__error">{{ errorMessage }}</p>
        </form>

        <div v-else-if="isDetails && isConfirmingDelete" class="block-popup__delete-confirm">
          <p>
            "{{ label(popupState.block) }}" has more than one fragment today. Delete just this
            one, or every fragment of this activity today?
          </p>
          <div class="block-popup__actions">
            <button type="button" @click="confirmDelete('self')">Delete this fragment only</button>
            <button type="button" class="block-popup__delete-all" @click="confirmDelete('activityDay')">
              Delete all fragments of this activity today
            </button>
            <button type="button" @click="cancelDelete">Cancel</button>
          </div>
        </div>
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
