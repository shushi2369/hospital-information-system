<template>
  <div class="not-found">
    <h1>404</h1>
    <p>抱歉，您访问的页面不存在或没有访问权限</p>
    <el-button type="primary" @click="goHome">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.replace('/')
}
</script>
