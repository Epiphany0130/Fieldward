import { ref, shallowRef } from 'vue'
import { ApiError, api, type Account } from './api'

export const account = shallowRef<Account | null>(null)
export const sessionUnavailable = ref(false)
let restored = false

export async function restoreSession() {
  if (restored) return
  sessionUnavailable.value = false

  try {
    account.value = await api<Account>('/auth/me')
    restored = true
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      restored = true
    } else {
      sessionUnavailable.value = true
    }
  }
}

export async function signIn(email: string, password: string) {
  account.value = await api<Account>('/auth/login', 'POST', { email, password })
  restored = true
  sessionUnavailable.value = false
}

export function forgetSession() {
  account.value = null
}

export async function signOut() {
  await api<void>('/auth/logout', 'POST')
  forgetSession()
}
