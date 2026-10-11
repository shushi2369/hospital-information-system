import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 后端统一响应结构（接口设计 §1.1）：
 * {"code":"OK","message":"成功","data":...,"traceId":"..."}
 * code === 'OK' 才算成功
 */
export interface ApiResult<T = unknown> {
  code: string
  message: string
  data: T
  traceId?: string
}

/** 分页响应结构：data: { total, list } */
export interface PageResult<T = unknown> {
  total: number
  list: T[]
}

export const TOKEN_KEY = 'his_token'

/**
 * 兼容列表接口两种返回形态：
 * 分页（{total, list}）或非分页（数组）
 */
export function toList<T>(data: T[] | PageResult<T> | null | undefined): T[] {
  if (!data) return []
  if (Array.isArray(data)) return data
  return Array.isArray(data.list) ? data.list : []
}

/** 生成幂等键（crypto.randomUUID 仅在安全上下文可用，做一次降级兜底） */
function genIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

const service: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

/**
 * 在途写请求去重（八十七轮前端审计 P1-2）：同 method+url+body 的写请求在上一笔返回前
 * 再次发起（典型=双击按钮）直接本地拒绝。后端幂等头按"每请求新 UUID"生成，防不住双击；
 * 项目内约 29 个提交按钮无 loading 守卫，此处是兜底防线。仅影响同一浏览器页签。
 */
const pendingWrites = new Set<string>()

function writeKey(method: string, url: string, data: unknown): string {
  let body = ''
  try {
    body = JSON.stringify(data ?? null)
  } catch {
    body = String(data)
  }
  return `${method}:${url}:${body}`
}

// 请求拦截器：注入 token 与幂等键
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const method = (config.method || '').toLowerCase()
  // 所有写请求（POST/PUT/DELETE）自动携带 X-Idempotency-Key（接口设计 §1.1 / A0004）。
  // DELETE 也要：切面对挂 @Idempotent 的端点强制校验该头（八十八轮审计 S1：删除诊断 100% 失败根因）
  if (method === 'post' || method === 'put' || method === 'delete') {
    if (method !== 'delete') {
      const key = writeKey(method, config.url || '', config.data)
      if (pendingWrites.has(key)) {
        ElMessage.warning('请求处理中，请勿重复提交')
        return Promise.reject({ code: 'DUP', message: '请求处理中，请勿重复提交' })
      }
      pendingWrites.add(key)
      // 一百一十五轮 F6：内部去重键改存 config 自定义字段，不再作为 HTTP 头发出
      //（原 X-Inflight-Key 会把请求体摘要暴露在请求头且服务端无消费）
      ;(config as InternalAxiosRequestConfig & { _inflightKey?: string })._inflightKey = key
    }
    config.headers['X-Idempotency-Key'] = genIdempotencyKey()
  }
  return config
})

// 响应拦截器：统一处理业务码与 HTTP 错误
service.interceptors.response.use(
  (response) => {
    const inflight = (response.config as InternalAxiosRequestConfig & { _inflightKey?: string })._inflightKey
    if (inflight) pendingWrites.delete(String(inflight))
    const res = response.data as ApiResult
    if (res && res.code === 'OK') {
      // 直接返回 data，业务层拿到的是 data 本体
      return res.data as never
    }
    const message = (res && res.message) || '请求失败'
    ElMessage.error(message)
    return Promise.reject({ code: res && res.code, message })
  },
  (error: AxiosError) => {
    const inflight = (error.config as (InternalAxiosRequestConfig & { _inflightKey?: string }) | undefined)?._inflightKey
    if (inflight) pendingWrites.delete(String(inflight))
    const status = error.response?.status
    // 401 未登录/会话失效：清 token 跳登录页。
    // 一百一十五轮 F1：登录接口的 401 是"用户名或密码错误"（后端 A0002 映射 401）——
    // 原实现硬编码"未登录或登录已过期"且误清 token，最高频的输错密码路径被完全误导；
    // 一百一十五轮 F2：并发 N 个 401 时 single-flight，只提示跳转一次（弹窗风暴收敛）。
    if (status === 401) {
      const data = error.response?.data as ApiResult | undefined
      const backendMessage = data?.message || ''
      const isLoginEndpoint = (error.config?.url || '').includes('/auth/login')
      if (isLoginEndpoint) {
        ElMessage.error({ message: backendMessage || '用户名或密码错误', grouping: true })
        return Promise.reject({ code: data?.code || 'A0002', message: backendMessage || '用户名或密码错误' })
      }
      if (handle401Once()) {
        resetAuthState()
        ElMessage.error({ message: backendMessage || '未登录或登录已过期，请重新登录', grouping: true })
        if (!window.location.pathname.startsWith('/login')) {
          const redirect = encodeURIComponent(window.location.pathname + window.location.search)
          window.location.href = `/login?redirect=${redirect}`
        }
      }
      return Promise.reject({ code: 'A0002', message: '未登录或登录已过期' })
    }
    let message = '网络异常，请稍后重试'
    const data = error.response?.data as ApiResult | undefined
    if (data && data.message) {
      message = data.message
    } else if (error.message === 'Network Error') {
      message = '网络连接失败，请检查后端服务是否已启动'
    }
    ElMessage.error(message)
    return Promise.reject({ code: data && data.code, message })
  }
)

/** 一百一十五轮 F2：401 single-flight——首个 401 负责提示跳转，其余静默 reject */
let handling401 = false
function handle401Once(): boolean {
  if (handling401) return false
  handling401 = true
  window.setTimeout(() => {
    handling401 = false
  }, 1500)
  return true
}

/** 一百一十五轮 F8：401 时同步清 Pinia 状态——消除对整页刷新重建 store 的隐式依赖 */
function resetAuthState() {
  try {
    localStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem('his_started_visits')
  } catch {
    // 存储不可用时忽略
  }
}

/** GET 请求 */
export function get<T = unknown>(url: string, params?: Record<string, unknown>): Promise<T> {
  return service.get(url, { params }) as unknown as Promise<T>
}

/** POST 请求（自动携带幂等键） */
export function post<T = unknown>(url: string, data?: unknown): Promise<T> {
  return service.post(url, data) as unknown as Promise<T>
}

/** PUT 请求（自动携带幂等键） */
export function put<T = unknown>(url: string, data?: unknown): Promise<T> {
  return service.put(url, data) as unknown as Promise<T>
}

export default service
