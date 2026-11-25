import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', ['FIELDWARD_API_PORT', 'FIELDWARD_WEB_PORT'])
  return {
    plugins: [vue()],
    server: {
      host: '127.0.0.1',
      port: Number(env.FIELDWARD_WEB_PORT || 5178),
      strictPort: true,
      proxy: { '/api': `http://127.0.0.1:${env.FIELDWARD_API_PORT || 18080}` },
    },
  }
})
