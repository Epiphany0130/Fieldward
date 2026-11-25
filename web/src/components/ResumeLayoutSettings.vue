<script setup lang="ts">
import { ref } from 'vue'
import { defaultAppearance, resumeTemplates, type ResumeLayout } from '../resumeDraft'

const layout = defineModel<ResumeLayout>({ required: true })
defineProps<{ busy: boolean }>()
const tab = ref<'templates' | 'type' | 'page'>('templates')

function changeDensity(event: Event) {
  const density = (event.target as HTMLSelectElement).value as ResumeLayout['density']
  layout.value.density = density
  const preset = defaultAppearance(density)
  layout.value.appearance.lineSpacing = preset.lineSpacing
  layout.value.appearance.sectionGap = preset.sectionGap
}

function resetLayout() {
  layout.value.theme = 'RULED'
  layout.value.density = 'STANDARD'
  layout.value.appearance = defaultAppearance()
}
</script>

<template>
  <section id="resume-format" class="format-settings" aria-label="成稿版式设置">
    <nav aria-label="版式设置栏目" class="format-tabs">
      <button type="button" :aria-pressed="tab === 'templates'" @click="tab = 'templates'">模板与颜色</button>
      <button type="button" :aria-pressed="tab === 'type'" @click="tab = 'type'">字体与页眉</button>
      <button type="button" :aria-pressed="tab === 'page'" @click="tab = 'page'">纸张与间距</button>
    </nav>
    <fieldset :disabled="busy">
      <template v-if="tab === 'templates'">
        <div class="template-grid">
          <button v-for="template in resumeTemplates" :key="template.id" type="button"
                  :aria-pressed="layout.theme === template.id" @click="layout.theme = template.id">
            <span class="template-sheet" :data-theme="template.id" aria-hidden="true">
              <i></i><i></i><i></i><i></i>
            </span>
            <strong>{{ template.name }}</strong><small>{{ template.detail }}</small>
          </button>
        </div>
        <label class="field">
          <span>主题色</span>
          <select v-model="layout.appearance.accent">
            <option value="FOREST">循职深绿</option><option value="GRAPHITE">石墨黑</option><option value="NAVY">钢笔蓝</option><option value="SIENNA">陶土棕</option>
          </select>
        </label>
      </template>
      <div v-else-if="tab === 'type'" class="format-grid">
        <label class="field">
          <span>正文字体</span>
          <select v-model="layout.appearance.font"><option value="SANS">无衬线</option><option value="SERIF">衬线</option></select>
        </label>
        <label class="field">
          <span>正文字号</span>
          <select v-model.number="layout.appearance.fontSize"><option v-for="size in [9, 10, 11, 12, 13]" :key="size" :value="size">{{ size }} pt</option></select>
        </label>
        <label class="field">
          <span>页眉对齐</span>
          <select v-model="layout.appearance.alignment"><option value="LEFT">左对齐</option><option value="CENTER">居中</option><option value="RIGHT">右对齐</option></select>
        </label>
        <label class="field">
          <span>联系信息</span>
          <select v-model="layout.appearance.detailsStyle"><option value="INLINE">仅显示内容</option><option value="LABELED">显示字段标签</option></select>
        </label>
      </div>
      <template v-else>
        <label class="field">
          <span>排版密度</span>
          <select :value="layout.density" @change="changeDensity">
            <option value="COMPACT">紧凑</option><option value="STANDARD">标准</option><option value="RELAXED">宽松</option>
          </select>
        </label>
        <div class="format-grid">
          <label class="field"><span>行距 <output>{{ (layout.appearance.lineSpacing / 100).toFixed(2) }} 倍</output></span><input v-model.number="layout.appearance.lineSpacing" type="range" min="120" max="190" step="1" /></label>
          <label class="field"><span>栏目间距 <output>{{ layout.appearance.sectionGap }} mm</output></span><input v-model.number="layout.appearance.sectionGap" type="range" min="2" max="10" step="1" /></label>
          <label class="field"><span>标题间距 <output>{{ layout.appearance.headingGap }} mm</output></span><input v-model.number="layout.appearance.headingGap" type="range" min="1" max="6" step="1" /></label>
          <label class="field"><span>内容缩进 <output>{{ layout.appearance.contentIndent }} mm</output></span><input v-model.number="layout.appearance.contentIndent" type="range" min="0" max="8" step="1" /></label>
          <label class="field"><span>页眉边距 <output>{{ layout.appearance.topMargin }} mm</output></span><input v-model.number="layout.appearance.topMargin" type="range" min="8" max="25" step="1" /></label>
          <label class="field"><span>左侧边距 <output>{{ layout.appearance.leftMargin }} mm</output></span><input v-model.number="layout.appearance.leftMargin" type="range" min="8" max="25" step="1" /></label>
          <label class="field"><span>右侧边距 <output>{{ layout.appearance.rightMargin }} mm</output></span><input v-model.number="layout.appearance.rightMargin" type="range" min="8" max="25" step="1" /></label>
          <label class="field"><span>页底边距 <output>{{ layout.appearance.bottomMargin }} mm</output></span><input v-model.number="layout.appearance.bottomMargin" type="range" min="8" max="25" step="1" /></label>
        </div>
      </template>
      <button class="button button-quiet reset-layout" type="button" @click="resetLayout">恢复默认排版</button>
    </fieldset>
  </section>
</template>

<style scoped>
.format-settings { padding: .85rem; border-bottom: 1px solid #dce4d8; background: var(--surface); }
.format-tabs { display: flex; flex-wrap: wrap; gap: .35rem; margin-bottom: .75rem; }
.format-tabs button {
  min-height: 44px; border: 1px solid var(--outline); border-radius: .45rem;
  padding-inline: .55rem; background: transparent; color: var(--muted); cursor: pointer;
}
.format-tabs [aria-pressed="true"], .template-grid [aria-pressed="true"] {
  background: #e8efe4; border-color: #759581; color: var(--primary);
}
fieldset { border: 0; min-width: 0; padding: 0; margin: 0; display: grid; gap: .75rem; }
.format-grid, .template-grid { display: grid; gap: .65rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.template-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.template-grid button {
  display: grid; justify-items: start; min-height: 44px; gap: .3rem; padding: .45rem;
  border: 1px solid var(--outline); border-radius: .5rem; color: var(--ink); background: #fff; text-align: left; cursor: pointer;
}
.template-grid small { color: var(--muted); font-size: .73rem; }
.template-sheet {
  display: grid; align-content: start; gap: .27rem; width: 2.6rem; height: 3.15rem; padding: .37rem;
  background: white; border: 1px solid #d4ddd3;
}
.template-sheet i { height: 2px; background: #b4c5b8; }
.template-sheet i:first-child { width: 52%; height: 4px; background: #2f5a43; }
.template-sheet[data-theme="EDITORIAL"] { border-top: 2px solid #76543c; }
.template-sheet[data-theme="MODERN"] i:first-child { width: 100%; height: .7rem; background: #d3e4d7; }
.reset-layout { justify-self: start; font-size: .85rem; }
output { color: var(--muted); font-size: .8em; }
input[type="range"] { padding: 0; accent-color: var(--primary); }
@media (max-width: 480px) { .template-grid { grid-template-columns: minmax(0, 1fr); } .template-sheet { display: none; } }
</style>
