<template>
  <div class="shift-handovers">
    <!-- 交班进行中横幅（数据全部来自服务端落库，重开页面仍在） -->
    <el-alert
      v-if="overview.inProgress"
      class="handover-banner"
      type="warning"
      show-icon
      :closable="false"
    >
      <template #title>
        <div class="banner-title">
          <el-icon><AlarmClock /></el-icon>
          交班进行中：{{ overview.handoverNo }}（{{ overview.fromTeam }} → {{ overview.toTeam }}），
          共 {{ overview.totalCount }} 项，还剩
          <span class="banner-count">{{ overview.unconfirmedCount }}</span>
          项待接班人逐条确认
          <template v-if="overview.unconfirmedGaugeCount > 0">
            （含拦截中工装
            <span class="banner-count">{{ overview.unconfirmedGaugeCount }}</span>
            件，须校准合格后方可确认）
          </template>
          ；交班完成前禁止新开借用预约
          <el-button type="warning" size="small" plain class="banner-btn" @click="openDetailById(overview.handoverId)">
            前往确认
          </el-button>
        </div>
      </template>
    </el-alert>

    <div class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><SwitchButton /></el-icon>
          班组交班
        </div>
        <div class="header-actions">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" :disabled="overview.inProgress" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            发起交班
          </el-button>
        </div>
      </div>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">当前交班单</div>
            <div class="mini-value primary handover-no">{{ overview.inProgress ? overview.handoverNo : '无' }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="mini-stat">
            <div class="mini-label">事项总数</div>
            <div class="mini-value primary">{{ overview.inProgress ? overview.totalCount : 0 }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="mini-stat">
            <div class="mini-label">已确认</div>
            <div class="mini-value success">{{ overview.inProgress ? overview.confirmedCount : 0 }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="mini-stat">
            <div class="mini-label">未确认</div>
            <div class="mini-value" :class="overview.inProgress && overview.unconfirmedCount > 0 ? 'danger' : 'success'">
              {{ overview.inProgress ? overview.unconfirmedCount : 0 }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">未确认工装（与校准台拦截同源）</div>
            <div class="mini-value" :class="overview.inProgress && overview.unconfirmedGaugeCount > 0 ? 'danger' : 'success'">
              {{ overview.inProgress ? overview.unconfirmedGaugeCount : 0 }}
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="filter-bar">
        <el-select
          v-model="query.status"
          placeholder="交班状态"
          clearable
          style="width: 150px;"
          @change="handleSearch"
        >
          <el-option label="交班中" value="IN_PROGRESS" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
        <el-input
          v-model="query.handoverNo"
          placeholder="交班单号"
          clearable
          style="width: 190px;"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>

      <el-alert
        class="tip-bar"
        type="info"
        :closable="false"
        show-icon
        title="交班时一次性登记当班全部未还预约、待确认移交、待处理盘点差异与当前拦截中的点检工装；接班人须逐条确认（工装须确认编号与拦截原因，仍拦截中的工装须校准合格移出拦截后才能确认），全部确认后交班才完成；交班未完成期间禁止新开借用预约；清单与未确认条数全部落库，未确认工装条数与校准台拦截条数同源，刷新页面后保持一致。"
      />

      <el-table :data="tableData" stripe v-loading="loading" row-key="id">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="handoverNo" label="交班单号" width="170">
          <template #default="scope">
            <el-tag type="primary" effect="plain">{{ scope.row.handoverNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交班班组 → 接班班组" min-width="170" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.fromTeam }} → {{ scope.row.toTeam }}
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="事项总数" width="90" align="center" />
        <el-table-column prop="confirmedCount" label="已确认" width="80" align="center" />
        <el-table-column label="未确认" width="90" align="center">
          <template #default="scope">
            <span :class="{ 'unconfirmed-text': scope.row.unconfirmedCount > 0 }">
              {{ scope.row.unconfirmedCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'IN_PROGRESS' ? 'warning' : 'success'" effect="dark" disable-transitions>
              {{ scope.row.status === 'IN_PROGRESS' ? '交班中' : '已完成' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handoverOperator" label="交班登记人" width="110" show-overflow-tooltip />
        <el-table-column prop="receiveOperator" label="接班人" width="100" show-overflow-tooltip />
        <el-table-column label="登记时间" width="165">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="165">
          <template #default="scope">{{ scope.row.finishTime ? formatTime(scope.row.finishTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openDetail(scope.row)">
              <el-icon><View /></el-icon>详情
            </el-button>
            <el-button
              v-if="scope.row.status === 'IN_PROGRESS'"
              size="small"
              type="warning"
              link
              @click="openDetail(scope.row)"
            >
              <el-icon><CircleCheck /></el-icon>接班确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 发起交班：预览将一次性登记的四类未结事项 -->
    <el-dialog v-model="createVisible" title="发起班组交班" width="780px" @close="resetCreateForm">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="提交后以下事项（含当前拦截中的点检工装）将一次性快照登记为交班清单，接班人须逐条确认；交班完成前禁止新开借用预约。"
        class="create-alert"
      />
      <div v-loading="previewLoading">
        <el-row :gutter="12" class="preview-stat">
          <el-col :span="6">
            <div class="mini-stat">
              <div class="mini-label">未还预约</div>
              <div class="mini-value danger">{{ preview.borrowCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <div class="mini-label">待确认移交</div>
              <div class="mini-value warning">{{ preview.transferCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <div class="mini-label">待处理盘点差异</div>
              <div class="mini-value primary">{{ preview.stocktakeCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat">
              <div class="mini-label">拦截中工装</div>
              <div class="mini-value danger">{{ preview.gaugeCount }}</div>
            </div>
          </el-col>
        </el-row>
        <el-table :data="preview.items" size="small" max-height="260" border class="preview-table">
          <el-table-column label="类型" width="120" align="center">
            <template #default="scope">
              <el-tag :type="itemTypeMeta(scope.row.itemType).type" size="small" effect="plain">
                {{ itemTypeMeta(scope.row.itemType).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="refNo" label="源单号/工装编号" width="160" show-overflow-tooltip />
          <el-table-column prop="blockCode" label="挡块编号" width="120" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.blockCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="事项摘要" min-width="260" show-overflow-tooltip />
        </el-table>
      </div>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px" class="create-form">
        <el-form-item label="交班班组" prop="fromTeam">
          <el-input v-model="createForm.fromTeam" placeholder="如：甲班" />
        </el-form-item>
        <el-form-item label="接班班组" prop="toTeam">
          <el-input v-model="createForm.toTeam" placeholder="如：乙班" />
        </el-form-item>
        <el-form-item label="交班登记人" prop="handoverOperator">
          <el-input v-model="createForm.handoverOperator" placeholder="交班登记人" />
        </el-form-item>
        <el-form-item label="接班人" prop="receiveOperator">
          <el-input v-model="createForm.receiveOperator" placeholder="负责逐条确认的接班人" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认交班登记</el-button>
      </template>
    </el-dialog>

    <!-- 交班详情 + 接班人逐条确认 -->
    <el-dialog v-model="detailVisible" title="交班详情与接班确认" width="920px">
      <template v-if="currentHandover">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="交班单号">
            <el-tag type="primary" effect="plain">{{ currentHandover.handoverNo }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentHandover.status === 'IN_PROGRESS' ? 'warning' : 'success'" effect="dark" disable-transitions>
              {{ currentHandover.status === 'IN_PROGRESS' ? '交班中' : '已完成' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ formatTime(currentHandover.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="交班班组">{{ currentHandover.fromTeam }}</el-descriptions-item>
          <el-descriptions-item label="接班班组">{{ currentHandover.toTeam }}</el-descriptions-item>
          <el-descriptions-item label="接班人">{{ currentHandover.receiveOperator }}</el-descriptions-item>
          <el-descriptions-item label="交班登记人">{{ currentHandover.handoverOperator }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ currentHandover.finishTime ? formatTime(currentHandover.finishTime) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ currentHandover.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="progress-row">
          <span class="progress-label">
            确认进度：{{ currentHandover.confirmedCount }}/{{ currentHandover.totalCount }}
            <template v-if="currentHandover.status === 'IN_PROGRESS'">
              （还剩 <span class="unconfirmed-text">{{ currentHandover.unconfirmedCount }}</span> 项待确认）
            </template>
          </span>
          <el-progress
            :percentage="confirmPercent"
            :status="currentHandover.status === 'COMPLETED' ? 'success' : undefined"
            class="progress-bar"
          />
        </div>

        <el-alert
          v-if="blockedGaugeItems.length > 0"
          class="gauge-alert"
          type="error"
          :closable="false"
          show-icon
          :title="`有 ${blockedGaugeItems.length} 件点检工装仍拦截中（${blockedGaugeItems.map(i => i.refNo).join('、')}），须先在工装校准台校准合格移出拦截清单，接班人才能确认该条`"
        />

        <el-table :data="items" size="small" border v-loading="itemsLoading" max-height="380">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column label="类型" width="130" align="center">
            <template #default="scope">
              <el-tag :type="itemTypeMeta(scope.row.itemType).type" size="small" effect="plain">
                {{ itemTypeMeta(scope.row.itemType).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="refNo" label="源单号/工装编号" width="160" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.refNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="blockCode" label="挡块编号" width="115" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.blockCode || '-' }}</template>
          </el-table-column>
          <el-table-column prop="summary" label="事项摘要（登记时快照）" min-width="240" show-overflow-tooltip />
          <el-table-column label="源单当前状态" width="130" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.sourceStillOpen ? 'danger' : 'info'" size="small" effect="plain">
                {{ scope.row.sourceStatusText || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="确认状态" width="170" align="center">
            <template #default="scope">
              <el-tooltip
                v-if="scope.row.status === 'CONFIRMED'"
                effect="dark"
                placement="top"
                :content="`${scope.row.confirmOperator || ''} · ${formatTime(scope.row.confirmTime)}${scope.row.confirmNote ? ' · ' + scope.row.confirmNote : ''}`"
              >
                <el-tag type="success" size="small" effect="dark" disable-transitions>已确认</el-tag>
              </el-tooltip>
              <el-tag v-else type="warning" size="small" effect="dark" disable-transitions>待确认</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="currentHandover.status === 'IN_PROGRESS'" label="操作" width="100" fixed="right" align="center">
            <template #default="scope">
              <el-tooltip
                v-if="scope.row.status === 'PENDING' && scope.row.itemType === 'GAUGE_BLOCKED' && scope.row.sourceStillOpen"
                effect="dark"
                placement="top"
                content="工装仍拦截中，须校准合格移出拦截清单后才能确认"
              >
                <span>
                  <el-button size="small" type="warning" link disabled>
                    <el-icon><CircleCheck /></el-icon>确认
                  </el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else-if="scope.row.status === 'PENDING'"
                size="small"
                type="warning"
                link
                @click="openConfirm(scope.row)"
              >
                <el-icon><CircleCheck /></el-icon>确认
              </el-button>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 单条事项确认 -->
    <el-dialog v-model="confirmVisible" title="接班确认" width="480px">
      <div v-if="confirmTarget" class="confirm-target">
        <el-tag :type="itemTypeMeta(confirmTarget.itemType).type" effect="plain" size="small">
          {{ itemTypeMeta(confirmTarget.itemType).text }}
        </el-tag>
        <span>{{ confirmTarget.refNo }}</span>
        <span v-if="confirmTarget.blockCode" class="muted">{{ confirmTarget.blockCode }}</span>
      </div>
      <div v-if="confirmTarget" class="confirm-summary">{{ confirmTarget.summary }}</div>
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="90px">
        <el-form-item label="确认人" prop="operator">
          <el-input v-model="confirmForm.operator" placeholder="确认人（接班人）" />
        </el-form-item>
        <el-form-item label="确认备注" prop="note">
          <el-input v-model="confirmForm.note" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">返回</el-button>
        <el-button type="warning" :loading="submitting" @click="submitConfirm">确认该事项</el-button>
      </template>
    </el-dialog>

    <!-- 确认被拒：工装仍在拦截中，弹窗明示编号与拦截原因 -->
    <el-dialog
      v-model="gaugeBlockedVisible"
      title="该工装仍在拦截中，暂不能确认"
      width="560px"
    >
      <el-result
        icon="warning"
        title="点检工装尚未移出拦截清单"
        sub-title="请先到工装校准台校准合格（或停用）移出拦截清单后，再由接班人确认该条事项"
      />
      <div v-if="gaugeBlockedDetail" class="gauge-blocked-detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工装编号">
            <el-tag type="danger" effect="plain" size="small">{{ gaugeBlockedDetail.toolCode }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工装类型">
            {{ gaugeBlockedDetail.toolTypeName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="保管班组">
            {{ gaugeBlockedDetail.keeperTeam || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="拦截原因">
            <el-tag
              :type="gaugeBlockedDetail.blockedReason === 'OVERDUE' ? 'danger' : 'warning'"
              effect="dark"
              size="small"
            >
              {{ gaugeBlockedReasonText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="gaugeBlockedDetail.blockedReason === 'FAIL'
            && gaugeBlockedDetail.lastCalibrationDate" label="最近校准">
            {{ formatDate(gaugeBlockedDetail.lastCalibrationDate) }} 不合格
            <template v-if="gaugeBlockedDetail.lastCalibrator">
              （校准人：{{ gaugeBlockedDetail.lastCalibrator }}）
            </template>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-alert
        v-else-if="gaugeBlockedMessage"
        class="gauge-blocked-msg"
        type="error"
        :closable="false"
        show-icon
        :title="gaugeBlockedMessage"
      />
      <div class="gauge-blocked-tip">
        本次确认未落库，该条仍为「待确认」；详情已刷新，未确认条数与校准台拦截清单重新对账。
      </div>
      <template #footer>
        <el-button @click="gaugeBlockedVisible = false">我知道了</el-button>
        <el-button type="primary" @click="goGaugeCalibration">前往工装校准台</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import {
  queryShiftHandovers,
  getHandoverOverview,
  getHandoverPreview,
  createShiftHandover,
  getShiftHandoverById,
  getHandoverItems,
  confirmHandoverItem
} from '@/api/handover'
import { BIZ_CODE_HANDOVER_GAUGE_STILL_BLOCKED } from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)

const overview = ref({
  inProgress: false,
  handoverId: null,
  handoverNo: '',
  fromTeam: '',
  toTeam: '',
  totalCount: 0,
  confirmedCount: 0,
  unconfirmedCount: 0,
  unconfirmedGaugeCount: 0
})

const query = reactive({ status: '', handoverNo: '', page: 1, size: 10 })

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-')
const formatDate = (d) => (d ? dayjs(d).format('YYYY-MM-DD') : '-')

const itemTypeMeta = (type) => {
  const map = {
    BORROW_UNRETURNED: { text: '未还预约', type: 'danger' },
    TRANSFER_PENDING: { text: '待确认移交', type: 'warning' },
    STOCKTAKE_PENDING: { text: '待处理盘点差异', type: 'primary' },
    GAUGE_BLOCKED: { text: '拦截中工装', type: 'danger' }
  }
  return map[type] || { text: type || '-', type: 'info' }
}

const loadList = async () => {
  loading.value = true
  try {
    const page = await queryShiftHandovers({
      status: query.status || null,
      handoverNo: query.handoverNo || null,
      page: query.page,
      size: query.size
    })
    tableData.value = page.content
    total.value = page.totalElements
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const loadOverview = async () => {
  try {
    overview.value = await getHandoverOverview()
  } catch (e) {
    // ignore
  }
}

const loadData = async () => {
  await Promise.all([loadList(), loadOverview()])
}

const handleSearch = () => {
  query.page = 1
  loadList()
}

const handleReset = () => {
  query.status = ''
  query.handoverNo = ''
  query.page = 1
  loadList()
}

// ---------------- 发起交班 ----------------

const createVisible = ref(false)
const previewLoading = ref(false)
const createFormRef = ref(null)
const preview = ref({ items: [], borrowCount: 0, transferCount: 0, stocktakeCount: 0, gaugeCount: 0 })
const defaultCreateForm = () => ({
  fromTeam: '',
  toTeam: '',
  handoverOperator: '',
  receiveOperator: '',
  remark: ''
})
const createForm = reactive(defaultCreateForm())

const createRules = {
  fromTeam: [{ required: true, message: '请填写交班班组', trigger: 'blur' }],
  toTeam: [{ required: true, message: '请填写接班班组', trigger: 'blur' }],
  handoverOperator: [{ required: true, message: '请填写交班登记人', trigger: 'blur' }],
  receiveOperator: [{ required: true, message: '请填写接班人', trigger: 'blur' }]
}

const openCreateDialog = async () => {
  Object.assign(createForm, defaultCreateForm())
  createVisible.value = true
  previewLoading.value = true
  try {
    preview.value = await getHandoverPreview()
  } catch (e) {
    preview.value = { items: [], borrowCount: 0, transferCount: 0, stocktakeCount: 0, gaugeCount: 0 }
  } finally {
    previewLoading.value = false
  }
}

const resetCreateForm = () => {
  createFormRef.value?.resetFields()
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await createShiftHandover({ ...createForm })
      ElMessage.success('交班登记成功，事项清单已落库，请接班人逐条确认')
      createVisible.value = false
      await loadData()
    } catch (e) {
      // 后端返回明确中文原因（上次交班未完成/无未结事项等），拦截器已弹窗
    } finally {
      submitting.value = false
    }
  })
}

// ---------------- 详情 + 逐条确认 ----------------

const detailVisible = ref(false)
const itemsLoading = ref(false)
const currentHandover = ref(null)
const items = ref([])

const confirmPercent = computed(() => {
  if (!currentHandover.value || !currentHandover.value.totalCount) return 0
  return Math.round((currentHandover.value.confirmedCount / currentHandover.value.totalCount) * 100)
})

// 仍拦截中的工装事项（与校准台拦截清单同源）：须校准合格移出拦截后才能确认
const blockedGaugeItems = computed(() =>
  items.value.filter(i => i.itemType === 'GAUGE_BLOCKED' && i.status === 'PENDING' && i.sourceStillOpen)
)

const openDetail = async (row) => {
  await openDetailById(row.id)
}

const openDetailById = async (id) => {
  detailVisible.value = true
  itemsLoading.value = true
  try {
    const [handover, itemList] = await Promise.all([getShiftHandoverById(id), getHandoverItems(id)])
    currentHandover.value = handover
    items.value = itemList
  } catch (e) {
    detailVisible.value = false
  } finally {
    itemsLoading.value = false
  }
}

const reloadDetail = async () => {
  if (!currentHandover.value) return
  try {
    const [handover, itemList] = await Promise.all([
      getShiftHandoverById(currentHandover.value.id),
      getHandoverItems(currentHandover.value.id)
    ])
    currentHandover.value = handover
    items.value = itemList
  } catch (e) {
    // ignore
  }
}

// ---------------- 单条确认 ----------------

const confirmVisible = ref(false)
const confirmTarget = ref(null)
const confirmFormRef = ref(null)
const confirmForm = reactive({ operator: '', note: '' })
const confirmRules = {
  operator: [{ required: true, message: '请填写确认人（接班人）', trigger: 'blur' }]
}

const openConfirm = (row) => {
  confirmTarget.value = row
  // 默认带出交班单上登记的接班人
  confirmForm.operator = currentHandover.value?.receiveOperator || ''
  confirmForm.note = ''
  confirmVisible.value = true
}

const submitConfirm = async () => {
  if (!confirmFormRef.value) return
  await confirmFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const handover = await confirmHandoverItem(currentHandover.value.id, confirmTarget.value.id, {
        operator: confirmForm.operator,
        note: confirmForm.note
      })
      confirmVisible.value = false
      if (handover.status === 'COMPLETED') {
        ElMessage.success('全部事项已确认，交班完成，借用预约已恢复开放')
      } else {
        ElMessage.success(`已确认，还剩 ${handover.unconfirmedCount} 项待确认`)
      }
      await Promise.all([reloadDetail(), loadData()])
    } catch (e) {
      if (e.code === BIZ_CODE_HANDOVER_GAUGE_STILL_BLOCKED) {
        // 工装仍拦截中：后端返回结构化明细（编号/类型/保管班组/拦截原因），
        // 关闭确认弹窗并弹出拦截原因，接班人核对编号后须先到校准台处理
        confirmVisible.value = false
        gaugeBlockedDetail.value = e.detail || null
        gaugeBlockedMessage.value = e.message || ''
        gaugeBlockedVisible.value = true
        // 失败未落库：刷新详情与概览，与校准台拦截清单重新对账，
        // 保证顶部提示、行内“待确认/拦截中”状态和未确认条数对得上
        await Promise.all([reloadDetail(), loadData()])
      }
      // 其他错误由请求拦截器统一提示；确认弹窗保持打开便于修改
    } finally {
      submitting.value = false
    }
  })
}

// 确认被“工装仍拦截中”拒绝：明细弹窗（与点检打卡工装拦截同款）
const gaugeBlockedVisible = ref(false)
const gaugeBlockedDetail = ref(null)
const gaugeBlockedMessage = ref('')

// 拦截原因展示文本（OVERDUE 含到期日与超期天数，FAIL 含最近校准信息）
const gaugeBlockedReasonText = computed(() => {
  const g = gaugeBlockedDetail.value
  if (!g) return gaugeBlockedMessage.value || ''
  if (g.blockedReason === 'OVERDUE') {
    let text = `到期未校准（到期日 ${formatDate(g.calibrationDueDate)}）`
    if (g.overdueDays != null) {
      text += `，已超期 ${g.overdueDays} 天`
    }
    return text
  }
  if (g.blockedReason === 'FAIL') {
    return '校准结论不合格'
  }
  return gaugeBlockedMessage.value || '该工装仍在拦截中'
})

const goGaugeCalibration = () => {
  gaugeBlockedVisible.value = false
  router.push('/gauge-calibration')
}

onMounted(loadData)
</script>

<style scoped>
.handover-banner {
  margin-bottom: 16px;
}
.banner-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  flex-wrap: wrap;
}
.banner-count {
  color: #f56c6c;
  font-size: 18px;
  padding: 0 2px;
}
.banner-btn {
  margin-left: 10px;
}
.header-actions {
  display: flex;
  gap: 10px;
}
.stat-row {
  margin-bottom: 16px;
}
.mini-stat {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.mini-label {
  color: #909399;
  font-size: 13px;
}
.mini-value {
  font-size: 24px;
  font-weight: 700;
}
.mini-value.primary { color: #409eff; }
.mini-value.warning { color: #e6a23c; }
.mini-value.danger { color: #f56c6c; }
.mini-value.success { color: #67c23a; }
.mini-value.handover-no {
  font-size: 16px;
}
.tip-bar {
  margin-bottom: 16px;
}
.unconfirmed-text {
  color: #f56c6c;
  font-weight: 700;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.create-alert {
  margin-bottom: 14px;
}
.preview-stat {
  margin-bottom: 12px;
}
.preview-table {
  margin-bottom: 16px;
}
.create-form {
  margin-top: 6px;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 16px 0 12px;
}
.progress-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.progress-bar {
  flex: 1;
}
.gauge-alert {
  margin-bottom: 12px;
}
.confirm-target {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
}
.confirm-summary {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 14px;
  line-height: 1.5;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.gauge-blocked-detail {
  margin: 4px 0 12px;
}
.gauge-blocked-msg {
  margin: 4px 0 12px;
}
.gauge-blocked-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  padding: 0 4px;
}
</style>
