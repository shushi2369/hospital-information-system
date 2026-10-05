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
  // 所有 POST/PUT 请求自动携带 X-Idempotency-Key（接口设计 §1.1 / A0004）
  if (method === 'post' || method === 'put') {
    const key = writeKey(method, config.url || '', config.data)
    if (pendingWrites.has(key)) {
      ElMessage.warning('请求处理中，请勿重复提交')
      return Promise.reject({ code: 'DUP', message: '请求处理中，请勿重复提交' })
    }
    pendingWrites.add(key)
    config.headers['X-Inflight-Key'] = key
    config.headers['X-Idempotency-Key'] = genIdempotencyKey()
  }
  return config
})

// 响应拦截器：统一处理业务码与 HTTP 错误
service.interceptors.response.use(
  (response) => {
    const inflight = response.config.headers?.['X-Inflight-Key']
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
    const inflight = error.config?.headers?.['X-Inflight-Key']
    if (inflight) pendingWrites.delete(String(inflight))
    const status = error.response?.status
    // 401 未登录/会话失效：清 token 跳登录页
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.error('未登录或登录已过期，请重新登录')
      if (!window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
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
