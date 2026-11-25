<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import ResumePreview from '../components/ResumePreview.vue'
import {
  confirmDiscardDraft, draftHasUnsavedChanges,
  type Education, type Experience, type Profile, type ResumeContent, type ResumeDraft, type ResumeSubmission, type Skill,
} from '../resumeDraft'
import { account, forgetSession } from '../session'

type Row<T> = { key: number; value: T }
type Panel = 'basics' | 'experiences' | 'skills'

const panels: { id: Panel; label: string }[] = [
  { id: 'basics', label: '基本资料' },
  { id: 'experiences', label: '经历' },
  { id: 'skills', label: '技能' },
]
const limit = 30
const router = useRouter()
const profile = reactive<Profile>({ name: '', email: '', phone: '' })
const education = reactive<Education>({ school: '', major: '', degree: '', graduation: '' })
const experiences = ref<Row<Experience>[]>([])
const skills = ref<Row<Skill>[]>([])
const base = ref<ResumeDraft>()
const savedContent = ref('')
const panel = ref<Panel>('basics')
const mobileView = ref<'edit' | 'preview'>('edit')
const loading = ref(false)
const action = ref<'save' | 'submit' | null>(null)
const loadError = ref('')
const saveError = ref('')
const conflict = ref(false)
const checkBasics = ref(false)
let nextKey = 0

const content = computed<ResumeContent>(() => ({
  profile: { ...profile },
  education: { ...education },
  experiences: experiences.value.map(({ value }) => value)
    .filter(({ title, context, contribution }) => [title, context, contribution].some((field) => field.trim())),
  skills: skills.value.map(({ value }) => value).filter(({ name }) => name.trim()),
}))
const fingerprint = computed(() => JSON.stringify(content.value))
const dirty = computed(() => Boolean(base.value) && fingerprint.value !== savedContent.value)
const submitted = computed(() => Boolean(base.value?.submission)
  && base.value?.submission?.draftRevision === base.value?.revision && !dirty.value)
const nameInvalid = computed(() => checkBasics.value && !profile.name.trim())
const schoolInvalid = computed(() => checkBasics.value && !education.school.trim())
const status = computed(() => {
  if (action.value === 'submit') return '正在提交…'
  if (action.value === 'save') return '正在保存…'
  if (dirty.value) return '有未保存的修改'
  if (submitted.value) return `已提交第 ${base.value?.submission?.version} 版`
  if (base.value?.updatedAt) return '草稿已保存'
  return '新简历'
})

function row<T>(value: T): Row<T> {
  return { key: nextKey++, value }
}

async function addExperience() {
  if (experiences.value.length >= limit) return
  const item = row<Experience>({ type: 'COURSEWORK', title: '', context: '', contribution: '' })
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
  if (loading.value || action.value) return
  loading.value = true
  loadError.value = ''
  try {
    const draft = await api<ResumeDraft>('/resume/draft')
    if (draft.accountId !== account.value?.id) {
      throw new ApiError(403, '当前登录账号已变更，请刷新页面以打开当前账号的草稿。')
    }
    Object.assign(profile, draft.content.profile)
    Object.assign(education, draft.content.education)
    experiences.value = draft.content.experiences.map((value) => row({ ...value }))
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

async function writeResume(nextAction: 'save' | 'submit') {
  if (!base.value || loading.value || action.value || conflict.value) return
  if (nextAction === 'save' && !dirty.value) return
  if (nextAction === 'submit') {
    if (submitted.value) return
    checkBasics.value = true
    if (nameInvalid.value || schoolInvalid.value) {
      panel.value = 'basics'
      mobileView.value = 'edit'
      await nextTick()
      document.getElementById(nameInvalid.value ? 'resume-name' : 'resume-school')?.focus()
      return
    }
  }
  const snapshot = fingerprint.value
  action.value = nextAction
  saveError.value = ''
  try {
    if (dirty.value) {
      base.value = await api<ResumeDraft>('/resume/draft', 'PUT', {
        accountId: base.value.accountId,
        revision: base.value.revision,
        content: JSON.parse(snapshot),
      })
      savedContent.value = snapshot
    }
    if (nextAction === 'submit') {
      base.value.submission = await api<ResumeSubmission>('/resume/submit', 'POST', {
        accountId: base.value.accountId,
        revision: base.value.revision,
      })
    }
  } catch (error) {
    conflict.value = error instanceof ApiError && error.status === 409
    saveError.value = error instanceof ApiError && error.status === 401
      ? '登录已失效。请在另一个标签页重新登录该账号，再回到这里继续。'
      : errorMessage(error)
  } finally {
    action.value = null
  }
}

function reloadSavedDraft() {
  if (confirmDiscardDraft()) void loadDraft()
}

function warnBeforeUnload(event: BeforeUnloadEvent) {
  event.preventDefault()
  event.returnValue = ''
}

watch(dirty, (value) => {
  draftHasUnsavedChanges.value = value
  if (value) window.addEventListener('beforeunload', warnBeforeUnload)
  else window.removeEventListener('beforeunload', warnBeforeUnload)
}, { flush: 'sync' })
onBeforeRouteLeave(() => !account.value || confirmDiscardDraft())
onMounted(loadDraft)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', warnBeforeUnload)
  draftHasUnsavedChanges.value = false
})
</script>

