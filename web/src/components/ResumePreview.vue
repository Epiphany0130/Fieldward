<script setup lang="ts">
import { computed, type CSSProperties } from 'vue'
import { activeSections, hasEducation, resumeSectionTitle, safeLink, type Proficiency, type ResumeContent, type ResumePanel } from '../resumeDraft'

const props = withDefaults(defineProps<{ content: ResumeContent; editable?: boolean }>(), { editable: true })
const emit = defineEmits<{ edit: [panel: ResumePanel] }>()

const proficiencyLabels: Record<Proficiency, string> = {
  EXPOSURE: '仅接触过',
  GUIDED: '在指导下用过',
  INDEPENDENT: '能独立使用',
}
const appearance = computed(() => props.content.layout.appearance)
const styles = computed<CSSProperties>(() => ({
  '--font-size': appearance.value.fontSize,
  '--line-spacing': appearance.value.lineSpacing / 100,
  '--pad-top': appearance.value.topMargin,
  '--pad-left': appearance.value.leftMargin,
  '--pad-right': appearance.value.rightMargin,
  '--pad-bottom': appearance.value.bottomMargin,
  '--section-gap': appearance.value.sectionGap,
  '--content-indent': appearance.value.contentIndent,
  '--heading-gap': appearance.value.headingGap,
}))
const contact = computed(() => {
  const { email, phone, links } = props.content.profile
  return [
    { label: '电话', value: phone },
    { label: '邮箱', value: email },
    { label: '城市', value: links.city },
    { label: '微信', value: links.wechat },
    { label: '网站', value: links.website, href: safeLink(links.website) },
    { label: 'GitHub', value: links.github, href: safeLink(links.github) },
  ].filter(({ value }) => value.trim())
})
const career = computed(() => {
  const { status, position, preferredCity } = props.content.profile.career
  return [position, preferredCity, status].filter((value) => value.trim()).join(' · ')
})
const educationEntries = computed(() => {
  const entries = [props.content.education, ...props.content.additionalEducation].filter(hasEducation)
  return entries.length || !props.editable ? entries : [props.content.education]
})
const sections = computed(() => activeSections(props.content).filter((id) => !props.content.layout.hiddenSections.includes(id)).map((id) => ({
  id,
  title: resumeSectionTitle(props.content.layout, id),
  items: props.content.experiences.filter((experience) => experience.type === id),
})).filter(({ id, items }) => id === 'SKILLS' ? props.content.skills.length : items.length))

function lines(value: string) {
  return value.split(/\r?\n/).map((line) => line.trim().replace(/^(?:[-•]|\d+[.)])\s+/, '')).filter(Boolean)
}
</script>

