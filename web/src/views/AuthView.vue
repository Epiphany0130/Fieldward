<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, errorMessage } from '../api'
import { accountHome } from '../router'
import { sessionUnavailable, signIn } from '../session'

const route = useRoute()
const router = useRouter()
const isRegister = computed(() => route.name === 'register')
const email = ref(typeof history.state?.registeredEmail === 'string' ? history.state.registeredEmail : '')
const registered = ref(Boolean(email.value))
const displayName = ref('')
const password = ref('')
const visible = ref(false)
const pending = ref(false)
const message = ref('')

async function submit() {
  pending.value = true
  message.value = ''
  try {
    if (isRegister.value) {
      await api<void>('/auth/register', 'POST', {
        email: email.value.trim(),
        displayName: displayName.value.trim(),
        password: password.value,
      })
      await router.replace({ name: 'login', state: { registeredEmail: email.value.trim() } })
    } else {
      await signIn(email.value.trim(), password.value)
      await router.replace(accountHome())
    }
  } catch (error) {
    message.value = errorMessage(error)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <main id="main" tabindex="-1" class="auth-layout">
    <aside class="auth-intro">
      <div class="auth-intro-copy">
        <p class="auth-intro-title">从你的真实经历<br />开始求职</p>
        <p>为实习和校招准备第一份简历。</p>
      </div>
      <div class="auth-art" aria-hidden="true">
        <span></span><span></span><span></span>
      </div>
    </aside>

    <section class="auth-content" aria-labelledby="auth-title">
      <div class="auth-inner">
        <h1 id="auth-title">{{ isRegister ? '创建学生账号' : '登录循职' }}</h1>
        <p class="auth-switch">
          {{ isRegister ? '已有账号？' : '第一次使用？' }}
          <RouterLink :to="{ name: isRegister ? 'login' : 'register' }">
            {{ isRegister ? '去登录' : '注册学生账号' }}
          </RouterLink>
        </p>
        <p v-if="registered && !isRegister" class="notice notice-success" role="status">账号已创建，请登录。</p>
        <p v-if="sessionUnavailable" class="notice notice-error" role="alert">暂时无法恢复会话，请重试登录。</p>

        <form class="auth-form" @submit.prevent="submit">
          <div v-if="isRegister" class="field">
            <label for="display-name">姓名或昵称</label>
            <input id="display-name" v-model.trim="displayName" autocomplete="nickname" maxlength="60" required />
          </div>
          <div class="field">
            <label for="email">邮箱</label>
            <input id="email" v-model.trim="email" type="email" autocomplete="username" maxlength="254" required />
          </div>
          <div class="field">
            <label for="password">密码</label>
            <div class="password-field">
              <input
                id="password"
                v-model="password"
                :type="visible ? 'text' : 'password'"
                :autocomplete="isRegister ? 'new-password' : 'current-password'"
                :minlength="isRegister ? 8 : undefined"
                maxlength="72"
                :aria-describedby="isRegister ? 'password-help' : undefined"
                required
              />
              <button class="button button-quiet" type="button" :aria-pressed="visible" @click="visible = !visible">
                {{ visible ? '隐藏密码' : '显示密码' }}
              </button>
            </div>
            <span v-if="isRegister" id="password-help" class="help">至少 8 个字符。</span>
          </div>
          <p v-if="message" class="notice notice-error" role="alert">{{ message }}</p>
          <button class="button button-primary" type="submit" :disabled="pending">
            {{ pending ? (isRegister ? '正在注册…' : '正在登录…') : (isRegister ? '注册' : '登录') }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>