<template>
  <main id="main" tabindex="-1" class="resume-page">
    <header class="resume-header">
      <div>
        <RouterLink class="resume-back" :to="{ name: 'workspace' }">学生工作台</RouterLink>
        <h1>简历制作</h1>
        <p>填写姓名和学校即可提交，经历和技能可以以后补充。</p>
      </div>
      <div v-if="base" class="resume-actions">
        <p role="status" class="resume-status">{{ status }}</p>
        <button class="button button-quiet" type="button" :disabled="!dirty || Boolean(action) || loading || conflict"
                @click="writeResume('save')">保存草稿</button>
        <button class="button button-primary" type="button" :disabled="submitted || Boolean(action) || loading || conflict"
                @click="writeResume('submit')">{{ submitted ? '已提交' : '提交简历' }}</button>
      </div>
    </header>

    <div v-if="!base" class="surface resume-placeholder">
      <p v-if="loading" role="status">正在加载简历…</p>
      <template v-else-if="loadError">
        <p class="notice notice-error" role="alert">{{ loadError }}</p>
        <button class="button button-quiet" type="button" @click="loadDraft">重新加载</button>
      </template>
    </div>

    <template v-else>
      <div v-if="saveError" class="resume-feedback">
        <p class="notice notice-error" role="alert">{{ saveError }}</p>
        <button v-if="conflict" class="button button-quiet" type="button" :disabled="loading" @click="reloadSavedDraft">
          {{ loading ? '正在加载…' : '加载已保存版本' }}
        </button>
      </div>

      <div class="mobile-controls" role="group" aria-label="简历视图">
        <button type="button" :aria-pressed="mobileView === 'edit'" @click="mobileView = 'edit'">编辑</button>
        <button type="button" :aria-pressed="mobileView === 'preview'" @click="mobileView = 'preview'">预览</button>
      </div>

      <div class="resume-studio" :data-mobile-view="mobileView">
        <section class="surface resume-editor" aria-label="编辑简历">
          <nav class="resume-panels" aria-label="简历栏目">
            <button v-for="item in panels" :key="item.id" type="button" :aria-pressed="panel === item.id"
                    @click="panel = item.id">{{ item.label }}</button>
          </nav>
          <form novalidate @submit.prevent="writeResume('save')">
            <fieldset class="editor-fields" :disabled="Boolean(action) || loading">
              <section v-if="panel === 'basics'" class="editor-content" aria-labelledby="basics-heading">
                <div class="panel-heading">
                  <h2 id="basics-heading">基本资料</h2>
                  <p>姓名和学校会显示在简历首页。</p>
                </div>

                <div class="field">
                  <label for="resume-name">姓名 <span class="required-label">必填</span></label>
                  <input id="resume-name" v-model="profile.name" autocomplete="name" maxlength="80"
                         placeholder="你的姓名" required :aria-invalid="nameInvalid"
                         :aria-describedby="nameInvalid ? 'resume-name-error' : undefined" />
                  <span v-if="nameInvalid" id="resume-name-error" class="field-error">请填写姓名</span>
                </div>
                <div class="field">
                  <label for="resume-school">学校 <span class="required-label">必填</span></label>
                  <input id="resume-school" v-model="education.school" maxlength="120"
                         placeholder="学校全称" required :aria-invalid="schoolInvalid"
                         :aria-describedby="schoolInvalid ? 'resume-school-error' : undefined" />
                  <span v-if="schoolInvalid" id="resume-school-error" class="field-error">请填写学校</span>
                </div>

                <details class="optional-fields">
                  <summary>补充教育及联系方式 <span>选填</span></summary>
                  <div class="optional-grid">
                    <label class="field"><span>专业</span><input v-model="education.major" maxlength="120" /></label>
                    <label class="field"><span>学历</span><input v-model="education.degree" maxlength="30" /></label>
                    <label class="field full-width"><span>毕业时间</span><input v-model="education.graduation" maxlength="30" placeholder="例如：2028 年 6 月" /></label>
                    <label class="field full-width"><span>邮箱</span><input v-model="profile.email" inputmode="email" autocomplete="email" maxlength="254" /></label>
                    <label class="field full-width"><span>电话</span><input v-model="profile.phone" type="tel" autocomplete="tel" maxlength="40" /></label>
                  </div>
                </details>
              </section>

              <section v-else-if="panel === 'experiences'" class="editor-content" aria-labelledby="experiences-heading">
                <div class="panel-heading">
                  <h2 id="experiences-heading">经历</h2>
                  <p>课程作业、项目、实习和社团活动都可以记录。可以从自己实际完成的部分写起。</p>
                </div>
                <fieldset v-for="(item, index) in experiences" :key="item.key" class="editor-entry">
                  <legend>经历 {{ index + 1 }}</legend>
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
                  <label class="field"><span>任务与背景</span><textarea v-model="item.value.context" rows="2" maxlength="1000" /></label>
                  <label class="field"><span>你完成的部分</span><textarea v-model="item.value.contribution" rows="3" maxlength="2000" /></label>
                  <button class="button button-quiet entry-action" type="button" :aria-label="`移除经历 ${index + 1}`"
                          @click="experiences.splice(index, 1)">移除经历</button>
                </fieldset>
                <button class="button button-quiet entry-action" type="button" :disabled="experiences.length >= limit" @click="addExperience">添加经历</button>
                <p v-if="experiences.length >= limit" class="help">每份简历最多可记录 30 项经历。</p>
              </section>

              <section v-else class="editor-content" aria-labelledby="skills-heading">
                <div class="panel-heading">
                  <h2 id="skills-heading">专业技能</h2>
                  <p>记录实际用过的工具或技术。如果还不确定自己会什么，可以先留空。</p>
                </div>
                <fieldset v-for="(item, index) in skills" :key="item.key" class="editor-entry">
                  <legend>技能 {{ index + 1 }}</legend>
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
                  <button class="button button-quiet entry-action" type="button" :aria-label="`移除技能 ${index + 1}`"
                          @click="skills.splice(index, 1)">移除技能</button>
                </fieldset>
                <button class="button button-quiet entry-action" type="button" :disabled="skills.length >= limit" @click="addSkill">添加技能</button>
                <p v-if="skills.length >= limit" class="help">每份简历最多可记录 30 项技能。</p>
              </section>
            </fieldset>
          </form>
        </section>

        <section class="resume-preview" aria-labelledby="preview-heading">
          <h2 id="preview-heading">简历预览</h2>
          <ResumePreview :content="content" />
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.resume-page { width: min(100% - 2rem, 74rem); margin: 2rem auto 4rem; }
.resume-header { display: flex; justify-content: space-between; align-items: end; gap: 1.5rem; margin-bottom: 1.4rem; }
.resume-back { display: inline-flex; align-items: center; min-height: 38px; font-size: .85rem; }
.resume-header h1 { font-size: clamp(1.8rem, 4vw, 2.5rem); margin-bottom: .45rem; }
.resume-header p { color: var(--muted); line-height: 1.55; }
.resume-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: end; gap: .55rem; }
.resume-actions .button { flex-shrink: 0; }
.resume-status { font-size: .85rem; padding: .25rem .4rem; }
.resume-placeholder { padding: 1.25rem; }
.resume-placeholder .button { margin-top: .65rem; }
.resume-feedback { display: flex; align-items: center; flex-wrap: wrap; gap: .6rem; margin-bottom: 1rem; }
.resume-feedback p { flex: 1 1 18rem; }

