<script setup lang="ts">
import { computed } from 'vue'
import type { Experience, ExperienceType } from '../resumeDraft'

const experience = defineModel<Experience>({ required: true })
defineProps<{ fieldId: string }>()
const settings: Record<ExperienceType, {
  title: string; role?: string; organization?: string; period?: string
  city?: boolean; link?: string; context?: string; contribution: string
}> = {
  COURSEWORK: { title: '课程或作业名称', role: '个人分工', period: '完成时间', context: '题目与要求', contribution: '你完成的部分和结果' },
  PROJECT: { title: '项目名称', role: '担任角色', period: '起止时间', city: true, link: '项目链接', context: '项目背景', contribution: '项目详情与个人贡献' },
  INTERNSHIP: { title: '岗位名称', organization: '公司或机构', role: '部门或团队', period: '起止时间', city: true, context: '工作范围', contribution: '工作内容和成果' },
  ACTIVITY: { title: '活动名称', organization: '组织或社团', role: '承担职责', period: '活动时间', city: true, context: '活动背景', contribution: '你的工作和结果' },
  STRENGTH: { title: '要点标题', contribution: '个人优势与实例' },
  HONOR: { title: '奖项或证书名称', organization: '颁发机构', role: '级别或类别', period: '获奖或取得时间', link: '证明链接', contribution: '奖项说明' },
  RESEARCH: { title: '研究课题', organization: '实验室或组织', role: '研究分工', period: '起止时间', link: '成果链接', context: '研究方向或方法', contribution: '研究成果和个人贡献' },
  PORTFOLIO: { title: '作品名称', role: '个人分工', period: '完成时间', link: '作品链接', context: '作品背景', contribution: '作品简介' },
  OTHER: { title: '经历名称', organization: '关联机构', role: '角色或分工', period: '起止时间', city: true, link: '相关链接', context: '任务与背景', contribution: '经历说明' },
  CUSTOM: { title: '条目名称', organization: '关联机构', role: '角色或类别', period: '时间', link: '相关链接', context: '背景', contribution: '条目内容' },
}
const fields = computed(() => settings[experience.value.type])
</script>

<template>
  <label class="field">
    <span>{{ fields.title }}</span>
    <input :id="fieldId" v-model="experience.title" maxlength="120"
           :placeholder="experience.type === 'COURSEWORK' ? '例如：数据库课程作业' : undefined" />
  </label>
  <div v-if="fields.organization || fields.role || fields.period || fields.city" class="editor-grid">
    <label v-if="fields.organization" class="field"><span>{{ fields.organization }}</span><input v-model="experience.details.organization" maxlength="120" /></label>
    <label v-if="fields.role" class="field"><span>{{ fields.role }}</span><input v-model="experience.role" maxlength="80" /></label>
    <label v-if="fields.period" class="field"><span>{{ fields.period }}</span><input v-model="experience.period" maxlength="60" placeholder="例如：2026.03–2026.06" /></label>
    <label v-if="fields.city" class="field"><span>所在城市</span><input v-model="experience.details.city" maxlength="80" /></label>
  </div>
  <label v-if="fields.link" class="field"><span>{{ fields.link }}</span><input v-model="experience.details.link" inputmode="url" maxlength="300" placeholder="https://" /></label>
  <label v-if="fields.context" class="field"><span>{{ fields.context }}</span><textarea v-model="experience.context" rows="2" maxlength="1000" /></label>
  <label class="field"><span>{{ fields.contribution }}</span><textarea v-model="experience.contribution" rows="4" maxlength="2000" /></label>
  <label class="field">
    <span>正文排版</span>
    <select v-model="experience.details.textStyle">
      <option value="PARAGRAPH">段落</option><option value="BULLETS">项目符号，每行一项</option><option value="NUMBERED">编号，每行一项</option>
    </select>
  </label>
</template>
