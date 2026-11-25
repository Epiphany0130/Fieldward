<script setup lang="ts">
import { ref } from 'vue'
import type { Profile } from '../resumeDraft'

const profile = defineModel<Profile>({ required: true })
defineProps<{ invalid: boolean }>()
const emit = defineEmits<{ education: [] }>()
const photoError = ref('')
const processing = ref(false)

async function changePhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  photoError.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    photoError.value = '请选择 5 MB 以内的 JPG、PNG 或 WebP 图片。'
    return
  }
  processing.value = true
  let bitmap: ImageBitmap | undefined
  try {
    bitmap = await createImageBitmap(file)
    const edge = Math.min(bitmap.width, bitmap.height)
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 320
    const context = canvas.getContext('2d')
    if (!context) throw new Error('Canvas unavailable')
    context.fillStyle = '#fff'
    context.fillRect(0, 0, 320, 320)
    context.drawImage(bitmap, (bitmap.width - edge) / 2, (bitmap.height - edge) / 2, edge, edge, 0, 0, 320, 320)
    const photo = canvas.toDataURL('image/jpeg', .82)
    if (photo.length > 150000) throw new Error('Photo too large')
    profile.value.photo = photo
  } catch {
    photoError.value = '无法处理这张图片，请尝试另一张。'
  } finally {
    bitmap?.close()
    processing.value = false
  }
}
</script>

<template>
  <div class="field">
    <label for="resume-name">姓名 <span class="required-label">必填</span></label>
    <input id="resume-name" v-model="profile.name" autocomplete="name" maxlength="80"
           placeholder="填写简历上显示的姓名" required :aria-invalid="invalid"
           :aria-describedby="invalid ? 'resume-name-error' : undefined" />
    <span v-if="invalid" id="resume-name-error" class="field-error">请填写姓名</span>
  </div>
  <div class="editor-grid">
    <label class="field"><span>邮箱</span><input v-model="profile.email" inputmode="email" autocomplete="email" maxlength="254" /></label>
    <label class="field"><span>电话</span><input v-model="profile.phone" type="tel" autocomplete="tel" maxlength="40" /></label>
  </div>
  <details class="editor-details">
    <summary>求职意向</summary>
    <div class="detail-fields">
      <label class="field">
        <span>当前状态</span>
        <select v-model="profile.career.status">
          <option value="">请选择</option><option>在校找实习</option><option>准备校招</option><option>应届求职</option><option>其他</option>
        </select>
      </label>
      <div class="editor-grid">
        <label class="field"><span>目标岗位</span><input v-model="profile.career.position" maxlength="80" placeholder="例如：后端开发实习生" /></label>
        <label class="field"><span>期望城市</span><input v-model="profile.career.preferredCity" maxlength="80" /></label>
      </div>
    </div>
  </details>
  <details class="editor-details">
    <summary>照片和其他联系方式</summary>
    <div class="detail-fields">
      <div v-if="profile.photo" class="photo-choice">
        <img :src="profile.photo" alt="当前简历照片" width="50" height="50" />
        <button class="button button-quiet" type="button" :disabled="processing" @click="profile.photo = ''">移除照片</button>
      </div>
      <label class="field"><span>简历照片</span><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="processing" @change="changePhoto" /></label>
      <p v-if="photoError" class="field-error" role="alert">{{ photoError }}</p>
      <div class="editor-grid">
        <label class="field"><span>所在城市</span><input v-model="profile.links.city" maxlength="80" /></label>
        <label class="field"><span>微信号</span><input v-model="profile.links.wechat" maxlength="80" /></label>
      </div>
      <label class="field"><span>个人网站</span><input v-model="profile.links.website" inputmode="url" maxlength="300" placeholder="https://" /></label>
      <label class="field"><span>GitHub 链接</span><input v-model="profile.links.github" inputmode="url" maxlength="300" placeholder="https://" /></label>
    </div>
  </details>
  <div class="editor-note">
    <p>暂时不知道经历和技能怎么写，可以先填好姓名和学校，再提交第一版。</p>
    <button type="button" @click="emit('education')">去填写学校 <span aria-hidden="true">→</span></button>
  </div>
</template>

<style scoped>
.photo-choice { display: flex; align-items: center; gap: .65rem; }
.photo-choice img { object-fit: cover; border: 1px solid var(--outline); border-radius: .45rem; }
input[type="file"] { max-width: 100%; }
</style>
