import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-dom'

const source = readFileSync(new URL('../src/components/MentorChat.vue', import.meta.url), 'utf8')
test('model availability does not block composing, sending, or retrying messages', () => {
  const send = source.match(/async function send\([^]*?\n}/)[0]
  const canSend = source.match(/const canSend = [^\n]+/)[0]
  assert.doesNotMatch(send, /!available\.value/)
  assert.doesNotMatch(canSend, /available\.value/)
  const template = source.match(/<template>([\s\S]*)<\/template>\s*<style/)[1]
  const ast = parse(template)
  let textarea, retries = []
  function visit(node) {
    if (node.type === 1 && node.tag === 'textarea') textarea = node
    if (node.type === 1 && node.tag === 'button' && node.props.some(p => p.type === 7 && p.name === 'on' && /^(retry\(|send\(pending)/.test(p.exp?.content ?? ''))) retries.push(node)
    for (const child of node.children ?? []) visit(child)
  }
  visit(ast)
  assert.ok(textarea)
  for (const node of [textarea, ...retries]) assert.doesNotMatch(node.props.find(p => p.type === 7 && p.arg?.content === 'disabled')?.exp?.content ?? '', /available/)
  assert.equal(retries.length, 2)
  assert.match(template, /投递进度/)
  assert.match(source, /error\.status === 503/)
  assert.match(source, /模型未配置/)
})
