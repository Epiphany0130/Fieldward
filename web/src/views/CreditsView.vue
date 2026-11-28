<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api'
interface Credits { balance: number; rules: string; stages: { stage: string; points: number }[]; ledger: { request_id: string; kind: string; points: number; reason: string | null; created_at: string }[] }
const data = ref<Credits>(); const message = ref('')
onMounted(async () => { try { data.value = await api<Credits>('/credits') } catch (error) { message.value = errorMessage(error) } })
</script>
<template>
  <main id="main" tabindex="-1" class="page"><h1>我的积分</h1><RouterLink to="/workspace">返回工作台</RouterLink>
    <p v-if="message" role="alert">{{ message }}</p><p v-else-if="!data" role="status">正在加载…</p>
    <template v-else><section class="surface"><h2>剩余 {{ data.balance }} 积分</h2><p>{{ data.rules }}</p><h3>阶段预留</h3><ul><li v-for="stage in data.stages" :key="stage.stage">{{ stage.stage }}：{{ stage.points }}</li></ul></section>
      <section class="surface"><h2>积分流水</h2><p v-if="!data.ledger.length">暂无流水</p><ul><li v-for="entry in data.ledger" :key="entry.request_id">{{ entry.created_at }} · {{ entry.kind }} · {{ entry.points }} <span v-if="entry.reason">· {{ entry.reason }}</span></li></ul></section></template>
  </main>
</template>
