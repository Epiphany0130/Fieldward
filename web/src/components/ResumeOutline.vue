<script setup lang="ts">
import { computed, ref } from 'vue'
import { resumeSections, resumeSectionTitle, type ResumeContent, type ResumePanel, type ResumeSection } from '../resumeDraft'

const props = defineProps<{ content: ResumeContent; panel: ResumePanel; busy: boolean }>()
const emit = defineEmits<{
  select: [panel: ResumePanel]
  add: [section: ResumeSection]
}>()
const picker = ref<HTMLDetailsElement>()
const schoolFilled = computed(() => [props.content.education, ...props.content.additionalEducation]
  .some(({ school }) => school.trim()))
const outline = computed(() => [
  { id: 'PROFILE' as const, label: '个人信息', icon: '人', status: props.content.profile.name.trim() ? '已填' : '' },
  { id: 'EDUCATION' as const, label: '教育背景', icon: '学', status: schoolFilled.value ? '已填' : '' },
  ...props.content.layout.sections.map((id) => {
    const section = resumeSections.find((entry) => entry.id === id)!
    const count = id === 'SKILLS'
      ? props.content.skills.length
      : props.content.experiences.filter((item) => item.type === id).length
    return {
      ...section, label: resumeSectionTitle(props.content.layout, id),
      status: props.content.layout.hiddenSections.includes(id) ? '已隐藏' : count ? String(count) : '',
    }
  }),
])
const available = computed(() => resumeSections.filter(({ id }) => !props.content.layout.sections.includes(id)))

function addSection(section: ResumeSection) {
  if (picker.value) picker.value.open = false
  emit('add', section)
}
</script>

<template>
  <aside class="outline surface" aria-label="简历栏目">
    <header class="outline-heading">
      <h2>简历栏目</h2>
      <p>{{ outline.length }} 个栏目</p>
    </header>
    <nav aria-label="已启用的简历栏目">
      <ul>
        <li v-for="item in outline" :key="item.id">
          <button type="button" :aria-current="panel === item.id ? 'true' : undefined" @click="emit('select', item.id)">
            <span class="section-glyph" aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
            <span v-if="item.status" class="section-count">{{ item.status }}</span>
          </button>
        </li>
      </ul>
    </nav>
    <details v-if="available.length" ref="picker" class="section-picker">
      <summary>＋ 添加栏目</summary>
      <div>
        <button v-for="item in available" :key="item.id" type="button" :disabled="busy" @click="addSection(item.id)">
          {{ item.label }}
        </button>
      </div>
    </details>
    <div class="outline-checklist">
      <p>提交所需</p>
      <button type="button" @click="emit('select', 'PROFILE')">
        <span>姓名</span><span>{{ content.profile.name.trim() ? '已填写' : '待填写' }}</span>
      </button>
      <button type="button" @click="emit('select', 'EDUCATION')">
        <span>学校</span><span>{{ schoolFilled ? '已填写' : '待填写' }}</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.outline { padding: .85rem; min-width: 0; }
.outline-heading { display: flex; justify-content: space-between; align-items: baseline; gap: .4rem; margin: .25rem .25rem .75rem; }
.outline-heading h2 { font-size: 1rem; }
.outline-heading p, .section-count, .outline-checklist button span:last-child { font-size: .73rem; color: var(--muted); }
.outline ul { list-style: none; padding: 0; margin: 0; display: grid; gap: .22rem; }
.outline button, .section-picker summary {
  display: flex; align-items: center; width: 100%; padding: .4rem .55rem; gap: .55rem; min-height: 44px;
  border: 1px solid transparent; border-radius: .5rem; color: var(--ink); background: transparent;
  text-align: left; font-weight: 620; cursor: pointer;
}
.outline button:hover, .section-picker summary:hover { background: #ecf0e8; }
.outline button[aria-current] { background: #e4ede3; border-color: #c9d8cc; color: #214c37; }
.section-glyph {
  display: grid; place-items: center; width: 1.55rem; height: 1.55rem;
  border-radius: .35rem; background: #e8ede4; color: #365547; font-size: .73rem; font-weight: 700;
}
button[aria-current] .section-glyph { background: #2b6048; color: #fff; }
.section-count, .outline-checklist button span:last-child { margin-inline-start: auto; }
.section-picker { margin-top: .75rem; }
.section-picker summary { border-color: var(--outline); list-style: none; color: var(--primary); }
.section-picker summary::-webkit-details-marker { display: none; }
.section-picker[open] summary { background: #e8eee4; }
.section-picker div { max-height: 17rem; overflow: auto; margin-top: .3rem; padding: .2rem; border: 1px solid #dbe2d9; border-radius: .45rem; }
.outline-checklist { margin-top: 1rem; padding-top: .75rem; border-top: 1px solid #dfe5dc; }
.outline-checklist p { margin-bottom: .2rem; padding-inline: .55rem; color: var(--muted); font-size: .75rem; font-weight: 650; }
.outline-checklist button { font-weight: 500; }
@media (max-width: 620px) {
  .outline ul { display: flex; flex-wrap: wrap; }
  .outline ul li { flex: 0 1 auto; }
  .outline-checklist { display: none; }
  .section-picker div { display: flex; flex-wrap: wrap; gap: .1rem; }
  .section-picker div button { width: auto; }
}
</style>
