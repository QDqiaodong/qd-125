<template>
  <div class="transfer-confirm">
    <div class="page-card no-print">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><CircleCheck /></el-icon>
          移交确认
        </div>
        <div class="header-actions">
          <el-button type="primary" plain @click="doExport" :loading="exporting">
            <el-icon><Download /></el-icon>
            导出结果
          </el-button>
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-select
          v-model="query.status"
          placeholder="确认状态"
          clearable
          style="width: 150px;"
          @change="handleSearch"
        >
          <el-option label="待确认" value="PENDING" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-select
          v-model="query.lineId"
          placeholder="相关产线"
          clearable
          filterable
          style="width: 200px;"
        >
          <el-option
            v-for="line in leafLines"
            :key="line.id"
            :label="line.lineName"
            :value="line.id"
          />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="登记开始日期"
          end-placeholder="登记结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 280px;"
        />
        <el-input
          v-model="query.blockCode"
          placeholder="挡块编号"
          clearable
          style="width: 170px;"
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
        <el-radio-group
          v-model="query.waitSort"
          class="wait-sort-group"
          @change="handleSearch"
        >
          <el-radio-button value="LONGEST_FIRST">等待最长优先</el-radio-button>
          <el-radio-button value="SHORTEST_FIRST">等待最短优先</el-radio-button>
        </el-radio-group>
      </div>

      <el-alert
        class="tip-bar"
        type="info"
        :closable="false"
        show-icon
        title="移交登记后进入“待确认”状态；接收方确认接收后系统才更新挡块当前产线绑定，驳回则保留原归属。待确认单按等待时长排队（默认最长优先），等待超过 24 小时的单据将醒目标记为“积压过久”。"
      />

      <el-table :data="tableData" stripe v-loading="loading" row-key="id" :row-class-name="rowClassName">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="transferNo" label="移交单号" width="175">
          <template #default="scope">
            <el-tag type="success" effect="plain">{{ scope.row.transferNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="blockCode" label="挡块编号" width="135" />
        <el-table-column label="挡块规格" min-width="190" show-overflow-tooltip>
          <template #default="scope">
            <div class="spec-cell">
              <span>{{ scope.row.adapterModel }}</span>
              <span class="spec-sub">
                厚度 {{ scope.row.thickness }}mm<template v-if="scope.row.specTemplate"> · {{ scope.row.specTemplate }}</template>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fromLineName" label="原归属产线" min-width="140" show-overflow-tooltip />
        <el-table-column prop="toLineName" label="目标产线" min-width="140" show-overflow-tooltip />
        <el-table-column prop="transferReason" label="移交原因" min-width="160" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.transferReason || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusMeta(scope.row.status).type" effect="light">
              {{ statusMeta(scope.row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等待时长" width="150" align="center">
          <template #default="scope">
            <div class="wait-cell">
              <el-tooltip
                v-if="scope.row.status === 'PENDING'"
                :content="scope.row.longWaiting
                  ? `登记于 ${scope.row.createTime}，等待已超过 24 小时，请优先处理`
                  : `登记于 ${scope.row.createTime}`"
                placement="top"
              >
                <el-tag type="danger" effect="plain" size="small">
                  <el-icon style="vertical-align: -2px;"><Timer /></el-icon>
                  {{ scope.row.waitingDuration }}
                </el-tag>
              </el-tooltip>
              <el-tooltip
                v-else
                :content="`办理于 ${formatTime(scope.row.handleTime)}，等待时长已定格`"
                placement="top"
              >
                <el-tag
                  :type="scope.row.status === 'CONFIRMED' ? 'success' : 'info'"
                  effect="plain"
                  size="small"
                >
                  {{ waitingDurationText(scope.row) }}
                </el-tag>
              </el-tooltip>
              <el-tag
                v-if="scope.row.longWaiting"
                type="danger"
                effect="dark"
                size="small"
                class="backlog-tag"
              >
                <el-icon style="vertical-align: -2px;"><WarningFilled /></el-icon>
                积压过久
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="登记时间" width="165" />
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'PENDING'"
              size="small"
              type="success"
              link
              @click="openHandleDialog(scope.row)"
            >
              <el-icon><Check /></el-icon>确认/驳回
            </el-button>
            <el-button size="small" type="primary" link @click="openDetailDrawer(scope.row)">
              <el-icon><View /></el-icon>流转记录
            </el-button>
            <el-button
              v-if="scope.row.status === 'CONFIRMED'"
              size="small"
              type="warning"
              link
              @click="openReceiptDialog(scope.row)"
            >
              <el-icon><Printer /></el-icon>回执
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 确认/驳回处理弹窗 -->
    <el-dialog
      v-model="handleVisible"
      title="移交确认处理"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetHandleForm"
    >
      <template v-if="currentTransfer">
        <el-descriptions :column="2" border size="small" class="handle-desc">
          <el-descriptions-item label="移交单号">
            <strong>{{ currentTransfer.transferNo }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ currentTransfer.createTime }}</el-descriptions-item>
          <el-descriptions-item label="挡块编号">{{ currentTransfer.blockCode }}</el-descriptions-item>
          <el-descriptions-item label="厚度规格">{{ currentTransfer.thickness }}mm</el-descriptions-item>
          <el-descriptions-item label="适配机型" :span="2">{{ currentTransfer.adapterModel }}</el-descriptions-item>
          <el-descriptions-item label="原归属产线">
            <el-tag type="danger" size="small">{{ currentTransfer.fromLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标产线">
            <el-tag type="success" size="small">{{ currentTransfer.toLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="移交人">{{ currentTransfer.transferOperator }}</el-descriptions-item>
          <el-descriptions-item label="移交日期">{{ currentTransfer.transferDate }}</el-descriptions-item>
          <el-descriptions-item label="移交原因" :span="2">
            {{ currentTransfer.transferReason || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form
          ref="handleFormRef"
          :model="handleForm"
          :rules="handleRules"
          label-width="110px"
          class="handle-form"
        >
          <el-form-item label="接收方处理人" prop="receiveOperator">
            <el-input v-model="handleForm.receiveOperator" placeholder="请输入实际接收/处理人姓名" />
          </el-form-item>
          <el-form-item label="处理说明" prop="handleNote">
            <el-input
              v-model="handleForm.handleNote"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              :placeholder="handleForm.action === 'CONFIRM'
                ? '请填写验收情况、处理说明（必填）'
                : '请填写驳回原因及处理说明（必填）'"
            />
          </el-form-item>
          <el-alert
            v-if="handleForm.action === 'CONFIRM'"
            type="success"
            :closable="false"
            show-icon
            title="确认后系统将把挡块当前产线绑定更新为目标产线，并可打印确认回执。"
          />
          <el-alert
            v-else
            type="warning"
            :closable="false"
            show-icon
            title="驳回后挡块保留原归属产线，不做绑定变更。"
          />
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitHandle('REJECT')">
          驳回
        </el-button>
        <el-button type="success" :loading="submitting" @click="submitHandle('CONFIRM')">
          <el-icon><Check /></el-icon>确认接收
        </el-button>
      </template>
    </el-dialog>

    <!-- 流转记录抽屉 -->
    <el-drawer v-model="detailVisible" title="移交流转详情" size="640px">
      <template v-if="currentTransfer">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="移交单号" :span="2">
            <strong>{{ currentTransfer.transferNo }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="挡块编号">{{ currentTransfer.blockCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMeta(currentTransfer.status).type" size="small">
              {{ statusMeta(currentTransfer.status).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="适配机型" :span="2">{{ currentTransfer.adapterModel }}</el-descriptions-item>
          <el-descriptions-item label="厚度规格">{{ currentTransfer.thickness }}mm</el-descriptions-item>
          <el-descriptions-item label="规格模板">{{ currentTransfer.specTemplate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原归属产线">
            <el-tag type="danger" size="small">{{ currentTransfer.fromLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标产线">
            <el-tag type="success" size="small">{{ currentTransfer.toLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="移交人">{{ currentTransfer.transferOperator }}</el-descriptions-item>
          <el-descriptions-item label="接收方">{{ currentTransfer.receiveOperator || '-' }}</el-descriptions-item>
          <el-descriptions-item label="移交日期">{{ currentTransfer.transferDate }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ currentTransfer.createTime }}</el-descriptions-item>
          <el-descriptions-item label="移交原因" :span="2">
            {{ currentTransfer.transferReason || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTransfer.handleTime" label="处理时间">
            {{ formatTime(currentTransfer.handleTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="等待时长">
            <el-tag
              :type="currentTransfer.status === 'PENDING' ? 'danger'
                : currentTransfer.status === 'CONFIRMED' ? 'success' : 'info'"
              size="small"
            >
              {{ waitingDurationText(currentTransfer) }}
            </el-tag>
            <el-tag
              v-if="currentTransfer.longWaiting"
              type="danger"
              effect="dark"
              size="small"
              class="backlog-tag"
            >
              <el-icon style="vertical-align: -2px;"><WarningFilled /></el-icon>
              积压过久
            </el-tag>
            <span v-if="currentTransfer.status !== 'PENDING'" class="muted-text duration-note">
              （停在办理时刻{{ formatTime(currentTransfer.handleTime) }}）
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTransfer.status === 'CONFIRMED'" label="回执打印次数">
            <el-tag :type="(currentTransfer.receiptPrintCount || 0) > 0 ? 'warning' : 'info'" size="small">
              {{ currentTransfer.receiptPrintCount || 0 }} 次
            </el-tag>
            <span v-if="currentTransfer.lastReceiptPrintTime" class="muted-text duration-note">
              最近 {{ formatTime(currentTransfer.lastReceiptPrintTime) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="移交单打印次数">
            <el-tag :type="(currentTransfer.printCount || 0) > 0 ? 'success' : 'info'" size="small">
              {{ currentTransfer.printCount || 0 }} 次
            </el-tag>
            <span v-if="currentTransfer.lastPrintTime" class="muted-text duration-note">
              最近 {{ formatTime(currentTransfer.lastPrintTime) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTransfer.handleNote" label="处理说明" :span="2">
            {{ currentTransfer.handleNote }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTransfer.remark" label="登记备注" :span="2">
            {{ currentTransfer.remark }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">
          <el-icon><List /></el-icon>
          完整流转记录
        </h4>
        <el-timeline v-loading="flowLoading">
          <el-timeline-item
            v-for="record in flowRecords"
            :key="record.id"
            :type="flowMeta(record.action).type"
            :timestamp="`${formatTime(record.createTime)} · ${record.operator || '系统'}`"
            placement="top"
          >
            <div class="flow-item">
              <el-tag :type="flowMeta(record.action).type" size="small">
                {{ flowMeta(record.action).text }}
              </el-tag>
              <span v-if="record.fromStatus || record.toStatus" class="flow-status">
                {{ statusMeta(record.fromStatus).text || '—' }}
                <el-icon><Right /></el-icon>
                {{ statusMeta(record.toStatus).text || '—' }}
              </span>
              <div v-if="record.note" class="flow-note">{{ record.note }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>

        <h4 class="section-title">
          <el-icon><Files /></el-icon>
          该挡块历史移交记录
        </h4>
        <el-table :data="blockTransfers" size="small" stripe>
          <el-table-column prop="transferNo" label="单号" width="165" />
          <el-table-column prop="fromLineName" label="原产线" show-overflow-tooltip />
          <el-table-column prop="toLineName" label="目标产线" show-overflow-tooltip />
          <el-table-column prop="transferDate" label="日期" width="110" />
          <el-table-column label="状态" width="85" align="center">
            <template #default="scope">
              <el-tag :type="statusMeta(scope.row.status).type" size="small">
                {{ statusMeta(scope.row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-footer">
          <el-button
            v-if="currentTransfer.status === 'PENDING'"
            type="success"
            @click="detailVisible = false; openHandleDialog(currentTransfer)"
          >
            <el-icon><Check /></el-icon>去处理
          </el-button>
          <el-button
            v-if="currentTransfer.status === 'CONFIRMED'"
            type="warning"
            @click="detailVisible = false; openReceiptDialog(currentTransfer)"
          >
            <el-icon><Printer /></el-icon>打印确认回执
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 确认回执打印预览 -->
    <el-dialog
      v-model="receiptVisible"
      title="移交确认回执"
      width="780px"
      :close-on-click-modal="false"
    >
      <div class="receipt-area" v-if="currentTransfer">
        <div class="receipt-header">
          <h2>缓冲挡块跨产线移交确认回执</h2>
          <div class="receipt-no">回执编号：{{ currentTransfer.transferNo }}-R{{ receiptOrdinal }}</div>
        </div>

        <table class="receipt-table">
          <tbody>
            <tr>
              <td class="label">移交单号</td>
              <td>{{ currentTransfer.transferNo }}</td>
              <td class="label">确认状态</td>
              <td><strong>已确认接收</strong></td>
            </tr>
            <tr>
              <td class="label">挡块编号</td>
              <td><strong>{{ currentTransfer.blockCode }}</strong></td>
              <td class="label">厚度规格</td>
              <td>{{ currentTransfer.thickness }}mm</td>
            </tr>
            <tr>
              <td class="label">规格模板</td>
              <td>{{ currentTransfer.specTemplate || '-' }}</td>
              <td class="label">适配输送机型</td>
              <td>{{ currentTransfer.adapterModel }}</td>
            </tr>
            <tr>
              <td class="label">原归属产线</td>
              <td>{{ currentTransfer.fromLineName }}</td>
              <td class="label">目标产线</td>
              <td>{{ currentTransfer.toLineName }}</td>
            </tr>
            <tr>
              <td class="label">移交日期</td>
              <td>{{ currentTransfer.transferDate }}</td>
              <td class="label">登记时间</td>
              <td>{{ formatTime(currentTransfer.createTime) }}</td>
            </tr>
            <tr>
              <td class="label">移交人</td>
              <td>{{ currentTransfer.transferOperator }}</td>
              <td class="label">接收方处理人</td>
              <td>{{ currentTransfer.receiveOperator || '-' }}</td>
            </tr>
            <tr>
              <td class="label">确认时间</td>
              <td>{{ formatTime(currentTransfer.handleTime) }}</td>
              <td class="label">等待时长</td>
              <td>{{ waitingDurationText(currentTransfer) }}</td>
            </tr>
            <tr>
              <td class="label">移交原因</td>
              <td colspan="3">{{ currentTransfer.transferReason || '-' }}</td>
            </tr>
            <tr>
              <td class="label">接收处理说明</td>
              <td colspan="3">{{ currentTransfer.handleNote || '-' }}</td>
            </tr>
          </tbody>
        </table>

        <div class="receipt-flow">
          <div class="flow-step">
            <span class="step-dot done"></span>
            <span>登记移交</span>
            <span class="step-time">{{ formatTime(currentTransfer.createTime) }}</span>
          </div>
          <div class="flow-link"></div>
          <div class="flow-step">
            <span class="step-dot done"></span>
            <span>接收方确认</span>
            <span class="step-time">{{ formatTime(currentTransfer.handleTime) }}</span>
          </div>
          <div class="flow-link"></div>
          <div class="flow-step">
            <span class="step-dot done"></span>
            <span>产线绑定已更新</span>
            <span class="step-time">{{ currentTransfer.toLineName }}</span>
          </div>
        </div>

        <div class="receipt-sign">
          <div class="sign-box"><span>移交人签字：</span><span class="sign-line"></span></div>
          <div class="sign-box"><span>接收人签字：</span><span class="sign-line"></span></div>
          <div class="sign-box"><span>打印日期：</span><span class="sign-line"></span></div>
        </div>
        <div class="receipt-meta">
          本回执第 {{ receiptOrdinal }} 次打印 ·
          打印时间 {{ nowText }}
        </div>
      </div>

      <template #footer>
        <el-button @click="receiptVisible = false">关闭</el-button>
        <el-button type="warning" :loading="printing" @click="doPrintReceipt">
          <el-icon><Printer /></el-icon>打印回执
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  queryTransfers,
  confirmTransfer,
  rejectTransfer,
  getTransferById,
  getTransferFlowRecords,
  getTransfersByBlockId,
  markReceiptPrinted,
  exportTransfers
} from '@/api/transfer'
import { getLeafLines } from '@/api/line'
import { waitingDurationText } from '@/utils/duration'

const loading = ref(false)
const submitting = ref(false)
const printing = ref(false)
const exporting = ref(false)
const tableData = ref([])
const total = ref(0)
const leafLines = ref([])
const dateRange = ref([])

// 等待时长排序：默认最长优先，压得越久的待确认单越靠前；状态保留在组件内，
// 打开/关闭详情抽屉不会重置，排队顺序与积压标记保持不变
const query = reactive({
  page: 1,
  size: 10,
  status: 'PENDING',
  lineId: null,
  startDate: null,
  endDate: null,
  blockCode: '',
  waitSort: 'LONGEST_FIRST'
})

const handleVisible = ref(false)
const detailVisible = ref(false)
const receiptVisible = ref(false)
const currentTransfer = ref(null)
const handleFormRef = ref(null)
const flowLoading = ref(false)
const flowRecords = ref([])
const blockTransfers = ref([])
const nowText = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
// 本次打开回执弹窗期间是否已打印：打印前预览“第 N 次”，打印后与后端计数保持一致
const receiptJustPrinted = ref(false)
const receiptOrdinal = computed(() => {
  const count = currentTransfer.value?.receiptPrintCount || 0
  return receiptJustPrinted.value ? count : count + 1
})

const handleForm = reactive({
  action: 'CONFIRM',
  receiveOperator: '',
  handleNote: ''
})

const handleRules = {
  receiveOperator: [{ required: true, message: '请输入接收方处理人', trigger: 'blur' }],
  handleNote: [{ required: true, message: '请填写处理说明', trigger: 'blur' }]
}

const statusMeta = (status) => {
  const map = {
    PENDING: { text: '待确认', type: 'warning' },
    CONFIRMED: { text: '已确认', type: 'success' },
    REJECTED: { text: '已驳回', type: 'danger' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const flowMeta = (action) => {
  const map = {
    REGISTER: { text: '移交登记', type: 'primary' },
    CONFIRM: { text: '确认接收', type: 'success' },
    REJECT: { text: '驳回', type: 'danger' },
    PRINT_RECEIPT: { text: '打印回执', type: 'warning' },
    PRINT_ORDER: { text: '打印移交单', type: 'info' }
  }
  return map[action] || { text: action, type: 'info' }
}

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-')

const handleSearch = () => {
  syncDateRange()
  query.page = 1
  loadData()
}

const syncDateRange = () => {
  if (dateRange.value && dateRange.value.length === 2) {
    query.startDate = dateRange.value[0]
    query.endDate = dateRange.value[1]
  } else {
    query.startDate = null
    query.endDate = null
  }
}

const handleReset = () => {
  dateRange.value = []
  Object.assign(query, {
    page: 1,
    size: 10,
    status: null,
    lineId: null,
    startDate: null,
    endDate: null,
    blockCode: '',
    waitSort: 'LONGEST_FIRST'
  })
  loadData()
}

// 压得太久（等待超过 24 小时）的待确认单行整行标红，标记由后端按统一阈值口径给出
const rowClassName = ({ row }) => (row.longWaiting ? 'row-long-waiting' : '')

const buildQueryPayload = () => {
  syncDateRange()
  return { ...query, blockCode: query.blockCode?.trim() || null }
}

const doExport = async () => {
  exporting.value = true
  try {
    await exportTransfers(buildQueryPayload())
    ElMessage.success('当前筛选结果已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await queryTransfers(buildQueryPayload())
    tableData.value = res.content
    total.value = res.totalElements
  } catch (e) {
    // 请求拦截器已提示
  } finally {
    loading.value = false
  }
}

const openHandleDialog = async (row) => {
  try {
    currentTransfer.value = await getTransferById(row.id)
    handleForm.action = 'CONFIRM'
    handleForm.receiveOperator = currentTransfer.value.receiveOperator || ''
    handleForm.handleNote = ''
    handleVisible.value = true
  } catch (e) {
    ElMessage.error('获取移交单详情失败')
  }
}

const resetHandleForm = () => {
  handleFormRef.value?.resetFields()
  handleForm.action = 'CONFIRM'
  handleForm.receiveOperator = ''
  handleForm.handleNote = ''
}

const submitHandle = async (action) => {
  handleForm.action = action
  if (!handleFormRef.value) return
  await handleFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await ElMessageBox.confirm(
        action === 'CONFIRM'
          ? '确认接收后挡块当前产线绑定将更新为目标产线，是否继续？'
          : '确认驳回该移交？驳回后挡块保留原归属产线。',
        action === 'CONFIRM' ? '确认接收' : '驳回移交',
        {
          type: action === 'CONFIRM' ? 'success' : 'warning',
          confirmButtonText: action === 'CONFIRM' ? '确认接收' : '确认驳回',
          cancelButtonText: '取消',
          confirmButtonClass: action === 'REJECT' ? 'el-button--danger' : ''
        }
      )
    } catch (e) {
      return
    }

    submitting.value = true
    try {
      const payload = {
        action,
        receiveOperator: handleForm.receiveOperator,
        handleNote: handleForm.handleNote
      }
      if (action === 'CONFIRM') {
        await confirmTransfer(currentTransfer.value.id, payload)
        ElMessage.success('已确认接收，产线绑定已更新')
      } else {
        await rejectTransfer(currentTransfer.value.id, payload)
        ElMessage.success('已驳回，挡块保留原归属')
      }
      handleVisible.value = false
      await loadData()
    } catch (e) {
      // 错误信息由拦截器提示
    } finally {
      submitting.value = false
    }
  })
}

const openDetailDrawer = async (row) => {
  detailVisible.value = true
  flowLoading.value = true
  flowRecords.value = []
  blockTransfers.value = []
  currentTransfer.value = row
  try {
    const [detail, records, history] = await Promise.all([
      getTransferById(row.id),
      getTransferFlowRecords(row.id),
      getTransfersByBlockId(row.blockId)
    ])
    currentTransfer.value = detail
    flowRecords.value = records
    blockTransfers.value = history
  } catch (e) {
    ElMessage.error('加载流转记录失败')
  } finally {
    flowLoading.value = false
  }
}

const openReceiptDialog = async (row) => {
  try {
    currentTransfer.value = await getTransferById(row.id)
    receiptJustPrinted.value = false
    nowText.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
    receiptVisible.value = true
  } catch (e) {
    ElMessage.error('获取回执数据失败')
  }
}

const doPrintReceipt = async () => {
  if (!currentTransfer.value) return
  printing.value = true
  try {
    const updated = await markReceiptPrinted(currentTransfer.value.id)
    currentTransfer.value = updated
    nowText.value = dayjs().format('YYYY-MM-DD HH:mm:ss')

    const receiptNo = `${updated.transferNo}-R${updated.receiptPrintCount}`
    const printWindow = window.open('', '_blank')
    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>移交确认回执 - ${updated.transferNo}</title>
        <style>
          * { margin: 0; padding: 0; box-sizing: border-box; }
          body { font-family: "SimSun", "宋体", serif; padding: 30px; color: #000; }
          .receipt-header { text-align: center; margin-bottom: 18px; }
          .receipt-header h2 { font-size: 22px; font-weight: bold; margin-bottom: 6px; }
          .receipt-no { font-size: 13px; color: #444; }
          table.receipt-table { width: 100%; border-collapse: collapse; margin: 14px 0; }
          .receipt-table td { border: 1px solid #333; padding: 8px 10px; font-size: 13px; }
          .receipt-table td.label { background: #f5f5f5; width: 110px; font-weight: bold; white-space: nowrap; }
          .receipt-flow { display: flex; align-items: center; justify-content: space-between; margin: 22px 10px; }
          .flow-step { text-align: center; font-size: 13px; }
          .step-dot { display: inline-block; width: 14px; height: 14px; border-radius: 50%; background: #22c55e; margin-bottom: 4px; }
          .flow-link { flex: 1; height: 2px; background: #22c55e; margin: 0 8px; position: relative; top: -10px; }
          .step-time { display: block; color: #555; font-size: 12px; margin-top: 2px; }
          .receipt-sign { display: flex; justify-content: space-between; margin-top: 36px; font-size: 13px; }
          .sign-line { display: inline-block; min-width: 120px; border-bottom: 1px solid #333; }
          .receipt-meta { margin-top: 18px; text-align: right; font-size: 12px; color: #666; }
          @media print { body { padding: 10px; } .no-print { display: none; } }
        </style>
      </head>
      <body>
        <div class="receipt-header">
          <h2>缓冲挡块跨产线移交确认回执</h2>
          <div class="receipt-no">回执编号：${receiptNo}</div>
        </div>
        <table class="receipt-table">
          <tr><td class="label">移交单号</td><td>${updated.transferNo}</td><td class="label">确认状态</td><td><strong>已确认接收</strong></td></tr>
          <tr><td class="label">挡块编号</td><td><strong>${updated.blockCode}</strong></td><td class="label">厚度规格</td><td>${updated.thickness}mm</td></tr>
          <tr><td class="label">规格模板</td><td>${updated.specTemplate || '-'}</td><td class="label">适配输送机型</td><td>${updated.adapterModel}</td></tr>
          <tr><td class="label">原归属产线</td><td>${updated.fromLineName}</td><td class="label">目标产线</td><td>${updated.toLineName}</td></tr>
          <tr><td class="label">移交日期</td><td>${updated.transferDate}</td><td class="label">登记时间</td><td>${formatTime(updated.createTime)}</td></tr>
          <tr><td class="label">移交人</td><td>${updated.transferOperator}</td><td class="label">接收方处理人</td><td>${updated.receiveOperator || '-'}</td></tr>
          <tr><td class="label">确认时间</td><td>${formatTime(updated.handleTime)}</td><td class="label">等待时长</td><td>${waitingDurationText(updated)}</td></tr>
          <tr><td class="label">移交原因</td><td colspan="3">${updated.transferReason || '-'}</td></tr>
          <tr><td class="label">接收处理说明</td><td colspan="3">${updated.handleNote || '-'}</td></tr>
        </table>
        <div class="receipt-flow">
          <div class="flow-step"><span class="step-dot"></span><span>登记移交</span><span class="step-time">${formatTime(updated.createTime)}</span></div>
          <div class="flow-link"></div>
          <div class="flow-step"><span class="step-dot"></span><span>接收方确认</span><span class="step-time">${formatTime(updated.handleTime)}</span></div>
          <div class="flow-link"></div>
          <div class="flow-step"><span class="step-dot"></span><span>产线绑定已更新</span><span class="step-time">${updated.toLineName}</span></div>
        </div>
        <div class="receipt-sign">
          <div><span>移交人签字：</span><span class="sign-line"></span></div>
          <div><span>接收人签字：</span><span class="sign-line"></span></div>
          <div><span>打印日期：</span><span class="sign-line"></span></div>
        </div>
        <div class="receipt-meta">本回执第 ${updated.receiptPrintCount} 次打印 · 打印时间 ${nowText.value}</div>
      </body>
      </html>
    `)
    printWindow.document.close()
    setTimeout(() => printWindow.print(), 300)

    ElMessage.success('确认回执已生成')
    await loadData()
  } catch (e) {
    ElMessage.error('回执打印失败')
  } finally {
    printing.value = false
  }
}

onMounted(async () => {
  try {
    leafLines.value = await getLeafLines()
  } catch (e) {
    console.error(e)
  }
  await loadData()
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}
.wait-sort-group {
  margin-left: auto;
}
.wait-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.backlog-tag {
  font-weight: 600;
}
/* 压得太久的待确认单行整行标红，与“积压过久”标记呼应 */
:deep(.row-long-waiting) {
  background-color: #fef0f0 !important;
}
.tip-bar {
  margin: 12px 0 16px;
}
.spec-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
}
.spec-sub {
  color: #909399;
  font-size: 12px;
}
.muted-text {
  color: #909399;
  font-size: 12px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.handle-desc {
  margin-bottom: 18px;
}
.handle-form {
  margin-top: 4px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 24px 0 16px;
  font-size: 15px;
  color: #303133;
}
.flow-item {
  line-height: 1.6;
}
.flow-status {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.flow-note {
  color: #606266;
  font-size: 13px;
  margin-top: 2px;
}
.drawer-footer {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
.receipt-header {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 2px solid #1f2937;
  margin-bottom: 18px;
}
.receipt-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 6px;
}
.receipt-no {
  color: #6b7280;
  font-size: 13px;
}
.receipt-table {
  width: 100%;
  border-collapse: collapse;
}
.receipt-table :deep(td) {
  border: 1px solid #606266;
  padding: 9px 10px;
  font-size: 13px;
}
.receipt-table :deep(td.label) {
  background: #f5f7fa;
  font-weight: bold;
  width: 110px;
  white-space: nowrap;
}
.receipt-flow {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin: 26px 8px 0;
}
.flow-step {
  text-align: center;
  font-size: 13px;
  color: #303133;
}
.step-dot {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #22c55e;
  margin: 0 auto 6px;
}
.flow-link {
  flex: 1;
  height: 2px;
  background: #22c55e;
  margin: 6px 10px 0;
}
.step-time {
  display: block;
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.receipt-sign {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  font-size: 13px;
}
.sign-line {
  display: inline-block;
  min-width: 110px;
  border-bottom: 1px solid #333;
}
.receipt-meta {
  margin-top: 16px;
  text-align: right;
  font-size: 12px;
  color: #909399;
}
</style>
