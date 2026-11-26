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
