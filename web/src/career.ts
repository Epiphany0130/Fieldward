export interface CareerOption {
  id: string
  title: string
  description: string
  roles: string
  topics: string[]
}

export interface CareerSkill {
  skillId: string
  title: string
  weight: number
  core: boolean
  level: 'PROJECT' | 'INFERRED' | 'MENTION' | 'MISSING' | 'UNKNOWN'
  quote: string | null
  inference: string | null
}

export interface CareerStep {
  skillId: string
  title: string
  objective: string
  task: string
}

export interface CareerReport {
  matchPercent: number | null
  stage: 'LEARNING' | 'INTERVIEW_READY'
  interviewThreshold: number
  coreCovered: boolean
  sourceQuality: 'READABLE' | 'OCR' | 'UNREADABLE'
  skills: CareerSkill[]
  roadmap: CareerStep[]
  route: { sourceUrl: string | null; labels?: Record<string, string> | null; phases: { title: string; topics: string[] }[] } | null
  topicSuggestions: Record<string, { mark: SkillMark; quote: string; basis: string }> | null
}

export interface CareerAssessment {
  id: string
  resumeVersion: number
  directionId: string
  directionTitle: string
  status: 'WAITING_TEXT' | 'ANALYZING' | 'READY' | 'FAILED'
  report: CareerReport | null
  needsReanalysis: boolean
}

export interface CareerState {
  accountId: string
  resumeVersion: number | null
  resumeName: string | null
  waitingForText: boolean
  current: CareerAssessment | null
  directions: CareerOption[]
  referenceUrl: string
}

export type SkillMark = 'GREEN' | 'YELLOW' | 'RED'

export const selfMarkLabels: Record<SkillMark, string> = {
  GREEN: '会',
  YELLOW: '接触过',
  RED: '不会',
}

export const testedMarkLabels: Record<SkillMark, string> = {
  GREEN: '已掌握',
  YELLOW: '部分掌握',
  RED: '需补学',
}

export interface SkillGrade {
  topic: string
  mark: SkillMark
  score: number
  feedback: string
}

export interface SkillSession {
  labels?: Record<string, string> | null
  assessmentId: string
  sideId: string
  revision: number
  phase: 'PREPARING' | 'TEST' | 'REPORT' | 'LEARNING' | 'DONE'
  selfMarks: Record<string, SkillMark>
  marks: Record<string, SkillMark>
  grades: SkillGrade[]
  currentTopic: string | null
  question: string | null
  testCount: number
  studyTopics: string[]
  lessonIndex: number
}
