import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-dom'

test('learning route entry is outside the legacy report.route branch', () => {
  const source = readFileSync(new URL('../src/views/CareerResultView.vue', import.meta.url), 'utf8')
  const template = source.match(/<template>([\s\S]*?)<\/template>\s*<style/)[1]
  const ast = parse(template)
  let entry
  function visit(node, ancestors = []) {
    if (node.type === 1 && node.tag === 'RouterLink' && node.props.some(p => p.type === 7 && p.name === 'bind' && p.exp?.content.includes("name: 'learning-route'"))) entry = ancestors
    for (const child of node.children ?? []) visit(child, [...ancestors, node])
  }
  visit(ast)
  assert.ok(entry, 'learning route link should exist')
  assert.ok(!entry.some(n => n.type === 1 && n.props.some(p => p.type === 7 && p.name === 'else')), 'link must be available for route reports')
})
