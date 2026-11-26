<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import type { CareerAssessment, CareerSkill, SkillSession } from '../career'
import SkillRoadmap from '../components/SkillRoadmap.vue'
import { forgetSession } from '../session'

const labels: Record<CareerSkill['level'], string> = {
  PROJECT: '简历有实践证据',
  INFERRED: '已掌握（前置推断）',
  MENTION: '仅提及，待核验',
  MISSING: '简历未体现',
  UNKNOWN: '待核对',
}

const route = useRoute()
const router = useRouter()
const assessment = ref<CareerAssessment>()
const session = ref<SkillSession>()
const report = computed(() => assessment.value?.report)
const interview = computed(() => !report.value?.route && report.value?.stage === 'INTERVIEW_READY')
const loading = ref(false)
const message = ref('')
let request = 0

async function load() {
  const id = route.params.id
  const generation = ++request
  loading.value = true
  message.value = ''
  assessment.value = undefined
  session.value = undefined
  try {
    if (typeof id !== 'string' || !/^[1-9][0-9]*$/.test(id)) throw new ApiError(400, '分析编号无效。')
    const result = await api<CareerAssessment>(`/career/assessment/${encodeURIComponent(id)}`)
    if (generation !== request) return
    if (!result.report) {
      await router.replace({ name: 'career-direction' })
      return
    }
    if (result.report.route) {
      const saved = await api<SkillSession | undefined>(`/career/assessment/${id}/skills`)
      if (generation !== request) return
      session.value = saved
    }
    assessment.value = result
  } catch (error) {
    if (generation !== request) return
    if (error instanceof ApiError && error.status === 401) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      message.value = error instanceof ApiError && error.status === 404 ? '找不到该分析结果。' : errorMessage(error)
    }
  } finally {
    if (generation === request) loading.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })
onBeforeUnmount(() => { request++ })
</script>

<template>
  <main id="main" tabindex="-1" class="page career-result">
    <RouterLink :to="{ name: 'workspace' }">返回工作台</RouterLink>
    <p v-if="loading" role="status" class="result-feedback">正在读取匹配结果…</p>
    <div v-else-if="message" class="result-feedback">
      <p class="notice notice-error" role="alert">{{ message }}</p>
      <button class="button button-quiet" type="button" @click="load">重新加载</button>
    </div>

    <template v-else-if="assessment && report">
      <header class="career-result-heading">
        <div>
          <p>{{ assessment.directionTitle }}</p>
          <h1>{{ interview ? '模拟面试准备' : '匹配分析与学习路线' }}</h1>
          <p v-if="interview">匹配度和核心技能已达到面试准备标准。</p>
          <p v-else-if="!report.route">从待补的技能开始，每一步都有一个可以交给 Mentor 核对的小任务。</p>
        </div>
        <div class="result-actions">
          <RouterLink v-if="assessment.needsReanalysis" class="button button-primary"
                      :to="{ name: 'career-direction', query: { reanalyze: assessment.directionId } }">使用新版 AI 分析</RouterLink>
          <RouterLink class="button button-quiet"
                      :to="{ name: 'career-direction', query: { change: '1' } }">更换方向或重新分析</RouterLink>
        </div>
      </header>

      <section class="surface match-summary" aria-labelledby="match-heading">
        <div class="match-score">
          <h2 id="match-heading">岗位方向匹配度</h2>
          <strong v-if="report.matchPercent !== null">{{ report.matchPercent }}%</strong>
          <strong v-else class="unknown-score">暂未评分</strong>
          <progress v-if="report.matchPercent !== null" aria-label="岗位方向匹配度" :value="report.matchPercent" max="100"></progress>
        </div>
        <div class="match-details">
          <RouterLink :to="{ name: 'resume-final', query: { version: String(assessment.resumeVersion) } }">
            查看本次分析使用的简历第 {{ assessment.resumeVersion }} 版
          </RouterLink>
          <p v-if="report.sourceQuality === 'UNREADABLE'">当前原件无可读正文，已提供基础学习路线。可以补交清晰或带文本的简历后重评。</p>
          <p v-else-if="report.route">这是简历初评。逐项核验会更新技术状态，并给出带学顺序。</p>
          <p v-else>直接实践和推断已掌握的前置技能计全分，仅提及计半分。达到 {{ report.interviewThreshold }}% 且核心技能已覆盖时进入面试准备。</p>
          <p v-if="report.sourceQuality === 'OCR'">分析参考扫描件识别文本，引用可与原件核对。</p>
        </div>
      </section>

      <SkillRoadmap v-if="report.route" :key="assessment.id" :assessment-id="assessment.id"
                    :route="report.route" :suggestions="report.topicSuggestions" :session="session" />

      <div v-else class="result-grid">
        <section class="surface result-panel" aria-labelledby="skills-heading">
          <h2 id="skills-heading">技术栈与证据</h2>
          <ul class="skills">
            <li v-for="skill in report.skills" :key="skill.skillId">
              <div class="skill-title">
                <h3>{{ skill.title }}</h3>
                <span v-if="skill.core" class="core-label">核心</span>
                <span class="core-label">权重 {{ skill.weight }} 分</span>
                <span class="skill-level" :data-level="skill.level">{{ labels[skill.level] }}</span>
              </div>
              <p v-if="skill.inference">{{ skill.inference }}</p>
              <blockquote v-if="skill.quote">{{ skill.quote }}</blockquote>
            </li>
          </ul>
        </section>
        <section class="surface result-panel" aria-labelledby="roadmap-heading">
          <h2 id="roadmap-heading">{{ interview ? '面试前可以补强' : '接下来的学习路线' }}</h2>
          <p v-if="!report.roadmap.length">可以先回顾核心项目、职责和技术取舍。</p>
          <ol v-else class="roadmap">
            <li v-for="step in report.roadmap" :key="step.skillId">
              <h3>{{ step.title }}</h3>
              <p>{{ step.objective }}</p>
              <p><strong>练习</strong> {{ step.task }}</p>
            </li>
          </ol>
          <RouterLink class="button button-primary open-chat" :to="{ name: 'workspace' }">
            {{ interview ? '返回工作台' : '继续与 Mentor 学习' }}
          </RouterLink>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.career-result { width: min(100% - 2rem, 65rem); }
