<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import type { MentorLibrary } from '../mentor'
import { account, forgetSession } from '../session'

type Section = 'notes' | 'resumes' | 'files'
const titles: Record<Section, string> = { notes: '长期记忆', resumes: '简历版本', files: '资料文件' }
const categories = { NOTE: '笔记', GOAL: '求职目标', EXPERIENCE: '经历自述', PREFERENCE: '偏好' }
const cursors: Record<Section, string> = { notes: 'beforeNote', resumes: 'beforeResume', files: 'beforeFile' }
const router = useRouter()
const section = ref<Section>('notes')
const library = ref<MentorLibrary>()
const note = ref('')
const upload = shallowRef<File>()
const picker = ref<HTMLInputElement>()
const busy = ref(false)
const message = ref('')
const feedback = ref('')
const dates = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' })
let fileRequestId = ''

async function handle(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  message.value = ''
  feedback.value = ''
  try {
    await work()
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      message.value = errorMessage(error)
    }
  } finally {
    busy.value = false
  }
}

async function read(query = '') {
  const result = await api<MentorLibrary>(`/mentor/memory${query}`)
  if (result.accountId !== account.value?.id) throw new ApiError(403, '登录账号已变更，请刷新页面。')
  return result
}

function load() {
  void handle(async () => { library.value = await read() })
}

function more() {
  const page = library.value
  const key = section.value
  const cursor = page?.[key].nextBefore
  if (!page || !cursor) return
  void handle(async () => {
    const next = await read(`?${cursors[key]}=${encodeURIComponent(cursor)}`)
    if (key === 'notes') page.notes = { ...next.notes, items: [...page.notes.items, ...next.notes.items] }
    if (key === 'resumes') page.resumes = { ...next.resumes, items: [...page.resumes.items, ...next.resumes.items] }
    if (key === 'files') page.files = { ...next.files, items: [...page.files.items, ...next.files.items] }
  })
}

function saveNote() {
  const body = note.value.trim()
  const accountId = account.value?.id
  if (!body || !accountId) return
  void handle(async () => {
    await api('/mentor/memory/notes', 'POST', { accountId, body })
    note.value = ''
    library.value = await read()
    feedback.value = '已存入共享记忆。'
  })
}

function choose(event: Event) {
  const input = event.target as HTMLInputElement
  upload.value = input.files?.[0]
  if (upload.value) fileRequestId = crypto.randomUUID()
  message.value = ''
  feedback.value = ''
}

function saveFile() {
  const file = upload.value
  const accountId = account.value?.id
  if (!file || !accountId) return
  if (!/\.(pdf|docx)$/i.test(file.name) || file.size > 5 * 1024 * 1024 || !file.size) {
    message.value = '请选择 5 MB 以内、有内容的 PDF 或 Word（.docx）文件。'
    return
  }
  void handle(async () => {
    const form = new FormData()
    form.set('accountId', accountId)
    form.set('requestId', fileRequestId)
    form.set('file', file)
    await api('/mentor/files', 'POST', form)
    upload.value = undefined
    if (picker.value) picker.value.value = ''
    library.value = await read()
    feedback.value = '原件已保存，可在主对话和旁聊中引用。'
  })
}

function origin(item: MentorLibrary['notes']['items'][number]) {
  return item.sideTitle ? `旁聊 · ${item.sideTitle}` : item.sourceTurnId ? '主对话' : '手动保存'
}

function readingStatus(item: { readable: boolean; ocrStatus: MentorLibrary['resumes']['items'][number]['ocrStatus'] }) {
  if (item.ocrStatus === 'READY') return '已识别扫描正文'
  if (item.readable) return ''
  if (item.ocrStatus === 'PROCESSING') return '正在识别扫描正文'
  if (item.ocrStatus === 'PENDING' || item.ocrStatus === 'RETRY') return '扫描正文待识别'
  return '已保存原件，暂无可用正文'
}

onMounted(load)
</script>

