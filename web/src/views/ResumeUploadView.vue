<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import type { ResumeSubmission } from '../resumeDraft'
import { account, forgetSession } from '../session'

const MAX_BYTES = 5 * 1024 * 1024
const route = useRoute()
const router = useRouter()
const fromWorkspace = computed(() => route.query.from === 'workspace')
const picker = ref<HTMLInputElement>()
const file = shallowRef<File>()
const message = ref('')
const pending = ref(false)
const dragging = ref(false)
const formatSize = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })
const fileDetails = computed(() => {
  if (!file.value) return ''
  const format = file.value.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'Word'
  const size = file.value.size >= 1024 * 1024
    ? `${formatSize.format(file.value.size / (1024 * 1024))} MB`
    : `${formatSize.format(file.value.size / 1024)} KB`
  return `${format} · ${size}`
})
let requestId = ''
let active = true

function select(files: FileList | null) {
  if (!files?.length || pending.value) return
  const selected = files[0]
  file.value = undefined
  if (files.length !== 1) {
    message.value = '请一次选择一份简历。'
  } else if (!/\.(pdf|docx)$/i.test(selected.name)) {
    message.value = '请选择 PDF 或 Word（.docx）文件。'
  } else if (!selected.size || selected.size > MAX_BYTES) {
    message.value = selected.size ? '文件大小不能超过 5 MB。' : '文件是空的，请重新选择。'
  } else {
    file.value = selected
    message.value = ''
    requestId = crypto.randomUUID()
  }
}

function choose(event: Event) {
  const input = event.target as HTMLInputElement
  select(input.files)
  input.value = ''
}

function drop(event: DragEvent) {
  dragging.value = false
  select(event.dataTransfer?.files ?? null)
}

function leave(event: DragEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) dragging.value = false
}

async function upload() {
  if (!file.value || pending.value || !account.value) return
  const form = new FormData()
  form.set('accountId', account.value.id)
  form.set('requestId', requestId)
  form.set('file', file.value)
  pending.value = true
  message.value = ''
  try {
    await api<ResumeSubmission>('/resume/upload', 'POST', form)
    if (active) await router.replace({ name: 'workspace' })
  } catch (error) {
    if (!active) return
    if (error instanceof ApiError && error.status === 401) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      message.value = errorMessage(error)
    }
  } finally {
    if (active) pending.value = false
  }
}

onBeforeUnmount(() => { active = false })
</script>

<template>
  <main id="main" tabindex="-1" class="upload-page">
    <header>
      <RouterLink :to="{ name: fromWorkspace ? 'workspace' : 'resume-draft' }">
        {{ fromWorkspace ? '返回工作台' : '返回简历制作' }}
      </RouterLink>
      <h1>{{ fromWorkspace ? '上传新版本' : '上传已有简历' }}</h1>
      <p>将文件保存为正式简历版本，上传后可在工作台查看和下载原件。</p>
    </header>

    <form class="surface upload-form" :aria-busy="pending" @submit.prevent="upload">
      <div class="upload-drop" :data-dragging="dragging" @dragover.prevent="dragging = !pending"
           @dragleave="leave" @drop.prevent="drop">
        <input ref="picker" type="file" aria-label="选择简历文件" accept=".pdf,.docx" :disabled="pending" hidden @change="choose">
        <div class="upload-file" aria-live="polite">
          <strong>{{ file ? file.name : '将简历拖到这里' }}</strong>
          <p>{{ file ? fileDetails : 'PDF 或 Word（.docx），最大 5 MB' }}</p>
        </div>
        <button class="button button-quiet" type="button" :disabled="pending" @click="picker?.click()">
          {{ file ? '更换文件' : '选择文件' }}
        </button>
      </div>
      <p v-if="message" class="notice notice-error" role="alert">{{ message }}</p>
      <footer class="upload-actions">
        <p v-if="pending" role="status">正在上传并保存简历…</p>
        <button class="button button-primary" type="submit" :disabled="!file || pending">上传并使用</button>
      </footer>
    </form>
  </main>
</template>

<style scoped>
.upload-page { width: min(100% - 2rem, 43rem); margin: clamp(1.2rem, 5vw, 3.5rem) auto 3rem; }
.upload-page header > a { display: inline-flex; align-items: center; min-height: 2.75rem; font-size: .9rem; }
.upload-page h1 { margin: .45rem 0; font-size: clamp(1.7rem, 5vw, 2.4rem); }
.upload-page p { color: var(--muted); line-height: 1.55; }
.upload-form { display: grid; gap: 1rem; margin-top: 1.3rem; padding: clamp(.9rem, 4vw, 1.5rem); }
.upload-drop {
  display: grid; justify-items: center; align-content: center; gap: 1rem;
  padding: 1.25rem; min-height: 12rem; border: 1px dashed var(--primary); border-radius: .7rem;
  background: color-mix(in srgb, var(--surface) 70%, var(--outline)); text-align: center;
}
.upload-drop[data-dragging="true"] { background: #e8f2e4; outline: 2px solid var(--primary); outline-offset: 2px; }
.upload-file { max-width: 100%; overflow-wrap: anywhere; }
.upload-file p { margin-top: .3rem; font-size: .9rem; }
.upload-form .button { min-height: 2.75rem; }
.upload-form .notice-error { color: var(--danger); }
.upload-actions { display: flex; flex-wrap: wrap; justify-content: end; align-items: center; gap: .75rem; }
.upload-actions p { font-size: .9rem; }
</style>
