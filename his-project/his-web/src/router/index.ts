import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/views/layout/Layout.vue'
import NotFound from '@/views/NotFound.vue'
import Login from '@/views/Login.vue'
import { useUserStore } from '@/stores/user'
import type { MenuNode } from '@/api/auth'

/** 视图组件按 component 字符串解析：'system/UserList' -> '../views/system/UserList.vue' */
const viewModules = import.meta.glob('../views/**/*.vue') as Record<
  string,
  () => Promise<unknown>
>

/** 常量路由：登录页与主布局 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录' },
  },
  {
    path: '/',
    name: 'Layout',
    component: Layout,
    children: [], // 动态路由（菜单页面）挂载在此下
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
})

/** 按菜单 component 字符串解析视图组件，解析失败回落到 NotFound */
function resolveViewComponent(component?: string | null) {
  if (!component) return undefined
  return viewModules[`../views/${component}.vue`]
}

/** 把菜单树（menuType=2 页面节点）映射为 Layout 的子路由 */
export function buildDynamicRoutes(menus: MenuNode[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  const walk = (nodes: MenuNode[]) => {
    nodes.forEach((node) => {
      if (node.menuType === 2 && node.fullPath) {
        routes.push({
          path: node.fullPath,
          name: `menu-${node.id}`,
          component: (resolveViewComponent(node.component) ?? NotFound) as RouteRecordRaw['component'],
          meta: { title: node.menuName, menuId: node.id },
        })
      }
      if (node.children && node.children.length > 0) {
        walk(node.children)
      }
    })
  }
  walk(menus)
  return routes
}

router.beforeEach(async (to) => {
  const store = useUserStore()

  // 页面标题
  const title = to.meta?.title as string | undefined
  document.title = title ? `${title} - 医院信息系统` : '医院信息系统'

  // 未登录
  if (!store.token) {
    if (to.path === '/login') return true
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录访问登录页 → 回首页（/ 会被重定向到第一个可见菜单）
  if (to.path === '/login') {
    return { path: store.firstMenuPath || '/' }
  }

  // 登录后（含刷新）首次导航：加载用户信息与菜单并注册动态路由
  if (!store.routesLoaded) {
    try {
      await store.loadUserInfo()
      await store.loadMenus()
    } catch {
      // 加载失败（token 失效/后端不可用）：清理本地状态回登录页，避免死循环
      store.resetAuth()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    buildDynamicRoutes(store.menus).forEach((route) => {
      if (route.name) dynamicRouteNames.push(String(route.name))
      router.addRoute('Layout', route)
    })
    store.routesLoaded = true
    if (to.path === '/') {
      // / 重定向到第一个可见菜单
      return store.firstMenuPath ? { path: store.firstMenuPath, replace: true } : true
    }
    // 重新解析目标路由（原目标此刻可能匹配的是 NotFound 兜底）
    return { path: to.path, query: to.query, hash: to.hash, replace: true }
  }

  if (to.path === '/' && store.firstMenuPath) {
    return { path: store.firstMenuPath, replace: true }
  }

  return true
})

export default router

/** 已注册的动态路由名（登出/切换账号时卸载，防低权限账号直输 URL 到达前任页面壳） */
const dynamicRouteNames: string[] = []

export function clearDynamicRoutes(): void {
  for (const name of dynamicRouteNames.splice(0)) {
    if (router.hasRoute(name)) router.removeRoute(name)
  }
}
