<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import {
  confirmDiscardDraft, draftHasUnsavedChanges,
  type Education, type Experience, type ResumeContent, type ResumeDraft, type Skill,
} from '../resumeDraft'
import { account, forgetSession } from '../session'

type Row<T> = { key: number; value: T }

const router = useRouter()
const education = reactive<Education>({ school: '', major: '', degree: '', graduation: '' })
const experiences = ref<Row<Experience>[]>([])
const skills = ref<Row<Skill>[]>([])
const base = ref<ResumeDraft>()
const savedContent = ref('')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const conflict = ref(false)
let nextKey = 0

const limit = 30
const dateFormat = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
})

const content = computed<ResumeContent>(() => ({
  education: { ...education },
  experiences: experiences.value.map(({ value }) => value)
    .filter(({ title, context, contribution }) => [title, context, contribution].some((field) => field.trim())),
  skills: skills.value.map(({ value }) => value).filter(({ name }) => name.trim()),
}))
const fingerprint = computed(() => JSON.stringify(content.value))
const dirty = computed(() => Boolean(base.value) && fingerprint.value !== savedContent.value)
const status = computed(() => {
  if (saving.value) return '正在保存…'
  if (dirty.value) return '有未保存的修改'
  if (base.value?.updatedAt) return `已保存 · ${dateFormat.format(new Date(base.value.updatedAt))}`
  return '保存后可重新登录继续编辑'
})

function row<T>(value: T): Row<T> {
  return { key: nextKey++, value }
}

function newExperience(): Experience {
  return { type: 'COURSEWORK', title: '', context: '', contribution: '' }
}

async function addExperience() {
  if (experiences.value.length >= limit) return
  const item = row(newExperience())
  experiences.value.push(item)
  await nextTick()
  document.getElementById(`experience-title-${item.key}`)?.focus()
}

async function addSkill() {
  if (skills.value.length >= limit) return
  const item = row<Skill>({ name: '', proficiency: 'EXPOSURE' })
  skills.value.push(item)
  await nextTick()
  document.getElementById(`skill-name-${item.key}`)?.focus()
}

async function loadDraft() {
  if (loading.value || saving.value) return
  loading.value = true
  loadError.value = ''
  try {
    const draft = await api<ResumeDraft>('/resume/draft')
    if (draft.accountId !== account.value?.id) {
      throw new ApiError(403, '当前登录账号已变更，请刷新页面以打开当前账号的草稿。')
    }
    Object.assign(education, draft.content.education)
    const items = draft.content.experiences
    experiences.value = (items.length ? items : [newExperience()]).map((value) => row({ ...value }))
    skills.value = draft.content.skills.map((value) => row({ ...value }))
    savedContent.value = fingerprint.value
    base.value = draft
    saveError.value = ''
    conflict.value = false
  } catch (error) {
    if (error instanceof ApiError && error.status === 401 && !base.value) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      const target = base.value ? saveError : loadError
      target.value = errorMessage(error)
    }
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!base.value || !dirty.value || loading.value || saving.value || conflict.value) return
  const snapshot = fingerprint.value
  saving.value = true
  saveError.value = ''
  try {
    base.value = await api<ResumeDraft>('/resume/draft', 'PUT', {
      accountId: base.value.accountId,
      revision: base.value.revision,
      content: JSON.parse(snapshot),
    })
    savedContent.value = snapshot
  } catch (error) {
    conflict.value = error instanceof ApiError && error.status === 409
    saveError.value = error instanceof ApiError && error.status === 401
      ? '登录已失效。请在另一个标签页重新登录该账号，再回到这里保存。'
      : errorMessage(error)
  } finally {
    saving.value = false
  }
}

function reloadSavedDraft() {
  if (!dirty.value || window.confirm('加载已保存版本会放弃本页未保存的修改，确定继续吗？')) {
    void loadDraft()
  }
}

function warnBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(dirty, (value) => { draftHasUnsavedChanges.value = value }, { flush: 'sync' })
onBeforeRouteLeave(() => !account.value || confirmDiscardDraft())
onMounted(() => {
  window.addEventListener('beforeunload', warnBeforeUnload)
  void loadDraft()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', warnBeforeUnload)
  draftHasUnsavedChanges.value = false
})
</script>

