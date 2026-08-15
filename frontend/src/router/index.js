import { createRouter, createWebHistory } from 'vue-router'

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10)
}

// Day-navigation route skeleton (tasks.md T019). DayView is a placeholder
// until User Story 1 (T036) implements real day-to-day navigation bounded
// by the horizon (FR-023); BacklogView/RoutineTemplateView routes are added
// alongside their views in User Stories 2 and 4.
const routes = [
  { path: '/', redirect: () => `/days/${todayIsoDate()}` },
  {
    path: '/days/:date',
    name: 'day',
    component: () => import('../views/DayView.vue'),
    props: true,
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