<template>
  <main id="main" tabindex="-1" class="page library">
    <header class="library-heading">
      <RouterLink :to="{ name: 'workspace' }">返回工作台</RouterLink>
      <h1>Mentor 记忆库</h1>
      <p>主对话和旁聊共用这份资料。你可以保存需要长期参考的信息，并核对简历版本和原件。</p>
    </header>
    <section class="surface library-panel" :aria-busy="busy">
      <nav class="library-nav" aria-label="记忆库分类">
        <button v-for="(label, key) in titles" :key="key" type="button" class="button button-quiet"
                :aria-pressed="section === key" @click="section = key; message = ''; feedback = ''">{{ label }}</button>
      </nav>
      <div class="library-content">
        <p v-if="message" class="notice notice-error" role="alert">{{ message }}</p>
        <p v-if="feedback" class="notice notice-success" role="status">{{ feedback }}</p>
        <p v-if="!library && busy" role="status">正在读取记忆库…</p>
        <button v-else-if="!library" class="button button-quiet" type="button" @click="load">重试加载</button>
        <template v-else>
          <div v-if="section === 'notes'" class="library-section">
            <form class="library-form" @submit.prevent="saveNote">
              <label for="memory-note">手动保存记忆</label>
              <textarea id="memory-note" v-model="note" rows="3" maxlength="1000"
                        placeholder="例如：我的目标改为杭州的 Java 后端实习。" :disabled="busy"></textarea>
              <button class="button button-primary" :disabled="busy || !note.trim()" type="submit">保存记忆</button>
            </form>
            <p v-if="!library.notes.items.length" class="library-empty">还没有长期记忆。你可以在对话中告诉 Mentor 要记住什么。</p>
            <ol v-else class="library-list">
              <li v-for="item in library.notes.items" :key="item.id">
                <p class="library-meta">
                  <strong>{{ categories[item.category] }}</strong>
                  <RouterLink v-if="item.sideId" :to="{ name: 'workspace', query: { side: item.sideId } }">{{ origin(item) }}</RouterLink>
                  <span v-else>{{ origin(item) }}</span>
                  <time :datetime="item.createdAt">{{ dates.format(new Date(item.createdAt)) }}</time>
                </p>
                <p class="note-content">{{ item.body }}</p>
              </li>
            </ol>
          </div>
          <div v-else-if="section === 'resumes'" class="library-section">
            <ol class="library-list">
              <li v-for="item in library.resumes.items" :key="item.version" class="library-file">
                <div>
                  <strong>第 {{ item.version }} 版</strong>
                  <p>{{ item.fileName ?? '在线编写' }}</p>
                  <p class="library-meta">
                    <time :datetime="item.submittedAt">{{ dates.format(new Date(item.submittedAt)) }}</time>
                    <span v-if="readingStatus(item)">{{ readingStatus(item) }}</span>
                  </p>
                </div>
                <nav aria-label="简历版本操作">
                  <RouterLink :to="{ name: 'resume-final', query: { version: String(item.version) } }">查看版本</RouterLink>
                  <a v-if="item.fileName" :href="`/api/resume/final/original?version=${item.version}`">下载原件</a>
                </nav>
              </li>
            </ol>
          </div>
          <div v-else class="library-section">
            <form class="library-form" @submit.prevent="saveFile">
              <label for="memory-file">添加求职资料</label>
              <input id="memory-file" ref="picker" type="file" accept=".pdf,.docx" :disabled="busy" @change="choose">
              <p class="library-meta">PDF 或 Word（.docx），单份最大 5 MB。</p>
              <button class="button button-primary" :disabled="busy || !upload" type="submit">保存到资料库</button>
            </form>
            <p v-if="!library.files.items.length" class="library-empty">还没有单独上传的求职资料。</p>
            <ol v-else class="library-list">
              <li v-for="file in library.files.items" :key="file.id" class="library-file">
                <div>
                  <strong>{{ file.name }}</strong>
                  <p class="library-meta">
                    <time :datetime="file.createdAt">{{ dates.format(new Date(file.createdAt)) }}</time>
                    <span v-if="readingStatus(file)">{{ readingStatus(file) }}</span>
                  </p>
                </div>
                <a :href="`/api/mentor/files/${file.id}`">下载原件</a>
              </li>
            </ol>
          </div>
          <button v-if="library[section].nextBefore" class="button button-quiet more" type="button"
                  :disabled="busy" @click="more">加载更多</button>
        </template>
      </div>
    </section>
  </main>
</template>

<style scoped>
.library { max-width: 57rem; }
.library-heading { display: grid; gap: .7rem; margin-bottom: 1.15rem; }
.library-heading a { justify-self: start; }
.library-heading p, .library-empty, .library-meta { color: var(--muted); line-height: 1.55; }
.library-nav { display: flex; flex-wrap: wrap; gap: .5rem; padding: .85rem; border-bottom: 1px solid var(--outline); }
.library-nav button[aria-pressed="true"] { background: var(--primary); border-color: var(--primary); color: white; }
.library-content, .library-section { display: grid; gap: .9rem; }
.library-content { padding: clamp(.8rem, 3vw, 1.3rem); }
.library-form { display: grid; gap: .55rem; padding-bottom: .9rem; border-bottom: 1px solid var(--outline); }
.library-form label { font-weight: 650; }
.library-form textarea { padding: .65rem; border: 1px solid var(--outline); border-radius: .45rem; resize: vertical; }
.library-form input { max-width: 100%; }
.library-form button, .more { justify-self: start; }
.library-list { display: grid; gap: .7rem; padding: 0; margin: 0; list-style: none; }
.library-list li { min-width: 0; padding: .75rem .85rem; border: 1px solid var(--outline); border-radius: .6rem; overflow-wrap: anywhere; }
.library-meta { display: flex; flex-wrap: wrap; gap: .35rem .75rem; font-size: .85rem; }
.note-content { margin-top: .35rem; line-height: 1.65; white-space: pre-wrap; }
.library-file { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .55rem 1rem; }
.library-file nav { display: flex; flex-wrap: wrap; gap: .6rem; }
</style>
