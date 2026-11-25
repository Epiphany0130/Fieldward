export interface Account {
  id: string
  email: string
  displayName: string
  role: 'STUDENT' | 'ADMIN'
  enabled: boolean
  createdAt: string
}

export interface UserPage {
  users: Account[]
  page: number
  hasNext: boolean
}

interface CsrfToken {
  headerName: string
  token: string
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

export async function api<T>(path: string, method = 'GET', data?: object): Promise<T> {
  const response = await requestApi(path, method, data)
  if (response.status === 204) return undefined as T
  const body = await response.text()
  return body ? JSON.parse(body) : undefined as T
}

export async function requestApi(path: string, method: string, data?: object,
                                 accept = 'application/json', signal?: AbortSignal): Promise<Response> {
  const headers = new Headers({ Accept: accept })
  if (method !== 'GET') {
    const csrf = await api<CsrfToken>('/auth/csrf')
    headers.set(csrf.headerName, csrf.token)
  }
  const body = data instanceof FormData ? data : data ? JSON.stringify(data) : undefined
  if (typeof body === 'string') headers.set('Content-Type', 'application/json')

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body,
    credentials: 'same-origin',
    cache: 'no-store',
    signal,
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const statusMessages: Record<number, string> = {
      400: '请检查填写的信息后重试。',
      401: '登录已失效，请重新登录。',
      403: '当前请求没有权限，请刷新页面后重试。',
      413: '简历文件不能超过 5 MB。',
    }
    const detail = problem?.detail
    const message = typeof detail === 'string' && /[\u3400-\u9fff]/.test(detail)
      ? detail
      : statusMessages[response.status] ?? '服务暂时不可用，请稍后重试。'
    throw new ApiError(response.status, message)
  }
  return response
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '无法连接服务，请稍后重试。'
}
