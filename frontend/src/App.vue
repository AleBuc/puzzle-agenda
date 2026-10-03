<script setup>
// Root shell: routed views (DayView, BacklogView, RoutineTemplateView) are
// added under router/index.js as their user stories are implemented.
import { todayIsoDate } from './date-utils'
</script>

<template>
  <nav class="app-nav">
    <router-link :to="{ name: 'day', params: { date: todayIsoDate() } }">
      Today
    </router-link>
    <router-link :to="{ name: 'backlog' }">Backlog</router-link>
    <router-link :to="{ name: 'routine-template' }">Routine template</router-link>
  </nav>
  <router-view />
</template>

<style scoped>
.app-nav {
  display: flex;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--border);
}

/* Without these the links keep the browser's default blue and visited
   purple, outside the single-accent palette of style.css. */
.app-nav a {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 2px solid transparent;
}

.app-nav a:hover {
  text-decoration: underline;
}

.app-nav a.router-link-active {
  color: var(--text-h);
  font-weight: 600;
  border-bottom-color: var(--accent);
}

.app-nav a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
