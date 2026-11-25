<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, api, errorMessage } from '../api'
import ResumePreview from '../components/ResumePreview.vue'
import type { ResumeFinal } from '../resumeDraft'
import { account, forgetSession } from '../session'

const route = useRoute()
const router = useRouter()
const resume = ref<ResumeFinal>()
const loading = ref(false)
const message = ref('')
let requestId = 0
let printMargins: HTMLStyleElement | undefined

async function loadVersion() {
  const id = ++requestId
  resume.value = undefined
  printMargins?.remove()
  printMargins = undefined
  loading.value = true
  message.value = ''
  const version = route.query.version
  try {
    if (version !== undefined && (typeof version !== 'string' || !/^[1-9][0-9]*$/.test(version))) {
      throw new ApiError(400, '简历版本编号无效。')
    }
    const query = version ? `?version=${encodeURIComponent(version)}` : ''
    const result = await api<ResumeFinal>(`/resume/final${query}`)
    if (id !== requestId) return
    if (result.accountId !== account.value?.id) throw new ApiError(403, '登录账号已变更，请刷新页面。')
    resume.value = result
    if (!result.content) return
    printMargins = document.createElement('style')
    const { topMargin, rightMargin, bottomMargin, leftMargin } = result.content.layout.appearance
    printMargins.textContent = `@page { size: A4; margin: ${topMargin}mm ${rightMargin}mm ${bottomMargin}mm ${leftMargin}mm; }`
    document.head.append(printMargins)
  } catch (error) {
    if (id !== requestId) return
    if (error instanceof ApiError && error.status === 401) {
      forgetSession()
      await router.replace({ name: 'login' })
    } else {
      message.value = errorMessage(error)
    }
  } finally {
    if (id === requestId) loading.value = false
  }
}

function printDocument() {
  if (resume.value) window.print()
}

watch(() => route.query.version, loadVersion, { immediate: true })
onBeforeUnmount(() => { requestId++; printMargins?.remove() })
</script>

<template>
  <main id="main" tabindex="-1" class="final-page">
    <header class="surface final-header">
      <div>
        <RouterLink v-if="resume?.upload" :to="{ name: 'workspace' }">返回工作台</RouterLink>
        <RouterLink v-else :to="{ name: 'resume-draft' }">返回编辑</RouterLink>
        <h1>简历成稿</h1>
        <p v-if="resume">已提交 · 第 {{ resume.version }} 版</p>
      </div>
      <nav v-if="resume" aria-label="成稿操作">
        <RouterLink v-if="resume.version > 1" class="button button-quiet"
                    :to="{ name: 'resume-final', query: { version: String(resume.version - 1) } }">上一版</RouterLink>
        <RouterLink v-if="route.query.version" class="button button-quiet" :to="{ name: 'resume-final' }">最新版本</RouterLink>
        <a v-if="resume.upload" class="button button-primary"
           :href="`/api/resume/final/original?version=${resume.version}`">下载原件</a>
        <button v-else class="button button-primary" type="button" @click="printDocument">打印 / 存为 PDF</button>
      </nav>
    </header>
    <p v-if="loading" class="final-feedback" role="status">正在加载成稿…</p>
    <p v-else-if="message" class="notice notice-error final-feedback" role="alert">{{ message }}</p>
    <section v-if="resume?.upload" class="surface original-preview" aria-labelledby="original-name">
      <header>
        <h2 id="original-name">{{ resume.upload.name }}</h2>
        <p>原件上传 · {{ resume.upload.contentType === 'application/pdf' ? 'PDF' : 'Word' }}</p>
      </header>
      <div class="original-text">
        <h3>正文预览</h3>
        <p v-if="resume.upload.ocrStatus === 'READY'">以下是扫描件识别文本，可下载原件核对。</p>
        <p v-if="resume.upload.text" class="original-extracted">{{ resume.upload.text }}</p>
        <p v-else-if="resume.upload.ocrStatus && ['PENDING', 'PROCESSING', 'RETRY'].includes(resume.upload.ocrStatus)">
          扫描件等待识别。可以先下载原件查看。
        </p>
        <p v-else>文件中没有可用的正文。下载原件即可查看完整内容和版式。</p>
      </div>
    </section>
    <div v-else-if="resume?.content" class="final-paper">
      <ResumePreview :content="resume.content" :editable="false" />
    </div>
  </main>
</template>

<style scoped>
.final-page { width: min(100% - 2rem, 64rem); margin: 1.5rem auto 3rem; }
.final-header { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; padding: 1rem; gap: .65rem; }
.final-header h1 { font-size: clamp(1.6rem, 4vw, 2rem); margin: .35rem 0; }
.final-header p { color: var(--muted); font-size: .9rem; }
.final-header nav { display: flex; flex-wrap: wrap; gap: .45rem; }
.final-header nav a { display: inline-flex; align-items: center; text-decoration: none; }
.final-paper { padding: clamp(.45rem, 3vw, 1.8rem); margin-top: .8rem; border-radius: .7rem; background: #e6ebe3; }
.final-paper > :deep(.resume-paper) { max-width: 210mm; }
.original-preview { margin-top: .8rem; overflow: hidden; }
.original-preview header, .original-text { padding: clamp(1rem, 4vw, 1.6rem); }
.original-preview header { border-bottom: 1px solid var(--outline); }
.original-preview header h2 { font-size: 1.08rem; overflow-wrap: anywhere; }
.original-preview header p { margin-top: .35rem; color: var(--muted); font-size: .9rem; }
.original-text h3 { margin-bottom: .7rem; font-size: .95rem; }
.original-text p { line-height: 1.65; }
.original-extracted { white-space: pre-wrap; overflow-wrap: anywhere; }
.final-feedback { margin-top: .75rem; }
@media print {
  :global(html), :global(body) { background: white !important; }
  :global(body:has(.final-page) .topbar), :global(body:has(.final-page) .skip-link),
  :global(body:has(.final-page) .app-notice), .final-header, .final-feedback { display: none !important; }
  .final-page, .final-paper { width: auto; max-width: none; margin: 0; padding: 0; background: white; }
  .final-paper > :deep(.resume-paper) { max-width: none; }
}
</style>
