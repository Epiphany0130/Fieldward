<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, errorMessage } from '../api'
import { account } from '../session'
import type { SideChat } from '../mentor'

interface Evidence { id: number; outcome: string }
interface Capability { capabilityId: string; title: string; objective: string; evidence: Evidence[] }
interface RouteSnapshot { capabilities: Capability[] }
const route = useRoute()
const router = useRouter()
const snapshot = ref<RouteSnapshot>()
const error = ref('')
const busy = ref(false)
onMounted(async () => {
  try { snapshot.value = await api<RouteSnapshot>(`/career/directions/${encodeURIComponent(String(route.params.directionId))}/route`) }
  catch (e) { error.value = errorMessage(e) }
})
async function open(capabilityId: string) {
  if (!account.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const side = await api<SideChat>(`/learning/topics/${encodeURIComponent(capabilityId)}`, 'POST', { accountId: account.value.id })
    await router.push({ name: 'workspace', query: { side: side.id } })
  } catch (e) { error.value = errorMessage(e) }
  finally { busy.value = false }
}
</script>

<template>
  <main id="main" tabindex="-1" class="page learning-route">
    <RouterLink :to="{ name: 'workspace' }">返回工作台</RouterLink>
    <h1>学习路线</h1>
    <p>可以按照推荐顺序学习，也可以选择任何感兴趣的知识点。选择后进入文字旁聊。</p>
    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <p v-if="!snapshot && !error" role="status">正在读取学习路线…</p>
    <ol v-if="snapshot" class="topics">
      <li v-for="item in snapshot.capabilities" :key="item.capabilityId" class="surface topic">
        <h2>{{ item.title }}</h2>
        <p>{{ item.objective }}</p>
        <p v-if="item.evidence.some(e => e.outcome === 'PASSED')">已有通过记录，可以复习巩固。</p>
        <button class="button button-primary" type="button" :disabled="busy" @click="open(item.capabilityId)">学习这个知识点</button>
      </li>
    </ol>
  </main>
</template>

<style scoped>
.learning-route { width: min(100% - 2rem, 65rem); }
.topics { display: grid; gap: 1rem; padding-left: 1.5rem; }
.topic { padding: 1rem; }
.topic h2 { font-size: 1.15rem; }
.topic p { color: var(--muted); line-height: 1.6; }
</style>
