<script lang="ts">
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({ html: false, breaks: true })
// Show image descriptions without fetching external images from a reply.
markdown.renderer.rules.image = (tokens, index) => markdown.utils.escapeHtml(tokens[index].content)
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()
const rendered = computed(() => markdown.render(props.content))
</script>

<template>
  <div class="mentor-markdown" v-html="rendered"></div>
</template>

<style scoped>
.mentor-markdown { min-width: 0; line-height: 1.65; }
.mentor-markdown :deep(> * + *) { margin-top: .6rem; }
.mentor-markdown :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin-bottom: .25rem;
  font-size: 1.05em;
  line-height: 1.4;
  letter-spacing: normal;
}
.mentor-markdown :deep(:is(ul, ol)) { padding-inline-start: 1.45em; margin-bottom: 0; }
.mentor-markdown :deep(li + li) { margin-top: .2rem; }
.mentor-markdown :deep(blockquote) {
  margin-inline: 0;
  margin-bottom: 0;
  padding-inline-start: .75rem;
  border-inline-start: 2px solid var(--outline);
  color: var(--muted);
}
.mentor-markdown :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: .9em;
}
.mentor-markdown :deep(:not(pre) > code) {
  padding: .08rem .28rem;
  border-radius: .2rem;
  background: #e7ede3;
}
.mentor-markdown :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  margin-bottom: 0;
  padding: .65rem .75rem;
  border-radius: .45rem;
  background: #e7ede3;
}
.mentor-markdown :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}
.mentor-markdown :deep(:is(th, td)) { padding: .3rem .55rem; border: 1px solid var(--outline); text-align: start; }
.mentor-markdown :deep(hr) { border: 0; border-top: 1px solid var(--outline); }
</style>
