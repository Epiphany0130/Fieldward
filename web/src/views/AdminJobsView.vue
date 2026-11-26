<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api'

interface Job { id: number; sourceId: string; status: string; title: string; sourceUrl: string; verifiedAt: string }
const jobs = ref<Job[]>([])
const sources = ref<string[]>([])
const loaded = ref(false)
const message = ref('')
const busy = ref(false)
async function load() {
  try { const [list, configured] = await Promise.all([api<Job[]>('/admin/jobs'), api<string[]>('/admin/jobs/sources')]); jobs.value = list; sources.value = configured; loaded.value = true; message.value = '' } catch (e) { loaded.value = false; message.value = errorMessage(e) }
}
async function remove(job: Job) {
  if (busy.value || !window.confirm(`下架「${job.title}」？`)) return
  busy.value = true
  try { await api(`/admin/jobs/${job.id}/remove`, 'POST'); await load() } catch (e) { message.value = errorMessage(e) } finally { busy.value = false }
}
async function sync(source: string) {
  if (busy.value) return
  busy.value = true
  try { await api(`/admin/jobs/sources/${encodeURIComponent(source)}/sync`, 'POST'); await load() } catch (e) { message.value = errorMessage(e) } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <main id="main" tabindex="-1" class="page-shell">
    <nav><RouterLink to="/admin/users">用户管理</RouterLink></nav>
    <h1>岗位管理</h1>
    <p v-if="message" role="alert">{{ message }}</p>
    <button v-for="source in sources" :key="source" :disabled="busy" @click="sync(source)">同步 {{ source }}</button>
    <button v-if="!loaded" @click="load">重试</button>
    <p v-if="loaded && !jobs.length">暂无已收录岗位。</p>
    <ul v-else-if="loaded">
      <li v-for="job in jobs" :key="job.id">
        <a :href="job.sourceUrl" target="_blank" rel="noopener noreferrer">{{ job.title }}</a>
        · {{ job.sourceId }} · {{ job.status === 'ACTIVE' ? '展示中' : '已下架' }} · 核验于 {{ new Date(job.verifiedAt).toLocaleString('zh-CN') }}
        <button v-if="job.status === 'ACTIVE'" :disabled="busy" @click="remove(job)">下架</button>
      </li>
    </ul>
  </main>
</template>
