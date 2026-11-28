<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import { testedMarkLabels, type SkillSession } from '../career'
import MentorChat from '../components/MentorChat.vue'
import { account, forgetSession } from '../session'

const route = useRoute()
const router = useRouter()
const session = ref<SkillSession>()
const answer = ref('')
const compose = ref<HTMLTextAreaElement>()
const message = ref('')
const loading = ref(false)
const busy = ref(false)
const id = computed(() => typeof route.params.id === 'string' ? route.params.id : '')
const testing = computed(() => session.value?.phase === 'TEST')
const learning = computed(() => session.value?.phase === 'LEARNING')
const readOnly = computed(() => !learning.value && session.value?.phase !== 'DONE')
const counts = computed(() => {
  const values = Object.values(session.value?.marks ?? {})
  return {
    GREEN: values.filter(mark => mark === 'GREEN').length,
    YELLOW: values.filter(mark => mark === 'YELLOW').length,
    RED: values.filter(mark => mark === 'RED').length,
  }
})
let generation = 0

async function failure(error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    await expireSession()
  } else {
    message.value = errorMessage(error)
  }
}

async function expireSession() {
  forgetSession()
  await router.replace({ name: 'login' })
}

async function load() {
  const current = ++generation
  loading.value = true
  busy.value = false
  message.value = ''
  try {
    if (!/^[1-9][0-9]*$/.test(id.value)) throw new ApiError(404, '找不到技能核验旁聊。')
    const result = await api<SkillSession | undefined>(`/career/assessment/${id.value}/skills`)
    if (current !== generation) return
    if (!result) {
      await router.replace({ name: 'career-plan', params: { id: id.value } })
      return
    }
    session.value = result
    if (result.phase === 'PREPARING') void step()
  } catch (error) {
    if (generation === current) await failure(error)
  } finally {
    if (generation === current) loading.value = false
  }
}

async function step(text?: string) {
  if (busy.value || !session.value || !account.value || (testing.value && !text?.trim())) return
  const current = generation
  busy.value = true
  message.value = ''
  try {
    const updated = await api<SkillSession>(`/career/assessment/${id.value}/skills/step`, 'POST', {
      accountId: account.value.id, revision: session.value.revision, answer: text,
    })
    if (current !== generation) return
    session.value = updated
    answer.value = ''
    if (updated.phase === 'TEST') void nextTick(() => compose.value?.focus())
  } catch (error) {
    if (current === generation) await failure(error)
  } finally {
    if (current === generation) busy.value = false
  }
}

function onEnter(event: KeyboardEvent) {
  if (event.isComposing || event.shiftKey || event.keyCode === 229) return
  event.preventDefault()
  if (!event.repeat) void step(answer.value)
}

