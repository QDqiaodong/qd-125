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
      <el-col :span="24">
        <div class="page-card pending-banner" @click="goConfirm">
          <div class="pending-left">
            <el-icon :size="26" color="#e6a23c"><Bell /></el-icon>
            <div>
              <div class="pending-title">待确认移交单</div>
              <div class="pending-desc">接收方需及时确认接收或驳回，确认后才更新产线绑定</div>
            </div>
          </div>
          <div class="pending-right">
            <span class="pending-count">{{ stats.pendingTransfers }}</span>
            <span class="pending-unit">单</span>
            <el-button type="warning" plain size="small">前往处理</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <div class="page-card stocktake-banner" @click="goStocktake">
          <div class="pending-left">
            <el-icon :size="26" color="#409eff"><DocumentChecked /></el-icon>
            <div>
              <div class="pending-title">盘点差异闭环</div>
              <div class="pending-desc">按产线创建盘点批次，逐项录入实物并闭环缺失、错线、重复盘点等差异</div>
            </div>
          </div>
          <div class="pending-right">
            <span class="stocktake-count">{{ stocktake.countingBatches }}</span>
            <span class="pending-unit">个批次盘点中 ·</span>
            <span class="stocktake-pending">{{ stocktake.pendingDiscrepancies }}</span>
            <span class="pending-unit">条差异待处理</span>
            <el-button type="primary" plain size="small">前往盘点</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <div class="page-card borrow-banner" :class="{ 'borrow-overdue': borrow.overdueCount > 0 || borrow.overdueReturnCount > 0 }" @click="goBorrow">
          <div class="pending-left">
            <el-icon :size="26" :color="borrow.overdueCount > 0 || borrow.overdueReturnCount > 0 ? '#f56c6c' : '#722ed1'"><Calendar /></el-icon>
            <div>
              <div class="pending-title">挡块借用预约</div>
              <div class="pending-desc">班组可预约空闲挡块、约定取用时段与归还点，占用期间档案标记“已约出”；过点未取自动提醒，超期未还整行标红</div>
            </div>
          </div>
          <div class="pending-right">
            <span class="borrow-count">{{ borrow.activeCount }}</span>
            <span class="pending-unit">块档案已约出 ·</span>
            <span class="borrow-reserved">{{ borrow.reservedCount }}</span>
            <span class="pending-unit">单待取 ·</span>
            <span :class="borrow.overdueCount > 0 ? 'stocktake-pending' : 'borrow-count'">{{ borrow.overdueCount }}</span>
            <span class="pending-unit">单逾时未取 ·</span>
            <span :class="borrow.overdueReturnCount > 0 ? 'stocktake-pending' : 'borrow-count'">{{ borrow.overdueReturnCount }}</span>
            <span class="pending-unit">单超期未还</span>
            <el-button :type="borrow.overdueCount > 0 || borrow.overdueReturnCount > 0 ? 'danger' : 'primary'" plain size="small">前往预约</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <div class="page-card handover-banner" :class="{ 'handover-active': handover.inProgress }" @click="goHandover">
          <div class="pending-left">
            <el-icon :size="26" :color="handover.inProgress ? '#e6a23c' : '#67c23a'"><SwitchButton /></el-icon>
            <div>
              <div class="pending-title">班组交班</div>
              <div class="pending-desc">交班一次性登记未还预约、待确认移交、待处理盘点差异与拦截中点检工装，接班人逐条确认后完成；交班未完成时禁止新开借用预约</div>
            </div>
          </div>
          <div class="pending-right">
            <template v-if="handover.inProgress">
              <span class="handover-count">{{ handover.unconfirmedCount }}</span>
              <span class="pending-unit">项待接班确认<template v-if="handover.unconfirmedGaugeCount > 0">（含拦截中工装 {{ handover.unconfirmedGaugeCount }} 件）</template> ·</span>
              <span class="pending-unit">{{ handover.handoverNo }}</span>
              <el-button type="warning" plain size="small">前往确认</el-button>
            </template>
            <template v-else>
              <span class="handover-done">无进行中的交班</span>
              <el-button type="success" plain size="small">查看交班</el-button>
            </template>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <div class="page-card due-soon-banner" :class="{ 'due-soon-active': dueSoon.count > 0 }" @click="dueSoonDialogVisible = true">
          <div class="pending-left">
            <el-icon :size="26" :color="dueSoon.count > 0 ? '#e6a23c' : '#67c23a'"><AlarmClock /></el-icon>
            <div>
              <div class="pending-title">校准临期提醒</div>
              <div class="pending-desc">下次应校日期进入 {{ dueSoon.windowDays }} 天临期窗口的在用挡块（挂起待修与已逾期不计入）；点开清单可核对应校日、所属产线与最近一次校准结论，临期挡块移交需二次确认</div>
            </div>
          </div>
          <div class="pending-right">
            <span class="due-soon-count" :class="{ 'due-soon-zero': dueSoon.count === 0 }">{{ dueSoon.count }}</span>
            <span class="pending-unit">块临期</span>
            <el-button :type="dueSoon.count > 0 ? 'warning' : 'success'" plain size="small">核对清单</el-button>
          </div>
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
            <el-table-column label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="statusMeta(scope.row.status).type" size="small">
                  {{ statusMeta(scope.row.status).text }}
                </el-tag>
              </template>
            </el-table-column>
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
                <li>跨产线移交登记（待确认）</li>
                <li>接收方确认/驳回，确认后更新产线绑定</li>
                <li>等待时长、流转记录与确认回执打印</li>
                <li>挡块盘点差异闭环：缺失/错线/重复/盘盈自动标记与处理追溯</li>
                <li>挡块借用预约：空闲挡块预约取用/归还点，占用档案标记“已约出”，取消必写原因，到点未取自动提醒</li>
                <li>班组交班：一次性登记未还预约/待确认移交/待处理盘点差异/拦截中点检工装，接班人逐条确认，交班未完成禁止新开预约</li>
                <li>挡块校准：临期窗口提醒与待办清单，逾期/挂起禁止移交，临期移交须二次确认</li>
              </ul>
            </el-descriptions-item>
            <el-descriptions-item label="使用说明">
              <ul style="margin: 0; padding-left: 20px;">
                <li>左侧菜单可进入各功能模块</li>
                <li>产线视图以树形结构展示车间与产线</li>
                <li>移交台账支持登记、日期筛选与移交单打印</li>
                <li>“移交确认”支持按状态、产线、日期筛选并打印确认回执</li>
                <li>“挡块盘点差异”按产线创建批次、逐项录入、闭环处理并导出结果</li>
              </ul>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <due-soon-list-dialog
      v-model="dueSoonDialogVisible"
      :items="dueSoon.items"
      :window-days="dueSoon.windowDays"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAllBlocks } from '@/api/block'
