import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_DT_BACKEND_URL ?? 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
