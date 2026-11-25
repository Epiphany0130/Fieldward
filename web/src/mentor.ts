import { ApiError, requestApi } from './api'

export interface MentorTurn {
  id: string
  requestId: string
  studentMessage: string
  mentorReply: string | null
  status: 'STREAMING' | 'COMPLETED' | 'INTERRUPTED'
  createdAt: string
}

export interface MentorHistory {
  accountId: string
  available: boolean
  turns: MentorTurn[]
  nextBefore: string | null
  active: MentorTurn | null
  watched: MentorTurn | null
}

export interface StudentMessage {
  requestId: string
  content: string
}

type MentorEvent =
  | { name: 'turn' | 'done' | 'interrupted'; turn: MentorTurn }
  | { name: 'delta'; text: string }

function readEvent(frame: string): MentorEvent | null {
  let name = ''
  const data: string[] = []
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length) return null
  if (name === 'delta') return { name, text: JSON.parse(data.join('\n')).text }
  if (name === 'turn' || name === 'done' || name === 'interrupted') {
    return { name, turn: JSON.parse(data.join('\n')) }
  }
  return null
}

export async function streamMentor(accountId: string, message: StudentMessage, signal: AbortSignal,
                                   receive: (event: MentorEvent) => void) {
  const response = await requestApi('/mentor/turns', 'POST', { accountId, ...message },
    'text/event-stream, application/problem+json', signal)
  if (!response.body || !response.headers.get('Content-Type')?.includes('text/event-stream')) {
    throw new ApiError(0, '未能连接 Mentor，请稍后重试。')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true })
      for (let separator = /\r?\n\r?\n/.exec(buffer); separator; separator = /\r?\n\r?\n/.exec(buffer)) {
        const event = readEvent(buffer.slice(0, separator.index))
        buffer = buffer.slice(separator.index + separator[0].length)
        if (!event) continue
        receive(event)
        if (event.name === 'done' || event.name === 'interrupted') return
      }
      if (done || buffer.length > 96000) {
        throw new ApiError(0, '连接已中断，可以重试这条消息。')
      }
    }
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
