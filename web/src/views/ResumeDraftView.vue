<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import ResumeEducationFields from '../components/ResumeEducationFields.vue'
import ResumeExperienceFields from '../components/ResumeExperienceFields.vue'
import ResumeLayoutSettings from '../components/ResumeLayoutSettings.vue'
import ResumeProfileFields from '../components/ResumeProfileFields.vue'
import ResumeOutline from '../components/ResumeOutline.vue'
import ResumePreview from '../components/ResumePreview.vue'
import {
  activeSections, confirmDiscardDraft, defaultAppearance, draftHasUnsavedChanges, emptyEducation, emptyProfile,
  hasEducation, resumeSections, resumeSectionTitle,
  type Education, type Experience, type ExperienceType, type Profile, type ResumeContent, type ResumeDraft,
  type ResumeLayout, type ResumePanel, type ResumeSection, type ResumeSubmission, type Skill,
} from '../resumeDraft'
import { account, forgetSession } from '../session'

type Row<T> = { key: number; value: T }
const experiencePrompts: Record<ExperienceType, string> = {
  COURSEWORK: '想想一门课的大作业或一次实验。写清题目和你独立完成的部分。',
  PROJECT: '交代项目解决了什么问题、你的分工，以及最后做出的成果。',
  INTERNSHIP: '说明你参与了什么工作、交付了什么，哪些部分由你负责。',
  ACTIVITY: '可以记录社团、竞赛或志愿经历。说明你承担的职责和结果。',
  STRENGTH: '用一个可说明的例子展示你的优势，例如你怎样独立解决了一个问题。',
  HONOR: '记录奖项或证书的名称、颁发机构和时间。能够核验时可补上链接。',
  RESEARCH: '写清研究问题、你的分工和实际成果。课题正在进行也可以注明。',
  PORTFOLIO: '放上作品或源码链接，并说明能在其中看到什么和你完成的部分。',
  OTHER: '补充与求职目标有关的经历，例如助教、志愿服务或学生组织工作。',
  CUSTOM: '给栏目取一个容易理解的标题，再写下与求职目标相关的内容。',
}
const limit = 30
const router = useRouter()
const profile = ref(emptyProfile())
const educationRows = ref<Row<Education>[]>([{ key: 0, value: emptyEducation() }])
const experiences = ref<Row<Experience>[]>([])
const skills = ref<Row<Skill>[]>([])
const base = ref<ResumeDraft>()
const savedContent = ref('')
const panel = ref<ResumePanel>('PROFILE')
const layout = ref<ResumeLayout>({
  theme: 'RULED', density: 'STANDARD', sections: ['COURSEWORK', 'SKILLS'], titles: {}, hiddenSections: [], appearance: defaultAppearance(),
})
const formatting = ref(false)
const zoom = ref(100)
const mobileView = ref<'edit' | 'preview'>('edit')
const loading = ref(false)
const action = ref<'save' | 'submit' | null>(null)
const loadError = ref('')
const saveError = ref('')
const conflict = ref(false)
const checkBasics = ref(false)
let nextKey = 1

const content = computed<ResumeContent>(() => {
  const [education, ...additional] = educationRows.value.map(({ value }) => value)
  return {
    profile: profile.value,
    education: education ?? emptyEducation(),
    additionalEducation: additional.filter(hasEducation),
    experiences: experiences.value.map(({ value }) => value)
      .filter(({ title, role, period, context, contribution, details }) =>
        [title, role, period, context, contribution, details.organization, details.city, details.link]
          .some((field) => field.trim())),
    skills: skills.value.map(({ value }) => value).filter(({ name, description }) => name.trim() || description.trim()),
    layout: layout.value,
  }
})
const optionalSection = computed(() => resumeSections.find(({ id }) => id === panel.value))
const sectionIndex = computed(() => layout.value.sections.findIndex((id) => id === panel.value))
const experienceRows = computed(() => experiences.value.filter(({ value }) => value.type === panel.value))
const sectionHidden = computed(() => Boolean(optionalSection.value && layout.value.hiddenSections.includes(optionalSection.value.id)))
const sectionHasContent = computed(() => panel.value === 'SKILLS'
  ? content.value.skills.length > 0
  : content.value.experiences.some(({ type }) => type === panel.value))
