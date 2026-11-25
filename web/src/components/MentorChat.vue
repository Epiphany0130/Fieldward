<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { ApiError, api, errorMessage } from '../api'
import { streamMentor, type MentorHistory, type MentorTurn, type StudentMessage } from '../mentor'

const props = defineProps<{ accountId: string }>()
const emit = defineEmits<{ expired: [] }>()
const turns = ref<MentorTurn[]>([])
const loading = ref(true)
const loadingEarlier = ref(false)
const ready = ref(false)
const available = ref(false)
const message = ref('')
const draft = ref('')
const editSeed = ref<StudentMessage>()
const syncing = ref(false)
const partial = ref('')
const active = ref<MentorTurn | null>(null)
const pending = ref<StudentMessage | null>(null)
const currentRequest = ref<string>()
const aborter = shallowRef<AbortController>()
const nextBefore = ref<string | null>(null)
const thread = ref<HTMLElement>()
const compose = ref<HTMLTextAreaElement>()
const busy = computed(() => Boolean(active.value || aborter.value))
const canSend = computed(() => ready.value && available.value && !busy.value && !syncing.value && !pending.value && draft.value.trim())
const timeFormat = new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
let poll: number | undefined
let unmounted = false

function merge(incoming: MentorTurn[]) {
  const items = new Map(turns.value.map((turn) => [turn.requestId, turn]))
  for (const turn of incoming) items.set(turn.requestId, turn)
  turns.value = [...items.values()].sort((a, b) => a.id.length - b.id.length || a.id.localeCompare(b.id))
  if (pending.value && items.has(pending.value.requestId)) pending.value = null
}

function nearEnd() {
  const pane = thread.value
  return !pane || pane.scrollHeight - pane.scrollTop - pane.clientHeight <= 88
}

async function scrollToEnd() {
  await nextTick()
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}

function schedulePoll() {
  window.clearTimeout(poll)
  if (!unmounted && active.value && !aborter.value) {
    poll = window.setTimeout(() => void syncHistory(), 1800)
  }
}

function handleError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) emit('expired')
  else if (!(error instanceof DOMException && error.name === 'AbortError')) message.value = errorMessage(error)
}

async function syncHistory(watch = active.value?.requestId, before?: string) {
  if (syncing.value || unmounted) return
  syncing.value = true
  if (before) loadingEarlier.value = true
  const shouldScroll = !ready.value || nearEnd()
  const oldHeight = thread.value?.scrollHeight ?? 0
  try {
    const query = new URLSearchParams()
    if (watch) query.set('watch', watch)
    if (before) query.set('before', before)
    const snapshot = await api<MentorHistory>(`/mentor/turns?${query}`)
    if (unmounted) return
    if (snapshot.accountId !== props.accountId) {
      throw new ApiError(403, '登录账号已变更，请刷新页面。')
    }
    const records = [...snapshot.turns]
    if (snapshot.watched) records.push(snapshot.watched)
    if (snapshot.active) records.push(snapshot.active)
    merge(records)
    active.value = snapshot.active ?? turns.value.find(({ status }) => status === 'STREAMING') ?? null
    available.value = snapshot.available
    if (!ready.value || before) nextBefore.value = snapshot.nextBefore
    ready.value = true
    if (before) {
      await nextTick()
      if (thread.value) thread.value.scrollTop += thread.value.scrollHeight - oldHeight
    } else if (shouldScroll) {
      void scrollToEnd()
    }
  } catch (error) {
    if (!unmounted) handleError(error)
  } finally {
    loading.value = false
    loadingEarlier.value = false
    syncing.value = false
    schedulePoll()
  }
}

async function send(input: StudentMessage) {
  if (busy.value || !ready.value || !available.value || syncing.value) return
  message.value = ''
  if (!turns.value.some(({ requestId }) => requestId === input.requestId)) pending.value = input
  currentRequest.value = input.requestId
  partial.value = ''
  const controller = new AbortController()
  aborter.value = controller
  window.clearTimeout(poll)
  void scrollToEnd()

  try {
    await streamMentor(props.accountId, input, controller.signal, (event) => {
      if (unmounted) return
      const shouldScroll = nearEnd()
      if (event.name === 'delta') {
        partial.value += event.text
      } else {
        merge([event.turn])
        active.value = event.turn.status === 'STREAMING' ? event.turn : null
        if (event.name !== 'turn') partial.value = ''
      }
      if (shouldScroll) void scrollToEnd()
    })
  } catch (error) {
    if (!unmounted) {
      handleError(error)
      partial.value = ''
      await syncHistory(input.requestId)
    }
  } finally {
    aborter.value = undefined
    currentRequest.value = undefined
    partial.value = ''
    schedulePoll()
  }
}

function submit() {
  if (!canSend.value) return
  const content = draft.value.trim()
  const requestId = editSeed.value?.content === content ? editSeed.value.requestId : crypto.randomUUID()
  draft.value = ''
  editSeed.value = undefined
  void send({ requestId, content })
}

function retry(turn: MentorTurn) {
  void send({ requestId: turn.requestId, content: turn.studentMessage })
}

function resumeEditing() {
  if (!pending.value) return
  editSeed.value = pending.value
  draft.value = pending.value.content
  pending.value = null
  message.value = ''
  void nextTick(() => compose.value?.focus())
}

function reloadHistory() {
  loading.value = !ready.value
  message.value = ''
  void syncHistory()
}

onMounted(() => void syncHistory())
onBeforeUnmount(() => {
  unmounted = true
  window.clearTimeout(poll)
  aborter.value?.abort()
})
</script>