<template>
  <main id="main" tabindex="-1" class="page resume-page">
    <header class="resume-heading">
      <div>
        <h1>简历制作</h1>
        <p>从一项真实的课程或作业开始，其他内容可以稍后补充。</p>
      </div>
      <RouterLink class="resume-workspace-link" :to="{ name: 'workspace' }">学生工作台</RouterLink>
    </header>

    <div v-if="!base" class="surface draft-placeholder">
      <p v-if="loading" role="status">正在加载草稿…</p>
      <template v-else-if="loadError">
        <p class="notice notice-error" role="alert">{{ loadError }}</p>
        <button class="button button-quiet" type="button" @click="loadDraft">重新加载</button>
      </template>
    </div>

    <div v-else class="resume-layout">
      <nav class="resume-nav" aria-label="简历编辑栏目">
        <a href="#experiences">课程与经历</a>
        <a href="#education">教育背景</a>
        <a href="#skills">技能</a>
      </nav>

      <form class="resume-form" @submit.prevent="saveDraft">
        <fieldset class="resume-fields" :disabled="saving || loading">
          <section id="experiences" class="surface resume-card" aria-labelledby="experiences-title">
            <div class="resume-card-heading">
              <h2 id="experiences-title">课程与经历</h2>
              <p>想一项你亲手完成的任务，写下当时做了什么、你负责哪一部分。</p>
            </div>

            <fieldset v-for="(item, index) in experiences" :key="item.key" class="resume-entry">
              <legend>经历 {{ index + 1 }}</legend>
              <div class="resume-field-grid">
                <div class="field">
                  <label :for="`experience-type-${item.key}`">经历类型</label>
                  <select :id="`experience-type-${item.key}`" v-model="item.value.type">
                    <option value="COURSEWORK">课程或作业</option>
                    <option value="PROJECT">项目</option>
                    <option value="INTERNSHIP">实习</option>
                    <option value="ACTIVITY">社团或活动</option>
                  </select>
                </div>
                <label class="field">
                  <span>经历名称</span>
                  <input :id="`experience-title-${item.key}`" v-model="item.value.title" maxlength="120"
                         :placeholder="item.value.type === 'COURSEWORK' ? '例如：数据库课程作业' : undefined" />
                </label>
              </div>
              <label class="field">
                <span>任务与背景</span>
                <textarea v-model="item.value.context" rows="2" maxlength="1000"
                          placeholder="这项任务要解决什么问题？" />
              </label>
              <label class="field">
                <span>你完成的部分</span>
                <textarea v-model="item.value.contribution" rows="3" maxlength="2000"
                          placeholder="你做了哪些具体工作？有哪些可以说明的结果？" />
              </label>
              <button class="button button-quiet resume-remove" type="button" :aria-label="`移除经历 ${index + 1}`"
                      @click="experiences.splice(index, 1)">移除这项经历</button>
            </fieldset>
            <button class="button button-quiet" type="button" :disabled="experiences.length >= limit" @click="addExperience">添加经历</button>
            <p v-if="experiences.length >= limit" class="help">每份草稿最多可记录 30 项经历。</p>
          </section>

          <section id="education" class="surface resume-card" aria-labelledby="education-title">
            <div class="resume-card-heading">
              <h2 id="education-title">教育背景</h2>
            </div>
            <div class="resume-field-grid">
              <label class="field"><span>学校</span><input v-model="education.school" maxlength="120" /></label>
              <label class="field"><span>专业</span><input v-model="education.major" maxlength="120" /></label>
              <label class="field"><span>学历</span><input v-model="education.degree" maxlength="30" placeholder="例如：本科" /></label>
              <label class="field"><span>毕业时间</span><input v-model="education.graduation" maxlength="30" placeholder="例如：2028 年 6 月" /></label>
            </div>
          </section>

          <section id="skills" class="surface resume-card" aria-labelledby="skills-title">
            <div class="resume-card-heading">
              <h2 id="skills-title">技能</h2>
              <p>记录实际用过的工具或技术，以及使用时需要的帮助。</p>
            </div>

            <fieldset v-for="(item, index) in skills" :key="item.key" class="resume-entry">
              <legend>技能 {{ index + 1 }}</legend>
              <div class="resume-field-grid">
                <label class="field">
                  <span>技能名称</span>
                  <input :id="`skill-name-${item.key}`" v-model="item.value.name" maxlength="80" placeholder="例如：SQL" />
                </label>
                <div class="field">
                  <label :for="`skill-proficiency-${item.key}`">使用程度</label>
                  <select :id="`skill-proficiency-${item.key}`" v-model="item.value.proficiency">
                    <option value="EXPOSURE">仅接触过</option>
                    <option value="GUIDED">在指导下用过</option>
                    <option value="INDEPENDENT">能独立使用</option>
                  </select>
                </div>
              </div>
              <button class="button button-quiet resume-remove" type="button" :aria-label="`移除技能 ${index + 1}`"
                      @click="skills.splice(index, 1)">移除这项技能</button>
            </fieldset>
            <button class="button button-quiet" type="button" :disabled="skills.length >= limit" @click="addSkill">添加技能</button>
            <p v-if="skills.length >= limit" class="help">每份草稿最多可记录 30 项技能。</p>
          </section>
        </fieldset>

        <div class="surface resume-save">
          <p v-if="saveError" class="notice notice-error" role="alert">{{ saveError }}</p>
          <div class="resume-save-row">
            <p role="status">{{ status }}</p>
            <button v-if="conflict" class="button button-quiet" type="button" :disabled="loading" @click="reloadSavedDraft">
              {{ loading ? '正在加载…' : '加载已保存版本' }}
            </button>
            <button v-else class="button button-primary" type="submit" :disabled="!dirty || saving || loading">
              {{ saving ? '正在保存…' : '保存草稿' }}
            </button>
          </div>
        </div>
      </form>
    </div>
  </main>
