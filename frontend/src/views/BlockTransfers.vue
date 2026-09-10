<template>
  <div class="block-transfers">
    <div class="page-card no-print">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><Switch /></el-icon>
          移交台账管理
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          登记移交
        </el-button>
      </div>

      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 280px;"
        />
        <el-select
          v-model="query.fromLineId"
          placeholder="移出产线"
          clearable
          style="width: 180px;"
        >
          <el-option
            v-for="line in leafLines"
            :key="line.id"
            :label="line.lineName"
            :value="line.id"
          />
        </el-select>
        <el-select
          v-model="query.toLineId"
          placeholder="移入产线"
          clearable
          style="width: 180px;"
        >
          <el-option
            v-for="line in leafLines"
            :key="line.id"
            :label="line.lineName"
            :value="line.id"
          />
        </el-select>
        <el-input
          v-model="query.blockCode"
          placeholder="挡块编号"
          clearable
          style="width: 180px;"
        />
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>

      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="transferNo" label="移交单号" width="180">
          <template #default="scope">
            <el-tag type="success" effect="plain">{{ scope.row.transferNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="blockCode" label="挡块编号" width="140" />
        <el-table-column prop="adapterModel" label="适配机型" show-overflow-tooltip />
        <el-table-column prop="thickness" label="厚度(mm)" width="100" align="center" />
        <el-table-column prop="fromLineName" label="移出产线" show-overflow-tooltip />
        <el-table-column prop="toLineName" label="移入产线" show-overflow-tooltip />
        <el-table-column prop="transferDate" label="移交日期" width="120" />
        <el-table-column prop="transferOperator" label="移交人" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusMeta(scope.row.status).type" size="small" effect="light">
              {{ statusMeta(scope.row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等待时长" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'PENDING'" type="danger" effect="plain" size="small">
              {{ scope.row.waitingDuration }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="printCount" label="打印次数" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.printCount > 0 ? 'success' : 'info'" size="small">
              {{ scope.row.printCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="openPrintDialog(scope.row)">
              <el-icon><Printer /></el-icon>打印单据
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

    <el-dialog
      v-model="createVisible"
      title="登记跨产线移交"
      width="600px"
      @close="resetCreateForm"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="120px">
        <el-form-item label="挡块" prop="blockId">
          <el-select
            v-model="createForm.blockId"
            placeholder="请选择挡块"
            style="width: 100%;"
            filterable
          >
            <el-option
              v-for="block in availableBlocks"
              :key="block.id"
              :label="`${block.blockCode} - ${block.adapterModel} - ${block.lineName || '未绑定'}`"
              :value="block.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="移出产线" prop="fromLineId">
          <el-select
            v-model="createForm.fromLineId"
            placeholder="请选择移出产线"
            style="width: 100%;"
          >
            <el-option
              v-for="line in leafLines"
              :key="line.id"
              :label="line.lineName"
              :value="line.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="移入产线" prop="toLineId">
          <el-select
            v-model="createForm.toLineId"
            placeholder="请选择移入产线"
            style="width: 100%;"
          >
            <el-option
              v-for="line in leafLines"
              :key="line.id"
              :label="line.lineName"
              :value="line.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="移交日期" prop="transferDate">
          <el-date-picker
            v-model="createForm.transferDate"
            type="date"
            placeholder="选择移交日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="移交人" prop="transferOperator">
          <el-input v-model="createForm.transferOperator" placeholder="请输入移交人姓名" />
        </el-form-item>
        <el-form-item label="接收人" prop="receiveOperator">
          <el-input v-model="createForm.receiveOperator" placeholder="预填接收人姓名（可选，以确认时为准）" />
        </el-form-item>
        <el-form-item label="移交原因" prop="transferReason">
          <el-input
            v-model="createForm.transferReason"
            type="textarea"
            :rows="2"
            placeholder="请输入移交原因"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateForm">确定登记</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="printVisible"
      title="移交单据预览"
      width="800px"
      :close-on-click-modal="false"
    >
      <div class="print-area" ref="printAreaRef">
        <div class="print-header">
          <h2>输送设备缓冲挡块跨产线移交单</h2>
          <div class="print-no">单据编号：{{ currentPrint?.transferNo }}</div>
        </div>

        <el-descriptions :column="2" border class="print-desc">
          <el-descriptions-item label="挡块编号">
            <strong>{{ currentPrint?.blockCode }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="规格模板">
            {{ currentPrint?.thickness }}mm
          </el-descriptions-item>
          <el-descriptions-item label="适配输送机型" :span="2">
            {{ currentPrint?.adapterModel }}
          </el-descriptions-item>
          <el-descriptions-item label="移出产线" :span="2">
            <el-tag type="danger">{{ currentPrint?.fromLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="移入产线" :span="2">
            <el-tag type="success">{{ currentPrint?.toLineName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="移交日期">
            {{ currentPrint?.transferDate }}
          </el-descriptions-item>
          <el-descriptions-item label="确认状态">
            <el-tag :type="statusMeta(currentPrint?.status).type" size="small">
              {{ statusMeta(currentPrint?.status).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="移交人">
            {{ currentPrint?.transferOperator }}
          </el-descriptions-item>
          <el-descriptions-item label="接收人">
            {{ currentPrint?.receiveOperator || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="移交原因" :span="2">
            {{ currentPrint?.transferReason || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentPrint?.handleNote" label="处理说明" :span="2">
            {{ currentPrint.handleNote }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ currentPrint?.remark || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="打印次数">
            第 {{ (currentPrint?.printCount || 0) + 1 }} 次
          </el-descriptions-item>
        </el-descriptions>

        <div class="print-footer">
          <div class="sign-box">
            <span>移交人签字：</span>
            <span class="sign-line"></span>
          </div>
          <div class="sign-box">
            <span>接收人签字：</span>
            <span class="sign-line"></span>
          </div>
          <div class="sign-box">
            <span>日期：</span>
            <span class="sign-line"></span>
          </div>
        </div>

        <div class="print-stamp">
          <div class="stamp-box">
            <span>移出产线确认章</span>
          </div>
          <div class="stamp-box">
            <span>移入产线确认章</span>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="printVisible = false">关闭</el-button>
        <el-button type="primary" @click="doPrint">
          <el-icon><Printer /></el-icon>
          打印单据
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Printer, Plus } from '@element-plus/icons-vue'
import {
  queryTransfers,
  createTransfer,
  markTransferPrinted,
  getTransferById
} from '@/api/transfer'
import { getLeafLines, getAllLines } from '@/api/line'
import { getAllBlocks } from '@/api/block'
import dayjs from 'dayjs'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref([])
const leafLines = ref([])
const availableBlocks = ref([])

const query = reactive({
  page: 1,
  size: 10,
  startDate: null,
  endDate: null,
  fromLineId: null,
  toLineId: null,
  blockCode: ''
})

const createVisible = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  blockId: null,
  fromLineId: null,
  toLineId: null,
  transferDate: dayjs().format('YYYY-MM-DD'),
  transferReason: '',
  transferOperator: '',
  receiveOperator: '',
  remark: ''
})

const createRules = {
  blockId: [{ required: true, message: '请选择挡块', trigger: 'change' }],
  fromLineId: [{ required: true, message: '请选择移出产线', trigger: 'change' }],
  toLineId: [{ required: true, message: '请选择移入产线', trigger: 'change' }],
  transferDate: [{ required: true, message: '请选择移交日期', trigger: 'change' }],
  transferOperator: [{ required: true, message: '请输入移交人', trigger: 'blur' }]
}

const printVisible = ref(false)
const currentPrint = ref(null)
const printAreaRef = ref(null)

const handleSearch = () => {
  if (dateRange.value && dateRange.value.length === 2) {
    query.startDate = dateRange.value[0]
    query.endDate = dateRange.value[1]
  } else {
    query.startDate = null
    query.endDate = null
  }
  query.page = 1
  loadData()
}

const handleReset = () => {
  dateRange.value = []
  query.startDate = null
  query.endDate = null
  query.fromLineId = null
  query.toLineId = null
  query.blockCode = ''
  query.page = 1
  loadData()
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await queryTransfers({ ...query, blockCode: query.blockCode?.trim() || null })
    tableData.value = res.content
    total.value = res.totalElements
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const statusMeta = (status) => {
  const map = {
    PENDING: { text: '待确认', type: 'warning' },
    CONFIRMED: { text: '已确认', type: 'success' },
    REJECTED: { text: '已驳回', type: 'danger' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const openCreateDialog = async () => {
  createVisible.value = true
}

const resetCreateForm = () => {
  createFormRef.value?.resetFields()
  Object.assign(createForm, {
    blockId: null,
    fromLineId: null,
    toLineId: null,
    transferDate: dayjs().format('YYYY-MM-DD'),
    transferReason: '',
    transferOperator: '',
    receiveOperator: '',
    remark: ''
  })
}

const submitCreateForm = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await createTransfer(createForm)
      ElMessage.success('移交登记成功，等待接收方确认')
      createVisible.value = false
      await loadData()
    } catch (e) {
      // error handled
    }
  })
}

const openPrintDialog = async (row) => {
  try {
    currentPrint.value = await getTransferById(row.id)
    printVisible.value = true
  } catch (e) {
    ElMessage.error('获取单据详情失败')
  }
}

const doPrint = async () => {
  if (!currentPrint.value) return
  try {
    await markTransferPrinted(currentPrint.value.id)
    await nextTick()
    
    const printContent = printAreaRef.value?.innerHTML || ''
    const printWindow = window.open('', '_blank')
    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <title>移交单据 - ${currentPrint.value.transferNo}</title>
        <style>
          * { margin: 0; padding: 0; box-sizing: border-box; }
          body { font-family: "SimSun", "宋体", serif; padding: 30px; color: #000; }
          .print-header { text-align: center; margin-bottom: 20px; }
          .print-header h2 { font-size: 24px; font-weight: bold; margin-bottom: 8px; }
          .print-no { font-size: 14px; color: #666; }
          table { width: 100%; border-collapse: collapse; margin: 16px 0; }
          td, th { border: 1px solid #333; padding: 10px 12px; font-size: 14px; }
          .label { background: #f5f5f5; width: 120px; font-weight: bold; }
          .sign-box { display: flex; align-items: center; margin: 20px 0; gap: 12px; }
          .sign-line { display: inline-block; min-width: 150px; border-bottom: 1px solid #333; }
          .print-stamp { display: flex; justify-content: space-around; margin-top: 40px; }
          .stamp-box { width: 120px; height: 120px; border: 2px dashed #999; display: flex; align-items: center; justify-content: center; text-align: center; font-size: 12px; color: #666; border-radius: 50%; }
          @media print {
            body { padding: 10px; }
          }
        </style>
      </head>
      <body>
        <div class="print-header">
          <h2>输送设备缓冲挡块跨产线移交单</h2>
          <div class="print-no">单据编号：${currentPrint.value.transferNo}</div>
        </div>
        <table>
          <tr><td class="label">挡块编号</td><td><strong>${currentPrint.value.blockCode}</strong></td><td class="label">规格厚度</td><td>${currentPrint.value.thickness}mm</td></tr>
          <tr><td class="label">适配输送机型</td><td colspan="3">${currentPrint.value.adapterModel}</td></tr>
          <tr><td class="label">移出产线</td><td colspan="3">${currentPrint.value.fromLineName}</td></tr>
          <tr><td class="label">移入产线</td><td colspan="3">${currentPrint.value.toLineName}</td></tr>
          <tr><td class="label">移交日期</td><td>${currentPrint.value.transferDate}</td><td class="label">打印次数</td><td>第 ${(currentPrint.value.printCount || 0)} 次</td></tr>
          <tr><td class="label">移交人</td><td>${currentPrint.value.transferOperator}</td><td class="label">接收人</td><td>${currentPrint.value.receiveOperator || '-'}</td></tr>
          <tr><td class="label">移交原因</td><td colspan="3">${currentPrint.value.transferReason || '-'}</td></tr>
          <tr><td class="label">备注</td><td colspan="3">${currentPrint.value.remark || '-'}</td></tr>
        </table>
        <div style="display: flex; justify-content: space-between; margin-top: 30px;">
          <div class="sign-box"><span>移交人签字：</span><span class="sign-line"></span></div>
          <div class="sign-box"><span>接收人签字：</span><span class="sign-line"></span></div>
          <div class="sign-box"><span>日期：</span><span class="sign-line"></span></div>
        </div>
        <div class="print-stamp">
          <div class="stamp-box">移出产线确认章</div>
          <div class="stamp-box">移入产线确认章</div>
        </div>
      </body>
      </html>
    `)
    printWindow.document.close()
    setTimeout(() => {
      printWindow.print()
    }, 300)
    
    ElMessage.success('打印单据已生成')
    printVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('打印失败')
  }
}

onMounted(async () => {
  try {
    [leafLines.value, availableBlocks.value] = await Promise.all([
      getLeafLines(),
      getAllBlocks()
    ])
  } catch (e) {
    console.error(e)
  }
  await loadData()
})
</script>

<style scoped>
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.print-header {
  text-align: center;
  padding-bottom: 20px;
  border-bottom: 2px solid #1f2937;
  margin-bottom: 20px;
}
.print-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}
.print-no {
  color: #6b7280;
  font-size: 14px;
}
.print-desc {
  margin-bottom: 30px;
}
.print-footer {
  display: flex;
  justify-content: space-between;
  padding: 20px 0;
  border-top: 1px dashed #dcdfe6;
  margin-top: 20px;
}
.sign-box {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sign-line {
  display: inline-block;
  min-width: 120px;
  border-bottom: 1px solid #333;
}
.print-stamp {
  display: flex;
  justify-content: space-around;
  margin-top: 40px;
  padding-top: 20px;
}
.stamp-box {
  width: 120px;
  height: 120px;
  border: 2px dashed #c0c4cc;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  font-size: 12px;
  color: #909399;
}
</style>
