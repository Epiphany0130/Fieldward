import { ref } from 'vue'

export type ExperienceType = 'COURSEWORK' | 'PROJECT' | 'INTERNSHIP' | 'ACTIVITY'
export type Proficiency = 'EXPOSURE' | 'GUIDED' | 'INDEPENDENT'

export interface Education {
  school: string
  major: string
  degree: string
  graduation: string
}

export interface Experience {
  type: ExperienceType
  title: string
  context: string
  contribution: string
}

export interface Skill {
  name: string
  proficiency: Proficiency
}

export interface ResumeContent {
  education: Education
  experiences: Experience[]
  skills: Skill[]
}

export interface ResumeDraft {
  accountId: string
  revision: number
  updatedAt: string | null
  content: ResumeContent
}

export const draftHasUnsavedChanges = ref(false)

export function confirmDiscardDraft() {
  return !draftHasUnsavedChanges.value || window.confirm('简历草稿有未保存的修改，确定离开吗？')
}
