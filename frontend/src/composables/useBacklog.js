import { ref } from 'vue'
import { apiClient } from '../api/client'

export function useBacklog() {
  const activities = ref([])
  const loading = ref(false)
  const error = ref(null)

  async function load(status) {
    loading.value = true
    error.value = null
    try {
      const query = status ? `?status=${status}` : ''
      activities.value = await apiClient.get(`/activities${query}`)
    } catch (err) {
      error.value = err
    } finally {
      loading.value = false
    }
  }

  async function createActivity(activity) {
    await apiClient.post('/activities', activity)
    await load()
  }

  async function editActivity(id, activity) {
    await apiClient.put(`/activities/${id}`, activity)
    await load()
  }

  async function deleteActivity(id, confirm = false) {
    await apiClient.delete(`/activities/${id}${confirm ? '?confirm=true' : ''}`)
    await load()
  }

  return { activities, loading, error, load, createActivity, editActivity, deleteActivity }
}
