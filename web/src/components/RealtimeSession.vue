<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

const mode = ref<'PHONE' | 'VIDEO'>('PHONE')
const status = ref('请选择电话或视频，检查设备权限。')
const preview = ref<HTMLVideoElement | null>(null)
const busy = ref(false)
let stream: MediaStream | undefined
let generation = 0

function stop() {
  generation++
  stream?.getTracks().forEach(track => track.stop())
  stream = undefined
  if (preview.value) preview.value.srcObject = null
}
async function check() {
  stop()
  const current = generation
  busy.value = true
  try {
    const acquired = await navigator.mediaDevices.getUserMedia({ audio: true, video: mode.value === 'VIDEO' })
    if (current !== generation) { acquired.getTracks().forEach(track => track.stop()); return }
    stream = acquired
    if (preview.value) preview.value.srcObject = acquired
    status.value = mode.value === 'VIDEO' ? '麦克风和摄像头已授权。' : '麦克风已授权。'
  } catch {
    if (current === generation) status.value = '无法使用设备，请检查浏览器权限和设备连接后重试。'
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(stop)
</script>

<template>
  <main class="realtime-preview">
    <h1>音视频设备检查</h1>
    <p>实时对话尚未开放。这里仅检查设备权限，不会开始会话或消耗积分。</p>
    <fieldset>
      <legend>选择模式</legend>
      <label><input v-model="mode" type="radio" value="PHONE" @change="stop"> 电话（仅麦克风）</label>
      <label><input v-model="mode" type="radio" value="VIDEO" @change="stop"> 视频（麦克风和摄像头）</label>
    </fieldset>
    <video v-show="mode === 'VIDEO'" ref="preview" autoplay muted playsinline aria-label="摄像头预览" />
    <p role="status">{{ status }}</p>
    <button type="button" :disabled="busy" @click="check">{{ busy ? '等待授权…' : '检查设备' }}</button>
    <button type="button" @click="stop(); status = '设备已关闭。'">关闭设备</button>
    <p><RouterLink to="/workspace">返回工作台</RouterLink></p>
  </main>
</template>

<style scoped>
.realtime-preview { max-width: 42rem; margin: 2rem auto; padding: 1rem; line-height: 1.6; }
fieldset { display: flex; flex-wrap: wrap; gap: 1rem; margin: 1rem 0; }
label { min-height: 44px; display: inline-flex; align-items: center; gap: .4rem; }
button { min-height: 44px; margin: .25rem .5rem .25rem 0; }
video { width: 100%; max-width: 30rem; background: #222; border-radius: .5rem; }
</style>