const panelHeading = computed(() => optionalSection.value
  ? resumeSectionTitle(layout.value, optionalSection.value.id)
  : panel.value === 'PROFILE' ? '个人信息' : '教育背景')
const busy = computed(() => Boolean(action.value) || loading.value)
const fingerprint = computed(() => JSON.stringify(content.value))
const dirty = computed(() => Boolean(base.value) && fingerprint.value !== savedContent.value)
const submitted = computed(() => Boolean(base.value?.submission)
  && base.value?.submission?.draftRevision === base.value?.revision && !dirty.value)
const nameInvalid = computed(() => checkBasics.value && !profile.value.name.trim())
const schoolInvalid = computed(() => checkBasics.value && !educationRows.value.some(({ value }) => value.school.trim()))
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

function selectPanel(next: ResumePanel) {
  panel.value = next
  mobileView.value = 'edit'
}

async function editFromPreview(next: ResumePanel) {
  selectPanel(next)
  await nextTick()
  document.getElementById('editor-heading')?.focus()
}

function addSection(section: ResumeSection) {
  if (!layout.value.sections.includes(section)) layout.value.sections.push(section)
  selectPanel(section)
}

function moveSection(delta: number) {
  const from = sectionIndex.value
  const to = from + delta
  if (from < 0 || to < 0 || to >= layout.value.sections.length) return
  const [section] = layout.value.sections.splice(from, 1)
  layout.value.sections.splice(to, 0, section)
}

function toggleSection() {
  const section = optionalSection.value?.id
  if (!section) return
  if (sectionHidden.value) layout.value.hiddenSections = layout.value.hiddenSections.filter((id) => id !== section)
  else layout.value.hiddenSections.push(section)
}

function removeEmptySection() {
  if (sectionIndex.value < 0 || sectionHasContent.value) return
  if (panel.value === 'SKILLS') skills.value = []
  else experiences.value = experiences.value.filter(({ value }) => value.type !== panel.value)
  layout.value.sections.splice(sectionIndex.value, 1)
  delete layout.value.titles[panel.value as ResumeSection]
  layout.value.hiddenSections = layout.value.hiddenSections.filter((id) => id !== panel.value)
  selectPanel('PROFILE')
}

function changeZoom(delta: number) {
  zoom.value = Math.min(130, Math.max(70, zoom.value + delta))
}

async function addEducation() {
  if (educationRows.value.length >= 10) return
  const item = row(emptyEducation())
  educationRows.value.push(item)
  await nextTick()
  document.getElementById(`resume-school-${item.key}`)?.focus()
}

function moveEntry<T>(all: Row<T>[], group: Row<T>[], index: number, delta: number) {
  const target = group[index + delta]
  if (!target) return
  const from = all.findIndex(({ key }) => key === group[index].key)
  const to = all.findIndex(({ key }) => key === target.key)
  const current = all[from]
  all[from] = all[to]
  all[to] = current
}

async function addExperience() {
  if (!optionalSection.value || optionalSection.value.id === 'SKILLS' || experiences.value.length >= limit) return
  const item = row<Experience>({
    type: optionalSection.value.id, title: '', role: '', period: '', context: '', contribution: '',
    details: { organization: '', city: '', link: '', textStyle: 'PARAGRAPH' },
  })
  experiences.value.push(item)
  await nextTick()
  document.getElementById(`experience-title-${item.key}`)?.focus()
}

