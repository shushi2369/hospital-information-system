import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { perm } from './directives/perm'
import './styles.css'

// 一百四十六轮 F-A3：启动时恢复用户主题选择——原只在切换时写 localStorage，
// 刷新后永远回亮色，暗色选择形同虚设
document.documentElement.classList.toggle(
  'dark',
  localStorage.getItem('his_theme') === 'dark',
)

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 组件按需引入（unplugin-vue-components）；中文语言包经 App.vue 的 el-config-provider 注入
// 自定义权限指令
app.directive('perm', perm)

app.mount('#app')
