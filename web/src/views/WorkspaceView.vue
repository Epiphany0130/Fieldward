<script setup lang="ts">
import { account } from '../session'
</script>

<template>
  <main id="main" tabindex="-1" class="page workspace">
    <h1>学生工作台</h1>
    <article class="surface workspace-card">
      <h2>你好，{{ account?.displayName }}</h2>
      <p>{{ account?.email }}</p>
    </article>
  </main>
</template>
