<template>
  <div class="production-lines">
    <el-row :gutter="20">
      <el-col :span="8">
        <div class="page-card" style="height: calc(100vh - 160px); overflow-y: auto;">
          <div class="page-header">
            <div class="page-title">
              <el-icon :size="20" color="#409EFF"><OfficeBuilding /></el-icon>
              产线树形分组
            </div>
          </div>
          <el-tree
            ref="lineTree"
            :data="lineTree"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span class="node-label">
                  <el-icon v-if="!data.children || data.children.length === 0" color="#67c23a">
                    <Connection />
                  </el-icon>
                  <el-icon v-else color="#e6a23c">
                    <Folder />
                  </el-icon>
                  <span style="margin-left: 6px;">{{ node.label }}</span>
                  <el-tag v-if="data.children && data.children.length > 0" size="small" type="info" style="margin-left: 8px;">
                    {{ data.children.length }}
                  </el-tag>
                </span>
                <span class="node-badges" v-if="statsOf(data.id)">
                  <el-tag size="small" type="success" effect="plain">在用 {{ statsOf(data.id).inService }}</el-tag>
                  <el-tag size="small" type="danger" effect="plain">挂起 {{ statsOf(data.id).suspended }}</el-tag>
                  <el-tag v-if="statsOf(data.id).pending > 0" size="small" type="warning" effect="plain">
                    待确认 {{ statsOf(data.id).pending }}
                  </el-tag>
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="page-card">
          <div class="page-header">
            <div class="page-title">
              <el-icon :size="20" color="#409EFF"><Grid /></el-icon>
              {{ selectedLine ? selectedLine.lineName + ' - 挡块列表' : '全部挡块列表' }}
              <el-tag v-if="selectedLine" type="success" style="margin-left: 12px;">
                共 {{ currentBlocks.length }} 个挡块
              </el-tag>
              <template v-if="selectedLine">
                <el-tag type="success" effect="plain" style="margin-left: 8px;">
                  在用 {{ currentStats.inService }}
                </el-tag>
                <el-tag type="danger" effect="plain" style="margin-left: 8px;">
                  挂起 {{ currentStats.suspended }}
                </el-tag>
                <el-tag v-if="currentStats.pending > 0" type="warning" effect="plain" style="margin-left: 8px;">
                  待确认移交 {{ currentStats.pending }}
                </el-tag>
              </template>
            </div>
          </div>

          <el-table :data="currentBlocks" stripe v-loading="loading" :row-class-name="rowClassName">
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
            <el-table-column
              prop="serviceStatus"
              label="在用状态"
              width="110"
              align="center"
              :filters="serviceStatusFilters"
              :filter-method="filterServiceStatus"
            >
              <template #default="scope">
                <el-tooltip
                  v-if="scope.row.serviceStatus === 'SUSPENDED'"
                  :content="scope.row.suspendReason || '校准不合格，挂起待修'"
                  placement="top"
                >
                  <el-tag type="danger">挂起</el-tag>
                </el-tooltip>
                <el-tag v-else type="success">在用</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="移交状态"
              width="130"
              align="center"
              :filters="transferFilters"
              :filter-method="filterTransfer"
            >
              <template #default="scope">
                <el-tooltip
                  v-if="scope.row.pendingTransfer"
                  :content="'移交单 ' + (scope.row.pendingTransferNo || '') + ' 正待接收方确认'"
                  placement="top"
                >
                  <el-tag type="warning" effect="plain">移交待确认</el-tag>
                </el-tooltip>
                <span v-else class="no-transfer">—</span>
              </template>
            </el-table-column>
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
            <el-table-column prop="lineName" label="当前产线" show-overflow-tooltip />
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getLineTree } from '@/api/line'
import { getAllBlocks } from '@/api/block'

const loading = ref(false)
const lineTree = ref([])
const allBlocks = ref([])
const selectedLine = ref(null)

const treeProps = {
  children: 'children',
  label: 'lineName'
}

const serviceStatusFilters = [
  { text: '在用', value: 'IN_SERVICE' },
  { text: '挂起', value: 'SUSPENDED' }
]

const transferFilters = [
  { text: '移交待确认', value: 'PENDING' }
]

const filterServiceStatus = (value, row) => (row.serviceStatus || 'IN_SERVICE') === value
const filterTransfer = (value, row) => value !== 'PENDING' || row.pendingTransfer

const getAllChildIds = (node) => {
  const ids = [node.id]
  if (node.children && node.children.length > 0) {
    node.children.forEach(child => {
      ids.push(...getAllChildIds(child))
    })
  }
  return ids
}

const currentBlocks = computed(() => {
  if (!selectedLine.value) {
    return allBlocks.value
  }
  const lineIds = getAllChildIds(selectedLine.value)
  return allBlocks.value.filter(b => lineIds.includes(b.lineId))
})

const countOf = (blocks) => {
  const stats = { inService: 0, suspended: 0, pending: 0 }
  blocks.forEach(b => {
    if (b.serviceStatus === 'SUSPENDED') {
      stats.suspended++
    } else {
      stats.inService++
    }
    if (b.pendingTransfer) {
      stats.pending++
    }
  })
  return stats
}

const currentStats = computed(() => countOf(currentBlocks.value))

// 叶子产线直接统计自身挡块；车间节点递归汇总全部下级叶子，保证角标与列表同源一致
const statsByNodeId = computed(() => {
  const perLine = {}
  allBlocks.value.forEach(b => {
    if (b.lineId == null) return
    const s = perLine[b.lineId] || (perLine[b.lineId] = { inService: 0, suspended: 0, pending: 0 })
    if (b.serviceStatus === 'SUSPENDED') {
      s.suspended++
    } else {
      s.inService++
    }
    if (b.pendingTransfer) {
      s.pending++
    }
  })
  const result = {}
  const walk = (node) => {
    const agg = { inService: 0, suspended: 0, pending: 0 }
    if (node.children && node.children.length > 0) {
      node.children.forEach(child => {
        const childStats = walk(child)
        agg.inService += childStats.inService
        agg.suspended += childStats.suspended
        agg.pending += childStats.pending
      })
    } else {
      const s = perLine[node.id]
      if (s) {
        agg.inService = s.inService
        agg.suspended = s.suspended
        agg.pending = s.pending
      }
    }
    result[node.id] = agg
    return agg
  }
  lineTree.value.forEach(walk)
  return result
})

const statsOf = (id) => statsByNodeId.value[id]

const rowClassName = ({ row }) => {
  if (row.serviceStatus === 'SUSPENDED') return 'suspended-row'
  if (row.pendingTransfer) return 'pending-row'
  return ''
}

const handleNodeClick = (data) => {
  selectedLine.value = data
}

const loadData = async () => {
  loading.value = true
  try {
    [lineTree.value, allBlocks.value] = await Promise.all([
      getLineTree(),
      getAllBlocks()
    ])
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}
.node-label {
  display: flex;
  align-items: center;
}
.node-badges {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}
.no-transfer {
  color: #c0c4cc;
}
:deep(.suspended-row) {
  background-color: #fef0f0;
}
:deep(.pending-row) {
  background-color: #fdf6ec;
}
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
</style>
