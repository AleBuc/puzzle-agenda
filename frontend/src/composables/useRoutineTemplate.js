import { ref } from 'vue'
import { apiClient } from '../api/client'

export function useRoutineTemplate() {
  const entries = ref([])
  const loading = ref(false)
  const error = ref(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      entries.value = await apiClient.get('/routine-template/entries')
    } catch (err) {
      error.value = err
    } finally {
      loading.value = false
    }
  }

  async function createEntry(entry) {
    await apiClient.post('/routine-template/entries', entry)
    await load()
  }

  async function editEntry(id, entry) {
    await apiClient.put(`/routine-template/entries/${id}`, entry)
    await load()
  }

  async function deleteEntry(id) {
    await apiClient.delete(`/routine-template/entries/${id}`)
    await load()
  }

  return { entries, loading, error, load, createEntry, editEntry, deleteEntry }
}
