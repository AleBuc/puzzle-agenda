import { createRouter, createWebHistory } from 'vue-router'

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10)
}

// Day-navigation route skeleton (tasks.md T019), day view filled in by User
// Story 1 (T036). BacklogView (T046, US2) added here; RoutineTemplateView
// route is added alongside its view in User Story 4.
const routes = [
  { path: '/', redirect: () => `/days/${todayIsoDate()}` },
  {
    path: '/days/:date',
    name: 'day',
    component: () => import('../views/DayView.vue'),
    props: true,
  },
  {
    path: '/backlog',
    name: 'backlog',
    component: () => import('../views/BacklogView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
