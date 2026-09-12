<template>
  <div class="page-card">
    <el-tabs v-model="activeTab" class="log-tabs">
      <!-- 操作日志 -->
      <el-tab-pane label="操作日志" name="operation">
        <el-form class="search-bar" :model="opQuery" inline>
          <el-form-item label="用户名">
            <el-input
              v-model="opQuery.username"
              placeholder="请输入用户名"
              clearable
              style="width: 160px"
              @keyup.enter="searchOperation"
            />
          </el-form-item>
          <el-form-item label="模块">
            <el-input
              v-model="opQuery.module"
              placeholder="如 system、basedata"
              clearable
              style="width: 160px"
              @keyup.enter="searchOperation"
            />
          </el-form-item>
          <el-form-item label="操作时间">
            <el-date-picker
              v-model="opDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchOperation">查询</el-button>
            <el-button :icon="Refresh" @click="resetOperation">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="opLoading" :data="opList" border stripe>
          <el-table-column prop="createdAt" label="操作时间" width="170" />
          <el-table-column prop="username" label="操作人" min-width="110" />
          <el-table-column prop="module" label="模块" min-width="110" />
          <el-table-column prop="action" label="动作" min-width="160" show-overflow-tooltip />
          <el-table-column prop="bizId" label="业务单号" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.bizId || '-' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.resultCode === 'OK' ? 'success' : 'danger'" size="small">
                {{ row.resultCode === 'OK' ? '成功' : row.resultCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ip || '-' }}</template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时(ms)" width="95" align="right">
            <template #default="{ row }">{{ row.costMs ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="traceId" label="traceId" min-width="150" show-overflow-tooltip />
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="opQuery.pageNum"
            v-model:page-size="opQuery.pageSize"
            :total="opTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="searchOperation"
            @current-change="fetchOperation"
          />
        </div>
      </el-tab-pane>

      <!-- 登录日志 -->
      <el-tab-pane label="登录日志" name="login">
        <el-form class="search-bar" :model="loginQuery" inline>
          <el-form-item label="用户名">
            <el-input
              v-model="loginQuery.username"
              placeholder="请输入用户名"
              clearable
              style="width: 160px"
              @keyup.enter="searchLogin"
            />
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="loginQuery.success" placeholder="全部" clearable style="width: 120px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="登录时间">
            <el-date-picker
              v-model="loginDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchLogin">查询</el-button>
            <el-button :icon="Refresh" @click="resetLogin">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loginLoading" :data="loginList" border stripe>
          <el-table-column prop="createdAt" label="登录时间" width="170" />
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column label="结果" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
                {{ row.success === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="结果信息" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '-' }}</template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ip || '-' }}</template>
          </el-table-column>
          <el-table-column prop="userAgent" label="User-Agent" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.userAgent || '-' }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="loginQuery.pageNum"
            v-model:page-size="loginQuery.pageSize"
            :total="loginTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="searchLogin"
            @current-change="fetchLogin"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getLoginLogPage, getOperationLogPage, type LoginLog, type OperationLog } from '@/api/system'

const activeTab = ref('operation')

// ---------------- 操作日志 ----------------
const opLoading = ref(false)
const opList = ref<OperationLog[]>([])
const opTotal = ref(0)
const opDateRange = ref<[string, string] | null>(null)

const opQuery = ref({
  pageNum: 1,
  pageSize: 10,
  username: '',
  module: '',
})

async function fetchOperation() {
  opLoading.value = true
  try {
    const res = await getOperationLogPage({
      pageNum: opQuery.value.pageNum,
      pageSize: opQuery.value.pageSize,
      username: opQuery.value.username || undefined,
      module: opQuery.value.module || undefined,
      startDate: opDateRange.value?.[0],
      endDate: opDateRange.value?.[1],
    })
    opList.value = res.list ?? []
    opTotal.value = res.total ?? 0
  } finally {
    opLoading.value = false
  }
}

function searchOperation() {
  opQuery.value.pageNum = 1
  fetchOperation()
}

function resetOperation() {
  opQuery.value.username = ''
  opQuery.value.module = ''
  opDateRange.value = null
  searchOperation()
}

// ---------------- 登录日志 ----------------
const loginLoading = ref(false)
const loginList = ref<LoginLog[]>([])
const loginTotal = ref(0)
const loginDateRange = ref<[string, string] | null>(null)

const loginQuery = ref({
  pageNum: 1,
  pageSize: 10,
  username: '',
  success: undefined as number | undefined,
})

async function fetchLogin() {
  loginLoading.value = true
  try {
    const res = await getLoginLogPage({
      pageNum: loginQuery.value.pageNum,
      pageSize: loginQuery.value.pageSize,
      username: loginQuery.value.username || undefined,
      success: loginQuery.value.success,
      startDate: loginDateRange.value?.[0],
      endDate: loginDateRange.value?.[1],
    })
    loginList.value = res.list ?? []
    loginTotal.value = res.total ?? 0
  } finally {
    loginLoading.value = false
  }
}

function searchLogin() {
  loginQuery.value.pageNum = 1
  fetchLogin()
}

function resetLogin() {
  loginQuery.value.username = ''
  loginQuery.value.success = undefined
  loginDateRange.value = null
  searchLogin()
}

// 切换 tab 时按需加载
watch(activeTab, (tab) => {
  if (tab === 'login' && loginTotal.value === 0 && loginList.value.length === 0) {
    fetchLogin()
  }
})

onMounted(fetchOperation)
</script>
