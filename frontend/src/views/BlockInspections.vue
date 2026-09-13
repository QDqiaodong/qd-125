<template>
  <div class="block-inspections">
    <div class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><CircleCheck /></el-icon>
          挡块班次点检台账
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
        title="按产线筛选当前在用挡块（选车间含其下全部产线），逐块登记点检人、班次（早/中/晚班）与是否可用；最近一次点检结论实时显示在行内，刷新或重进页面后条数与列表保持一致。"
      />

      <div class="filter-bar">
        <el-tree-select
          v-model="query.lineId"
          :data="lineTreeData"
          node-key="id"
          :props="{ label: 'lineName', children: 'children' }"
          placeholder="全部产线（可按车间筛选）"
          check-strictly
          clearable
          filterable
          style="width: 260px;"
        />
        <el-select v-model="query.shiftCode" placeholder="最近点检班次" clearable style="width: 150px;">
          <el-option
            v-for="opt in shiftOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="query.result" placeholder="最近点检结论" clearable style="width: 150px;">
          <el-option label="可用" value="USABLE" />
          <el-option label="不可用" value="UNUSABLE" />
          <el-option label="从未点检" value="NEVER" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索挡块编号/机型"
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
      </div>

      <div class="count-bar">
        <div class="count-item count-total">
          <span class="count-num">{{ overview.totalCount }}</span>
          <span class="count-label">在用挡块</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num usable">{{ overview.usableCount }}</span>
          <span class="count-label">最近点检可用</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num unusable">{{ overview.unusableCount }}</span>
          <span class="count-label">最近点检不可用</span>
        </div>
        <el-divider direction="vertical" />
        <div class="count-item">
          <span class="count-num never">{{ overview.neverInspectedCount }}</span>
          <span class="count-label">从未点检</span>
        </div>
        <div class="count-tip">条数与下方列表同源统计（服务端实时派生，已全部落库）</div>
      </div>

      <el-table
        :data="overview.items"
        stripe
        v-loading="loading"
        :row-class-name="rowClassName"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="blockCode" label="挡块编号" width="150">
          <template #default="scope">
            <el-tag type="primary" effect="plain">{{ scope.row.blockCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adapterModel" label="适配输送机型" min-width="150" show-overflow-tooltip />
        <el-table-column prop="thickness" label="厚度(mm)" width="100" align="center" />
        <el-table-column prop="lineName" label="当前所属产线" min-width="150" show-overflow-tooltip />
        <el-table-column label="最近一次点检结论" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.lastResult === 'USABLE'" type="success" effect="dark">可用</el-tag>
            <el-tag v-else-if="scope.row.lastResult === 'UNUSABLE'" type="danger" effect="dark">不可用</el-tag>
            <el-tag v-else type="info">未点检</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近点检班次" width="110" align="center">
          <template #default="scope">
            <span v-if="scope.row.lastShiftCode">{{ shiftText(scope.row.lastShiftCode) }}班</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="点检人" width="100" align="center">
          <template #default="scope">
            <span v-if="scope.row.lastInspector">{{ scope.row.lastInspector }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近点检时刻" width="170" align="center">
          <template #default="scope">
            <el-tooltip
              v-if="scope.row.lastNote"
              effect="dark"
              placement="top"
            >
              <template #content>{{ scope.row.lastNote }}</template>
              <span class="with-note">{{ formatTime(scope.row.lastInspectionTime) }}</span>
            </el-tooltip>
            <span v-else-if="scope.row.lastInspectionTime">{{ formatTime(scope.row.lastInspectionTime) }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openCheckDialog(scope.row)">
              <el-icon><Pointer /></el-icon>点检打卡
            </el-button>
            <el-button size="small" type="info" link @click="openHistory(scope.row)">
              <el-icon><Tickets /></el-icon>点检记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 点检打卡弹窗 -->
    <el-dialog
      v-model="checkDialogVisible"
      title="班次点检打卡"
      width="520px"
      @close="resetCheckForm"
    >
      <el-descriptions :column="1" border v-if="currentRow" size="small" class="block-info">
        <el-descriptions-item label="挡块编号">{{ currentRow.blockCode }}</el-descriptions-item>
        <el-descriptions-item label="适配输送机型">{{ currentRow.adapterModel }}</el-descriptions-item>
        <el-descriptions-item label="当前所属产线">{{ currentRow.lineName || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRow.lastInspectionTime" label="上一次点检">
          {{ formatTime(currentRow.lastInspectionTime) }} ·
          {{ shiftText(currentRow.lastShiftCode) }}班 ·
          {{ currentRow.lastInspector }} ·
          <el-tag :type="currentRow.lastResult === 'USABLE' ? 'success' : 'danger'" size="small" effect="dark">
            {{ currentRow.lastResult === 'USABLE' ? '可用' : '不可用' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form
        ref="checkFormRef"
        :model="checkForm"
        :rules="checkRules"
        label-width="92px"
        class="check-form"
      >
        <el-form-item label="点检班次" prop="shiftCode">
          <el-radio-group v-model="checkForm.shiftCode">
            <el-radio-button
              v-for="opt in shiftOptions"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="点检人" prop="inspector">
          <el-input v-model="checkForm.inspector" placeholder="请输入点检人姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="是否可用" prop="result">
          <el-radio-group v-model="checkForm.result">
            <el-radio value="USABLE">
              <el-tag type="success" effect="plain" size="small">可用</el-tag>
            </el-radio>
            <el-radio value="UNUSABLE">
              <el-tag type="danger" effect="plain" size="small">不可用</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="点检备注">
          <el-input
            v-model="checkForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="不可用时请注明异常情况（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCheck">提交打卡</el-button>
      </template>
    </el-dialog>

    <!-- 点检记录抽屉 -->
    <el-drawer v-model="historyVisible" title="挡块点检记录" size="640px">
      <div v-if="historyBlock" class="history-head">
        <el-tag type="primary">{{ historyBlock.blockCode }}</el-tag>
        <span class="muted">{{ historyBlock.lineName || '未绑定产线' }}</span>
      </div>
      <el-table :data="historyList" stripe v-loading="historyLoading" size="small">
        <el-table-column label="打卡时刻" width="170">
          <template #default="scope">{{ formatTime(scope.row.inspectionTime) }}</template>
        </el-table-column>
        <el-table-column label="班次" width="80" align="center">
          <template #default="scope">{{ shiftText(scope.row.shiftCode) }}班</template>
        </el-table-column>
        <el-table-column prop="inspector" label="点检人" width="90" />
        <el-table-column label="结论" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.result === 'USABLE' ? 'success' : 'danger'" size="small" effect="dark">
              {{ scope.row.result === 'USABLE' ? '可用' : '不可用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.note || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getLineTree } from '@/api/line'
import { getInspectionOverview, createInspection, getBlockInspections } from '@/api/inspection'
import dayjs from 'dayjs'

const shiftOptions = [
  { value: 'MORNING', label: '早' },
  { value: 'AFTERNOON', label: '中' },
  { value: 'NIGHT', label: '晚' }
]

const loading = ref(false)
const lineTreeData = ref([])
const query = ref({
  lineId: null,
  shiftCode: '',
  result: '',
  keyword: ''
})
const overview = ref({
  totalCount: 0,
  usableCount: 0,
  unusableCount: 0,
  neverInspectedCount: 0,
  items: []
})

const checkDialogVisible = ref(false)
const submitting = ref(false)
const checkFormRef = ref(null)
const currentRow = ref(null)
const checkForm = ref(defaultForm())

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyBlock = ref(null)
const historyList = ref([])

function defaultForm() {
  return {
    blockId: null,
    shiftCode: '',
    inspector: '',
    result: 'USABLE',
    note: ''
  }
}

const checkRules = {
  shiftCode: [{ required: true, message: '请选择点检班次', trigger: 'change' }],
  inspector: [{ required: true, message: '请填写点检人', trigger: 'blur' }],
  result: [{ required: true, message: '请选择是否可用', trigger: 'change' }]
}

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-')

const shiftText = (code) => {
  const opt = shiftOptions.find(o => o.value === code)
  return opt ? opt.label : code
}

const rowClassName = ({ row }) => (row.lastResult === 'UNUSABLE' ? 'inspection-unusable-row' : '')

const buildQuery = () => ({
  lineId: query.value.lineId || null,
  shiftCode: query.value.shiftCode || null,
  result: query.value.result || null,
  keyword: query.value.keyword || null
})

const loadOverview = async () => {
  loading.value = true
  try {
    // 条数与列表由同一个接口、同一份清单返回，刷新/重进页面后必然一致
    const data = await getInspectionOverview(buildQuery())
    overview.value = {
      totalCount: data.totalCount || 0,
      usableCount: data.usableCount || 0,
      unusableCount: data.unusableCount || 0,
      neverInspectedCount: data.neverInspectedCount || 0,
      items: data.items || []
    }
  } catch (e) {
    // request interceptor already shows the error
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.value = { lineId: null, shiftCode: '', result: '', keyword: '' }
  loadOverview()
}

const openCheckDialog = (row) => {
  currentRow.value = row
  checkForm.value = defaultForm()
  checkForm.value.blockId = row.blockId
  checkDialogVisible.value = true
}

const resetCheckForm = () => {
  checkFormRef.value?.resetFields()
  currentRow.value = null
}

const submitCheck = async () => {
  if (!checkFormRef.value) return
  await checkFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await createInspection({ ...checkForm.value })
      ElMessage.success('点检打卡成功')
      checkDialogVisible.value = false
      await loadOverview()
    } catch (e) {
      // keep dialog open so the operator can correct the input
    } finally {
      submitting.value = false
    }
  })
}

const openHistory = async (row) => {
  historyBlock.value = row
  historyList.value = []
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyList.value = await getBlockInspections(row.blockId)
  } catch (e) {
    // ignore
  } finally {
    historyLoading.value = false
  }
}

onMounted(async () => {
  try {
    lineTreeData.value = await getLineTree()
  } catch (e) {
    lineTreeData.value = []
  }
  loadOverview()
})
</script>

<style scoped>
.tip-bar {
  margin-bottom: 14px;
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
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}
.count-num.usable { color: #67c23a; }
.count-num.unusable { color: #f56c6c; }
.count-num.never { color: #909399; }
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
.with-note {
  border-bottom: 1px dashed #409eff;
  cursor: help;
}
.block-info {
  margin-bottom: 16px;
}
.check-form {
  margin-top: 4px;
}
.history-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
</style>

<style>
.inspection-unusable-row {
  background-color: #fef0f0 !important;
}
</style>
