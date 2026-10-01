import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// Em desenvolvimento (npm run dev), a API vem do backend local (./mvnw spring-boot:test-run).
// No container, quem faz esse papel é o nginx (nginx.conf).
const backendLocal = 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': backendLocal,
      '/actuator/health': backendLocal,
    },
  },
})
