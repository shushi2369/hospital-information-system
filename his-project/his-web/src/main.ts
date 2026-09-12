import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { perm } from './directives/perm'
import './styles.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 组件按需引入（unplugin-vue-components）；中文语言包经 App.vue 的 el-config-provider 注入
// 自定义权限指令
app.directive('perm', perm)

app.mount('#app')
