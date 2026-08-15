<script setup>
// Root shell: routed views (DayView, BacklogView, RoutineTemplateView) are
// added under router/index.js as their user stories are implemented.
</script>

<template>
  <nav class="app-nav">
    <router-link :to="{ name: 'day', params: { date: new Date().toISOString().slice(0, 10) } }">
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
  border-bottom: 1px solid #ddd;
}
</style>
