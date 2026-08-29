import { ref, watch } from 'vue'
import { apiClient } from '../api/client'

// dateRef: a Ref<string> (YYYY-MM-DD) — reloads the day whenever it changes.
export function useDaySchedule(dateRef) {
  const day = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      day.value = await apiClient.get(`/days/${dateRef.value}`)
    } catch (err) {
      error.value = err
    } finally {
      loading.value = false
    }
  }

  async function createBlock(block) {
    await apiClient.post(`/days/${dateRef.value}/blocks`, block)
    await load()
  }

  async function editBlock(id, patch) {
    await apiClient.put(`/blocks/${id}`, patch)
    await load()
  }

  // scope: 'self' (default, this fragment only) or 'activityDay' (every fragment
  // of the same activity on this day) — see spec.md US4 / contracts/api.md.
  async function deleteBlock(id, scope = 'self') {
    await apiClient.delete(`/blocks/${id}?scope=${scope}`)
    await load()
  }

  watch(dateRef, load, { immediate: true })

  return { day, loading, error, load, createBlock, editBlock, deleteBlock }
}