watch(id, () => {
  session.value = undefined
  answer.value = ''
  void load()
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <main id="main" tabindex="-1" class="page skill-session">
    <nav aria-label="技能路线导航">
      <RouterLink :to="{ name: 'career-plan', params: { id } }">返回学习路线</RouterLink>
      <RouterLink :to="{ name: 'workspace' }">工作台</RouterLink>
    </nav>
    <header>
      <h1>{{ learning || session?.phase === 'DONE' ? 'Mentor 带学' : 'Mentor 技能核验' }}</h1>
      <p v-if="testing && session">
        第 {{ session.grades.length + 1 }} / {{ session.testCount }} 项 · {{ session.labels?.[session.currentTopic ?? ''] ?? session.currentTopic }}
      </p>
      <p v-else-if="learning && session">
        第 {{ session.lessonIndex + 1 }} / {{ session.studyTopics.length }} 项 · {{ session.labels?.[session.currentTopic ?? ''] ?? session.currentTopic }}
      </p>
    </header>
    <p v-if="loading" role="status">正在读取旁聊…</p>
    <template v-else-if="session && account">
      <section v-if="session.phase === 'REPORT' || learning || session.phase === 'DONE'" class="surface report">
        <details :open="session.phase === 'REPORT'">
          <summary><strong>技能核验报告</strong> · 已核验 {{ session.grades.length }} 项</summary>
          <div class="report-content">
            <p>已掌握 <strong>{{ counts.GREEN }}</strong> · 部分掌握 <strong>{{ counts.YELLOW }}</strong> · 待学 <strong>{{ counts.RED }}</strong></p>
            <p v-if="Object.keys(session.marks).length > session.grades.length">原自评红项共 {{ Object.keys(session.marks).length - session.grades.length }} 项，直接加入待学路线。</p>
            <ul v-if="session.grades.length" class="grades">
              <li v-for="grade in session.grades" :key="grade.topic">
                <div class="grade-title">
                  <strong>{{ session.labels?.[grade.topic] ?? grade.topic }}</strong>
                  <span :data-mark="grade.mark">{{ testedMarkLabels[grade.mark] }} · {{ grade.score }}/100</span>
                </div>
                <p>{{ grade.feedback }}</p>
              </li>
            </ul>
          </div>
        </details>
        <template v-if="session.phase === 'REPORT'">
          <p v-if="session.studyTopics.length">下一项 · <strong>{{ session.labels?.[session.studyTopics[0]!] ?? session.studyTopics[0] }}</strong></p>
          <p v-else>这条路线的技术点均已通过核验。</p>
          <button class="button button-primary" type="button" :disabled="busy" @click="step()">
            {{ busy ? 'Mentor 正在准备…' : session.studyTopics.length ? '开始第一项带学' : '完成核验' }}
          </button>
        </template>
      </section>

      <section v-if="session.phase !== 'PREPARING'" class="surface chat">
        <MentorChat :key="`${id}:${session.revision}`" :account-id="account.id"
                    :side-id="session.sideId" :read-only="readOnly" @expired="expireSession" />
        <form v-if="testing" class="answer-form" @submit.prevent="step(answer)">
          <label class="field">
            <span>你的回答</span>
            <textarea id="skill-answer" ref="compose" v-model="answer" rows="3" maxlength="8000" :disabled="busy"
                    placeholder="可以描述思路、步骤或直接写代码" @keydown.enter="onEnter"></textarea>
          </label>
          <div class="answer-actions">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <button class="button button-quiet" type="button" :disabled="busy" @click="step('这道题我暂时不会。')">这题不会</button>
            <button class="button button-primary" type="submit" :disabled="busy || !answer.trim()">
              {{ busy ? 'Mentor 正在核验…' : '提交回答' }}
            </button>
          </div>
        </form>
      </section>
      <p v-else-if="busy" role="status">Mentor 正在出第一题…</p>
      <button v-else class="button button-primary" type="button" @click="step()">生成第一题</button>

      <div v-if="learning" class="lesson-actions">
        <p v-if="session.studyTopics[session.lessonIndex + 1]">下一项 · {{ session.labels?.[session.studyTopics[session.lessonIndex + 1]!] ?? session.studyTopics[session.lessonIndex + 1] }}</p>
        <button type="button" class="button button-primary" :disabled="busy" @click="step()">
          {{ busy ? '正在准备…' : session.lessonIndex + 1 === session.studyTopics.length ? '完成本轮学习' : '完成本项，继续' }}
        </button>
      </div>
    </template>
    <div v-if="message" class="session-error">
      <p class="notice notice-error" role="alert">{{ message }}</p>
      <button class="button button-quiet" type="button" :disabled="busy" @click="load">刷新进度</button>
    </div>
  </main>
</template>

<style scoped>
.skill-session { width: min(100% - 2rem, 54rem); display: grid; gap: 1rem; }
.skill-session nav, .answer-actions, .lesson-actions, .grade-title { display: flex; align-items: center; flex-wrap: wrap; gap: .65rem 1rem; }
.skill-session header h1 { margin: .2rem 0 .4rem; }
.skill-session header p, .report p, .lesson-actions p, .answer-actions span { color: var(--muted); line-height: 1.55; }
.chat { overflow: hidden; }
.answer-form, .report, .session-error { display: grid; gap: .65rem; padding: .85rem 1rem; }
.answer-form { border-top: 1px solid var(--outline); }
.answer-form textarea { width: 100%; resize: vertical; line-height: 1.55; }
.answer-actions span { margin-right: auto; font-size: .83rem; }
.report summary { cursor: pointer; }
.report-content { padding-top: .6rem; display: grid; gap: .35rem; }
.report .button { justify-self: start; }
.grades { list-style: none; margin: .45rem 0 0; padding: 0; }
.grades li { border-top: 1px solid var(--outline); padding: .45rem 0; }
.grade-title span { border-radius: .35rem; padding: .15rem .4rem; font-size: .85rem; }
.grade-title [data-mark=GREEN] { color: #20583b; background: #e3f0e3; }
.grade-title [data-mark=YELLOW] { color: #6a4c0b; background: #fff1cb; }
.grade-title [data-mark=RED] { color: #8d3933; background: #fae8e4; }
.grades p { margin-top: .25rem; font-size: .92rem; }
.lesson-actions { justify-content: space-between; }
.session-error { justify-items: start; padding: 0; }
</style>
