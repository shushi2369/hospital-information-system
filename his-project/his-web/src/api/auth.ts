import { get, post, put } from './request'

/** 登录返回（A-01） */
export interface LoginResult {
  token: string
  userId: number
  username: string
  realName: string
  roleCodes: string[]
  permissions: string[]
}

/** 当前登录用户信息（A-03） */
export interface UserInfo {
  userId: number
  username: string
  realName: string
  roleCodes: string[]
  permissions: string[]
}

/**
 * 菜单树节点（A-04）
 * menuType: 1 目录 2 页面 3 按钮/权限点
 * component 形如 "system/UserList"、"basedata/DrugList"
 */
export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: number
  permissionCode?: string | null
  path?: string | null
  component?: string | null
  children?: MenuNode[]
  /** 前端补全的完整路由路径（目录 path 以 / 开头，页面 path 相对拼接），非后端字段 */
  fullPath?: string
}

/** 登录（A-01） */
export const login = (data: { username: string; password: string }) =>
  post<LoginResult>('/auth/login', data)

/** 当前用户信息 + 权限码（A-03） */
export const getMe = () => get<UserInfo>('/auth/me')

/** 当前用户菜单树（A-04），动态路由数据源 */
export const getMenus = () => get<MenuNode[]>('/auth/menus')

/** 退出登录（A-02） */
export const logout = () => post<void>('/auth/logout')

/** 修改本人密码（A-05） */
export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  put<void>('/auth/password', data)
