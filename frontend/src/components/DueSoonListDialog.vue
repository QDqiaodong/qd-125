<template>
  <el-dialog
    :model-value="modelValue"
    title="校准临期待办清单"
    width="860px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="due-soon-tip">
      下次应校日期进入 {{ windowDays }} 天临期窗口的在用挡块，按应校日升序排列；挂起待修与已逾期的不计入本清单。
    </div>
    <el-table :data="items" stripe max-height="480">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="blockCode" label="挡块编号" width="140">
        <template #default="scope">
          <el-tag type="primary" effect="plain">{{ scope.row.blockCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="adapterModel" label="适配机型" min-width="140" show-overflow-tooltip />
      <el-table-column label="所属产线" min-width="130" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.lineName || '未绑定' }}
        </template>
      </el-table-column>
      <el-table-column prop="nextDueDate" label="下次应校日" width="120" align="center">
        <template #default="scope">
          <span class="due-date">{{ scope.row.nextDueDate }}</span>
        </template>
      </el-table-column>
      <el-table-column label="剩余天数" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.daysUntilDue <= 7 ? 'danger' : 'warning'" size="small" effect="dark">
            {{ scope.row.daysUntilDue === 0 ? '今天到期' : `剩 ${scope.row.daysUntilDue} 天` }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近一次校准结论" width="150" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.lastResult === 'PASS' ? 'success' : 'danger'" size="small">
            {{ scope.row.lastResult === 'PASS' ? '合格' : '不合格' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近校准" width="160">
        <template #default="scope">
          <div>{{ scope.row.lastCalibrationDate || '-' }}</div>
          <div class="muted">{{ scope.row.lastCalibrator || '-' }}</div>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="当前没有校准临期的挡块" :image-size="80" />
      </template>
    </el-table>
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  windowDays: { type: Number, default: 30 }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.due-soon-tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}
.due-date {
  font-weight: 600;
  color: #e6a23c;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
