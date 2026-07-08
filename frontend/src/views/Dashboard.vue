<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">
            <el-icon><OfficeBuilding /></el-icon>
            产线总数
          </div>
          <div class="stat-value">{{ stats.totalLines }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card blue">
          <div class="stat-label">
            <el-icon><Grid /></el-icon>
            挡块档案总数
          </div>
          <div class="stat-value">{{ stats.totalBlocks }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card green">
          <div class="stat-label">
            <el-icon><Switch /></el-icon>
            本月移交次数
          </div>
          <div class="stat-value">{{ stats.monthTransfers }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card orange">
          <div class="stat-label">
            <el-icon><CollectionTag /></el-icon>
            规格模板数
          </div>
          <div class="stat-value">{{ stats.specTemplates }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <div class="page-card">
          <div class="page-header">
            <div class="page-title">
              <el-icon :size="20" color="#409EFF"><Warning /></el-icon>
              最近移交记录
            </div>
          </div>
          <el-table :data="recentTransfers" stripe>
            <el-table-column prop="transferNo" label="移交单号" width="180" />
            <el-table-column prop="blockCode" label="挡块编号" width="140" />
            <el-table-column prop="fromLineName" label="移出产线" show-overflow-tooltip />
            <el-table-column prop="toLineName" label="移入产线" show-overflow-tooltip />
            <el-table-column prop="transferDate" label="移交日期" width="120" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <div class="page-header">
            <div class="page-title">
              <el-icon :size="20" color="#409EFF"><Collection /></el-icon>
              产线分布概览
            </div>
          </div>
          <el-table :data="lineStats" stripe>
            <el-table-column prop="lineName" label="产线名称" show-overflow-tooltip />
            <el-table-column prop="blockCount" label="挡块数量" width="120" align="center">
              <template #default="scope">
                <el-tag type="primary">{{ scope.row.blockCount }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <div class="page-card">
          <div class="page-header">
            <div class="page-title">
              <el-icon :size="20" color="#409EFF"><InfoFilled /></el-icon>
              系统说明
            </div>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="系统名称">输送设备缓冲挡块跨产线移交划转系统</el-descriptions-item>
            <el-descriptions-item label="功能定位">挡块档案管理与跨产线移交单据打印</el-descriptions-item>
            <el-descriptions-item label="核心模块">
              <ul style="margin: 0; padding-left: 20px;">
                <li>缓冲挡块基础建档</li>
                <li>初始产线归属绑定</li>
                <li>跨产线移交划转登记</li>
                <li>移交台账筛选与单据打印</li>
              </ul>
            </el-descriptions-item>
            <el-descriptions-item label="使用说明">
              <ul style="margin: 0; padding-left: 20px;">
                <li>左侧菜单可进入各功能模块</li>
                <li>产线视图以树形结构展示车间与产线</li>
                <li>移交台账支持按日期区间筛选并生成打印单据</li>
              </ul>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAllBlocks } from '@/api/block'
import { getLeafLines } from '@/api/line'
import { getTransfersByDateRange, getTransfersByBlockId } from '@/api/transfer'
import { getSpecTemplates } from '@/api/block'
import dayjs from 'dayjs'

const stats = ref({
  totalLines: 0,
  totalBlocks: 0,
  monthTransfers: 0,
  specTemplates: 0
})

const recentTransfers = ref([])
const lineStats = ref([])

onMounted(async () => {
  try {
    const [blocks, lines, templates, transfers] = await Promise.all([
      getAllBlocks(),
      getLeafLines(),
      getSpecTemplates(),
      getTransfersByDateRange()
    ])

    stats.value.totalLines = lines.length
    stats.value.totalBlocks = blocks.length
    stats.value.specTemplates = templates.length

    const now = dayjs()
    stats.value.monthTransfers = transfers.filter(t => 
      dayjs(t.transferDate).isSame(now, 'month')
    ).length

    recentTransfers.value = transfers.slice(0, 8)

    const lineBlockMap = {}
    blocks.forEach(b => {
      if (b.lineId) {
        lineBlockMap[b.lineId] = (lineBlockMap[b.lineId] || 0) + 1
      }
    })
    lineStats.value = lines.map(l => ({
      ...l,
      blockCount: lineBlockMap[l.id] || 0
    })).sort((a, b) => b.blockCount - a.blockCount).slice(0, 8)
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.dashboard {
  width: 100%;
}
.stat-label {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
