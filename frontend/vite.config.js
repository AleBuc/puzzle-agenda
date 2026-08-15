import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    // The API client (src/api/client.js) calls same-origin `/api/...` paths;
    // proxy them to the backend so `npm run dev` works without CORS setup.
    // Production serves the built assets separately, from behind whatever
    // reverse proxy or gateway sits in front of the backend — this block
    // only applies to `vite dev`.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.spec.js'],
  },
})
