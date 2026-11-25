<script setup lang="ts">
import { onMounted, ref } from 'vue'

const status = ref('正在检查连接…')

async function checkConnection() {
  status.value = '正在检查连接…'
  try {
    const response = await fetch('/api/health')
    const health = await response.json()
    if (!response.ok || health.status !== 'ready') throw new Error('API unavailable')
    status.value = 'API 和 PostgreSQL 已连接'
  } catch {
    status.value = '连接失败，请检查 API 和数据库后重试'
  }
}

onMounted(checkConnection)
</script>

<template>
  <main>
    <h1>循职 Fieldward</h1>
    <p role="status">{{ status }}</p>
    <button type="button" @click="checkConnection">重新检查</button>
  </main>
</template>

<style>
body { margin: 0; background: #f5f3eb; color: #18332d; font: 16px/1.55 system-ui, sans-serif; }
main { margin: 5rem auto; max-width: 34rem; padding: 1.5rem; }
button { min-height: 44px; padding: 0 1rem; font: inherit; cursor: pointer; }
</style>
