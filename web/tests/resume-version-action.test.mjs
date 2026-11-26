import test from 'node:test'
import assert from 'node:assert/strict'
import { performVersionAction } from '../src/resumeVersionAction.ts'

test('uses clicked version when navigation occurs while the baseline loads', async () => {
  let release
  const baseline = new Promise(resolve => { release = resolve })
  let selected = 1
  let requested
  const pending = performVersionAction(selected, () => baseline, () => true, (version, snapshot) => { requested = { version, snapshot } })
  selected = 2
  release({ revision: 3 })
  await pending
  assert.deepEqual(requested, { version: 1, snapshot: { revision: 3 } })
})
