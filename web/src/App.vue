<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { errorMessage } from './api'
import { account, signOut } from './session'
import './style.css'

const router = useRouter()
const signingOut = ref(false)
const signOutError = ref('')

async function logout() {
  signingOut.value = true
  signOutError.value = ''
  try {
    await signOut()
    await router.replace({ name: 'login' })
  } catch (error) {
    signOutError.value = errorMessage(error)
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <div class="app">
    <a class="skip-link" href="#main">跳到主要内容</a>
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink class="brand" to="/" aria-label="循职 Fieldward 首页">
          <span class="brand-mark" aria-hidden="true"></span>
          <span class="brand-name">循职 <span>Fieldward</span></span>
        </RouterLink>
        <div v-if="account" class="account-nav">
          <span class="account-name">{{ account.displayName }}</span>
          <button class="button button-quiet" type="button" :disabled="signingOut" @click="logout">
            {{ signingOut ? '正在退出…' : '退出登录' }}
          </button>
        </div>
      </div>
    </header>
    <p v-if="signOutError" class="notice notice-error app-notice" role="alert">{{ signOutError }}</p>
    <RouterView v-slot="{ Component, route }">
      <component :is="Component" v-if="Component" :key="route.name" />
      <main v-else id="main" tabindex="-1" class="page"><p role="status">正在加载…</p></main>
    </RouterView>
  </div>
</template>
