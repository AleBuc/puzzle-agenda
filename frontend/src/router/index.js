import { createRouter, createWebHistory } from 'vue-router'
import { todayIsoDate } from '../date-utils'

// Day-navigation route skeleton (tasks.md T019), day view filled in by User
// Story 1 (T036). BacklogView (T046, US2) and RoutineTemplateView (T068,
// US4) added here alongside their views.
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
  {
    path: '/routine-template',
    name: 'routine-template',
    component: () => import('../views/RoutineTemplateView.vue'),
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
