<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
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
const verifying = ref('')
const question = ref<{ id: number; question: string }>()
const answer = ref('')
const result = ref<{ outcome: string; feedback: string }>()
watch(() => account.value?.id, () => { verifying.value = ''; question.value = undefined; result.value = undefined; answer.value = ''; error.value = '' })
function requestKey(owner: string, capability: string) { return `learning-verification:${owner}:${capability}` }
async function startVerification(capability: string) {
  if (!account.value || busy.value) return
  const owner = account.value.id
  busy.value = true; error.value = ''; verifying.value = capability; question.value = undefined; result.value = undefined; answer.value = ''
  const key = requestKey(owner, capability)
  const requestId = sessionStorage.getItem(key) || crypto.randomUUID()
  sessionStorage.setItem(key, requestId)
  try {
    await api<SideChat>(`/learning/topics/${encodeURIComponent(capability)}`, 'POST', { accountId: owner })
    if (account.value?.id !== owner) return
    const value = await api<{ id: number; question: string }>(`/learning/topics/${encodeURIComponent(capability)}/verifications`, 'POST', { accountId: owner, requestId })
    if (account.value?.id === owner) question.value = value
  } catch (e) { if (account.value?.id === owner) error.value = errorMessage(e) }
  finally { busy.value = false }
}
async function submitVerification() {
  if (!account.value || !question.value || busy.value || !answer.value.trim()) return
  const owner = account.value.id
  busy.value = true; error.value = ''
  try {
    const value = await api<{ outcome: string; feedback: string }>(`/learning/verifications/${question.value.id}/answer`, 'POST', { accountId: owner, answer: answer.value })
    if (account.value?.id === owner) result.value = value
  } catch (e) { if (account.value?.id === owner) error.value = errorMessage(e) }
  finally { busy.value = false }
}
function newVerification() {
  if (account.value) sessionStorage.removeItem(requestKey(account.value.id, verifying.value))
  const capability = verifying.value
  verifying.value = ''; question.value = undefined; result.value = undefined
  void startVerification(capability)
}
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
        <button class="button" type="button" :disabled="busy" @click="startVerification(item.capabilityId)">独立核验</button>
        <section v-if="verifying === item.capabilityId" aria-label="独立核验">
          <p v-if="busy" role="status">正在处理，请稍候…</p>
          <template v-if="question">
            <p>{{ question.question }}</p>
            <form v-if="!result" @submit.prevent="submitVerification">
              <label>你的回答<textarea v-model="answer" required maxlength="10000" rows="5" /></label>
              <button class="button" type="submit" :disabled="busy || !answer.trim()">提交核验</button>
            </form>
            <div v-else role="status">
              <p>{{ result.outcome === 'PASSED' ? '核验通过' : '建议继续练习' }}</p>
              <p>{{ result.feedback }}</p>
              <button class="button" type="button" :disabled="busy" @click="newVerification">再做一道</button>
            </div>
          </template>
        </section>
      </li>
    </ol>
  </main>
</template>

<style scoped>
.learning-route { width: min(100% - 2rem, 65rem); }
.topics { display: grid; gap: 1rem; padding-left: 1.5rem; }
.topic { padding: 1rem; }
.topic textarea { display: block; width: 100%; margin: .5rem 0; }
.topic h2 { font-size: 1.15rem; }
.topic p { color: var(--muted); line-height: 1.6; }
</style>
