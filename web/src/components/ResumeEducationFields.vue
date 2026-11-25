<script setup lang="ts">
import type { Education } from '../resumeDraft'

const education = defineModel<Education>({ required: true })
defineProps<{ fieldId: string; invalid: boolean; primary: boolean }>()
</script>

<template>
  <div class="field">
    <label :for="fieldId">学校 <span v-if="primary" class="required-label">至少填写一所</span></label>
    <input :id="fieldId" v-model="education.school" maxlength="120" placeholder="学校全称"
           :aria-invalid="invalid" :aria-describedby="invalid ? 'resume-school-error' : undefined" />
    <span v-if="invalid" id="resume-school-error" class="field-error">请填写学校</span>
  </div>
  <div class="editor-grid">
    <label class="field"><span>专业</span><input v-model="education.major" maxlength="120" /></label>
    <label class="field"><span>学历</span><input v-model="education.degree" maxlength="30" placeholder="例如：本科" /></label>
    <label class="field"><span>入学时间</span><input v-model="education.details.start" maxlength="30" placeholder="例如：2024.09" /></label>
    <label class="field"><span>毕业时间</span><input v-model="education.graduation" maxlength="30" placeholder="例如：2028.06" /></label>
  </div>
  <details class="editor-details">
    <summary>学院与在校表现</summary>
    <div class="detail-fields">
      <label class="field"><span>学院</span><input v-model="education.details.department" maxlength="120" /></label>
      <div class="editor-grid">
        <label class="field">
          <span>学历类型</span>
          <select v-model="education.details.studyType">
            <option value="">请选择</option><option>全日制</option><option>非全日制</option><option>交换或访学</option><option>其他</option>
          </select>
        </label>
        <label class="field"><span>所在城市</span><input v-model="education.details.city" maxlength="80" /></label>
      </div>
      <label class="field"><span>在校经历</span><textarea v-model="education.details.notes" rows="3" maxlength="1000"
               placeholder="可填写真实的课程方向、GPA、专业排名或在校成果。" /></label>
    </div>
  </details>
</template>