<template>
  <article class="resume-paper" aria-label="简历预览内容" :data-theme="content.layout.theme"
           :data-font="appearance.font" :data-accent="appearance.accent" :data-alignment="appearance.alignment"
           :style="styles">
    <header class="paper-header">
      <div class="paper-identity">
        <component :is="editable ? 'h3' : 'h2'" class="paper-name">
          <button v-if="editable" type="button" :class="{ placeholder: !content.profile.name.trim() }" @click="emit('edit', 'PROFILE')">
            {{ content.profile.name.trim() || '你的姓名' }}
          </button>
          <span v-else>{{ content.profile.name }}</span>
        </component>
        <p v-if="career" class="paper-career">{{ career }}</p>
        <ul v-if="contact.length" class="paper-contact">
          <li v-for="item in contact" :key="item.label">
            <span v-if="appearance.detailsStyle === 'LABELED'">{{ item.label }}｜</span>
            <a v-if="item.href" :href="item.href" target="_blank" rel="noopener noreferrer">{{ item.value }}</a>
            <span v-else>{{ item.value }}</span>
          </li>
        </ul>
      </div>
      <img v-if="content.profile.photo" class="paper-photo" :src="content.profile.photo" alt="简历照片" />
    </header>

    <section v-if="educationEntries.length" class="paper-section">
      <component :is="editable ? 'h4' : 'h3'" class="paper-section-heading">
        <button v-if="editable" type="button" @click="emit('edit', 'EDUCATION')">教育背景</button>
        <span v-else>教育背景</span>
      </component>
      <div v-for="(item, index) in educationEntries" :key="index" class="paper-entry">
        <div class="paper-line">
          <strong v-if="item.school || editable" :class="{ placeholder: !item.school.trim() }">
            {{ item.school || '学校名称' }}
          </strong>
          <span v-if="item.details.start || item.graduation" class="paper-detail">
            {{ [item.details.start, item.graduation].filter((value) => value.trim()).join(' — ') }}
          </span>
        </div>
        <p v-if="item.major || item.degree || item.details.department || item.details.studyType || item.details.city" class="paper-detail">
          {{ [item.details.department, item.major, item.degree, item.details.studyType, item.details.city].filter((value) => value.trim()).join(' · ') }}
        </p>
        <p v-if="item.details.notes.trim()" class="paper-text">{{ item.details.notes }}</p>
      </div>
    </section>

    <section v-for="section in sections" :key="section.id" class="paper-section">
      <component :is="editable ? 'h4' : 'h3'" class="paper-section-heading">
        <button v-if="editable" type="button" @click="emit('edit', section.id)">{{ section.title }}</button>
        <span v-else>{{ section.title }}</span>
      </component>
      <ul v-if="section.id === 'SKILLS'" class="paper-skills">
        <li v-for="(skill, index) in content.skills" :key="index">
          <strong v-if="skill.name.trim()">{{ skill.name }}</strong>
          <span v-if="skill.name.trim()" class="paper-detail"> · {{ proficiencyLabels[skill.proficiency] }}</span>
          <p v-if="skill.description.trim()" class="paper-text">{{ skill.description }}</p>
        </li>
      </ul>
      <template v-else>
        <div v-for="(item, index) in section.items" :key="index" class="paper-entry">
          <div v-if="item.title.trim() || item.period.trim()" class="paper-line">
            <strong v-if="item.title.trim()">{{ item.title }}</strong>
            <span v-if="item.period.trim()" class="paper-detail">{{ item.period }}</span>
          </div>
          <p v-if="item.details.organization || item.role || item.details.city" class="paper-detail">
            {{ [item.details.organization, item.role, item.details.city].filter((value) => value.trim()).join(' · ') }}
          </p>
          <p v-if="item.details.link.trim()" class="paper-detail">
            <a v-if="safeLink(item.details.link)" :href="safeLink(item.details.link)!" target="_blank" rel="noopener noreferrer">{{ item.details.link }}</a>
            <span v-else>{{ item.details.link }}</span>
          </p>
          <p v-if="item.context.trim()" class="paper-text">{{ item.context }}</p>
          <template v-if="item.contribution.trim()">
            <p v-if="item.details.textStyle === 'PARAGRAPH'" class="paper-text">{{ item.contribution }}</p>
            <component :is="item.details.textStyle === 'NUMBERED' ? 'ol' : 'ul'" v-else class="paper-list">
              <li v-for="(line, lineIndex) in lines(item.contribution)" :key="lineIndex">{{ line }}</li>
            </component>
          </template>
        </div>
      </template>
    </section>
  </article>
</template>

