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
        <RouterLink :to="{ name: 'resume-draft' }">返回编辑</RouterLink>
        <h1>简历成稿</h1>
        <p v-if="resume">已提交 · 第 {{ resume.version }} 版</p>
      </div>
      <nav v-if="resume" aria-label="成稿操作">
        <RouterLink v-if="resume.version > 1" class="button button-quiet"
                    :to="{ name: 'resume-final', query: { version: String(resume.version - 1) } }">上一版</RouterLink>
        <RouterLink v-if="route.query.version" class="button button-quiet" :to="{ name: 'resume-final' }">最新版本</RouterLink>
        <button class="button button-primary" type="button" @click="printDocument">打印 / 存为 PDF</button>
      </nav>
    </header>
    <p v-if="loading" class="final-feedback" role="status">正在加载成稿…</p>
    <p v-else-if="message" class="notice notice-error final-feedback" role="alert">{{ message }}</p>
    <div v-if="resume" class="final-paper">
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
.final-feedback { margin-top: .75rem; }
@media print {
  :global(html), :global(body) { background: white !important; }
  :global(body:has(.final-page) .topbar), :global(body:has(.final-page) .skip-link),
  :global(body:has(.final-page) .app-notice), .final-header, .final-feedback { display: none !important; }
  .final-page, .final-paper { width: auto; max-width: none; margin: 0; padding: 0; background: white; }
  .final-paper > :deep(.resume-paper) { max-width: none; }
}
</style>
