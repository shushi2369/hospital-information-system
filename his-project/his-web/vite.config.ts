import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  // Element Plus 组件按需引入（importStyle:false：全局 CSS 已在 main.ts 引入，避免重复）
  plugins: [vue(), Components({ resolvers: [ElementPlusResolver({ importStyle: false })] })],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true,
    open: false,
    proxy: {
      // 后端服务 BaseURL /api/v1，代理到本地 Spring Boot 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // vendor 拆包（九十轮性能审计）：入口单包 453kB，任一依赖更新即整包缓存失效；
        // element-plus/vue 系/axios 分属三个长期稳定的 chunk，业务代码改动可保留约 80% 缓存命中
        manualChunks(id) {
          if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus')) {
            return 'vendor-element-plus'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/@vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) {
            return 'vendor-vue'
          }
          if (id.includes('node_modules/axios')) {
            return 'vendor-axios'
          }
        },
      },
    },
  },
})
