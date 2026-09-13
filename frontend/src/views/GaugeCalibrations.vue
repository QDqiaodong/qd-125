<template>
  <div class="gauge-calibration">
    <div class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><SetUp /></el-icon>
          点检工装校准台
        </div>
        <el-button @click="loadOverview">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <el-alert
        class="tip-bar"
        type="info"
        :closable="false"
        show-icon
        title="卡尺 / 塞尺 / 百分表台账：登记工装编号、校准到期日与保管班组；到期未校准或最近校准结论不合格的工装会拦住班次点检打卡并列出编号，校准合格后同一班次即可重新打卡。"
      />

      <!-- 拦截清单：条数与编号同源实时派生，刷新后与点检打卡拦截弹窗逐条对得上 -->
      <el-alert
        v-if="overview.blockedCount > 0"
        class="tip-bar blocked-bar"
        type="error"
        :closable="false"
        show-icon
        :title="`有 ${overview.blockedCount} 件点检工装到期未校准或校准结论不合格，班次点检打卡已被拦截，请先校准合格`"
      >
        <div class="blocked-list">
          <el-tag
            v-for="item in overview.blockedItems"
            :key="item.id"
            type="danger"
            effect="plain"
            size="small"
            class="blocked-tag"
          >
            {{ item.toolCode }}
            <span class="blocked-tag-reason">{{ reasonText(item.blockedReason) }}</span>
          </el-tag>
        </div>
      </el-alert>

      <div class="filter-bar">
        <el-select v-model="query.toolType" placeholder="工装类型" clearable style="width: 140px;">
          <el-option
            v-for="opt in typeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="query.status" placeholder="校准状态" clearable style="width: 170px;">
          <el-option label="合格在期" value="NORMAL" />
          <el-option label="到期未校准" value="OVERDUE" />
          <el-option label="校准结论不合格" value="FAIL" />
          <el-option label="从未校准" value="UNCALIBRATED" />
          <el-option label="已停用" value="DISABLED" />
          <el-option label="拦截中（超期/不合格）" value="BLOCKED" />
        </el-select>
        <el-select v-model="query.keeperTeam" placeholder="保管班组" clearable filterable style="width: 170px;">
          <el-option
            v-for="team in teamOptions"
            :key="team"
            :label="team"
            :value="team"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索工装编号/规格型号"
          clearable
          style="width: 220px;"
          :prefix-icon="Search"
          @keyup.enter="loadOverview"
        />
        <el-button type="primary" @click="loadOverview">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="resetQuery">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
        <el-button type="success" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新增工装台账
        </el-button>
      </div>

      <div class="count-bar">
        <div class="count-item">
          <span class="count-num">{{ overview.totalCount }}</span>
          <span class="count-label">筛选台账</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num blocked">{{ overview.blockedCount }}</span>
          <span class="count-label">拦截中（超期/不合格）</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num overdue">{{ overview.overdueCount }}</span>
          <span class="count-label">到期未校准</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num fail">{{ overview.failCount }}</span>
          <span class="count-label">结论不合格</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num normal">{{ overview.normalCount }}</span>
          <span class="count-label">合格在期</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num uncal">{{ overview.uncalibratedCount }}</span>
          <span class="count-label">从未校准</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num disabled">{{ overview.disabledCount }}</span>
          <span class="count-label">已停用</span>
        </div>
        <div class="count-tip">拦截条数与上方编号清单、班次点检打卡拦截同源（实时派生，已全部落库）</div>
      </div>

      <el-table
        :data="overview.items"
        stripe
        v-loading="loading"
        :row-class-name="rowClassName"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="toolCode" label="工装编号" width="150">
          <template #default="scope">
            <el-tag :type="scope.row.blocked ? 'danger' : 'primary'" effect="plain">
              {{ scope.row.toolCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工装类型" width="100" align="center">
          <template #default="scope">{{ scope.row.toolTypeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="specModel" label="规格型号" min-width="130" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.specModel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="keeperTeam" label="保管班组" width="130" align="center" />
        <el-table-column label="校准到期日" width="130" align="center">
          <template #default="scope">
            <span :class="{ 'due-overdue': scope.row.blockedReason === 'OVERDUE' }">
              {{ formatDate(scope.row.calibrationDueDate) }}
            </span>
            <div v-if="scope.row.blockedReason === 'OVERDUE' && scope.row.overdueDays != null" class="overdue-days">
              已超期 {{ scope.row.overdueDays }} 天
            </div>
          </template>
        </el-table-column>
        <el-table-column label="校准状态" width="150" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.calibrationStatus === 'NORMAL'" type="success" effect="dark">合格在期</el-tag>
            <el-tag v-else-if="scope.row.calibrationStatus === 'OVERDUE'" type="danger" effect="dark">到期未校准</el-tag>
            <el-tag v-else-if="scope.row.calibrationStatus === 'FAIL'" type="danger" effect="plain">结论不合格</el-tag>
            <el-tag v-else-if="scope.row.calibrationStatus === 'UNCALIBRATED'" type="warning" effect="plain">从未校准</el-tag>
            <el-tag v-else-if="scope.row.calibrationStatus === 'DISABLED'" type="info" effect="plain">已停用</el-tag>
            <el-tag v-else type="info">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近校准" min-width="200">
          <template #default="scope">
            <div v-if="scope.row.lastCalibrationDate" class="last-cal">
              <span>{{ formatDate(scope.row.lastCalibrationDate) }}</span>
              <el-tag
                :type="scope.row.lastResult === 'PASS' ? 'success' : 'danger'"
                size="small"
                effect="plain"
              >{{ scope.row.lastResult === 'PASS' ? '合格' : '不合格' }}</el-tag>
              <span class="muted">{{ scope.row.lastCalibrator }}</span>
            </div>
            <span v-else class="muted">尚未校准</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="warning" link @click="openCalibrateDialog(scope.row)">
              <el-icon><CircleCheck /></el-icon>校准登记
            </el-button>
            <el-button size="small" type="primary" link @click="openEditDialog(scope.row)">
              <el-icon><EditPen /></el-icon>编辑
            </el-button>
            <el-button size="small" type="info" link @click="openHistory(scope.row)">
              <el-icon><Tickets /></el-icon>校准记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增 / 编辑工装台账弹窗 -->
    <el-dialog
      v-model="toolDialogVisible"
      :title="editingId ? '编辑工装台账' : '新增工装台账'"
      width="520px"
      @close="resetToolForm"
    >
      <el-form
        ref="toolFormRef"
        :model="toolForm"
        :rules="toolRules"
        label-width="110px"
      >
        <el-form-item label="工装编号" prop="toolCode">
          <el-input v-model="toolForm.toolCode" placeholder="如 KC-001 / SC-001 / BFB-001" maxlength="50" />
        </el-form-item>
        <el-form-item label="工装类型" prop="toolType">
          <el-radio-group v-model="toolForm.toolType">
            <el-radio-button
              v-for="opt in typeOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规格型号" prop="specModel">
          <el-input v-model="toolForm.specModel" placeholder="如 0-150mm（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item label="校准到期日" prop="calibrationDueDate">
          <el-date-picker
            v-model="toolForm.calibrationDueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择当前校准到期日"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="保管班组" prop="keeperTeam">
          <el-input v-model="toolForm.keeperTeam" placeholder="如 总装一班" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="editingId" label="是否停用">
          <el-switch
            v-model="toolDisabledBool"
            active-text="停用（不参与点检拦截）"
            inactive-text="在期"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="toolForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="toolDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingTool" @click="submitTool">保存</el-button>
      </template>
    </el-dialog>

    <!-- 校准登记弹窗 -->
    <el-dialog
      v-model="calibrateDialogVisible"
      title="工装校准登记"
      width="520px"
      @close="resetCalibrateForm"
    >
      <el-descriptions :column="1" border v-if="currentRow" size="small" class="tool-info">
        <el-descriptions-item label="工装编号">{{ currentRow.toolCode }}</el-descriptions-item>
        <el-descriptions-item label="类型 / 规格">
          {{ currentRow.toolTypeName }} · {{ currentRow.specModel || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="保管班组">{{ currentRow.keeperTeam }}</el-descriptions-item>
        <el-descriptions-item label="当前校准到期日">{{ formatDate(currentRow.calibrationDueDate) }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        class="cal-tip"
        type="info"
        :closable="false"
        show-icon
        title="校准合格：台账到期日自动同步为“下次应校日期”，工装立即移出拦截清单，同一班次可重新打卡；结论不合格：工装继续拦截点检打卡，待复校合格后放行。"
      />

      <el-form
        ref="calibrateFormRef"
        :model="calibrateForm"
        :rules="calibrateRules"
        label-width="110px"
      >
        <el-form-item label="校准日期" prop="calibrationDate">
          <el-date-picker
            v-model="calibrateForm.calibrationDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择校准日期"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="校准结论" prop="result">
          <el-radio-group v-model="calibrateForm.result">
            <el-radio value="PASS">
              <el-tag type="success" effect="plain" size="small">合格</el-tag>
            </el-radio>
            <el-radio value="FAIL">
              <el-tag type="danger" effect="plain" size="small">不合格</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="有效期至" prop="validUntil">
          <el-date-picker
            v-model="calibrateForm.validUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="本次校准有效期至"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="下次应校日期" prop="nextDueDate">
          <el-date-picker
            v-model="calibrateForm.nextDueDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="合格后台账到期日同步为该日期"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="校准人" prop="calibrator">
          <el-input v-model="calibrateForm.calibrator" placeholder="请输入校准人姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="校准备注">
          <el-input
            v-model="calibrateForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="不合格时请注明偏差/处理情况（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="calibrateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingCal" @click="submitCalibration">提交校准</el-button>
      </template>
    </el-dialog>

    <!-- 校准记录抽屉 -->
    <el-drawer v-model="historyVisible" title="工装校准记录" size="640px">
      <div v-if="historyRow" class="history-head">
        <el-tag type="primary">{{ historyRow.toolCode }}</el-tag>
        <span class="muted">{{ historyRow.toolTypeName }} · {{ historyRow.keeperTeam }}</span>
      </div>
      <el-table :data="historyList" stripe v-loading="historyLoading" size="small">
        <el-table-column label="校准日期" width="120">
          <template #default="scope">{{ formatDate(scope.row.calibrationDate) }}</template>
        </el-table-column>
        <el-table-column label="结论" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.result === 'PASS' ? 'success' : 'danger'" size="small" effect="dark">
              {{ scope.row.result === 'PASS' ? '合格' : '不合格' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="120">
          <template #default="scope">{{ formatDate(scope.row.validUntil) }}</template>
        </el-table-column>
        <el-table-column label="下次应校日" width="120">
          <template #default="scope">{{ formatDate(scope.row.nextDueDate) }}</template>
        </el-table-column>
        <el-table-column prop="calibrator" label="校准人" width="90" />
        <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.note || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  getGaugeOverview,
  createGaugeTool,
  updateGaugeTool,
  getGaugeCalibrations,
  createGaugeCalibration
} from '@/api/gauge'

const typeOptions = [
  { value: 'CALIPER', label: '卡尺' },
  { value: 'FEELER', label: '塞尺' },
  { value: 'DIAL_INDICATOR', label: '百分表' }
]

const loading = ref(false)
const query = ref({
  toolType: '',
  status: '',
  keeperTeam: '',
  keyword: ''
})
const overview = ref({
  totalCount: 0,
  blockedCount: 0,
  overdueCount: 0,
  failCount: 0,
  normalCount: 0,
  uncalibratedCount: 0,
  disabledCount: 0,
  items: [],
  blockedItems: []
})

const formatDate = (d) => (d ? dayjs(d).format('YYYY-MM-DD') : '-')
const reasonText = (r) => (r === 'OVERDUE' ? '到期未校准' : r === 'FAIL' ? '结论不合格' : '')

const rowClassName = ({ row }) => (row.blocked ? (row.blockedReason === 'OVERDUE' ? 'gauge-overdue-row' : 'gauge-fail-row') : '')

// 保管班组下拉由台账数据实时去重派生
const teamOptions = computed(() => {
  const teams = new Set()
  for (const item of overview.value.items || []) {
    if (item.keeperTeam) teams.add(item.keeperTeam)
  }
  // 筛选后可能漏掉当前选中的班组，概览接口未返回全量班组时仅作可选辅助
  return [...teams].sort()
})

const buildQuery = () => ({
  toolType: query.value.toolType || null,
  status: query.value.status || null,
  keeperTeam: query.value.keeperTeam || null,
  keyword: query.value.keyword || null
})

const loadOverview = async () => {
  loading.value = true
  try {
    // 条数、清单、拦截编号全部由同一接口同一份数据返回
    const data = await getGaugeOverview(buildQuery())
    overview.value = {
      totalCount: data.totalCount || 0,
      blockedCount: data.blockedCount || (data.blockedItems || []).length,
      overdueCount: data.overdueCount || 0,
      failCount: data.failCount || 0,
      normalCount: data.normalCount || 0,
      uncalibratedCount: data.uncalibratedCount || 0,
      disabledCount: data.disabledCount || 0,
      items: data.items || [],
      blockedItems: data.blockedItems || []
    }
  } catch (e) {
    // request interceptor already shows the error
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.value = { toolType: '', status: '', keeperTeam: '', keyword: '' }
  loadOverview()
}

// ---------------- 新增 / 编辑 ----------------

const toolDialogVisible = ref(false)
const submittingTool = ref(false)
const toolFormRef = ref(null)
const editingId = ref(null)
const toolDisabledBool = ref(false)
const toolForm = ref(defaultToolForm())

function defaultToolForm() {
  return {
    toolCode: '',
    toolType: 'CALIPER',
    specModel: '',
    calibrationDueDate: '',
    keeperTeam: '',
    remark: ''
  }
}

const toolRules = {
  toolCode: [{ required: true, message: '请填写工装编号', trigger: 'blur' }],
  toolType: [{ required: true, message: '请选择工装类型', trigger: 'change' }],
  calibrationDueDate: [{ required: true, message: '请登记校准到期日', trigger: 'change' }],
  keeperTeam: [{ required: true, message: '请填写保管班组', trigger: 'blur' }]
}

const openCreateDialog = () => {
  editingId.value = null
  toolForm.value = defaultToolForm()
  toolDisabledBool.value = false
  toolDialogVisible.value = true
}

const openEditDialog = (row) => {
  editingId.value = row.id
  toolForm.value = {
    toolCode: row.toolCode,
    toolType: row.toolType,
    specModel: row.specModel || '',
    calibrationDueDate: row.calibrationDueDate,
    keeperTeam: row.keeperTeam,
    remark: row.remark || ''
  }
  toolDisabledBool.value = row.disabled === 1
  toolDialogVisible.value = true
}

const resetToolForm = () => {
  toolFormRef.value?.resetFields()
  editingId.value = null
}

const submitTool = async () => {
  if (!toolFormRef.value) return
  await toolFormRef.value.validate(async (valid) => {
    if (!valid) return
    submittingTool.value = true
    try {
      const payload = { ...toolForm.value, disabled: toolDisabledBool.value ? 1 : 0 }
      if (editingId.value) {
        await updateGaugeTool({ ...payload, id: editingId.value })
        ElMessage.success('工装台账已更新')
      } else {
        await createGaugeTool(payload)
        ElMessage.success('工装台账已建立')
      }
      toolDialogVisible.value = false
      await loadOverview()
    } catch (e) {
      // 错误由请求拦截器统一提示，弹窗保持打开
    } finally {
      submittingTool.value = false
    }
  })
}

// ---------------- 校准登记 ----------------

const calibrateDialogVisible = ref(false)
const submittingCal = ref(false)
const calibrateFormRef = ref(null)
const currentRow = ref(null)
const calibrateForm = ref(defaultCalibrateForm())

function defaultCalibrateForm() {
  return {
    toolId: null,
    calibrationDate: dayjs().format('YYYY-MM-DD'),
    result: 'PASS',
    validUntil: '',
    nextDueDate: dayjs().add(12, 'month').format('YYYY-MM-DD'),
    calibrator: '',
    note: ''
  }
}

const calibrateRules = {
  calibrationDate: [{ required: true, message: '请选择校准日期', trigger: 'change' }],
  result: [{ required: true, message: '请选择校准结论', trigger: 'change' }],
  validUntil: [{ required: true, message: '请填写有效期至', trigger: 'change' }],
  nextDueDate: [{ required: true, message: '请填写下次应校日期', trigger: 'change' }],
  calibrator: [{ required: true, message: '请填写校准人', trigger: 'blur' }]
}

const openCalibrateDialog = (row) => {
  currentRow.value = row
  calibrateForm.value = defaultCalibrateForm()
  calibrateForm.value.toolId = row.id
  calibrateDialogVisible.value = true
}

const resetCalibrateForm = () => {
  calibrateFormRef.value?.resetFields()
  currentRow.value = null
}

const submitCalibration = async () => {
  if (!calibrateFormRef.value) return
  await calibrateFormRef.value.validate(async (valid) => {
    if (!valid) return
    submittingCal.value = true
    try {
      await createGaugeCalibration({ ...calibrateForm.value })
      ElMessage.success(
        calibrateForm.value.result === 'PASS'
          ? '校准合格已登记，工装已移出拦截清单，同班次可重新打卡'
          : '校准不合格已登记，工装继续拦截点检打卡'
      )
      calibrateDialogVisible.value = false
      await loadOverview()
    } catch (e) {
      // 错误由请求拦截器统一提示，弹窗保持打开
    } finally {
      submittingCal.value = false
    }
  })
}

// ---------------- 校准记录 ----------------

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRow = ref(null)
const historyList = ref([])

const openHistory = async (row) => {
  historyRow.value = row
  historyList.value = []
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyList.value = await getGaugeCalibrations(row.id)
  } catch (e) {
    // ignore
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.tip-bar {
  margin-bottom: 14px;
}
.blocked-bar {
  margin-bottom: 14px;
}
.blocked-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}
.blocked-tag {
  font-size: 13px;
}
.blocked-tag-reason {
  margin-left: 4px;
  font-size: 12px;
}
.count-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  margin-bottom: 14px;
  border-radius: 4px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
}
.count-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.count-num {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.count-num.blocked { color: #f56c6c; }
.count-num.overdue { color: #f56c6c; }
.count-num.fail { color: #e6a23c; }
.count-num.normal { color: #67c23a; }
.count-num.uncal { color: #909399; }
.count-num.disabled { color: #909399; }
.count-label {
  color: #606266;
  font-size: 13px;
}
.count-tip {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
}
.due-overdue {
  color: #f56c6c;
  font-weight: 600;
}
.overdue-days {
  color: #f56c6c;
  font-size: 12px;
}
.last-cal {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tool-info {
  margin-bottom: 14px;
}
.cal-tip {
  margin-bottom: 12px;
}
.history-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
</style>

<style>
.gauge-overdue-row {
  background-color: #fef0f0 !important;
}
.gauge-fail-row {
  background-color: #fdf6ec !important;
}
</style>
