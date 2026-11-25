<script setup lang="ts">
import { computed } from 'vue'
import type { ExperienceType, Proficiency, ResumeContent } from '../resumeDraft'

const props = defineProps<{ content: ResumeContent }>()

const experienceSections: { type: ExperienceType; title: string }[] = [
  { type: 'COURSEWORK', title: '课程与作业' },
  { type: 'PROJECT', title: '项目经历' },
  { type: 'INTERNSHIP', title: '实习经历' },
  { type: 'ACTIVITY', title: '校园活动' },
]
const proficiencyLabels: Record<Proficiency, string> = {
  EXPOSURE: '仅接触过',
  GUIDED: '在指导下用过',
  INDEPENDENT: '能独立使用',
}
const contact = computed(() => [props.content.profile.email, props.content.profile.phone].filter((value) => value.trim()))
const educationDetails = computed(() => [props.content.education.major, props.content.education.degree].filter((value) => value.trim()))
const sections = computed(() => experienceSections.map(({ type, title }) => ({
  title,
  items: props.content.experiences.filter((experience) => experience.type === type),
})).filter(({ items }) => items.length > 0))
</script>

<template>
  <article class="resume-paper" aria-label="简历预览内容">
    <header class="paper-header">
      <h3 :class="{ placeholder: !content.profile.name.trim() }">{{ content.profile.name.trim() || '你的姓名' }}</h3>
      <ul v-if="contact.length" class="paper-contact">
        <li v-for="(value, index) in contact" :key="index">{{ value }}</li>
      </ul>
    </header>

    <section class="paper-section">
      <h4>教育背景</h4>
      <div class="paper-line">
        <strong :class="{ placeholder: !content.education.school.trim() }">
          {{ content.education.school.trim() || '学校名称' }}
        </strong>
        <span v-if="content.education.graduation.trim()" class="paper-detail">{{ content.education.graduation }}</span>
      </div>
      <p v-if="educationDetails.length" class="paper-detail">{{ educationDetails.join(' · ') }}</p>
    </section>

    <section v-for="section in sections" :key="section.title" class="paper-section">
      <h4>{{ section.title }}</h4>
      <div v-for="(item, index) in section.items" :key="index" class="paper-entry">
        <strong v-if="item.title.trim()">{{ item.title }}</strong>
        <p v-if="item.context.trim()">{{ item.context }}</p>
        <p v-if="item.contribution.trim()">{{ item.contribution }}</p>
      </div>
    </section>

    <section v-if="content.skills.length" class="paper-section">
      <h4>专业技能</h4>
      <ul class="paper-skills">
        <li v-for="(skill, index) in content.skills" :key="index">
          <strong>{{ skill.name }}</strong>
          <span> · {{ proficiencyLabels[skill.proficiency] }}</span>
        </li>
      </ul>
    </section>
  </article>
</template>

<style scoped>
.resume-paper {
  width: min(100%, 38rem);
  min-height: 36rem;
  aspect-ratio: 210 / 297;
  margin-inline: auto;
  padding: clamp(1.45rem, 5vw, 3.6rem);
  border: 1px solid #d2d9d2;
  border-top: 4px solid #294e42;
  background: #fff;
  box-shadow: 0 8px 22px #172e2310;
  color: #1d2925;
  font-size: .91rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
}
.paper-header { margin-bottom: 2rem; }
.paper-header h3 { margin: 0; font-size: clamp(1.5rem, 3vw, 2.3rem); line-height: 1.25; letter-spacing: -.03em; }
.paper-section + .paper-section { margin-top: 1.35rem; }
.paper-section h4 {
  margin: 0 0 .55rem;
  padding-bottom: .25rem;
  border-bottom: 1px solid #aebcb3;
  color: #285043;
  font-size: 1rem;
}
.paper-entry + .paper-entry { margin-top: .8rem; }
.paper-entry p { white-space: pre-wrap; margin-top: .2rem; }
.paper-line { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: .2rem .75rem; }
.paper-contact, .paper-skills { list-style: none; padding: 0; margin: 0; }
.paper-contact { display: flex; flex-wrap: wrap; gap: .15rem 1rem; margin-top: .6rem; }
.paper-contact, .paper-detail, .paper-skills span { color: #4b5e55; }
.placeholder { color: #63776d; }

@media (max-width: 740px) {
  .resume-paper { min-height: 29rem; }
  .paper-header { margin-bottom: 1.35rem; }
}
</style>
