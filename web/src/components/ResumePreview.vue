<script setup lang="ts">
import { computed } from 'vue'
import { resumeSections, type Proficiency, type ResumeContent, type ResumePanel } from '../resumeDraft'

const props = defineProps<{ content: ResumeContent }>()
const emit = defineEmits<{ edit: [panel: ResumePanel] }>()

const proficiencyLabels: Record<Proficiency, string> = {
  EXPOSURE: '仅接触过',
  GUIDED: '在指导下用过',
  INDEPENDENT: '能独立使用',
}
const contact = computed(() => [props.content.profile.email, props.content.profile.phone].filter((value) => value.trim()))
const educationDetails = computed(() => [props.content.education.major, props.content.education.degree].filter((value) => value.trim()))
const sections = computed(() => props.content.layout.sections.map((id) => ({
  id,
  title: resumeSections.find((item) => item.id === id)!.label,
  items: props.content.experiences.filter((experience) => experience.type === id),
})).filter(({ id, items }) => id === 'SKILLS' ? props.content.skills.length : items.length))
</script>

<template>
  <article class="resume-paper" aria-label="简历预览内容" :data-theme="content.layout.theme" :data-density="content.layout.density">
    <header class="paper-header">
      <h3>
        <button type="button" :class="{ placeholder: !content.profile.name.trim() }" @click="emit('edit', 'PROFILE')">
          {{ content.profile.name.trim() || '你的姓名' }}
        </button>
      </h3>
      <ul v-if="contact.length" class="paper-contact">
        <li v-for="(value, index) in contact" :key="index">{{ value }}</li>
      </ul>
    </header>
    <section class="paper-section">
      <h4><button type="button" @click="emit('edit', 'EDUCATION')">教育背景</button></h4>
      <div class="paper-line">
        <strong :class="{ placeholder: !content.education.school.trim() }">
          {{ content.education.school.trim() || '学校名称' }}
        </strong>
        <span v-if="content.education.graduation.trim()" class="paper-detail">{{ content.education.graduation }}</span>
      </div>
      <p v-if="educationDetails.length" class="paper-detail">{{ educationDetails.join(' · ') }}</p>
    </section>
    <section v-for="section in sections" :key="section.id" class="paper-section">
      <h4><button type="button" @click="emit('edit', section.id)">{{ section.title }}</button></h4>
      <ul v-if="section.id === 'SKILLS'" class="paper-skills">
        <li v-for="(skill, index) in content.skills" :key="index">
          <strong>{{ skill.name }}</strong><span> · {{ proficiencyLabels[skill.proficiency] }}</span>
        </li>
      </ul>
      <template v-else>
        <div v-for="(item, index) in section.items" :key="index" class="paper-entry">
          <div v-if="item.title.trim() || item.period.trim()" class="paper-line">
            <strong>{{ item.title }}</strong>
            <span v-if="item.period.trim()" class="paper-detail">{{ item.period }}</span>
          </div>
          <p v-if="item.role.trim()" class="paper-detail">{{ item.role }}</p>
          <p v-if="item.context.trim()">{{ item.context }}</p>
          <p v-if="item.contribution.trim()">{{ item.contribution }}</p>
        </div>
      </template>
    </section>
  </article>
</template>

<style scoped>
.resume-paper {
  --paper-accent: #2d513f;
  --paper-gap: 1.2rem;
  --paper-padding: clamp(1.5rem, 4vw, 3.25rem);
  width: 100%;
  min-height: 23rem;
  aspect-ratio: 210 / 297;
  margin-inline: auto;
  padding: var(--paper-padding);
  border: 1px solid #cbd6cd;
  background: #fff;
  box-shadow: 0 11px 30px #213d2921;
  color: #1c2b23;
  font-size: .9rem;
  line-height: 1.58;
  overflow-wrap: anywhere;
}
.resume-paper[data-density="COMPACT"] { --paper-gap: .7rem; line-height: 1.4; }
.resume-paper[data-density="RELAXED"] { --paper-gap: 1.8rem; line-height: 1.72; }
.resume-paper[data-theme="EDITORIAL"] { --paper-accent: #6a4b33; background: #fffdfa; border-top: 4px solid var(--paper-accent); }
.paper-header { margin-bottom: calc(var(--paper-gap) + .45rem); }
.paper-header h3 { margin: 0; font-size: clamp(1.6rem, 3vw, 2.35rem); line-height: 1.22; letter-spacing: -.045em; }
[data-theme="EDITORIAL"] .paper-header h3 { font-family: "Songti SC", "Noto Serif CJK SC", serif; }
.paper-section + .paper-section { margin-top: var(--paper-gap); }
.paper-section h4 {
  margin: 0 0 .45rem; padding-bottom: .22rem;
  border-bottom: 1px solid #c1cdc2; color: var(--paper-accent); font-size: 1rem;
}
[data-theme="EDITORIAL"] .paper-section h4 { border-bottom: 0; padding-inline-start: .55rem; border-inline-start: 3px solid var(--paper-accent); }
.paper-header button, .paper-section button {
  font: inherit; font-weight: inherit; text-align: start; padding: 0;
  color: inherit; background: transparent; border: 0; cursor: pointer; overflow-wrap: anywhere; max-width: 100%;
}
.paper-header button:hover, .paper-section button:hover { text-decoration: underline; text-underline-offset: .2em; }
.paper-entry + .paper-entry { margin-top: calc(var(--paper-gap) * .7); }
.paper-entry p { white-space: pre-wrap; margin-top: .2rem; }
.paper-line { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: .2rem .75rem; }
.paper-contact, .paper-skills { list-style: none; padding: 0; margin: 0; }
.paper-contact { display: flex; flex-wrap: wrap; gap: .15rem .9rem; margin-top: .6rem; }
.paper-contact, .paper-detail, .paper-skills span { color: #4f6056; }
.placeholder, .paper-header button.placeholder { color: #607469; }
</style>
