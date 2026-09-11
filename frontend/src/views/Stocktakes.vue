<template>
  <div class="stocktakes">
    <!-- ==================== 批次列表 ==================== -->
    <div v-if="!currentBatchId" class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><DocumentChecked /></el-icon>
          挡块盘点差异闭环
        </div>
        <div class="header-actions">
          <el-button @click="loadBatches">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新建盘点批次
          </el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-select
          v-model="batchQuery.lineId"
          placeholder="盘点产线"
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
        <el-select
          v-model="batchQuery.status"
          placeholder="批次状态"
          clearable
          style="width: 150px;"
        >
          <el-option label="盘点中" value="COUNTING" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
        <el-date-picker
          v-model="batchDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="盘点开始日期"
          end-placeholder="盘点结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          style="width: 280px;"
        />
        <el-input
          v-model="batchQuery.batchNo"
          placeholder="批次号"
          clearable
          style="width: 180px;"
          @keyup.enter="searchBatches"
        />
        <el-button type="primary" @click="searchBatches">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="resetBatchQuery">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
      </div>

      <el-alert
        class="tip-bar"
        type="info"
        :closable="false"
        show-icon
        title="按产线和盘点日期创建批次后，系统自动快照该产线当前绑定挡块作为应盘清单；逐项录入实物状态与现场产线，自动标记缺失、错线、重复盘点、盘盈、损坏报废等差异，差异全部确认/忽略后方可结束盘点。"
      />

      <el-table :data="batchData" stripe v-loading="batchLoading" row-key="id">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="batchNo" label="盘点批次号" width="175">
          <template #default="scope">
            <el-tag type="primary" effect="plain">{{ scope.row.batchNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lineName" label="盘点产线" min-width="150" show-overflow-tooltip />
        <el-table-column prop="stocktakeDate" label="盘点日期" width="115" align="center" />
        <el-table-column prop="operator" label="负责人" width="90" />
        <el-table-column label="盘点进度" width="150" align="center">
          <template #default="scope">
            <el-progress
              :percentage="progressPercent(scope.row)"
              :stroke-width="14"
              :text-inside="true"
            />
          </template>
        </el-table-column>
        <el-table-column label="应盘/已盘" width="105" align="center">
          <template #default="scope">
            <span>{{ scope.row.totalCount }} / {{ scope.row.countedCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异/待处理" width="110" align="center">
          <template #default="scope">
            <el-tag type="danger" size="small" effect="plain">{{ scope.row.discrepancyCount }}</el-tag>
            <span class="count-sep">/</span>
            <el-tag :type="scope.row.pendingCount > 0 ? 'danger' : 'success'" size="small">
              {{ scope.row.pendingCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="scope">
            <el-tag :type="batchStatusMeta(scope.row.status).type" size="small">
              {{ batchStatusMeta(scope.row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="165" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="enterBatch(scope.row.id)">
              <el-icon><View /></el-icon>进入盘点
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="batchQuery.page"
          v-model:page-size="batchQuery.size"
          :page-sizes="[10, 20, 50]"
          :total="batchTotal"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadBatches"
          @current-change="loadBatches"
        />
      </div>
    </div>

    <!-- ==================== 批次详情 / 逐项录入 ==================== -->
    <div v-else class="page-card">
      <div class="page-header detail-header">
        <div class="header-back">
          <el-button @click="backToList" :icon="ArrowLeft">返回批次列表</el-button>
        </div>
        <div class="page-title" v-if="batch">
          <el-icon :size="20" color="#409EFF"><DocumentChecked /></el-icon>
          <el-tag type="primary" effect="plain">{{ batch.batchNo }}</el-tag>
          <el-tag :type="batchStatusMeta(batch.status).type" size="small">
            {{ batchStatusMeta(batch.status).text }}
          </el-tag>
          <span class="title-line">{{ batch.lineName }}</span>
          <span class="title-sub">盘点日期 {{ batch.stocktakeDate }} · 负责人 {{ batch.operator }}</span>
        </div>
        <div class="header-actions">
          <el-button @click="loadDetail">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <el-button @click="doExport" :loading="exporting">
            <el-icon><Download /></el-icon>导出结果
          </el-button>
          <el-button
            v-if="batch && batch.status === 'COUNTING'"
            type="warning"
            @click="doFinish"
          >
            <el-icon><CircleCheck /></el-icon>结束盘点
          </el-button>
          <el-button
            v-if="batch && batch.status === 'COMPLETED'"
            type="success"
            plain
            @click="doReopen"
          >
            <el-icon><RefreshRight /></el-icon>重新打开
          </el-button>
        </div>
      </div>

      <template v-if="batch">
        <el-row :gutter="16" class="stat-row">
          <el-col :span="6">
            <div class="mini-stat"><span class="mini-label">应盘数量</span>
              <span class="mini-value">{{ batch.totalCount }}</span></div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat blue"><span class="mini-label">已盘数量</span>
              <span class="mini-value">{{ batch.countedCount }}</span></div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat orange"><span class="mini-label">差异总数</span>
              <span class="mini-value">{{ batch.discrepancyCount }}</span></div>
          </el-col>
          <el-col :span="6">
            <div class="mini-stat" :class="batch.pendingCount > 0 ? 'red' : 'green'">
              <span class="mini-label">待处理差异</span>
              <span class="mini-value">{{ batch.pendingCount }}</span>
            </div>
          </el-col>
        </el-row>

        <el-alert
          v-if="batch.pendingCount > 0"
          class="tip-bar"
          type="error"
          :closable="false"
          show-icon
          :title="`还有 ${batch.pendingCount} 条差异待处理，全部确认或忽略后才能结束盘点`"
        />

        <!-- 逐项录入 -->
        <div class="count-panel" v-if="batch.status === 'COUNTING'">
          <div class="panel-title">
            <el-icon :size="16" color="#409EFF"><EditPen /></el-icon>
            逐项录入实物（扫码/输入挡块编号）
          </div>
          <el-form :model="countForm" inline class="count-form">
            <el-form-item>
              <el-select
                v-model="countForm.blockCode"
                placeholder="挡块编号"
                filterable
                allow-create
                default-first-option
                style="width: 200px;"
                @keyup.enter="submitCount"
              >
                <el-option
                  v-for="b in allBlocks"
                  :key="b.id"
                  :label="b.blockCode"
                  :value="b.blockCode"
                >
                  <span>{{ b.blockCode }}</span>
                  <span class="option-sub">{{ b.lineName || '未绑定' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select v-model="countForm.physicalStatus" placeholder="实物状态" style="width: 130px;">
                <el-option label="正常" value="NORMAL" />
                <el-option label="损坏" value="DAMAGED" />
                <el-option label="报废" value="SCRAPPED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select
                v-model="countForm.siteLineId"
                placeholder="现场产线"
                filterable
                style="width: 190px;"
              >
                <el-option
                  v-for="line in leafLines"
                  :key="line.id"
                  :label="line.lineName"
                  :value="line.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="countForm.operator"
                placeholder="盘点人"
                style="width: 120px;"
              />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="countForm.remark"
                placeholder="现场备注（可选）"
                style="width: 200px;"
                @keyup.enter="submitCount"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="counting" @click="submitCount">
                <el-icon><Check /></el-icon>录入
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 差异/明细筛选 -->
        <div class="filter-bar item-filter">
          <el-select
            v-model="itemQuery.discrepancyType"
            placeholder="差异类型"
            clearable
            style="width: 140px;"
            @change="searchItems"
          >
            <el-option label="缺失" value="MISSING" />
            <el-option label="错线" value="WRONG_LINE" />
            <el-option label="重复盘点" value="DUPLICATE" />
            <el-option label="盘盈" value="EXTRA" />
            <el-option label="损坏" value="DAMAGED" />
            <el-option label="报废" value="SCRAPPED" />
            <el-option label="无差异" value="NONE" />
          </el-select>
          <el-select
            v-model="itemQuery.status"
            placeholder="处理状态"
            clearable
            style="width: 130px;"
            @change="searchItems"
          >
            <el-option label="待盘" value="WAITING" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已忽略" value="IGNORED" />
            <el-option label="无差异" value="NONE" />
          </el-select>
          <el-input
            v-model="itemQuery.blockCode"
            placeholder="挡块编号"
            clearable
            style="width: 170px;"
            @keyup.enter="searchItems"
          />
          <el-button type="primary" @click="searchItems">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="resetItemQuery">
            <el-icon><RefreshLeft /></el-icon>重置
          </el-button>
        </div>

        <el-table :data="itemData" stripe v-loading="itemLoading" row-key="id" :row-class-name="itemRowClass">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="blockCode" label="挡块编号" width="140">
            <template #default="scope">
              <el-tag :type="scope.row.isExtra === 1 ? 'warning' : 'primary'" effect="plain" size="small">
                {{ scope.row.blockCode || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="挡块规格" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <div class="spec-cell">
                <span>{{ scope.row.adapterModel || '档案不存在' }}</span>
                <span class="spec-sub">
                  厚度 {{ scope.row.thickness ?? '-' }}mm<template v-if="scope.row.specTemplate"> · {{ scope.row.specTemplate }}</template>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="75" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isExtra === 1 ? 'warning' : 'info'" size="small" effect="plain">
                {{ scope.row.isExtra === 1 ? '盘盈' : '应盘' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expectedLineName" label="应盘产线" min-width="130" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.expectedLineName || '-' }}</template>
          </el-table-column>
          <el-table-column label="当前绑定产线" min-width="130" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="!scope.row.boundLineName" class="muted-text">未绑定（账外）</span>
              <el-tag v-else :type="boundLineTagType(scope.row)" size="small" effect="plain">
                {{ scope.row.boundLineName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="现场产线" min-width="130" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.siteLineName || '-' }}</template>
          </el-table-column>
          <el-table-column label="实物状态" width="90" align="center">
            <template #default="scope">
              <el-tag :type="physicalMeta(scope.row.physicalStatus, scope.row.effectiveStatus === 'WAITING').type" size="small">
                {{ physicalMeta(scope.row.physicalStatus, scope.row.effectiveStatus === 'WAITING').text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重复" width="65" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.repeatCount > 1" type="danger" size="small">{{ scope.row.repeatCount }}</el-tag>
              <span v-else class="muted-text">{{ scope.row.repeatCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="差异类型" width="100" align="center">
            <template #default="scope">
              <el-tag
                v-if="scope.row.effectiveStatus !== 'WAITING' && scope.row.discrepancyType !== 'NONE'"
                :type="diffTypeMeta(scope.row.discrepancyType).type"
                size="small"
              >
                {{ diffTypeMeta(scope.row.discrepancyType).text }}
              </el-tag>
              <el-tag v-else type="info" size="small" effect="plain">
                {{ scope.row.effectiveStatus === 'WAITING' ? '待盘' : '无差异' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理状态" width="90" align="center">
            <template #default="scope">
              <el-tag :type="statusMeta(scope.row.effectiveStatus).type" size="small">
                {{ statusMeta(scope.row.effectiveStatus).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="countOperator" label="盘点人" width="85">
            <template #default="scope">{{ scope.row.countOperator || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right" align="center">
            <template #default="scope">
              <el-button size="small" type="primary" link @click="openTrace(scope.row)">
                <el-icon><View /></el-icon>追溯
              </el-button>
              <el-button
                v-if="canHandle(scope.row) && batch.status === 'COUNTING'"
                size="small"
                type="success"
                link
                @click="openHandleDialog(scope.row)"
              >
                <el-icon><Check /></el-icon>差异处理
              </el-button>
              <el-button
                v-if="scope.row.isExtra === 1 && batch.status === 'COUNTING'"
                size="small"
                type="danger"
                link
                @click="removeExtra(scope.row)"
              >
                <el-icon><Delete /></el-icon>删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="itemQuery.page"
            v-model:page-size="itemQuery.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="itemTotal"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="loadItems"
            @current-change="loadItems"
          />
        </div>
      </template>
    </div>

    <!-- 新建批次弹窗 -->
    <el-dialog v-model="createVisible" title="新建盘点批次" width="520px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="盘点产线" prop="lineId">
          <el-select
            v-model="createForm.lineId"
            placeholder="请选择具体产线（车间节点不可盘点）"
            filterable
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
        <el-form-item label="盘点日期" prop="stocktakeDate">
          <el-date-picker
            v-model="createForm.stocktakeDate"
            type="date"
            placeholder="选择盘点日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="盘点负责人" prop="operator">
          <el-input v-model="createForm.operator" placeholder="请输入负责人姓名" />
        </el-form-item>
        <el-form-item label="批次备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并进入</el-button>
      </template>
    </el-dialog>

    <!-- 差异处理弹窗 -->
    <el-dialog v-model="handleVisible" title="盘点差异处理" width="560px" :close-on-click-modal="false">
      <template v-if="currentItem">
        <el-descriptions :column="2" border size="small" class="handle-desc">
          <el-descriptions-item label="挡块编号">{{ currentItem.blockCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="差异类型">
            <el-tag :type="diffTypeMeta(currentItem.discrepancyType).type" size="small">
              {{ diffTypeMeta(currentItem.discrepancyType).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="应盘产线">{{ currentItem.expectedLineName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前绑定">{{ currentItem.boundLineName || '未绑定（账外）' }}</el-descriptions-item>
          <el-descriptions-item label="现场产线">{{ currentItem.siteLineName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实物状态">
            {{ physicalMeta(currentItem.physicalStatus, currentItem.effectiveStatus === 'WAITING').text }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentItem.transferHint" label="移交追溯" :span="2">
            <span class="hint-text">{{ currentItem.transferHint }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentItem.countRemark" label="现场备注" :span="2">
            {{ currentItem.countRemark }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="90px" class="handle-form">
          <el-form-item label="处理人" prop="operator">
            <el-input v-model="handleForm.operator" placeholder="请输入差异处理人" />
          </el-form-item>
          <el-form-item label="处理说明" prop="handleNote">
            <el-input
              v-model="handleForm.handleNote"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请填写差异核实情况与处理结论（必填）"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="info" plain :loading="handling" @click="submitHandle('IGNORE')">忽略差异</el-button>
        <el-button type="success" :loading="handling" @click="submitHandle('CONFIRM')">
          <el-icon><Check /></el-icon>确认差异
        </el-button>
      </template>
    </el-dialog>

    <!-- 差异追溯抽屉 -->
    <el-drawer v-model="traceVisible" title="盘点差异详情追溯" size="720px">
      <template v-if="trace">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="挡块编号" :span="2">
            <strong>{{ trace.item.blockCode || '—' }}</strong>
            <el-tag :type="trace.item.isExtra === 1 ? 'warning' : 'primary'" size="small" effect="plain" class="ml8">
              {{ trace.item.isExtra === 1 ? '盘盈' : '应盘' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="适配机型" :span="2">{{ trace.item.adapterModel || '档案不存在' }}</el-descriptions-item>
          <el-descriptions-item label="厚度规格">{{ trace.item.thickness ?? '-' }}mm</el-descriptions-item>
          <el-descriptions-item label="规格模板">{{ trace.item.specTemplate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应盘产线">{{ trace.item.expectedLineName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前绑定产线">{{ trace.item.boundLineName || '未绑定（账外）' }}</el-descriptions-item>
          <el-descriptions-item label="现场产线">{{ trace.item.siteLineName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实物状态">
            {{ physicalMeta(trace.item.physicalStatus, trace.item.effectiveStatus === 'WAITING').text }}
          </el-descriptions-item>
          <el-descriptions-item label="差异类型">
            <el-tag :type="diffTypeMeta(trace.item.discrepancyType).type" size="small">
              {{ trace.item.effectiveStatus === 'WAITING' ? '待盘' : diffTypeMeta(trace.item.discrepancyType).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <el-tag :type="statusMeta(trace.item.effectiveStatus).type" size="small">
              {{ statusMeta(trace.item.effectiveStatus).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="盘点人">{{ trace.item.countOperator || '-' }}</el-descriptions-item>
          <el-descriptions-item label="盘点时间">{{ formatTime(trace.item.countTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="trace.item.countRemark" label="现场备注" :span="2">
            {{ trace.item.countRemark }}
          </el-descriptions-item>
          <el-descriptions-item v-if="trace.item.handleOperator" label="处理人">
            {{ trace.item.handleOperator }}
          </el-descriptions-item>
          <el-descriptions-item v-if="trace.item.handleTime" label="处理时间">
            {{ formatTime(trace.item.handleTime) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="trace.item.handleNote" label="处理说明" :span="2">
            {{ trace.item.handleNote }}
          </el-descriptions-item>
          <el-descriptions-item v-if="trace.item.transferHint" label="移交追溯提示" :span="2">
            <span class="hint-text">{{ trace.item.transferHint }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">
          <el-icon><Connection /></el-icon>
          产线绑定历史（当前绑定以此为准）
        </h4>
        <el-table :data="trace.bindings" size="small" stripe>
          <el-table-column prop="lineName" label="产线" show-overflow-tooltip />
          <el-table-column label="类型" width="95" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.bindType === 1 ? 'success' : 'warning'" size="small">
                {{ scope.row.bindType === 1 ? '初始绑定' : '移交绑定' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bindTime" label="绑定时间" width="165" />
          <el-table-column prop="operator" label="操作人" width="90" />
          <el-table-column label="状态" width="70" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isCurrent === 1 ? 'primary' : 'info'" size="small">
                {{ scope.row.isCurrent === 1 ? '当前' : '历史' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="trace.latestTransfer">
          <h4 class="section-title">
            <el-icon><List /></el-icon>
            最近移交单 {{ trace.latestTransfer.transferNo }} 流转记录
          </h4>
          <el-timeline>
            <el-timeline-item
              v-for="record in trace.flowRecords"
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
                  {{ transferStatusMeta(record.fromStatus).text || '—' }}
                  <el-icon><Right /></el-icon>
                  {{ transferStatusMeta(record.toStatus).text || '—' }}
                </span>
                <div v-if="record.note" class="flow-note">{{ record.note }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </template>

        <h4 class="section-title">
          <el-icon><Files /></el-icon>
          该挡块历史移交单
        </h4>
        <el-table :data="trace.transfers" size="small" stripe>
          <el-table-column prop="transferNo" label="单号" width="160" />
          <el-table-column prop="fromLineName" label="移出产线" show-overflow-tooltip />
          <el-table-column prop="toLineName" label="移入产线" show-overflow-tooltip />
          <el-table-column prop="transferDate" label="日期" width="105" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="scope">
              <el-tag :type="transferStatusMeta(scope.row.status).type" size="small">
                {{ transferStatusMeta(scope.row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  queryStocktakeBatches,
  getStocktakeBatch,
  createStocktakeBatch,
  finishStocktakeBatch,
  reopenStocktakeBatch,
  queryStocktakeItems,
  countStocktakeItem,
  handleStocktakeItem,
  deleteStocktakeItem,
  getStocktakeTrace,
  exportStocktake
} from '@/api/stocktake'
import { getLeafLines } from '@/api/line'
import { getAllBlocks } from '@/api/block'

const route = useRoute()
const router = useRouter()

const OPERATOR_KEY = 'stocktake-last-operator'

const leafLines = ref([])
const allBlocks = ref([])

// ---------- 批次列表 ----------
const batchLoading = ref(false)
const batchData = ref([])
const batchTotal = ref(0)
const batchDateRange = ref([])
const batchQuery = reactive({
  page: 1,
  size: 10,
  lineId: null,
  status: null,
  startDate: null,
  endDate: null,
  batchNo: ''
})

// ---------- 批次详情 ----------
const currentBatchId = ref(null)
const batch = ref(null)
const exporting = ref(false)
const itemLoading = ref(false)
const itemData = ref([])
const itemTotal = ref(0)
const itemQuery = reactive({
  page: 1,
  size: 10,
  discrepancyType: null,
  status: null,
  blockCode: ''
})

const counting = ref(false)
const countForm = reactive({
  blockCode: '',
  physicalStatus: 'NORMAL',
  siteLineId: null,
  operator: localStorage.getItem(OPERATOR_KEY) || '',
  remark: ''
})

// ---------- 新建批次 ----------
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  lineId: null,
  stocktakeDate: dayjs().format('YYYY-MM-DD'),
  operator: localStorage.getItem(OPERATOR_KEY) || '',
  remark: ''
})
const createRules = {
  lineId: [{ required: true, message: '请选择盘点产线', trigger: 'change' }],
  stocktakeDate: [{ required: true, message: '请选择盘点日期', trigger: 'change' }],
  operator: [{ required: true, message: '请填写盘点负责人', trigger: 'blur' }]
}

// ---------- 差异处理 ----------
const handleVisible = ref(false)
const handling = ref(false)
const handleFormRef = ref(null)
const currentItem = ref(null)
const handleForm = reactive({ operator: '', handleNote: '' })
const handleRules = {
  operator: [{ required: true, message: '请填写处理人', trigger: 'blur' }],
  handleNote: [{ required: true, message: '请填写处理说明', trigger: 'blur' }]
}

// ---------- 追溯 ----------
const traceVisible = ref(false)
const trace = ref(null)

// ---------- 枚举映射 ----------
const batchStatusMeta = (status) => {
  const map = {
    COUNTING: { text: '盘点中', type: 'warning' },
    COMPLETED: { text: '已完成', type: 'success' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const statusMeta = (status) => {
  const map = {
    WAITING: { text: '待盘', type: 'info' },
    NONE: { text: '无差异', type: 'success' },
    PENDING: { text: '待处理', type: 'danger' },
    CONFIRMED: { text: '已确认', type: 'warning' },
    IGNORED: { text: '已忽略', type: 'info' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const diffTypeMeta = (type) => {
  const map = {
    NONE: { text: '无差异', type: 'success' },
    MISSING: { text: '缺失', type: 'danger' },
    WRONG_LINE: { text: '错线', type: 'danger' },
    DUPLICATE: { text: '重复盘点', type: 'warning' },
    EXTRA: { text: '盘盈', type: 'warning' },
    DAMAGED: { text: '损坏', type: 'danger' },
    SCRAPPED: { text: '报废', type: 'info' }
  }
  return map[type] || { text: type || '-', type: 'info' }
}

const physicalMeta = (status, waiting) => {
  if (waiting || !status) return { text: '待盘', type: 'info' }
  const map = {
    NORMAL: { text: '正常', type: 'success' },
    DAMAGED: { text: '损坏', type: 'danger' },
    SCRAPPED: { text: '报废', type: 'info' }
  }
  return map[status] || { text: status, type: 'info' }
}

const transferStatusMeta = (status) => {
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

const progressPercent = (row) => {
  if (!row.totalCount || row.totalCount === 0) return 0
  return Math.min(100, Math.round((row.countedCount / row.totalCount) * 100))
}

const boundLineTagType = (row) => {
  if (!row.boundLineId) return 'info'
  if (row.expectedLineId && row.boundLineId !== row.expectedLineId) return 'danger'
  return 'success'
}

const canHandle = (row) => {
  // 待盘行可“确认缺失”；已有差异且未处理可确认/忽略；无差异和已处理不可操作
  return row.effectiveStatus === 'PENDING' || row.effectiveStatus === 'WAITING'
}

const itemRowClass = ({ row }) => {
  if (row.effectiveStatus === 'WAITING') return 'row-waiting'
  if (row.effectiveStatus === 'PENDING') return 'row-pending'
  return ''
}

// ---------- 批次列表操作 ----------
const syncBatchDateRange = () => {
  if (batchDateRange.value && batchDateRange.value.length === 2) {
    batchQuery.startDate = batchDateRange.value[0]
    batchQuery.endDate = batchDateRange.value[1]
  } else {
    batchQuery.startDate = null
    batchQuery.endDate = null
  }
}

const loadBatches = async () => {
  batchLoading.value = true
  try {
    syncBatchDateRange()
    const res = await queryStocktakeBatches({
      ...batchQuery,
      batchNo: batchQuery.batchNo?.trim() || null
    })
    batchData.value = res.content
    batchTotal.value = res.totalElements
  } catch (e) {
    // 拦截器已提示
  } finally {
    batchLoading.value = false
  }
}

const searchBatches = () => {
  batchQuery.page = 1
  loadBatches()
}

const resetBatchQuery = () => {
  batchDateRange.value = []
  Object.assign(batchQuery, {
    page: 1,
    size: 10,
    lineId: null,
    status: null,
    startDate: null,
    endDate: null,
    batchNo: ''
  })
  loadBatches()
}

const openCreateDialog = () => {
  createForm.stocktakeDate = dayjs().format('YYYY-MM-DD')
  createForm.operator = localStorage.getItem(OPERATOR_KEY) || ''
  createVisible.value = true
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const created = await createStocktakeBatch({ ...createForm })
    localStorage.setItem(OPERATOR_KEY, createForm.operator.trim())
    ElMessage.success(`盘点批次 ${created.batchNo} 已创建，应盘 ${created.totalCount} 件`)
    createVisible.value = false
    enterBatch(created.id)
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

// ---------- 批次详情操作 ----------
const enterBatch = (id) => {
  currentBatchId.value = id
  router.replace({ path: '/stocktakes', query: { batchId: id } })
  loadDetail()
}

const backToList = () => {
  currentBatchId.value = null
  batch.value = null
  router.replace({ path: '/stocktakes' })
  loadBatches()
}

const loadDetail = async () => {
  if (!currentBatchId.value) return
  try {
    batch.value = await getStocktakeBatch(currentBatchId.value)
    if (!countForm.siteLineId) countForm.siteLineId = batch.value.lineId
    await loadItems()
  } catch (e) {
    // 拦截器已提示
  }
}

const loadItems = async () => {
  if (!currentBatchId.value) return
  itemLoading.value = true
  try {
    const res = await queryStocktakeItems(currentBatchId.value, {
      ...itemQuery,
      blockCode: itemQuery.blockCode?.trim() || null
    })
    itemData.value = res.content
    itemTotal.value = res.totalElements
  } catch (e) {
    // 拦截器已提示
  } finally {
    itemLoading.value = false
  }
}

const searchItems = () => {
  itemQuery.page = 1
  loadItems()
}

const resetItemQuery = () => {
  Object.assign(itemQuery, {
    page: 1,
    size: 10,
    discrepancyType: null,
    status: null,
    blockCode: ''
  })
  loadItems()
}

const submitCount = async () => {
  if (!countForm.blockCode) {
    ElMessage.warning('请录入挡块编号')
    return
  }
  if (!countForm.operator?.trim()) {
    ElMessage.warning('请填写盘点人')
    return
  }
  counting.value = true
  try {
    const saved = await countStocktakeItem(currentBatchId.value, {
      blockCode: countForm.blockCode,
      physicalStatus: countForm.physicalStatus,
      siteLineId: countForm.siteLineId || batch.value.lineId,
      operator: countForm.operator.trim(),
      remark: countForm.remark
    })
    localStorage.setItem(OPERATOR_KEY, countForm.operator.trim())
    if (saved.discrepancyType && saved.discrepancyType !== 'NONE') {
      ElMessage.warning(`已录入，标记差异：${diffTypeMeta(saved.discrepancyType).text}`)
    } else {
      ElMessage.success('盘点录入成功，账实相符')
    }
    countForm.blockCode = ''
    countForm.physicalStatus = 'NORMAL'
    countForm.remark = ''
    itemQuery.page = 1
    await loadDetail()
  } catch (e) {
    // 拦截器已提示
  } finally {
    counting.value = false
  }
}

const openHandleDialog = (row) => {
  currentItem.value = row
  handleForm.operator = localStorage.getItem(OPERATOR_KEY) || ''
  handleForm.handleNote = ''
  handleVisible.value = true
}

const submitHandle = async (action) => {
  if (!handleFormRef.value || !currentItem.value) return
  const valid = await handleFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await ElMessageBox.confirm(
      action === 'CONFIRM'
        ? (currentItem.value.effectiveStatus === 'WAITING'
            ? '确认该应盘挡块实物缺失？'
            : '确认该盘点差异情况属实？')
        : '确认忽略该差异（误盘或合理差异）？',
      action === 'CONFIRM' ? '确认差异' : '忽略差异',
      {
        type: action === 'CONFIRM' ? 'warning' : 'info',
        confirmButtonText: action === 'CONFIRM' ? '确认' : '忽略',
        cancelButtonText: '取消',
        confirmButtonClass: action === 'IGNORE' ? 'el-button--info' : ''
      }
    )
  } catch (e) {
    return
  }
  handling.value = true
  try {
    await handleStocktakeItem(currentBatchId.value, currentItem.value.id, {
      action,
      operator: handleForm.operator.trim(),
      handleNote: handleForm.handleNote.trim()
    })
    localStorage.setItem(OPERATOR_KEY, handleForm.operator.trim())
    ElMessage.success(action === 'CONFIRM' ? '差异已确认' : '差异已忽略')
    handleVisible.value = false
    await loadDetail()
  } catch (e) {
    // 拦截器已提示
  } finally {
    handling.value = false
  }
}

const removeExtra = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除盘盈记录 ${row.blockCode}？`, '删除盘盈记录', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteStocktakeItem(currentBatchId.value, row.id)
    ElMessage.success('盘盈记录已删除')
    await loadDetail()
  } catch (e) {
    // 拦截器已提示
  }
}

const doFinish = async () => {
  try {
    await ElMessageBox.confirm(
      '结束盘点后将无法继续录入；未盘到的应盘挡块会自动标记为“缺失”，且所有差异必须已处理。是否继续？',
      '结束盘点',
      { type: 'warning', confirmButtonText: '结束盘点', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await finishStocktakeBatch(currentBatchId.value)
    ElMessage.success('盘点已结束封账')
    await loadDetail()
  } catch (e) {
    // 待处理差异未闭环时后端返回错误，拦截器已提示；刷新批次统计
    await loadDetail()
  }
}

const doReopen = async () => {
  try {
    await ElMessageBox.confirm('重新打开后可继续补盘，差异需重新闭环。是否继续？', '重新打开批次', {
      type: 'warning',
      confirmButtonText: '重新打开',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await reopenStocktakeBatch(currentBatchId.value)
    ElMessage.success('批次已重新打开')
    await loadDetail()
  } catch (e) {
    // 拦截器已提示
  }
}

const doExport = async () => {
  exporting.value = true
  try {
    await exportStocktake(currentBatchId.value)
    ElMessage.success('盘点差异结果已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const openTrace = async (row) => {
  traceVisible.value = true
  trace.value = null
  try {
    trace.value = await getStocktakeTrace(currentBatchId.value, row.id)
  } catch (e) {
    ElMessage.error('加载追溯信息失败')
  }
}

onMounted(async () => {
  try {
    [leafLines.value, allBlocks.value] = await Promise.all([getLeafLines(), getAllBlocks()])
  } catch (e) {
    console.error(e)
  }
  const batchId = route.query.batchId
  if (batchId) {
    currentBatchId.value = Number(batchId)
    await loadDetail()
  } else {
    await loadBatches()
  }
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 8px;
}
.detail-header {
  flex-wrap: wrap;
  gap: 12px;
}
.header-back {
  width: 100%;
  margin-bottom: -4px;
}
.title-line {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.title-sub {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
}
.count-sep {
  margin: 0 4px;
  color: #c0c4cc;
}
.tip-bar {
  margin: 0 0 16px;
}
.stat-row {
  margin-bottom: 16px;
}
.mini-stat {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mini-stat.blue { background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%); }
.mini-stat.orange { background: linear-gradient(135deg, #fa709a 0%, #fee140 100%); }
.mini-stat.green { background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%); }
.mini-stat.red { background: linear-gradient(135deg, #eb4d4b 0%, #f0932b 100%); }
.mini-label { font-size: 13px; opacity: 0.9; }
.mini-value { font-size: 26px; font-weight: 700; }
.count-panel {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px 16px 0;
  margin-bottom: 18px;
  background: #fafcff;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
}
.count-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
.item-filter {
  margin-bottom: 14px;
}
.option-sub {
  color: #909399;
  font-size: 12px;
  margin-left: 10px;
  float: right;
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
.hint-text {
  color: #e6a23c;
  font-size: 12px;
}
.ml8 {
  margin-left: 8px;
}
.pagination {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
.handle-desc {
  margin-bottom: 16px;
}
.handle-form {
  margin-top: 4px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 24px 0 14px;
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
:deep(.row-pending) {
  background-color: #fef0f0 !important;
}
:deep(.row-waiting) {
  background-color: #fdf6ec !important;
}
</style>
