<template>
  <div class="buffer-blocks">
    <div class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><Grid /></el-icon>
          缓冲挡块档案管理
        </div>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新增挡块
        </el-button>
      </div>

      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="搜索挡块编号/机型"
          clearable
          style="width: 260px;"
          :prefix-icon="Search"
        />
        <el-select
          v-model="filterSpec"
          placeholder="选择规格模板"
          clearable
          style="width: 200px;"
        >
          <el-option
            v-for="spec in specTemplates"
            :key="spec"
            :label="spec"
            :value="spec"
          />
        </el-select>
      </div>

      <el-table :data="filteredBlocks" stripe v-loading="loading">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="blockCode" label="挡块编号" width="160">
          <template #default="scope">
            <el-tag type="primary" effect="plain">{{ scope.row.blockCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adapterModel" label="适配输送机型" show-overflow-tooltip />
        <el-table-column prop="thickness" label="厚度规格(mm)" width="140" align="center">
          <template #default="scope">
            <el-tag type="warning">{{ scope.row.thickness }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="specTemplate" label="规格模板" width="140" />
        <el-table-column label="实物图片" width="100" align="center">
          <template #default="scope">
            <div class="image-wrapper" v-if="scope.row.imageUrl">
              <el-image
                :src="scope.row.imageUrl"
                :preview-src-list="[scope.row.imageUrl]"
                fit="cover"
                style="width: 60px; height: 60px; border-radius: 4px;"
              />
            </div>
            <div v-else class="image-placeholder">
              <el-icon :size="24" color="#c0c4cc"><Picture /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="lineName" label="当前所属产线" show-overflow-tooltip />
        <el-table-column prop="createTime" label="建档时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="viewDetail(scope.row)">
              <el-icon><View /></el-icon>详情
            </el-button>
            <el-button size="small" type="warning" link @click="openEditDialog(scope.row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="挡块编号" prop="blockCode">
          <el-input v-model="form.blockCode" placeholder="请输入挡块编号" />
        </el-form-item>
        <el-form-item label="适配输送机型" prop="adapterModel">
          <el-input v-model="form.adapterModel" placeholder="请输入适配输送机型" />
        </el-form-item>
        <el-form-item label="厚度规格" prop="thickness">
          <el-input-number
            v-model="form.thickness"
            :min="1"
            :max="500"
            :precision="2"
            :step="5"
            style="width: 100%;"
          />
          <span style="color: #909399; font-size: 12px; margin-left: 8px;">mm</span>
        </el-form-item>
        <el-form-item label="规格模板" prop="specTemplate">
          <el-input v-model="form.specTemplate" placeholder="如：TEMP-A-50" />
        </el-form-item>
        <el-form-item label="图片URL" prop="imageUrl">
          <el-input v-model="form.imageUrl" placeholder="请输入实物图片URL" />
        </el-form-item>
        <el-form-item v-if="isCreate" label="初始产线" prop="lineId">
          <el-select v-model="form.lineId" placeholder="请选择初始所属产线" style="width: 100%;">
            <el-option
              v-for="line in leafLines"
              :key="line.id"
              :label="line.lineName"
              :value="line.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="挡块详情"
      width="700px"
    >
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="挡块编号">
          <el-tag type="primary">{{ currentDetail.blockCode }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="规格模板">{{ currentDetail.specTemplate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="适配输送机型" :span="2">{{ currentDetail.adapterModel }}</el-descriptions-item>
        <el-descriptions-item label="厚度规格">{{ currentDetail.thickness }} mm</el-descriptions-item>
        <el-descriptions-item label="当前产线">{{ currentDetail.lineName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建档时间">{{ currentDetail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="实物图片" :span="2">
          <div v-if="currentDetail.imageUrl" class="detail-image">
            <el-image
              :src="currentDetail.imageUrl"
              :preview-src-list="[currentDetail.imageUrl]"
              fit="contain"
              style="max-width: 300px; max-height: 200px;"
            />
          </div>
          <span v-else style="color: #909399;">暂无图片</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">绑定历史</el-divider>
      <el-table :data="bindingHistory" stripe size="small">
        <el-table-column prop="lineName" label="产线名称" />
        <el-table-column prop="bindType" label="绑定类型" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.bindType === 1" type="success">初始绑定</el-tag>
            <el-tag v-else type="warning">移交绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bindTime" label="绑定时间" width="180" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="isCurrent" label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isCurrent === 1" type="primary" size="small">当前</el-tag>
            <el-tag v-else type="info" size="small">历史</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">移交记录</el-divider>
      <el-table :data="transferHistory" stripe size="small">
        <el-table-column prop="transferNo" label="移交单号" width="180" />
        <el-table-column prop="fromLineName" label="移出产线" />
        <el-table-column prop="toLineName" label="移入产线" />
        <el-table-column prop="transferDate" label="移交日期" width="120" />
        <el-table-column prop="transferOperator" label="操作人" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  getAllBlocks,
  createBlock,
  updateBlock,
  getSpecTemplates,
  getBlockBindings
} from '@/api/block'
import { getTransfersByBlockId } from '@/api/transfer'
import { getLeafLines } from '@/api/line'

const loading = ref(false)
const allBlocks = ref([])
const specTemplates = ref([])
const leafLines = ref([])
const filterKeyword = ref('')
const filterSpec = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isCreate = ref(true)
const formRef = ref(null)
const form = ref({
  id: null,
  blockCode: '',
  adapterModel: '',
  thickness: 50,
  specTemplate: '',
  imageUrl: '',
  lineId: null
})

const rules = {
  blockCode: [{ required: true, message: '请输入挡块编号', trigger: 'blur' }],
  adapterModel: [{ required: true, message: '请输入适配输送机型', trigger: 'blur' }],
  thickness: [{ required: true, message: '请输入厚度规格', trigger: 'blur' }]
}

const detailVisible = ref(false)
const currentDetail = ref(null)
const bindingHistory = ref([])
const transferHistory = ref([])

const filteredBlocks = computed(() => {
  let result = allBlocks.value
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase()
    result = result.filter(b =>
      b.blockCode.toLowerCase().includes(kw) ||
      b.adapterModel.toLowerCase().includes(kw)
    )
  }
  if (filterSpec.value) {
    result = result.filter(b => b.specTemplate === filterSpec.value)
  }
  return result
})

const loadData = async () => {
  loading.value = true
  try {
    [allBlocks.value, specTemplates.value, leafLines.value] = await Promise.all([
      getAllBlocks(),
      getSpecTemplates(),
      getLeafLines()
    ])
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  isCreate.value = true
  dialogTitle.value = '新增挡块档案'
  form.value = {
    id: null,
    blockCode: '',
    adapterModel: '',
    thickness: 50,
    specTemplate: '',
    imageUrl: '',
    lineId: null
  }
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isCreate.value = false
  dialogTitle.value = '编辑挡块档案'
  form.value = {
    id: row.id,
    blockCode: row.blockCode,
    adapterModel: row.adapterModel,
    thickness: row.thickness,
    specTemplate: row.specTemplate,
    imageUrl: row.imageUrl,
    lineId: row.lineId
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isCreate.value) {
        await createBlock(form.value)
        ElMessage.success('新增成功')
      } else {
        await updateBlock(form.value.id, form.value)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      await loadData()
    } catch (e) {
      // error already handled
    }
  })
}

const viewDetail = async (row) => {
  currentDetail.value = row
  detailVisible.value = true
  try {
    [bindingHistory.value, transferHistory.value] = await Promise.all([
      getBlockBindings(row.id),
      getTransfersByBlockId(row.id)
    ])
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.image-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
}
.image-placeholder {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  background: #f5f7fa;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px dashed #dcdfe6;
}
.detail-image {
  display: flex;
  justify-content: center;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
}
</style>
