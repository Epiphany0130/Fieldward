import { ref } from 'vue'

export type ExperienceType = 'COURSEWORK' | 'PROJECT' | 'INTERNSHIP' | 'ACTIVITY'
  | 'STRENGTH' | 'HONOR' | 'RESEARCH' | 'PORTFOLIO' | 'OTHER' | 'CUSTOM'
export type ResumeSection = ExperienceType | 'SKILLS'
export type ResumePanel = 'PROFILE' | 'EDUCATION' | ResumeSection
export type Proficiency = 'EXPOSURE' | 'GUIDED' | 'INDEPENDENT'

export const resumeSections: { id: ResumeSection; label: string; icon: string }[] = [
  { id: 'COURSEWORK', label: '课程与作业', icon: '课' },
  { id: 'PROJECT', label: '项目经历', icon: '项' },
  { id: 'INTERNSHIP', label: '实习与工作', icon: '实' },
  { id: 'ACTIVITY', label: '校园活动', icon: '校' },
  { id: 'SKILLS', label: '专业技能', icon: '技' },
  { id: 'STRENGTH', label: '个人优势', icon: '介' },
  { id: 'HONOR', label: '荣誉与证书', icon: '奖' },
  { id: 'RESEARCH', label: '研究经历', icon: '研' },
  { id: 'PORTFOLIO', label: '作品与链接', icon: '作' },
  { id: 'OTHER', label: '其他经历', icon: '历' },
  { id: 'CUSTOM', label: '自定义栏目', icon: '自' },
]

export const resumeTemplates = [
  { id: 'RULED', name: '清晰线条', detail: '分节横线，便于快速浏览' },
  { id: 'EDITORIAL', name: '书页风格', detail: '衬线标题与侧边标记' },
  { id: 'MODERN', name: '现代校招', detail: '浅色页眉及紧凑分区' },
] as const

export interface Appearance {
  font: 'SANS' | 'SERIF'
  accent: 'FOREST' | 'GRAPHITE' | 'NAVY' | 'SIENNA'
  alignment: 'LEFT' | 'CENTER' | 'RIGHT'
  detailsStyle: 'INLINE' | 'LABELED'
  fontSize: number
  lineSpacing: number
  topMargin: number
  leftMargin: number
  rightMargin: number
  bottomMargin: number
  sectionGap: number
  contentIndent: number
  headingGap: number
}

export interface ResumeLayout {
  theme: typeof resumeTemplates[number]['id']
  density: 'COMPACT' | 'STANDARD' | 'RELAXED'
  sections: ResumeSection[]
  titles: Partial<Record<ResumeSection, string>>
  hiddenSections: ResumeSection[]
  appearance: Appearance
}

export function defaultAppearance(density: ResumeLayout['density'] = 'STANDARD'): Appearance {
  return {
    font: 'SANS', accent: 'FOREST', alignment: 'LEFT', detailsStyle: 'INLINE',
    fontSize: 10, lineSpacing: density === 'COMPACT' ? 137 : density === 'RELAXED' ? 173 : 155,
    topMargin: 14, leftMargin: 15, rightMargin: 15, bottomMargin: 14,
    contentIndent: 0, headingGap: 2,
    sectionGap: density === 'COMPACT' ? 3 : density === 'RELAXED' ? 7 : 5,
  }
}

export interface Profile {
  name: string
  email: string
  phone: string
  career: { status: string; position: string; preferredCity: string }
  links: { website: string; github: string; wechat: string; city: string }
  photo: string
}

export interface Education {
  school: string
  major: string
  degree: string
  graduation: string
  details: { start: string; studyType: string; department: string; city: string; notes: string }
}

export interface Experience {
  type: ExperienceType
  title: string
  role: string
  period: string
  context: string
  contribution: string
  details: { organization: string; city: string; link: string; textStyle: 'PARAGRAPH' | 'BULLETS' | 'NUMBERED' }
}

export interface Skill {
  name: string
  proficiency: Proficiency
  description: string
}

export interface ResumeContent {
  profile: Profile
  education: Education
  additionalEducation: Education[]
  experiences: Experience[]
  skills: Skill[]
  layout: ResumeLayout
}

export interface ResumeSubmission {
  version: number
  draftRevision: number | null
  submittedAt: string
}

export interface ResumeDraft {
  accountId: string
  revision: number
  updatedAt: string | null
  content: ResumeContent
  submission: ResumeSubmission | null
  submittedDraftVersion: number | null
}

export interface ResumeFinal {
  accountId: string
  version: number
  submittedAt: string
  content: ResumeContent | null
  upload: { name: string; contentType: string; sizeBytes: number; text: string } | null
}

export function emptyProfile(): Profile {
  return {
    name: '', email: '', phone: '', photo: '',
    career: { status: '', position: '', preferredCity: '' },
    links: { website: '', github: '', wechat: '', city: '' },
  }
}

export function emptyEducation(): Education {
  return {
    school: '', major: '', degree: '', graduation: '',
    details: { start: '', studyType: '', department: '', city: '', notes: '' },
  }
}

export function hasEducation(education: Education) {
  const { details, ...fields } = education
  return [...Object.values(fields), ...Object.values(details)].some((value) => value.trim())
}

export function activeSections(content: ResumeContent): ResumeSection[] {
  const populated = content.experiences.map<ResumeSection>(({ type }) => type)
  if (content.skills.length) populated.push('SKILLS')
  return [...new Set([...content.layout.sections, ...populated])]
}

export function resumeSectionTitle(layout: ResumeLayout, id: ResumeSection) {
  return layout.titles[id]?.trim() || resumeSections.find((section) => section.id === id)!.label
}

export function safeLink(value: string) {
  const input = value.trim()
  if (!input || !/^https?:\/\//i.test(input)) return null
  try {
    const url = new URL(input)
    return url.username || url.password ? null : url.href
  } catch {
    return null
  }
}

export const draftHasUnsavedChanges = ref(false)

export function confirmDiscardDraft() {
  return !draftHasUnsavedChanges.value || window.confirm('简历草稿有未保存的修改，确定离开吗？')
}
