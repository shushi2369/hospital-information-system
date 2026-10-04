import { get, post, put, type PageResult } from './request'

/** 角色（S-06） */
export interface Role {
  id: number
  roleCode: string
  roleName: string
  description?: string | null
  status: number
  createdAt?: string
}

/** 用户行（S-01，角色字段形态做兼容处理） */
export interface SystemUser {
  id: number
  username: string
  realName: string
  phone?: string | null
  status: number
  roleIds?: number[]
  roles?: Array<{ id: number; roleCode?: string; roleName?: string }> | string[]
  createdAt?: string
  lastLoginAt?: string | null
}

/** 全量菜单树节点（S-09），menuType: 1 目录 2 页面 3 权限点 */
export interface MenuTreeNode {
  id: number
  parentId: number
  menuName: string
  menuType: number
  permissionCode?: string | null
  path?: string | null
  component?: string | null
  sortNo?: number
  status?: number
  children?: MenuTreeNode[]
}

/** 操作日志行（sys_operation_log） */
export interface OperationLog {
  id: number
  traceId: string
  username: string
  module: string
  action: string
  bizType?: string | null
  bizId?: string | null
  method?: string | null
  resultCode: string
  ip?: string | null
  costMs?: number | null
  createdAt: string
}

/** 登录日志行（sys_login_log） */
export interface LoginLog {
  id: number
  username: string
  ip?: string | null
  userAgent?: string | null
  success: number
  message?: string | null
  createdAt: string
}

// ---------------- 用户管理（S-01 ~ S-05） ----------------

export interface UserQuery {
  pageNum?: number
  pageSize?: number
  username?: string
  realName?: string
  roleId?: number
  /** 七十九轮：状态筛选（1 启用 / 0 停用，缺省全部） */
  status?: number
}

export const getUserPage = (params: UserQuery) => get<PageResult<SystemUser>>('/system/users', params)

export const createUser = (data: {
  username: string
  realName: string
  password: string
  phone?: string
  roleIds: number[]
}) => post<void>('/system/users', data)

export const updateUser = (id: number, data: { realName: string; phone?: string; roleIds: number[] }) =>
  put<void>(`/system/users/${id}`, data)

export const updateUserStatus = (id: number, status: number) =>
  put<void>(`/system/users/${id}/status`, { status })

export const resetUserPassword = (id: number, newPassword: string) =>
  put<void>(`/system/users/${id}/password/reset`, { newPassword })

// ---------------- 角色管理（S-06 ~ S-09） ----------------

export const getRoleList = () => get<Role[]>('/system/roles')

export const createRole = (data: { roleCode: string; roleName: string; description?: string }) =>
  post<void>('/system/roles', data)

/** 角色已授权菜单 ID 集合 */
export const getRoleMenuIds = (roleId: number) => get<number[]>(`/system/roles/${roleId}/menus`)

export const updateRoleMenus = (roleId: number, menuIds: number[]) =>
  put<void>(`/system/roles/${roleId}/menus`, { menuIds })

/** 全量菜单权限树（配置用） */
export const getMenuTree = () => get<MenuTreeNode[]>('/system/menus/tree')

// ---------------- 日志查询（S-10、S-11） ----------------

export interface OperationLogQuery {
  pageNum?: number
  pageSize?: number
  username?: string
  module?: string
  startDate?: string
  endDate?: string
}

export const getOperationLogPage = (params: OperationLogQuery) =>
  get<PageResult<OperationLog>>('/logs/operations', params)

export interface LoginLogQuery {
  pageNum?: number
  pageSize?: number
  username?: string
  startDate?: string
  endDate?: string
  success?: number
}

export const getLoginLogPage = (params: LoginLogQuery) => get<PageResult<LoginLog>>('/logs/logins', params)
