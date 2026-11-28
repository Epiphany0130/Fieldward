<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, errorMessage } from '../api'
interface Job { id: number; sourceId: string; title: string; requirements: string; sourceUrl: string; verifiedAt: string }
interface Requirement { capabilityId: string | null; title: string; sourceText: string }
interface Match { status: string; jobVersion: number; updatedAt: string | null; result: { percent: number | null; matched: { requirement: Requirement; evidenceId: number; sourceType: string; sourceId: string }[]; pending: Requirement[]; missing: Requirement[] } }
interface Entry { job: Job; match: Match }
const entries = ref<Entry[]>([])
const loading = ref(true)
const message = ref('')
let timer: ReturnType<typeof setInterval> | undefined
async function load() {
  try { entries.value = await api<Entry[]>('/jobs/matches'); message.value = '' } catch (e) { message.value = errorMessage(e) } finally { loading.value = false }
}
onMounted(() => { void load(); timer = setInterval(() => void load(), 15000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <main id="main" tabindex="-1" class="page-shell">
    <RouterLink to="/workspace">返回工作台</RouterLink>
    <h1>岗位</h1>
    <p v-if="message" role="alert">{{ message }}</p>
    <p v-if="loading">正在加载岗位…</p>
    <p v-else-if="!entries.length && !message">暂无已收录岗位。</p>
    <article v-for="{ job, match } in entries" :key="job.id">
      <h2>{{ job.title }}</h2>
      <p>来源：{{ job.sourceId }} · 核验于 {{ new Date(job.verifiedAt).toLocaleString('zh-CN') }}</p>
      <p v-if="match.status === 'DIAGNOSIS_REQUIRED'">完成首次诊断后查看匹配度。</p>
      <p v-else-if="match.result.percent !== null">匹配度：{{ match.result.percent }}% · 岗位版本 {{ match.jobVersion }}</p>
      <p v-else-if="match.status === 'UNVERIFIED'">岗位要求尚待核验，暂无法计算匹配度。</p>
      <p v-else-if="match.status === 'FAILED'">岗位要求暂时无法处理。</p>
      <p v-else-if="match.status === 'READY'">暂无足够的岗位能力要求，无法计算匹配度。</p>
      <p v-else-if="match.status === 'PROCESSING'">正在处理岗位要求…</p>
      <p v-else>岗位要求等待核验，暂无法计算匹配度。</p>
      <details v-if="match.result.percent !== null">
        <summary>查看匹配依据</summary>
        <p v-for="item in match.result.matched" :key="item.evidenceId">已验证：{{ item.requirement.title }}（岗位原文：{{ item.requirement.sourceText }}；证据：{{ item.sourceType }} / {{ item.sourceId }}）</p>
        <p v-for="item in match.result.pending" :key="item.sourceText">待验证：{{ item.title }}（{{ item.sourceText }}）</p>
        <p v-for="item in match.result.missing" :key="item.sourceText">待补充：{{ item.title }}（{{ item.sourceText }}）</p>
      </details>
      <p style="white-space: pre-wrap">{{ job.requirements }}</p>
      <p>地点、毕业届别、到岗时间和实习时长请以岗位原文为准。</p>
      <a :href="job.sourceUrl" target="_blank" rel="noopener noreferrer">查看来源并投递</a>
    </article>
  </main>
</template>
