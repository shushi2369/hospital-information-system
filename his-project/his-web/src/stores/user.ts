import { defineStore } from 'pinia'
import { changePassword, getMe, getMenus, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { MenuNode, UserInfo } from '@/api/auth'
import { TOKEN_KEY } from '@/api/request'

interface UserState {
  token: string
  userInfo: UserInfo | null
  permissions: string[]
  menus: MenuNode[]
  /** 动态路由是否已注册 */
  routesLoaded: boolean
}

/** 目录 path 以 / 开头作前缀；页面 path 为相对路径，逐级拼接为完整路由 */
function resolveFullPaths(nodes: MenuNode[], parentPath = ''): MenuNode[] {
  return nodes.map((node) => {
    let path = node.path || ''
    if (path && !path.startsWith('/')) {
      path = parentPath ? `${parentPath}/${path}` : `/${path}`
    }
    const resolved: MenuNode = { ...node, fullPath: path || `/menu-${node.id}` }
    if (node.children && node.children.length > 0) {
      resolved.children = resolveFullPaths(node.children, path)
    }
    return resolved
  })
}

function findFirstPagePath(nodes: MenuNode[]): string {
  for (const node of nodes) {
    if (node.menuType === 2 && node.fullPath) {
      return node.fullPath
    }
    if (node.children && node.children.length > 0) {
      const child = findFirstPagePath(node.children)
      if (child) return child
    }
  }
  return ''
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: null,
    permissions: [],
    menus: [],
    routesLoaded: false,
  }),

  getters: {
    realName: (state) => state.userInfo?.realName || state.userInfo?.username || '',
    /** 第一个可见页面菜单的完整路径，用于 / 重定向 */
    firstMenuPath(state): string {
      return findFirstPagePath(state.menus)
    },
  },

  actions: {
    /** 是否拥有指定权限码；permissions 含 '*' 时视为全部权限 */
    hasPerm(code: string): boolean {
      if (!code) return true
      if (this.permissions.includes('*')) return true
      return this.permissions.includes(code)
    },

    /** 登录：保存 token 与用户信息，菜单/路由在守卫中按需加载 */
    async login(username: string, password: string) {
      const data = await loginApi({ username, password })
      this.token = data.token
      localStorage.setItem(TOKEN_KEY, data.token)
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        roleCodes: data.roleCodes ?? [],
        permissions: data.permissions ?? [],
      }
      this.permissions = data.permissions ?? []
      this.menus = []
      this.routesLoaded = false
    },

    /** 拉取当前用户信息（已有则跳过） */
    async loadUserInfo() {
      if (this.userInfo) return
      const me = await getMe()
      this.userInfo = me
      this.permissions = me.permissions ?? []
    },

    /** 拉取菜单树并补全完整路径 */
    async loadMenus() {
      const tree = await getMenus()
      this.menus = resolveFullPaths(tree ?? [])
    },

    /** 退出登录：调用后端销毁会话并清理本地状态 */
    async logout() {
      try {
        await logoutApi()
      } catch {
        // 后端会话已失效等情况忽略
      }
      this.resetAuth()
    },

    /** 仅清理本地认证状态（不调后端） */
    resetAuth() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.menus = []
      this.routesLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      // 业务 sessionStorage 一并清理：共用工作站的下一个账号不能继承上一班的接诊状态
      // （his_started_visits 是挂号Id→visitId 的跨页映射，八十六轮审计）
      sessionStorage.removeItem('his_started_visits')
    },

    changePassword(oldPassword: string, newPassword: string) {
      return changePassword({ oldPassword, newPassword })
    },
  },
})
