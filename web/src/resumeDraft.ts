import { ref } from 'vue'

export type ExperienceType = 'COURSEWORK' | 'PROJECT' | 'INTERNSHIP' | 'ACTIVITY'
export type ResumeSection = ExperienceType | 'SKILLS'
export type ResumePanel = 'PROFILE' | 'EDUCATION' | ResumeSection

export const resumeSections: { id: ResumeSection; label: string; icon: string }[] = [
  { id: 'COURSEWORK', label: '课程与作业', icon: '课' },
  { id: 'PROJECT', label: '项目经历', icon: '项' },
  { id: 'INTERNSHIP', label: '实习经历', icon: '实' },
  { id: 'ACTIVITY', label: '校园活动', icon: '校' },
  { id: 'SKILLS', label: '专业技能', icon: '技' },
]

export interface ResumeLayout {
  theme: 'RULED' | 'EDITORIAL'
  density: 'COMPACT' | 'STANDARD' | 'RELAXED'
  sections: ResumeSection[]
}

export type Proficiency = 'EXPOSURE' | 'GUIDED' | 'INDEPENDENT'

export interface Profile {
  name: string
  email: string
  phone: string
}

export interface Education {
  school: string
  major: string
  degree: string
  graduation: string
}

export interface Experience {
  type: ExperienceType
  title: string
  role: string
  period: string
  context: string
  contribution: string
}

export interface Skill {
  name: string
  proficiency: Proficiency
}

export interface ResumeContent {
  profile: Profile
  education: Education
  experiences: Experience[]
  skills: Skill[]
  layout: ResumeLayout
}

export interface ResumeSubmission {
  version: number
  draftRevision: number
  submittedAt: string
}

export interface ResumeDraft {
  accountId: string
  revision: number
  updatedAt: string | null
  content: ResumeContent
  submission: ResumeSubmission | null
}

export const draftHasUnsavedChanges = ref(false)

export function confirmDiscardDraft() {
  return !draftHasUnsavedChanges.value || window.confirm('简历草稿有未保存的修改，确定离开吗？')
}
