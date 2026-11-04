import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import test from 'node:test'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const { createPinia, setActivePinia } = require('pinia')

// 使用项目自身的编译器运行真实模块，只替换网络及浏览器边界，无需新增测试依赖。
function loadSource(relativePath, dependencies = {}) {
  const filename = fileURLToPath(new URL(`../${relativePath}`, import.meta.url))
  let source = readFileSync(filename, 'utf8')
  if (relativePath.endsWith('.vue')) {
    source = compileScript(parse(source, { filename }).descriptor, { id: '前端回归' }).content
  }
  const compiled = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText
  const module = { exports: {} }
  const storage = () => {
    const values = new Map()
    return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
  }
  const context = {
    module,
    exports: module.exports,
    require: (id) => dependencies[id] ?? require(id),
    localStorage: storage(),
    sessionStorage: storage(),
    window: { setTimeout },
    document: { documentElement: { classList: { contains: () => false } } },
    process: { env: { NODE_ENV: 'development' } },
    console,
  }
  // 在同一运行上下文编译，保持规则中的 RegExp 与表单验证器的 instanceof 语义一致。
  vm.compileFunction(compiled, Object.keys(context), { filename })(...Object.values(context))
  return module.exports
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

async function roleStore(roleCode, menus) {
  setActivePinia(createPinia())
  const { useUserStore } = loadSource('src/stores/user.ts', {
    '@/api/auth': {
      login: async () => ({ token: '测试令牌', userId: 1, username: '测试账号', roleCodes: [roleCode], permissions: [] }),
      getMenus: async () => menus,
    },
    '@/api/request': { TOKEN_KEY: 'his_token' },
  })
  const store = useUserStore()
  await store.login('测试账号', '测试密码')
  await store.loadMenus()
  return store
}

const directory = (id, path, childPath) => ({ id, menuType: 1, path, children: [{ id: id + 1, menuType: 2, path: childPath }] })

// 角色首页以迁移脚本里的真实菜单为夹具，防止测试沿用前端写错的路径。
function seededDirectory(filename, parentId) {
  const sql = readFileSync(new URL(`../../his-backend/src/main/resources/db/migration/${filename}`, import.meta.url), 'utf8')
  const nodes = [...sql.matchAll(/\((\d+),\s*(\d+),\s*'([^']+)',\s*([12]),\s*NULL,\s*'([^']+)'/g)]
    .map((match) => ({ id: Number(match[1]), parentId: Number(match[2]), menuName: match[3], menuType: Number(match[4]), path: match[5] }))
  const root = nodes.find((node) => node.id === parentId)
  assert.ok(root, `种子菜单目录缺失：${parentId}`)
  return { ...root, children: nodes.filter((node) => node.parentId === parentId) }
}

function cashierMenus() {
  const root = seededDirectory('V2__init_core_data.sql', 40)
  const phase3 = readFileSync(new URL('../../his-backend/src/main/resources/db/migration/V4__phase3_menus.sql', import.meta.url), 'utf8')
  assert.match(phase3, /\(60, 40, '收费结算', 2, NULL, 'billing'/)
  root.children.push({ id: 60, parentId: 40, menuName: '收费结算', menuType: 2, path: 'billing' })
  return [root]
}

test('收费员真实菜单中患者建档排首位时，仍优先进入挂号管理', async () => {
  const menus = cashierMenus()
  assert.equal(menus[0].children[0].path, 'patients')
  const store = await roleStore('CASHIER', menus)
  assert.equal(store.firstMenuPath, '/regdesk/registrations')
})

test('收费员未获挂号菜单时，优先进入已授权的收费结算', async () => {
  const menus = cashierMenus()
  menus[0].children = menus[0].children.filter((node) => node.path !== 'registrations')
  const store = await roleStore('CASHIER', menus)
  assert.equal(store.firstMenuPath, '/regdesk/billing')
})

test('病案员登录后落在实际病案页面', async () => {
  const store = await roleStore('MRCLERK', [directory(10, '/report', 'dashboard'), seededDirectory('V7__phase2_menus.sql', 140)])
  assert.equal(store.firstMenuPath, '/mrc/records')
})

test('角色首页目录匹配保留路径边界，不误匹配同前缀目录', async () => {
  const store = await roleStore('MRCLERK', [directory(10, '/mrc-history', 'records'), seededDirectory('V7__phase2_menus.sql', 140)])
  assert.equal(store.firstMenuPath, '/mrc/records')
})

test('医生、护士、药师及检验技师的首选页与真实种子菜单一致', async () => {
  for (const [role, filename, id, expected] of [
    ['DOCTOR', 'V2__init_core_data.sql', 50, '/clinic/queue'],
    ['NURSE', 'V7__phase2_menus.sql', 130, '/nursing/workbench'],
    ['PHARMACIST', 'V4__phase3_menus.sql', 70, '/pharmacy/dispense'],
    ['LIS_USER', 'V14__phase3_menus.sql', 170, '/lis/workbench'],
  ]) {
    const store = await roleStore(role, [directory(900, '/其他授权菜单', '首页'), seededDirectory(filename, id)])
    assert.equal(store.firstMenuPath, expected, role)
  }
})

test('没有角色首选菜单时回退到首个已授权页面', async () => {
  const store = await roleStore('DOCTOR', [directory(10, '/lis', 'workbench')])
  assert.equal(store.firstMenuPath, '/lis/workbench')
})

test('开发模式安装 Pinia 后，首次菜单加载确实调用接口并生成首页', async () => {
  // 启用 Pinia 自身的浏览器开发工具包装，复现非函数被误放入 actions 时的实际故障。
  const browserPinia = loadSource('node_modules/pinia/dist/pinia.cjs')
  const pinia = browserPinia.createPinia()
  require('vue').createApp({}).use(pinia)
  let requests = 0
  const { useUserStore } = loadSource('src/stores/user.ts', {
    pinia: browserPinia,
    '@/api/auth': { getMenus: async () => { requests += 1; return cashierMenus() } },
    '@/api/request': { TOKEN_KEY: 'his_token' },
  })
  const store = useUserStore(pinia)
  store.userInfo = { roleCodes: ['CASHIER'] }
  await store.loadMenus()
  assert.equal(requests, 1)
  assert.equal(store.firstMenuPath, '/regdesk/registrations')
  browserPinia.disposePinia(pinia)
})

function picker() {
  const requests = []
  const { default: component } = loadSource('src/components/RemotePicker.vue', {
    '@/api/request': { get: (url, params) => { const request = { url, params, ...deferred() }; requests.push(request); return request.promise } },
  })
  const state = component.setup({ kind: 'patient' }, { expose() {}, emit() {} })
  return { state, requests }
}

test('远程检索只保留最新关键词的结果，慢旧请求不能覆盖新结果', async () => {
  const { state, requests } = picker()
  const oldSearch = state.search('王')
  const newSearch = state.search('王明')
  requests[1].resolve([{ id: 2, name: '王明', patientNo: 'P2' }])
  await newSearch
  requests[0].resolve([{ id: 1, name: '王某', patientNo: 'P1' }])
  await oldSearch
  assert.equal(state.options.value.map((row) => row.label).join(','), '王明')
})

test('清空检索词立即结束加载，旧请求返回后不恢复过时选项', async () => {
  const { state, requests } = picker()
  const oldSearch = state.search('王')
  await state.search('   ')
  assert.equal(state.loading.value, false)
  requests[0].resolve([{ id: 1, name: '王某' }])
  await oldSearch
  assert.equal(state.options.value.length, 0)
})

test('旧请求失败不会清空新结果', async () => {
  const { state, requests } = picker()
  const oldSearch = state.search('王')
  const newSearch = state.search('李')
  requests[1].resolve([{ id: 2, name: '李某' }])
  await newSearch
  requests[0].reject(new Error('旧请求网络失败'))
  await oldSearch
  assert.equal(state.options.value[0]?.label, '李某')
})

test('旧请求完成时不会提前关闭最新请求的加载状态', async () => {
  const { state, requests } = picker()
  const oldSearch = state.search('王')
  const newSearch = state.search('李')
  requests[0].resolve([])
  await oldSearch
  assert.equal(state.loading.value, true)
  requests[1].resolve([])
  await newSearch
  assert.equal(state.loading.value, false)
})

test('重复提交仅提示处理中，保留 DUP 错误且不误报网络异常', async () => {
  const warnings = []
  const errors = []
  const { default: service } = loadSource('src/api/request.ts', {
    'element-plus': { ElMessage: { warning: (message) => warnings.push(message), error: (message) => errors.push(message) } },
  })
  const pending = deferred()
  const started = deferred()
  let calls = 0
  service.defaults.adapter = (config) => { calls += 1; started.resolve(config); return pending.promise }
  const first = service.post('/测试提交', { amount: 10 })
  const config = await started.promise
  const duplicate = await service.post('/测试提交', { amount: 10 }).catch((error) => error)
  pending.resolve({ data: { code: 'OK', data: 10 }, status: 200, statusText: 'OK', headers: {}, config })
  assert.equal(await first, 10)
  assert.equal(calls, 1)
  assert.equal(duplicate.code, 'DUP')
  assert.equal(warnings.length, 1)
  assert.equal(errors.length, 0)
})

function passwordDialog(changePassword = async () => {}) {
  const calls = []
  const { default: component } = loadSource('src/views/layout/Layout.vue', {
    'vue-router': { useRoute: () => ({}), useRouter: () => ({ replace: async (path) => calls.push(`导航:${path}`) }) },
    '@/router': { clearDynamicRoutes: () => calls.push('清理路由') },
    'element-plus': { ElMessage: { success: (message) => calls.push(`提示:${message}`) }, ElMessageBox: {} },
    '@element-plus/icons-vue': {},
    '@/stores/user': { useUserStore: () => ({ changePassword, resetAuth: () => calls.push('清理认证') }) },
  })
  const state = component.setup({}, { expose() {} })
  state.pwdFormRef.value = { validate: async () => true }
  state.pwdDialogVisible.value = true
  return { state, calls }
}

test('修改密码成功后清理失效认证与动态路由，并立即返回登录页', async () => {
  const { state, calls } = passwordDialog()
  await state.handleChangePassword()
  assert.ok(calls.indexOf('清理认证') >= 0)
  assert.ok(calls.indexOf('清理路由') > calls.indexOf('清理认证'))
  assert.ok(calls.indexOf('导航:/login') > calls.indexOf('清理路由'))
  assert.ok(calls.some((call) => call.includes('请重新登录')))
  assert.equal(state.pwdDialogVisible.value, false)
  assert.equal(state.pwdSubmitting.value, false)
})

test('修改密码失败时保留当前认证和表单，允许纠正后重试', async () => {
  const { state, calls } = passwordDialog(async () => { throw new Error('旧密码错误') })
  await state.handleChangePassword()
  assert.deepEqual(calls, [])
  assert.equal(state.pwdDialogVisible.value, true)
  assert.equal(state.pwdSubmitting.value, false)
})

test('修改密码在提交前拒绝不足八位及缺少字母或数字的密码', async () => {
  const { state } = passwordDialog()
  const Schema = require('async-validator').default
  const validator = new Schema({ newPassword: state.pwdRules.newPassword })
  for (const password of ['abc1234', 'abcdefgh', '12345678']) {
    await assert.rejects(validator.validate({ newPassword: password }))
  }
  await validator.validate({ newPassword: 'abc12345' })
})

test('患者主索引合并调用已导入的写请求函数', async () => {
  const calls = []
  const { mergeMpiIndex } = loadSource('src/api/plt.ts', {
    './request': { post: async (url, body) => { calls.push({ url, body }); return 'MPI-2' } },
  })
  assert.equal(await mergeMpiIndex('MPI-1', 'MPI-2'), 'MPI-2')
  assert.deepEqual(calls, [{ url: '/plt/index/merge', body: { sourceMpiNo: 'MPI-1', targetMpiNo: 'MPI-2' } }])
})