<template>
  <div class="mentor-chat">
    <div ref="thread" class="mentor-thread" aria-label="对话记录" tabindex="0">
      <p v-if="loading" class="muted" role="status">正在读取对话…</p>
      <button v-if="nextBefore" type="button" class="button button-quiet earlier"
              :disabled="loadingEarlier || busy || syncing" @click="syncHistory(undefined, nextBefore)">
        {{ loadingEarlier ? '加载中…' : '加载较早消息' }}
      </button>
      <div v-if="ready && !turns.length && !pending" class="thread-empty">
        <h3>想从哪里开始？</h3>
        <p>可以聊聊你想找的实习、手上的课程项目，或现在最拿不准的一件事。</p>
      </div>
      <ol v-if="turns.length || pending" class="turns">
        <li v-for="turn in turns" :key="turn.requestId" class="turn">
          <article class="bubble user-bubble">
            <header><strong>你</strong><time :datetime="turn.createdAt">{{ timeFormat.format(new Date(turn.createdAt)) }}</time></header>
            <p>{{ turn.studentMessage }}</p>
          </article>
          <article v-if="turn.status === 'COMPLETED'" class="bubble mentor-bubble">
            <header><strong>Mentor</strong></header>
            <p>{{ turn.mentorReply }}</p>
          </article>
          <article v-else-if="turn.status === 'STREAMING'" class="bubble mentor-bubble">
            <header><strong>Mentor</strong><span>正在回复</span></header>
            <p v-if="turn.requestId === currentRequest && partial">{{ partial }}</p>
            <p v-else class="muted">正在生成回复…</p>
          </article>
          <div v-else class="turn-feedback">
            <span>这次回复已中断。</span>
            <button type="button" class="button button-quiet" :disabled="busy || syncing || !available || Boolean(pending)" @click="retry(turn)">重试回复</button>
          </div>
        </li>
        <li v-if="pending" class="turn">
          <article class="bubble user-bubble">
            <header><strong>你</strong><span>{{ aborter ? '正在发送' : '待确认发送' }}</span></header>
            <p>{{ pending.content }}</p>
          </article>
          <div v-if="!aborter" class="turn-feedback">
            <button type="button" class="button button-quiet" :disabled="busy || syncing || !available" @click="send(pending)">重试发送</button>
            <button type="button" class="button button-quiet" :disabled="busy" @click="resumeEditing">返回编辑</button>
          </div>
        </li>
      </ol>
    </div>
    <div v-if="message" class="chat-notice">
      <p class="notice notice-error" role="alert">{{ message }}</p>
      <button v-if="!busy" type="button" class="button button-quiet" @click="reloadHistory">重新加载对话</button>
    </div>
    <p v-if="ready && !available" class="notice chat-notice setup-notice" role="status">当前环境尚未配置 Mentor 模型，可以查看已有对话。</p>
    <form class="composer" @submit.prevent="submit">
      <label class="field">
        <span>给 Mentor 发消息</span>
        <textarea ref="compose" v-model="draft" rows="3" maxlength="4000"
                  :disabled="!ready || !available || Boolean(pending)" placeholder="例如：我想准备 Java 后端实习，应该从哪开始？" />
      </label>
      <div class="composer-actions">
        <span class="muted" role="status">{{ busy ? 'Mentor 正在处理本轮对话' : '' }}</span>
        <button v-if="aborter" type="button" class="button button-quiet" @click="aborter.abort()">停止接收</button>
        <button v-else type="submit" class="button button-primary" :disabled="!canSend">发送消息</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.mentor-chat { display: grid; min-width: 0; }
.mentor-thread { min-height: 15rem; max-height: min(54dvh, 38rem); overflow: auto; padding: clamp(.9rem, 3vw, 1.35rem); }
.mentor-thread:focus-visible { outline-offset: -4px; }
.muted { color: var(--muted); }
.thread-empty { padding: .7rem 0; }
.thread-empty h3 { margin: 0 0 .4rem; font-size: 1.15rem; }
.thread-empty p { color: var(--muted); line-height: 1.6; }
.earlier { display: block; margin: 0 auto .85rem; }
.turns { display: grid; gap: 1.15rem; margin: 0; padding: 0; list-style: none; }
.turn { display: grid; gap: .65rem; min-width: 0; }
.bubble { min-width: 0; max-width: min(100%, 44rem); padding: .65rem .8rem; border-radius: .65rem; overflow-wrap: anywhere; }
.bubble header { display: flex; flex-wrap: wrap; align-items: baseline; gap: .3rem .6rem; margin-bottom: .25rem; }
.bubble header :is(time, span) { color: var(--muted); font-size: .76rem; }
.bubble p { white-space: pre-wrap; line-height: 1.65; }
.user-bubble { justify-self: end; background: #e6eee3; border: 1px solid #d6e1d4; }
.mentor-bubble { justify-self: start; background: #f6f7f2; border: 1px solid #dfe6dc; }
.turn-feedback { display: flex; justify-content: start; align-items: center; flex-wrap: wrap; gap: .4rem; color: var(--muted); font-size: .87rem; }
.chat-notice { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap; margin: 0 .9rem .7rem; }
.chat-notice .notice { flex: 1 1 12rem; }
.setup-notice { background: #eff1e7; color: var(--muted); }
.composer { display: grid; gap: .55rem; border-top: 1px solid #dde6dc; padding: .8rem .9rem; }
.composer textarea { resize: vertical; line-height: 1.55; }
.composer-actions { display: flex; align-items: center; justify-content: space-between; gap: .5rem; }
.composer-actions span { font-size: .8rem; }
@media (max-width: 680px) {
  .mentor-thread { max-height: 51dvh; }
}
</style>
