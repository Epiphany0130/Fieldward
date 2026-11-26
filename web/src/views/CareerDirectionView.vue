<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import type { CareerAssessment, CareerState } from '../career'
import { account, forgetSession } from '../session'

const route = useRoute()
const router = useRouter()
const state = ref<CareerState>()
const selected = ref('')
const message = ref('')
const submitting = ref(false)
const loading = ref(false)
const current = computed(() => state.value?.current)
const forLatest = computed(() => current.value?.resumeVersion === state.value?.resumeVersion)
const pending = computed(() => forLatest.value && (current.value?.status === 'ANALYZING' || current.value?.status === 'WAITING_TEXT'))
const showFailure = computed(() => forLatest.value && current.value?.status === 'FAILED')
const changing = computed(() => route.query.change === '1')
let timer: number | undefined
let active = true
let reanalysisRequested = false

async function showError(error: unknown) {
  if (!active) return
  if (error instanceof ApiError && error.status === 401) {
    forgetSession()
    await router.replace({ name: 'login' })
  } else {
    message.value = errorMessage(error)
  }
}

async function openResult(assessment: CareerAssessment) {
  await router.replace({ name: 'career-plan', params: { id: assessment.id } })
}

async function refresh(followResult: boolean) {
  window.clearTimeout(timer)
  if (!active) return
  loading.value = true
  message.value = ''
  try {
    const snapshot = await api<CareerState>('/career')
    if (!active) return
    if (snapshot.accountId !== account.value?.id) throw new ApiError(403, '当前登录账号已变更，请刷新页面。')
    if (snapshot.resumeVersion === null) {
      await router.replace({ name: 'resume-draft' })
      return
    }
    state.value = snapshot
    const result = snapshot.current
    const requestedDirection = route.query.reanalyze
    if (!reanalysisRequested && !submitting.value && !pending.value && typeof requestedDirection === 'string'
        && snapshot.directions.some(({ id }) => id === requestedDirection)
        && (result?.directionId !== requestedDirection || !forLatest.value || result.needsReanalysis)) {
      reanalysisRequested = true
      selected.value = requestedDirection
      await start(requestedDirection)
      return
    }
    if (!result) return
    if (!selected.value || pending.value) selected.value = result.directionId
    if (!forLatest.value) return
    if (result.status === 'READY' && (followResult || !changing.value)) {
      await openResult(result)
    } else if (result.status === 'WAITING_TEXT' && !snapshot.waitingForText && !submitting.value) {
      await start(result.directionId)
    } else if (pending.value) {
      timer = window.setTimeout(() => void refresh(true), 1800)
    }
  } catch (error) {
    await showError(error)
  } finally {
    if (active) loading.value = false
  }
}

async function explore() {
  if (submitting.value) return
  submitting.value = true
  message.value = ''
  try {
    await api('/career/exploration', 'POST')
    await router.push({ name: 'workspace' })
  } catch (error) {
    await showError(error)
  } finally {
    if (active) submitting.value = false
  }
}

async function start(directionId: string) {
  const accountId = account.value?.id
  const resumeVersion = state.value?.resumeVersion
  if (!accountId || !resumeVersion || !directionId || submitting.value) return
  window.clearTimeout(timer)
  submitting.value = true
  message.value = ''
  try {
    const result = await api<CareerAssessment>('/career/assessment', 'POST', { accountId, resumeVersion, directionId })
    if (!active) return
    if (result.status === 'READY') {
      await openResult(result)
    } else {
      await refresh(true)
    }
  } catch (error) {
    await showError(error)
  } finally {
    if (active) submitting.value = false
  }
}

onMounted(() => void refresh(false))
onBeforeUnmount(() => { active = false; window.clearTimeout(timer) })
</script>

