<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api'
interface Job { id: number; sourceId: string; title: string; requirements: string; sourceUrl: string; verifiedAt: string }
const jobs = ref<Job[]>([])
const loading = ref(true)
const message = ref('')
onMounted(async () => {
  try { jobs.value = await api<Job[]>('/jobs') } catch (e) { message.value = errorMessage(e) } finally { loading.value = false }
})
</script>
<template>
  <main id="main" tabindex="-1" class="page-shell">
    <RouterLink to="/workspace">返回工作台</RouterLink>
    <h1>岗位</h1>
    <p v-if="message" role="alert">{{ message }}</p>
    <p v-else-if="loading">正在加载岗位…</p>
    <p v-else-if="!jobs.length">暂无已收录岗位。</p>
    <article v-for="job in jobs" :key="job.id">
      <h2>{{ job.title }}</h2>
      <p>来源：{{ job.sourceId }} · 核验于 {{ new Date(job.verifiedAt).toLocaleString('zh-CN') }}</p>
      <p style="white-space: pre-wrap">{{ job.requirements }}</p>
      <a :href="job.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源并投递</a>
    </article>
  </main>
</template>