import { getLeafLines } from '@/api/line'
import { getTransfersByDateRange, getTransfersByBlockId } from '@/api/transfer'
import { getSpecTemplates } from '@/api/block'
import { getStocktakeOverview } from '@/api/stocktake'
import { getBorrowOverview } from '@/api/borrow'
import { getHandoverOverview } from '@/api/handover'
import { getDueSoonCalibrations } from '@/api/calibration'
import DueSoonListDialog from '@/components/DueSoonListDialog.vue'
import dayjs from 'dayjs'

const router = useRouter()

const stats = ref({
  totalLines: 0,
  totalBlocks: 0,
  monthTransfers: 0,
  specTemplates: 0,
  pendingTransfers: 0
})

const stocktake = ref({
  countingBatches: 0,
  pendingDiscrepancies: 0
})

const borrow = ref({
  reservedCount: 0,
  pickedUpCount: 0,
  overdueCount: 0,
  overdueReturnCount: 0,
  activeCount: 0
})

const handover = ref({
  inProgress: false,
  handoverNo: '',
  unconfirmedCount: 0,
  unconfirmedGaugeCount: 0
})

const dueSoon = ref({
  windowDays: 30,
  count: 0,
  items: []
})
const dueSoonDialogVisible = ref(false)

const recentTransfers = ref([])
const lineStats = ref([])

