<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 应用根组件：承载路由出口并注入 Element Plus 中文语言包（组件已改为按需引入）
</script>