.career-result-heading { display: flex; justify-content: space-between; align-items: end; flex-wrap: wrap; gap: .75rem; margin: 1rem 0; }
.career-result-heading h1 { margin: .3rem 0 .55rem; }
.career-result-heading p, .match-details p, .result-panel p { color: var(--muted); line-height: 1.65; }
.career-result-heading .button, .open-chat { display: inline-flex; align-items: center; text-decoration: none; }
.result-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.match-summary { display: grid; grid-template-columns: minmax(12rem, .35fr) 1fr; gap: 1rem 2rem; padding: clamp(.85rem, 3vw, 1.3rem); }
.match-score, .match-details { display: grid; gap: .5rem; }
.match-details { align-content: center; }
.match-score h2, .result-panel h2 { font-size: 1.15rem; }
.match-score > strong { line-height: 1; font-size: 2.25rem; color: var(--primary); }
.match-score > .unknown-score { font-size: 1.45rem; }
.match-score progress { width: 100%; height: .7rem; accent-color: var(--primary); }
.result-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); align-items: start; gap: .85rem; margin-top: .9rem; }
.result-panel { display: grid; gap: .65rem; padding: clamp(.85rem, 3vw, 1.2rem); }
.skills, .roadmap { display: grid; gap: .65rem; margin: 0; }
.skills { padding: 0; list-style: none; }
.roadmap { padding-inline-start: 1.35rem; }
.skills li, .roadmap li { border-top: 1px solid var(--outline); padding-top: .65rem; }
.skill-title { display: flex; align-items: baseline; flex-wrap: wrap; gap: .25rem .5rem; }
.result-panel h3 { font-size: 1rem; margin: 0; }
.core-label, .skill-level { font-size: .78rem; color: var(--muted); }
.skill-level:is([data-level="PROJECT"], [data-level="INFERRED"]) { color: var(--primary); }
.skill-level[data-level="MENTION"] { color: #79512b; }
.skills blockquote { margin: .35rem 0 0; border-inline-start: 2px solid var(--outline); padding-inline-start: .55rem; color: var(--muted); overflow-wrap: anywhere; }
.roadmap p { margin-top: .3rem; }
.open-chat { justify-self: start; margin-top: .15rem; }
.result-feedback { display: grid; justify-items: start; gap: .55rem; margin-top: 1rem; }
@media (max-width: 700px) {
  .result-grid, .match-summary { grid-template-columns: minmax(0, 1fr); }
}
</style>