</template>

<style scoped>
.resume-page { margin-top: clamp(1.75rem, 4vw, 3.25rem); }
.resume-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  flex-wrap: wrap;
  gap: .75rem 1.5rem;
  margin-bottom: 1.5rem;
}
.resume-heading h1 { margin-bottom: .5rem; }
.resume-heading p, .resume-card-heading p { color: var(--muted); line-height: 1.55; }
.resume-workspace-link { display: inline-flex; align-items: center; min-height: 44px; }

.resume-layout { display: grid; grid-template-columns: 11rem minmax(0, 1fr); gap: 1.5rem; }
.resume-nav { align-self: start; position: sticky; top: 1.25rem; display: grid; gap: .25rem; }
.resume-nav a {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: .5rem .75rem;
  border-radius: .45rem;
  font-weight: 650;
  text-decoration: none;
}
.resume-nav a:hover, .resume-nav a:focus-visible { background: #e8eee3; }
.resume-layout:has(#experiences:target) a[href="#experiences"],
.resume-layout:has(#education:target) a[href="#education"],
.resume-layout:has(#skills:target) a[href="#skills"] { background: #e8eee3; }
.resume-form, .resume-fields, .resume-card, .resume-entry { display: grid; gap: 1rem; }
.resume-fields { padding: 0; margin: 0; min-width: 0; border: 0; }
.resume-card, .draft-placeholder { padding: clamp(1rem, 3vw, 1.5rem); }
.resume-card { scroll-margin-top: 1rem; }
.resume-card-heading p { margin-top: .4rem; }
.resume-card > button, .resume-remove { justify-self: start; }
.resume-entry {
  min-width: 0;
  margin: 0;
  padding: .75rem 1rem 1rem;
  border: 1px solid var(--outline);
  border-radius: .65rem;
}
.resume-entry legend { padding-inline: .35rem; font-weight: 650; }
.resume-field-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem 1rem; }
.resume-card .field { font-size: .95rem; }
.resume-card .field :is(input, select, textarea) { font-size: 1rem; }
.resume-card textarea { resize: vertical; line-height: 1.5; }

.resume-save {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: grid;
  gap: .65rem;
  padding: .75rem 1rem max(.75rem, env(safe-area-inset-bottom));
  box-shadow: 0 -4px 20px #19312a0c;
}
.resume-save-row { display: flex; align-items: center; justify-content: space-between; gap: .65rem; }
.resume-save-row p { color: var(--muted); font-size: .875rem; }
.resume-save-row button { flex-shrink: 0; }
.draft-placeholder > .button { margin-top: .75rem; }

@media (max-width: 680px) {
  .resume-layout { grid-template-columns: minmax(0, 1fr); gap: .75rem; }
  .resume-nav { position: static; display: flex; flex-wrap: wrap; }
  .resume-heading { margin-bottom: .75rem; }
  .resume-field-grid { grid-template-columns: minmax(0, 1fr); }
  .resume-entry { padding-inline: .75rem; }
}
</style>