<style scoped>
.resume-paper {
  --paper-accent: #2d513f;
  --paper-font: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  width: 100%; min-height: 23rem; aspect-ratio: 210 / 297; margin-inline: auto;
  padding: calc(var(--pad-top) * .47619%) calc(var(--pad-right) * .47619%)
    calc(var(--pad-bottom) * .47619%) calc(var(--pad-left) * .47619%);
  border: 1px solid #cbd6cd; background: white; box-shadow: 0 11px 30px #213d2921;
  color: #192720; font-family: var(--paper-font);
  font-size: calc(var(--font-size) * .086rem); line-height: var(--line-spacing); overflow-wrap: anywhere;
}
.resume-paper[data-accent="GRAPHITE"] { --paper-accent: #353936; }
.resume-paper[data-accent="NAVY"] { --paper-accent: #294868; }
.resume-paper[data-accent="SIENNA"] { --paper-accent: #7b4c33; }
.resume-paper[data-font="SERIF"] { --paper-font: "Songti SC", "Noto Serif CJK SC", Georgia, serif; }
.resume-paper[data-theme="EDITORIAL"] { background: #fffdfa; border-top: 4px solid var(--paper-accent); }
.paper-header { display: flex; align-items: flex-start; gap: 1rem; margin-bottom: 1.4em; }
.paper-identity { flex: 1; min-width: 0; }
.paper-name { margin: 0; font-size: 2.35em; line-height: 1.15; letter-spacing: -.045em; }
[data-theme="EDITORIAL"] .paper-name { font-family: "Songti SC", "Noto Serif CJK SC", Georgia, serif; }
[data-theme="MODERN"] .paper-header {
  padding: .8em 1em; border-inline-start: 4px solid var(--paper-accent);
  background: color-mix(in srgb, var(--paper-accent) 9%, white);
}
.paper-photo { width: 4.2em; height: 4.8em; flex: none; border-radius: .15em; object-fit: cover; }
[data-alignment="CENTER"] .paper-identity { text-align: center; }
[data-alignment="RIGHT"] .paper-identity { text-align: right; }
[data-alignment="CENTER"] .paper-contact { justify-content: center; }
[data-alignment="RIGHT"] .paper-contact { justify-content: end; }
.paper-career { color: var(--paper-accent); font-weight: 700; margin-top: .35em; }
.paper-section + .paper-section { margin-top: calc(var(--section-gap) * .23em); }
.paper-section-heading {
  margin: 0 0 calc(var(--heading-gap) * .19em); padding-bottom: .2em; border-bottom: 1px solid #bdc7bf;
  color: var(--paper-accent); font-size: 1.15em; line-height: 1.35;
}
[data-theme="EDITORIAL"] .paper-section-heading { border-bottom: 0; padding-inline-start: .48em; border-inline-start: 3px solid var(--paper-accent); }
[data-theme="MODERN"] .paper-section-heading { border-bottom: 1px solid var(--paper-accent); letter-spacing: .025em; }
.paper-header button, .paper-section button {
  font: inherit; font-weight: inherit; text-align: inherit; padding: 0;
  color: inherit; background: transparent; border: 0; cursor: pointer; overflow-wrap: anywhere; max-width: 100%;
}
.paper-header button:hover, .paper-section button:hover { text-decoration: underline; text-underline-offset: .2em; }
.paper-entry, .paper-skills { margin-inline-start: calc(var(--content-indent) * .22em); }
.paper-entry + .paper-entry { margin-top: .65em; }
.paper-entry p, .paper-skills p { margin-top: .14em; }
.paper-text { white-space: pre-wrap; }
.paper-line { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: .2em .7em; }
.paper-contact { display: flex; flex-wrap: wrap; gap: .1em .65em; margin: .5em 0 0; list-style: none; padding: 0; }
.paper-list, .paper-skills { margin-block: .2em 0; padding-inline-start: 1.3em; }
.paper-list li + li, .paper-skills li + li { margin-top: .17em; }
.paper-contact, .paper-detail { color: #46564d; }
.resume-paper a { color: var(--paper-accent); }
.placeholder, .paper-header button.placeholder { color: #607169; }

@media print {
  .resume-paper {
    width: auto; min-height: 0; aspect-ratio: auto; padding: 0; margin: 0; border: 0; box-shadow: none;
    background: white; font-size: calc(var(--font-size) * 1pt); print-color-adjust: exact; -webkit-print-color-adjust: exact;
  }
  .paper-header, .paper-entry { break-inside: avoid; }
  .paper-section-heading { break-after: avoid; }
  .paper-skills li { break-inside: avoid; }
  .paper-entry p, .paper-list li { orphans: 2; widows: 2; }
}
</style>