<template>
  <main id="main" tabindex="-1" class="page career-page">
    <RouterLink v-if="changing" class="career-back" :to="{ name: 'workspace' }">返回工作台</RouterLink>
    <header class="career-heading">
      <div>
        <h1>选择求职方向</h1>
        <p>按你想申请的岗位选择方向。Mentor 会根据简历逐项判断匹配度，并给出下一步。</p>
      </div>
      <div v-if="state" class="surface career-resume">
        <strong>简历第 {{ state.resumeVersion }} 版</strong>
        <span v-if="state.resumeName">{{ state.resumeName }}</span>
        <RouterLink :to="{ name: 'resume-final', query: { version: String(state.resumeVersion) } }">查看简历</RouterLink>
      </div>
    </header>

    <p v-if="message" class="notice notice-error" role="alert">
      {{ message }}
      <button class="button button-quiet" type="button" :disabled="submitting" @click="refresh(false)">刷新状态</button>
    </p>
    <p v-if="!state && loading" role="status">正在读取职业方向…</p>

    <form v-if="state" @submit.prevent="start(selected)">
      <section v-if="pending || submitting" class="surface analysis-status" role="status">
        <div>
          <strong>{{ current?.status === 'WAITING_TEXT' ? '正在等待扫描件识别' : '正在分析简历' }}</strong>
          <p>{{ current?.status === 'WAITING_TEXT'
            ? '识别完成后会自动开始匹配分析。'
            : 'Mentor 正在核对技术栈、项目经历和该方向的要求。' }}</p>
        </div>
        <RouterLink v-if="current?.status === 'WAITING_TEXT'"
                    :to="{ name: 'resume-upload' }">上传更清晰的版本</RouterLink>
      </section>
      <p v-if="showFailure" class="notice notice-error" role="alert">这次分析没有完成。可以重试，或选择其他方向。</p>
      <section class="surface analysis-status">
        <div><strong>还没有确定方向？</strong><p>先和 Mentor 探索兴趣，或学习通用入门知识。</p></div>
        <button class="button button-quiet" type="button" :disabled="submitting || pending" @click="explore">开始探索与通用学习</button>
      </section>
      <fieldset :disabled="pending || submitting" class="directions">
        <legend>职业方向</legend>
        <label v-for="option in state.directions" :key="option.id" class="surface direction" :class="{ selected: selected === option.id }">
          <span class="direction-title">
            <strong>{{ option.title }}</strong>
            <input v-model="selected" type="radio" name="career-direction" :value="option.id">
          </span>
          <span class="direction-description">{{ option.description }}</span>
          <span class="direction-roles">{{ option.roles }}</span>
          <span class="direction-topics">
            <span v-for="topic in option.topics" :key="topic">{{ topic }}</span>
          </span>
        </label>
      </fieldset>
      <footer class="career-actions">
        <a :href="state.referenceUrl" target="_blank" rel="noopener noreferrer">方向参考 · 黑马程序员</a>
        <button class="button button-primary" type="submit" :disabled="!selected || pending || submitting">
          {{ showFailure && selected === current?.directionId ? '重新分析' : '确认方向并分析' }}
        </button>
      </footer>
    </form>
  </main>
</template>

<style scoped>
.career-page { width: min(100% - 2rem, 65rem); }
.career-heading { display: grid; grid-template-columns: 1fr auto; gap: 1rem; align-items: end; margin-bottom: 1.45rem; }
.career-heading h1 { margin-bottom: .55rem; }
.career-back { display: inline-block; margin-bottom: .7rem; }
.career-heading p, .analysis-status p { color: var(--muted); line-height: 1.65; }
.career-resume { display: grid; gap: .3rem; padding: .65rem .85rem; font-size: .9rem; max-width: 17rem; overflow-wrap: anywhere; }
.career-page form { display: grid; gap: 1rem; }
.career-page > .notice { margin-bottom: 1rem; }
.analysis-status { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: .6rem; padding: .85rem 1rem; }
.directions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; min-width: 0; border: 0; padding: 0; margin: 0; }
.directions legend { margin-bottom: .6rem; font-weight: 700; padding: 0; }
.direction { display: grid; align-content: start; gap: .6rem; padding: 1rem; cursor: pointer; }
.direction:hover, .direction.selected { border-color: var(--primary); }
.direction.selected { background: #edf3e9; box-shadow: inset 0 0 0 1px var(--primary); }
.direction:has(:focus-visible) { outline: 3px solid #38764f; outline-offset: 2px; }
.direction-title { display: flex; justify-content: space-between; align-items: baseline; gap: .5rem; font-size: 1.05rem; }
.direction-title input { margin: 0; accent-color: var(--primary); }
.direction-description { color: var(--muted); line-height: 1.55; }
.direction-roles { font-size: .86rem; }
.direction-topics { display: flex; flex-wrap: wrap; gap: .35rem; }
.direction-topics span { background: #edf0e7; border-radius: .35rem; padding: .2rem .45rem; font-size: .78rem; }
.career-actions { display: flex; justify-content: space-between; flex-wrap: wrap; align-items: center; gap: .6rem; }
.career-actions a { font-size: .85rem; padding-block: .55rem; }
.career-actions button { min-height: 2.75rem; }
@media (max-width: 660px) {
  .directions, .career-heading { grid-template-columns: minmax(0, 1fr); }
  .career-resume { max-width: 100%; }
}
</style>
