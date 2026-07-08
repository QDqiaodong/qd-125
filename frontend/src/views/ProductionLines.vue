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
            </div>
          </div>

          <el-table :data="currentBlocks" stripe v-loading="loading">
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
import { getAllBlocks, getBlocksByLineIds } from '@/api/block'

const loading = ref(false)
const lineTree = ref([])
const allBlocks = ref([])
const selectedLine = ref(null)

const treeProps = {
  children: 'children',
  label: 'lineName'
}

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