async function addSkill() {
  if (skills.value.length >= limit) return
  const item = row<Skill>({ name: '', proficiency: 'EXPOSURE', description: '' })
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
    profile.value = draft.content.profile
    educationRows.value = [draft.content.education, ...draft.content.additionalEducation].map(row)
    experiences.value = draft.content.experiences.map(row)
    skills.value = draft.content.skills.map(row)
    layout.value = { ...draft.content.layout, sections: activeSections(draft.content) }
    if (optionalSection.value && !layout.value.sections.includes(optionalSection.value.id)) selectPanel('PROFILE')
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
      selectPanel(nameInvalid.value ? 'PROFILE' : 'EDUCATION')
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
    <header class="resume-header surface">
      <div class="resume-heading">
        <RouterLink class="resume-back" :to="{ name: 'workspace' }">学生工作台</RouterLink>
        <h1>简历制作</h1>
        <p>填写姓名和学校即可提交，其他栏目可以按需添加。</p>
      </div>
      <div v-if="base" class="resume-actions">
        <p role="status" class="resume-status" :data-dirty="dirty"><span aria-hidden="true"></span>{{ status }}</p>
        <button class="button button-quiet" type="button" :disabled="!dirty || busy || conflict"
                @click="writeResume('save')">保存草稿</button>
        <RouterLink v-if="base.submission" class="button button-quiet final-link"
                    :to="{ name: 'resume-final' }">查看成稿</RouterLink>
        <button class="button button-primary" type="button" :disabled="submitted || busy || conflict"
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
        <ResumeOutline class="studio-outline" :content="content" :panel="panel" :busy="busy"
                       @select="selectPanel" @add="addSection" />

        <section class="surface resume-editor" aria-labelledby="editor-heading">
          <header class="editor-heading">
            <div>
              <h2 id="editor-heading" tabindex="-1">{{ panelHeading }}</h2>
              <p>{{ optionalSection ? '选填栏目' : '基础资料' }}</p>
            </div>
            <div v-if="optionalSection" class="section-tools" role="group" aria-label="栏目顺序">
              <button class="tool-button" type="button" aria-label="上移栏目" title="上移栏目"
                      :disabled="busy || sectionIndex <= 0" @click="moveSection(-1)">↑</button>
              <button class="tool-button" type="button" aria-label="下移栏目" title="下移栏目"
                      :disabled="busy || sectionIndex >= layout.sections.length - 1" @click="moveSection(1)">↓</button>
            </div>
          </header>

          <form novalidate @submit.prevent="writeResume('save')">
            <fieldset class="editor-fields" :disabled="busy">
              <label v-if="optionalSection" class="field section-title-field">
                <span>栏目名称</span>
                <input v-model="layout.titles[optionalSection.id]" :placeholder="optionalSection.label" maxlength="30" />
              </label>
              <div v-if="panel === 'PROFILE'" class="editor-content">
                <ResumeProfileFields v-model="profile" :invalid="nameInvalid" @education="selectPanel('EDUCATION')" />
              </div>

              <div v-else-if="panel === 'EDUCATION'" class="editor-content">
                <fieldset v-for="(item, index) in educationRows" :key="item.key" class="editor-entry">
                  <legend>教育经历 {{ index + 1 }}</legend>
                  <ResumeEducationFields v-model="item.value" :field-id="index === 0 ? 'resume-school' : `resume-school-${item.key}`"
                                         :invalid="index === 0 && schoolInvalid" :primary="index === 0" />
                  <div v-if="educationRows.length > 1" class="entry-tools" role="group" :aria-label="`教育经历 ${index + 1} 操作`">
                    <button class="button button-quiet" type="button" :disabled="index === 0"
                            @click="moveEntry(educationRows, educationRows, index, -1)">上移</button>
                    <button class="button button-quiet" type="button" :disabled="index === educationRows.length - 1"
                            @click="moveEntry(educationRows, educationRows, index, 1)">下移</button>
                    <button class="button button-quiet" type="button" @click="educationRows.splice(index, 1)">移除此段</button>
                  </div>
                </fieldset>
                <button class="button button-quiet entry-action" type="button" :disabled="educationRows.length >= 10" @click="addEducation">添加教育经历</button>
              </div>

              <div v-else-if="panel === 'SKILLS'" class="editor-content">
                <p class="editor-note">按实际使用程度记录工具或技术。暂时没有可写的技能，可以留空。</p>
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
                  <label class="field"><span>技能说明</span><textarea v-model="item.value.description" rows="3" maxlength="1000"
                           placeholder="写下你在哪些任务中用过，以及完成了什么。" /></label>
                  <div class="entry-tools" role="group" :aria-label="`技能 ${index + 1} 操作`">
                    <button class="button button-quiet" type="button" :disabled="index === 0"
                            @click="moveEntry(skills, skills, index, -1)">上移</button>
                    <button class="button button-quiet" type="button" :disabled="index === skills.length - 1"
                            @click="moveEntry(skills, skills, index, 1)">下移</button>
                    <button class="button button-quiet" type="button" @click="skills.splice(index, 1)">移除技能</button>
                  </div>
                </fieldset>
                <button class="button button-quiet entry-action" type="button" :disabled="skills.length >= limit" @click="addSkill">添加技能</button>
                <p v-if="skills.length >= limit" class="help">每份简历最多可记录 30 项技能。</p>
              </div>

              <div v-else class="editor-content">
                <p class="editor-note">{{ experiencePrompts[panel] }}</p>
                <fieldset v-for="(item, index) in experienceRows" :key="item.key" class="editor-entry">
                  <legend>经历 {{ index + 1 }}</legend>
                  <ResumeExperienceFields v-model="item.value" :field-id="`experience-title-${item.key}`" />
                  <div class="entry-tools" role="group" :aria-label="`经历 ${index + 1} 操作`">
                    <button class="button button-quiet" type="button" :disabled="index === 0"
                            @click="moveEntry(experiences, experienceRows, index, -1)">上移</button>
                    <button class="button button-quiet" type="button" :disabled="index === experienceRows.length - 1"
                            @click="moveEntry(experiences, experienceRows, index, 1)">下移</button>
                    <button class="button button-quiet" type="button"
                            @click="experiences = experiences.filter(({ key }) => key !== item.key)">移除经历</button>
                  </div>
                </fieldset>
                <button class="button button-quiet entry-action" type="button" :disabled="experiences.length >= limit" @click="addExperience">添加经历</button>
                <p v-if="experiences.length >= limit" class="help">每份简历最多可记录 30 项经历。</p>
              </div>
            </fieldset>
          </form>

          <footer v-if="optionalSection" class="editor-footer">
            <button type="button" :disabled="busy" @click="toggleSection">{{ sectionHidden ? '在成稿中显示' : '从成稿中隐藏' }}</button>
            <button v-if="!sectionHasContent" type="button" :disabled="busy" @click="removeEmptySection">移除空栏目</button>
          </footer>
        </section>

        <section class="resume-preview surface" aria-labelledby="preview-heading">
          <header class="preview-tools">
            <div>
              <h2 id="preview-heading">简历预览</h2>
              <p>A4 版式</p>
            </div>
            <div class="preview-actions">
              <div class="zoom-control" role="group" aria-label="预览缩放">
                <button class="tool-button" type="button" aria-label="缩小预览" :disabled="zoom <= 70" @click="changeZoom(-10)">−</button>
                <button class="tool-button zoom-value" type="button" aria-label="重置预览缩放" @click="zoom = 100">{{ zoom }}%</button>
                <button class="tool-button" type="button" aria-label="放大预览" :disabled="zoom >= 130" @click="changeZoom(10)">＋</button>
              </div>
              <button class="button button-quiet" type="button" :aria-expanded="formatting"
                      aria-controls="resume-format" @click="formatting = !formatting">版式设置</button>
            </div>
          </header>
          <ResumeLayoutSettings v-show="formatting" v-model="layout" :busy="busy" />
          <div class="paper-stage">
            <div class="paper-scale" :style="{ '--zoom': zoom / 100 }">
              <ResumePreview :content="content" @edit="editFromPreview" />
            </div>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.resume-page { width: min(100% - 2.25rem, 96rem); margin: 1.1rem auto 3rem; }
.resume-header {
  display: flex; justify-content: space-between; align-items: end; gap: 1rem;
  margin-bottom: 1rem; padding: 1rem 1.2rem; border-color: #d4ddd2;
}
.resume-back { display: inline-flex; align-items: center; min-height: 30px; font-size: .82rem; }
.resume-heading h1 { margin: .1rem 0 .25rem; font-size: clamp(1.65rem, 3vw, 2.2rem); }
.resume-heading p, .editor-heading p, .preview-tools p { color: var(--muted); line-height: 1.55; }
.resume-heading p { font-size: .9rem; }
.resume-actions, .preview-actions { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; }
.resume-actions { justify-content: end; }
.resume-actions .button { flex-shrink: 0; }
.resume-status {
  display: flex; align-items: center; gap: .35rem; padding: .4rem .55rem; color: var(--muted);
  border: 1px solid #dfe5da; border-radius: .45rem; background: #f5f7f2; font-size: .8rem;
}
.resume-status > span { width: .45rem; height: .45rem; border-radius: 50%; background: #487359; }
.resume-status[data-dirty="true"] > span { background: #9c6734; }
.resume-placeholder { padding: 1.25rem; }
.resume-placeholder .button { margin-top: .65rem; }
.resume-feedback { display: flex; align-items: center; flex-wrap: wrap; gap: .6rem; margin-bottom: .8rem; }
.resume-feedback p { flex: 1 1 18rem; }

.resume-studio {
  display: grid; grid-template-columns: minmax(11rem, .74fr) minmax(19rem, 1.35fr) minmax(0, 2.2fr);
  align-items: start; gap: .85rem;
}
.studio-outline { position: sticky; top: 1rem; }
.resume-editor, .resume-preview { min-width: 0; }
.editor-heading, .preview-tools {
  display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: .6rem;
  min-height: 4.25rem; padding: .7rem .95rem; border-bottom: 1px solid #dce4d8;
}
.editor-heading h2, .preview-tools h2 { font-size: 1.08rem; }
.editor-heading p, .preview-tools p { font-size: .77rem; margin-top: .12rem; }
.section-tools { display: flex; gap: .25rem; }
.tool-button {
  min-width: 44px; min-height: 44px; padding-inline: .5rem; border: 1px solid var(--outline);
  border-radius: .42rem; background: transparent; color: var(--ink); cursor: pointer; font-weight: 650;
}
.tool-button:hover:not(:disabled) { background: #e8eee4; }
.tool-button:disabled { opacity: .45; cursor: default; }

.editor-fields { min-width: 0; margin: 0; padding: 0; border: 0; }
.editor-content { display: grid; gap: .9rem; padding: 1rem .95rem 1.15rem; }
.editor-content :deep(.field), .section-title-field { font-size: .86rem; }
.editor-content :deep(.field :is(input, select, textarea)), .section-title-field input { font-size: 1rem; }
.editor-content :deep(.editor-grid) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .7rem; }
.editor-content :deep([aria-invalid="true"]) { border-color: var(--danger); }
.editor-content :deep(.field-error) { color: var(--danger); font-size: .83rem; }
.editor-content :deep(.required-label) { margin-inline-start: .15rem; color: var(--muted); font-size: .75rem; font-weight: 500; }
.editor-note, .editor-content :deep(.editor-note) {
  padding: .75rem .8rem; color: #375447; border: 1px solid #dbe5d9; border-radius: .5rem;
  background: #eff4ed; font-size: .86rem; line-height: 1.55;
}
.editor-content :deep(.editor-note button), .editor-footer button {
  min-height: 44px; padding: .35rem 0; background: transparent; border: 0; cursor: pointer; text-align: left;
  color: var(--primary); font-weight: 650;
}
.editor-content :deep(.editor-note button:hover), .editor-footer button:hover:not(:disabled) { text-decoration: underline; }
.editor-footer { display: flex; flex-wrap: wrap; gap: .85rem; padding: 0 .95rem .25rem; border-top: 1px solid #e4e9e1; }
.editor-footer button { color: var(--muted); font-size: .84rem; }
.editor-footer button:disabled { opacity: .55; cursor: default; }
.editor-entry {
  display: grid; gap: .7rem; min-width: 0; margin: 0; padding: .7rem;
  border: 1px solid #d4dacf; border-radius: .5rem;
}
.editor-entry legend { padding-inline: .3rem; font-weight: 650; }
.editor-entry textarea { resize: vertical; }
.entry-action { justify-self: start; }
.section-title-field { margin: .85rem .95rem 0; }
.entry-tools { display: flex; flex-wrap: wrap; gap: .35rem; }
.entry-tools .button { font-size: .82rem; padding-inline: .55rem; }
.editor-content :deep(.editor-details) { border: 1px solid #dce5d9; border-radius: .5rem; padding-inline: .65rem; }
.editor-content :deep(.editor-details summary) { min-height: 44px; padding-block: .65rem; cursor: pointer; font-weight: 650; }
.editor-content :deep(.detail-fields) { display: grid; gap: .7rem; padding-bottom: .65rem; }
.final-link { display: inline-flex; align-items: center; text-decoration: none; }


.preview-tools { background: #f9fbf6; border-radius: 1rem 1rem 0 0; }
.preview-actions .button { font-size: .86rem; }
.zoom-control { display: flex; border: 1px solid var(--outline); border-radius: .45rem; }
.zoom-control .tool-button { border: 0; border-radius: 0; min-height: 42px; }
.zoom-control .tool-button + .tool-button { border-inline-start: 1px solid var(--outline); }
.zoom-control .zoom-value { min-width: 3.15rem; font-variant-numeric: tabular-nums; font-size: .83rem; }
.paper-stage {
  container-type: inline-size; overflow: auto; padding: clamp(.75rem, 2.2vw, 1.65rem);
  max-height: max(440px, calc(100dvh - 14.5rem)); border-radius: 0 0 1rem 1rem;
  background-color: #e8eee7; background-image: radial-gradient(#93a89560 .7px, transparent .7px);
  background-size: 12px 12px;
}
.paper-scale { width: min(100cqw, 42rem); margin-inline: auto; zoom: var(--zoom); }
.mobile-controls { display: none; }

@media (max-width: 980px) {
  .resume-studio { grid-template-columns: 12rem minmax(0, 1fr); }
  .mobile-controls { display: flex; gap: .3rem; padding: .25rem; margin-bottom: .7rem; border-radius: .55rem; background: #e7ede3; }
  .mobile-controls button {
    flex: 1; min-height: 44px; border: 0; border-radius: .35rem; background: transparent;
    color: var(--muted); font-weight: 650; cursor: pointer;
  }
  .mobile-controls button[aria-pressed="true"] { background: var(--surface); color: var(--primary); }
  .resume-studio[data-mobile-view="edit"] .resume-preview,
  .resume-studio[data-mobile-view="preview"] :is(.resume-editor, .studio-outline) { display: none; }
  .resume-preview { grid-column: 1 / -1; }
  .paper-stage { max-height: none; }
}
@media (max-width: 620px) {
  .resume-page { width: min(100% - 1.25rem, 96rem); margin-top: .65rem; }
  .resume-header { align-items: stretch; flex-direction: column; gap: .65rem; padding: .75rem; }
  .resume-actions { justify-content: start; }
  .resume-status { margin-right: auto; }
  .resume-studio { grid-template-columns: minmax(0, 1fr); gap: .7rem; }
  .studio-outline { position: static; }
}
</style>
