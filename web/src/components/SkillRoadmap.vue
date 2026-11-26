<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import { selfMarkLabels, testedMarkLabels, type CareerReport, type SkillMark, type SkillSession } from '../career'
import { account, forgetSession } from '../session'

const props = defineProps<{
  assessmentId: string
  route: NonNullable<CareerReport['route']>
  suggestions: CareerReport['topicSuggestions']
  session: SkillSession | undefined
}>()

const router = useRouter()
const allTopics = props.route.phases.flatMap(phase => phase.topics)
const marks = ref<Record<string, SkillMark>>(Object.fromEntries(
  allTopics.map(topic => [topic, props.suggestions?.[topic]?.mark ?? 'RED']),
))
const gradesByTopic = computed(() => new Map(props.session?.grades.map(grade => [grade.topic, grade])))
const marksShown = computed(() => props.session?.marks ?? marks.value)
const testCount = computed(() => allTopics.filter(topic => marks.value[topic] !== 'RED').length)
const hasReport = computed(() => props.session && !['PREPARING', 'TEST'].includes(props.session.phase))
const saving = ref(false)
const message = ref('')
let active = true
onBeforeUnmount(() => { active = false })

async function start() {
  if (saving.value || !account.value) return
  saving.value = true
  message.value = ''
  try {
    await api<SkillSession>(`/career/assessment/${props.assessmentId}/skills`, 'POST', {
      accountId: account.value.id, marks: marks.value,
    })
    if (active) await router.push({ name: 'career-skills', params: { id: props.assessmentId } })
  } catch (error) {
    if (!active) return
    if (error instanceof ApiError && error.status === 401) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      message.value = errorMessage(error)
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section aria-labelledby="full-roadmap" class="full-roadmap">
    <header class="roadmap-intro">
      <div>
        <h2 id="full-roadmap">完整技术路线 <span>{{ allTopics.length }} 项</span></h2>
        <p v-if="!session">AI 根据简历给了初始标记。请逐项确认你实际会什么，再让 Mentor 核验绿项和黄项。</p>
        <p v-else-if="hasReport">已核验的技能按 Mentor 的评分更新。红色自评项保留为待学。</p>
        <p v-else>已保存你的自评。继续核验时，Mentor 会按顺序逐项提问。</p>
      </div>
      <a :href="route.sourceUrl" target="_blank" rel="noopener noreferrer">黑马公开课程</a>
    </header>
    <div class="status-key" aria-label="自评状态">
      <span v-for="(label, mark) in selfMarkLabels" :key="mark" :data-mark="mark">{{ label }}</span>
    </div>
    <section v-for="phase in route.phases" :key="phase.title" class="surface phase">
      <h3>{{ phase.title }}</h3>
      <ul>
        <li v-for="topic in phase.topics" :key="topic" class="topic">
          <div>
            <h4>{{ topic }}</h4>
            <template v-if="gradesByTopic.has(topic)">
              <p>{{ gradesByTopic.get(topic)?.feedback }}</p>
            </template>
            <details v-else-if="suggestions?.[topic] && !session">
              <summary>AI 初评 · {{ suggestions[topic]?.basis }}</summary>
              <blockquote>{{ suggestions[topic]?.quote }}</blockquote>
            </details>
          </div>
          <div v-if="!session" class="mark-controls" role="group" :aria-label="topic + ' 的自评'">
            <button v-for="(label, mark) in selfMarkLabels" :key="mark" type="button" :data-mark="mark"
                    :aria-pressed="marks[topic] === mark" :disabled="saving" @click="marks[topic] = mark">{{ label }}</button>
          </div>
          <div v-else class="saved-mark">
            <span :data-mark="marksShown[topic]" class="chosen">
              {{ gradesByTopic.has(topic) ? testedMarkLabels[marksShown[topic]!] : selfMarkLabels[marksShown[topic]!] }}
            </span>
            <small v-if="gradesByTopic.has(topic)">{{ gradesByTopic.get(topic)?.score }}/100</small>
            <small v-else>{{ hasReport ? '未核验' : '自评' }}</small>
          </div>
        </li>
      </ul>
    </section>
    <footer class="surface roadmap-action">
      <div v-if="!session">
        <strong>已选 {{ testCount }} 项待核验</strong>
        <p>{{ testCount ? '绿项和黄项各一道实操题。红项直接进入待学路线。' : '全部为红项。确认后可直接开始带学。' }}</p>
        <p v-if="message" class="notice notice-error" role="alert">{{ message }}</p>
      </div>
      <RouterLink v-if="session" class="button button-primary" :to="{ name: 'career-skills', params: { id: assessmentId } }">
        {{ hasReport ? '打开核验报告与带学旁聊' : '继续技能核验' }}
      </RouterLink>
      <button v-else class="button button-primary" type="button" :disabled="saving" @click="start">
        {{ saving ? '正在保存…' : testCount ? '确认自评，开始核验' : '确认自评，打开旁聊' }}
      </button>
    </footer>
  </section>
</template>

<style scoped>
.full-roadmap { display: grid; gap: .85rem; margin-top: 1.5rem; }
.roadmap-intro, .roadmap-action, .topic { display: flex; justify-content: space-between; align-items: center; gap: .8rem 1.2rem; }
.roadmap-intro { flex-wrap: wrap; }
.roadmap-intro h2 { margin-bottom: .35rem; }
.roadmap-intro h2 span { margin-left: .45rem; color: var(--muted); font-weight: 500; font-size: .9rem; }
.roadmap-intro p, .roadmap-action p, .topic p, .topic details, .topic small { color: var(--muted); line-height: 1.55; }
.roadmap-action p { margin-top: .2rem; }
.status-key, .mark-controls, .saved-mark { display: flex; align-items: center; gap: .35rem; flex-wrap: wrap; }
.status-key span, .chosen, .mark-controls button { border-radius: .45rem; padding: .35rem .62rem; font-size: .86rem; line-height: 1.35; border: 1px solid var(--outline); }
[data-mark=GREEN] { --mark-ink: #20583b; --mark-bg: #e3f0e3; --mark-line: #92b99b; }
[data-mark=YELLOW] { --mark-ink: #6a4c0b; --mark-bg: #fff1cb; --mark-line: #d6b56a; }
[data-mark=RED] { --mark-ink: #8d3933; --mark-bg: #fae8e4; --mark-line: #d9a198; }
.status-key span, .chosen, .mark-controls button[aria-pressed=true] { color: var(--mark-ink); background: var(--mark-bg); border-color: var(--mark-line); }
.mark-controls { flex-shrink: 0; }
.mark-controls button { min-height: 2.35rem; color: var(--ink); background: transparent; cursor: pointer; }
.mark-controls button:not([aria-pressed=true]):hover { background: #f0f1e9; }
.mark-controls button[aria-pressed=true] { font-weight: 700; }
.phase, .roadmap-action { padding: .9rem clamp(.8rem, 3vw, 1.25rem); }
.phase h3 { margin: .25rem 0 .6rem; font-size: 1.1rem; }
.phase ul { list-style: none; margin: 0; padding: 0; }
.topic { padding: .55rem 0; border-top: 1px solid var(--outline); }
.topic h4 { margin: 0; font-size: .99rem; }
.topic details, .topic p { margin-top: .22rem; font-size: .83rem; }
.topic summary { cursor: pointer; overflow-wrap: anywhere; }
.topic blockquote { margin: .4rem 0 0; padding-left: .55rem; border-left: 2px solid var(--outline); }
.roadmap-action { position: sticky; bottom: .5rem; flex-wrap: wrap; box-shadow: 0 4px 20px #1b382514; }
@media (max-width: 39rem) {
  .topic { align-items: start; flex-direction: column; gap: .4rem; }
  .roadmap-action { position: static; }
  .mark-controls button { min-height: 2.75rem; }
}
</style>