.resume-studio { display: grid; grid-template-columns: minmax(19rem, .85fr) minmax(0, 1.5fr); gap: 1rem; align-items: start; }
.resume-editor { min-width: 0; border-color: #d4dacf; }
.resume-panels { display: flex; gap: 1.05rem; padding: 0 1.2rem; border-bottom: 1px solid #dfe5dc; }
.resume-panels button, .mobile-controls button {
  border: 0;
  min-height: 49px;
  background: transparent;
  color: var(--muted);
  font-weight: 650;
  cursor: pointer;
}
.resume-panels button { border-bottom: 2px solid transparent; padding-inline: .3rem; }
.resume-panels button[aria-pressed="true"] { border-bottom-color: var(--primary); color: var(--primary); }

.editor-fields { min-width: 0; margin: 0; padding: 0; border: 0; }
.editor-content { display: grid; gap: 1rem; padding: 1.3rem 1.2rem; }
.panel-heading p { margin-top: .35rem; color: var(--muted); font-size: .9rem; line-height: 1.55; }
.editor-content .field { font-size: .9rem; }
.editor-content .field :is(input, select, textarea) { font-size: 1rem; border-color: #a4b6ab; }
.editor-content [aria-invalid="true"] { border-color: var(--danger); }
.field-error { color: var(--danger); font-size: .82rem; }
.required-label { margin-inline-start: .2rem; color: var(--muted); font-size: .76rem; font-weight: 500; }
.optional-fields { border-top: 1px solid #dfe5dc; padding-top: .45rem; }
.optional-fields summary { min-height: 44px; padding-block: .65rem; cursor: pointer; font-size: .92rem; font-weight: 650; }
.optional-fields summary span { margin-inline-start: .3rem; color: var(--muted); font-size: .78rem; font-weight: 500; }
.optional-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .8rem; padding-top: .5rem; }
.full-width { grid-column: 1 / -1; }
.editor-entry { display: grid; gap: .75rem; min-width: 0; margin: 0; padding: .85rem; border-radius: .55rem; border: 1px solid #d4dacf; }
.editor-entry legend { padding-inline: .35rem; font-weight: 650; }
.editor-entry textarea { resize: vertical; }
.entry-action { justify-self: start; }

.resume-preview { min-width: 0; border: 1px solid #d5ddd3; border-radius: 1rem; padding: 1rem clamp(.85rem, 3vw, 2rem) 1.8rem; background: #e9eee7; }
.resume-preview > h2 { width: min(100%, 38rem); margin: 0 auto .75rem; color: #435d50; font-size: .92rem; }
.mobile-controls { display: none; }

@media (max-width: 740px) {
  .resume-page { margin-top: .75rem; }
  .resume-header { flex-direction: column; align-items: stretch; gap: .75rem; margin-bottom: .75rem; }
  .resume-actions { justify-content: space-between; }
  .resume-status { flex: 1; padding-left: 0; }
  .mobile-controls { display: flex; gap: .25rem; background: #e9eee7; padding: .25rem; border-radius: .55rem; margin-bottom: .65rem; }
  .mobile-controls button { flex: 1; min-height: 44px; border-radius: .35rem; }
  .mobile-controls button[aria-pressed="true"] { background: var(--surface); color: var(--primary); }
  .resume-studio { grid-template-columns: minmax(0, 1fr); }
  .resume-studio[data-mobile-view="edit"] .resume-preview,
  .resume-studio[data-mobile-view="preview"] .resume-editor { display: none; }
}
</style>