const statusMeta = (status) => {
  const map = {
    PENDING: { text: '待确认', type: 'warning' },
    CONFIRMED: { text: '已确认', type: 'success' },
    REJECTED: { text: '已驳回', type: 'danger' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const goConfirm = () => {
  router.push('/transfer-confirm')
}

const goStocktake = () => {
  router.push('/stocktakes')
}

const goBorrow = () => {
  router.push('/borrow')
}

const goHandover = () => {
  router.push('/handovers')
}

onMounted(async () => {
  try {
    const [blocks, lines, templates, transfers, overview, borrowOverview, handoverOverview, dueSoonOverview] = await Promise.all([
      getAllBlocks(),
      getLeafLines(),
      getSpecTemplates(),
      getTransfersByDateRange(),
      getStocktakeOverview(),
      getBorrowOverview(),
      getHandoverOverview(),
      getDueSoonCalibrations()
    ])

    stocktake.value.countingBatches = overview.countingBatches
    stocktake.value.pendingDiscrepancies = overview.pendingDiscrepancies
    borrow.value.reservedCount = borrowOverview.reservedCount
    borrow.value.pickedUpCount = borrowOverview.pickedUpCount
    borrow.value.overdueCount = borrowOverview.overdueCount
    borrow.value.overdueReturnCount = borrowOverview.overdueReturnCount || 0
    borrow.value.activeCount = borrowOverview.activeCount
    handover.value = handoverOverview
    dueSoon.value = {
      windowDays: dueSoonOverview.windowDays || 30,
      count: dueSoonOverview.count || 0,
      items: dueSoonOverview.items || []
    }
    stats.value.totalLines = lines.length
    stats.value.totalBlocks = blocks.length
    stats.value.specTemplates = templates.length

    const now = dayjs()
    stats.value.monthTransfers = transfers.filter(t =>
      dayjs(t.transferDate).isSame(now, 'month')
    ).length
    stats.value.pendingTransfers = transfers.filter(t => t.status === 'PENDING').length

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
.pending-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-left: 4px solid #e6a23c;
  transition: box-shadow 0.2s;
}
.pending-banner:hover {
  box-shadow: 0 4px 14px rgba(230, 162, 60, 0.25);
}
.pending-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.pending-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.pending-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.pending-right {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.pending-count {
  font-size: 32px;
  font-weight: 700;
  color: #e6a23c;
}
.pending-unit {
  color: #909399;
  font-size: 13px;
  margin-right: 12px;
}
.stocktake-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-left: 4px solid #409eff;
  transition: box-shadow 0.2s;
}
.stocktake-banner:hover {
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.25);
}
.stocktake-count {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.stocktake-pending {
  font-size: 26px;
  font-weight: 700;
  color: #f56c6c;
}
.borrow-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-left: 4px solid #722ed1;
  transition: box-shadow 0.2s;
}
.borrow-banner:hover {
  box-shadow: 0 4px 14px rgba(114, 46, 209, 0.25);
}
.borrow-banner.borrow-overdue {
  border-left-color: #f56c6c;
}
.borrow-count {
  font-size: 26px;
  font-weight: 700;
  color: #722ed1;
}
.borrow-reserved {
  font-size: 26px;
  font-weight: 700;
  color: #e6a23c;
}
.handover-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-left: 4px solid #67c23a;
  transition: box-shadow 0.2s;
}
.handover-banner.handover-active {
  border-left-color: #e6a23c;
}
.handover-banner:hover {
  box-shadow: 0 4px 14px rgba(230, 162, 60, 0.25);
}
.handover-count {
  font-size: 26px;
  font-weight: 700;
  color: #e6a23c;
}
.handover-done {
  font-size: 15px;
  font-weight: 600;
  color: #67c23a;
  margin-right: 12px;
}
.due-soon-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  border-left: 4px solid #67c23a;
  transition: box-shadow 0.2s;
}
.due-soon-banner.due-soon-active {
  border-left-color: #e6a23c;
}
.due-soon-banner:hover {
  box-shadow: 0 4px 14px rgba(230, 162, 60, 0.25);
}
.due-soon-count {
  font-size: 26px;
  font-weight: 700;
  color: #e6a23c;
}
.due-soon-count.due-soon-zero {
  color: #67c23a;
}
</style>
