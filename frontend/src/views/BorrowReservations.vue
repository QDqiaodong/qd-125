<template>
  <div class="borrow-reservations">
    <!-- 到点未取提醒横幅（状态全部来自服务端落库数据，重开页面仍在） -->
    <el-alert
      v-if="overview.overdueCount > 0"
      class="overdue-banner"
      type="error"
      show-icon
      :closable="false"
    >
      <template #title>
        <div class="overdue-title">
          <el-icon><AlarmClock /></el-icon>
          有 {{ overview.overdueCount }} 张预约单已过约定取用时间仍未取走，请尽快提醒相关班组补取或取消
        </div>
      </template>
    </el-alert>

    <!-- 超期未还提醒横幅（已取走、过了计划还期仍未归还） -->
    <el-alert
      v-if="overview.overdueReturnCount > 0"
      class="overdue-banner"
      type="error"
      show-icon
      :closable="false"
    >
      <template #title>
        <div class="overdue-title">
          <el-icon><Warning /></el-icon>
          有 {{ overview.overdueReturnCount }} 张已取走的挡块超过计划归还时间仍未归还，请立即催还
        </div>
      </template>
    </el-alert>

    <!-- 班组交班未完成横幅：交班未完成期间禁止新开预约 -->
    <el-alert
      v-if="handover.inProgress"
      class="overdue-banner"
      type="warning"
      show-icon
      :closable="false"
    >
      <template #title>
        <div class="overdue-title">
          <el-icon><SwitchButton /></el-icon>
          班组交班未完成（{{ handover.handoverNo }}，还剩 {{ handover.unconfirmedCount }} 项待接班人确认<template v-if="handover.unconfirmedGaugeCount > 0">，含拦截中工装 {{ handover.unconfirmedGaugeCount }} 件</template>），完成前禁止新开借用预约
        </div>
      </template>
    </el-alert>

    <div class="page-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon :size="20" color="#409EFF"><Calendar /></el-icon>
          挡块借用预约
        </div>
        <div class="header-actions">
          <el-button @click="loadData">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-tooltip
            :disabled="!handover.inProgress"
            :content="`班组交班未完成（${handover.handoverNo}），接班人确认完成前禁止新开预约`"
            placement="top"
          >
            <span>
              <el-button type="primary" :disabled="handover.inProgress" @click="openCreateDialog">
                <el-icon><Plus /></el-icon>
                新开借用预约
              </el-button>
            </span>
          </el-tooltip>
        </div>
      </div>

      <el-row :gutter="12" class="stat-row">
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">已预约待取</div>
            <div class="mini-value warning">{{ overview.reservedCount }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">
              已取走占用中
              <el-tag v-if="overview.overdueReturnCount > 0" type="danger" size="small" effect="dark" disable-transitions class="overdue-tag">
                超期未还 {{ overview.overdueReturnCount }}
              </el-tag>
            </div>
            <div class="mini-value danger">{{ overview.pickedUpCount }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">逾时未取</div>
            <div class="mini-value danger">{{ overview.overdueCount }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="mini-stat">
            <div class="mini-label">档案已约出合计</div>
            <div class="mini-value primary">{{ overview.activeCount }}</div>
          </div>
        </el-col>
      </el-row>

      <div class="filter-bar">
        <el-select
          v-model="query.status"
          placeholder="预约状态"
          clearable
          style="width: 150px;"
          @change="handleSearch"
        >
          <el-option label="已预约" value="RESERVED" />
          <el-option label="已取走" value="PICKED_UP" />
          <el-option label="逾时未取" value="OVERDUE" />
          <el-option label="已归还" value="RETURNED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-input
          v-model="query.teamName"
          placeholder="借用班组"
          clearable
          style="width: 170px;"
          @keyup.enter="handleSearch"
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
      </div>

      <el-alert
        class="tip-bar"
        type="info"
        :closable="false"
        show-icon
        title="借用独立于移交划转：只可预约空闲挡块，占用期间挡块档案显示“已约出”；取走时记下实际取走人与取走时刻；归还时可修改实际归还点；取消必须填写原因；过了约定取用时间未取走将自动标记“逾时未取”，已取走超过计划还期未还将标红“超期未还”。"
      />

      <el-table :data="tableData" stripe v-loading="loading" row-key="id" :row-class-name="rowClassName">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="reservationNo" label="预约单号" width="175">
          <template #default="scope">
            <el-tag type="success" effect="plain">{{ scope.row.reservationNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="blockCode" label="挡块编号" width="140" />
        <el-table-column label="挡块规格" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <div class="spec-cell">
              <span>{{ scope.row.adapterModel }}</span>
              <span class="spec-sub">
                厚度 {{ scope.row.thickness }}mm<template v-if="scope.row.specTemplate"> · {{ scope.row.specTemplate }}</template>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="teamName" label="借用班组" width="130" show-overflow-tooltip />
        <el-table-column label="约定取用" width="165">
          <template #default="scope">
            <span :class="{ 'overdue-text': scope.row.status === 'OVERDUE' }">
              {{ formatTime(scope.row.pickupTime) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="计划归还" width="175">
          <template #default="scope">
            <span v-if="scope.row.plannedReturnTime" :class="{ 'overdue-text': scope.row.overdueReturn }">
              {{ formatTime(scope.row.plannedReturnTime) }}
              <el-tag v-if="scope.row.overdueReturn" type="danger" size="small" effect="dark" disable-transitions class="overdue-tag">
                超期未还 {{ scope.row.overdueReturnDuration }}
              </el-tag>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="归还点" min-width="140" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.status === 'RETURNED' && scope.row.actualReturnPoint ? scope.row.actualReturnPoint : scope.row.returnPoint }}</span>
            <el-tag
              v-if="scope.row.status === 'RETURNED' && scope.row.actualReturnPoint && scope.row.actualReturnPoint !== scope.row.returnPoint"
              type="warning"
              size="small"
              effect="plain"
              class="point-changed"
            >已改点</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105" align="center">
          <template #default="scope">
            <el-tooltip
              v-if="scope.row.status === 'OVERDUE'"
              effect="dark"
              placement="top"
              :content="`已超时 ${scope.row.overdueDuration}，提醒 ${scope.row.remindCount} 次`"
            >
              <el-tag :type="statusMeta(scope.row.status).type" effect="dark" disable-transitions class="overdue-tag">
                {{ statusMeta(scope.row.status).text }}
              </el-tag>
            </el-tooltip>
            <el-tooltip
              v-else-if="scope.row.status === 'PICKED_UP'"
              effect="dark"
              placement="top"
            >
              <template #content>
                <div>实际取走：{{ formatTime(scope.row.actualPickupTime) }}</div>
                <div>取走人：{{ scope.row.pickupOperator || '-' }}</div>
                <div v-if="scope.row.overdueReturn">已超期未还 {{ scope.row.overdueReturnDuration }}</div>
              </template>
              <el-tag :type="statusMeta(scope.row.status).type" effect="dark"
                      :disable-transitions="!!scope.row.overdueReturn"
                      :class="{ 'overdue-tag': scope.row.overdueReturn }">
                {{ statusMeta(scope.row.status).text }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="statusMeta(scope.row.status).type" effect="light">
              {{ statusMeta(scope.row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="235" fixed="right" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="viewDetail(scope.row)">
              <el-icon><View /></el-icon>详情
            </el-button>
            <el-button
              v-if="scope.row.status === 'RESERVED' || scope.row.status === 'OVERDUE'"
              size="small"
              type="success"
              link
              @click="handlePickup(scope.row)"
            >
              <el-icon><Van /></el-icon>取走
            </el-button>
            <el-button
              v-if="scope.row.status === 'PICKED_UP'"
              size="small"
              type="warning"
              link
              @click="handleReturn(scope.row)"
            >
              <el-icon><RefreshRight /></el-icon>归还
            </el-button>
            <el-button
              v-if="scope.row.status === 'RESERVED' || scope.row.status === 'OVERDUE'"
              size="small"
              type="danger"
              link
              @click="openCancelDialog(scope.row)"
            >
              <el-icon><CircleClose /></el-icon>取消
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

    <!-- 新开借用预约 -->
    <el-dialog v-model="createVisible" title="新开借用预约" width="640px" @close="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="空闲挡块" prop="blockId">
          <el-select
            v-model="createForm.blockId"
            placeholder="仅可选择空闲（未约出）挡块"
            filterable
            style="width: 100%;"
            @change="onBlockChange"
          >
            <el-option
              v-for="b in availableBlocks"
              :key="b.id"
              :label="`${b.blockCode}｜${b.adapterModel}｜厚度${b.thickness}mm${b.lineName ? '｜' + b.lineName : ''}`"
              :value="b.id"
            >
              <div class="block-option">
                <span>{{ b.blockCode }} · {{ b.adapterModel }} · {{ b.thickness }}mm</span>
                <span class="block-option-sub">{{ b.lineName || '未绑定产线' }}</span>
              </div>
            </el-option>
          </el-select>
          <div v-if="selectedBlock" class="selected-block-tip">
            当前产线：{{ selectedBlock.lineName || '未绑定' }}
            <el-tag type="success" effect="plain" size="small">空闲可约</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="借用班组" prop="teamName">
          <el-input v-model="createForm.teamName" placeholder="请输入借用班组，如：甲班" />
        </el-form-item>
        <el-form-item label="取用时间" prop="pickupTime">
          <el-date-picker
            v-model="createForm.pickupTime"
            type="datetime"
            placeholder="约定取用时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="计划归还" prop="plannedReturnTime">
          <el-date-picker
            v-model="createForm.plannedReturnTime"
            type="datetime"
            placeholder="选填，预计归还时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="归还点" prop="returnPoint">
          <el-select
            v-model="createForm.returnPoint"
            placeholder="选择或输入归还点"
            filterable
            allow-create
            default-first-option
            style="width: 100%;"
          >
            <el-option
              v-for="line in leafLines"
              :key="line.id"
              :label="`${line.lineName}（产线存放点）`"
              :value="line.lineName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="createForm.contactPerson" placeholder="班组联系人（选填）" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="createForm.contactPhone" placeholder="联系电话（选填）" />
        </el-form-item>
        <el-form-item label="借用用途" prop="purpose">
          <el-input v-model="createForm.purpose" type="textarea" :rows="2" placeholder="借用用途（选填）" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="备注（选填）" />
        </el-form-item>
        <el-form-item label="登记人" prop="operator">
          <el-input v-model="createForm.operator" placeholder="预约登记人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认预约</el-button>
      </template>
    </el-dialog>

    <!-- 取消必须写原因 -->
    <el-dialog v-model="cancelVisible" title="取消借用预约" width="480px">
      <div v-if="cancelTarget" class="cancel-target">
        <el-tag type="success" effect="plain">{{ cancelTarget.reservationNo }}</el-tag>
        <span>{{ cancelTarget.blockCode }} · {{ cancelTarget.teamName }}</span>
      </div>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="取消后该挡块立即恢复为空闲可约；取消操作必须填写原因并记入流转记录。"
        class="cancel-alert"
      />
      <el-form ref="cancelFormRef" :model="cancelForm" :rules="cancelRules" label-width="90px">
        <el-form-item label="取消原因" prop="cancelReason">
          <el-input
            v-model="cancelForm.cancelReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写取消原因（必填）"
          />
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="cancelForm.operator" placeholder="取消操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">返回</el-button>
        <el-button type="danger" :loading="submitting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 取走登记 -->
    <el-dialog v-model="pickupVisible" title="挡块取走登记" width="480px">
      <div v-if="actionTarget" class="cancel-target">
        <el-tag type="success" effect="plain">{{ actionTarget.reservationNo }}</el-tag>
        <span>{{ actionTarget.blockCode }} · {{ actionTarget.teamName }}</span>
      </div>
      <el-alert
        v-if="actionTarget && actionTarget.status === 'OVERDUE'"
        type="error"
        :closable="false"
        show-icon
        :title="`该单已逾时未取（超时 ${actionTarget.overdueDuration}），登记取走后状态变为“已取走”。`"
        class="cancel-alert"
      />
      <el-form ref="pickupFormRef" :model="pickupForm" :rules="actionRules" label-width="90px">
        <el-form-item label="取走人" prop="operator">
          <el-input v-model="pickupForm.operator" placeholder="取走登记人" />
        </el-form-item>
        <el-form-item label="备注" prop="note">
          <el-input v-model="pickupForm.note" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickupVisible = false">返回</el-button>
        <el-button type="success" :loading="submitting" @click="submitPickup">确认取走</el-button>
      </template>
    </el-dialog>

    <!-- 归还登记 -->
    <el-dialog v-model="returnVisible" title="挡块归还登记" width="480px">
      <div v-if="actionTarget" class="cancel-target">
        <el-tag type="success" effect="plain">{{ actionTarget.reservationNo }}</el-tag>
        <span>{{ actionTarget.blockCode }} · {{ actionTarget.teamName }}</span>
      </div>
      <el-alert
        :type="actionTarget && actionTarget.overdueReturn ? 'error' : 'info'"
        :closable="false"
        show-icon
        :title="returnAlertTitle"
        class="cancel-alert"
      />
      <el-form ref="returnFormRef" :model="returnForm" :rules="actionRules" label-width="90px">
        <el-form-item label="实际归还点" prop="actualReturnPoint">
          <el-select
            v-model="returnForm.actualReturnPoint"
            placeholder="默认沿用约定归还点，可改"
            filterable
            allow-create
            default-first-option
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="line in leafLines"
              :key="line.id"
              :label="`${line.lineName}（产线存放点）`"
              :value="line.lineName"
            />
          </el-select>
          <div v-if="actionTarget" class="return-point-hint">
            约定归还点：{{ actionTarget.returnPoint }}；不改点可直接留空。
          </div>
        </el-form-item>
        <el-form-item label="归还人" prop="operator">
          <el-input v-model="returnForm.operator" placeholder="归还登记人" />
        </el-form-item>
        <el-form-item label="备注" prop="note">
          <el-input v-model="returnForm.note" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">返回</el-button>
        <el-button type="warning" :loading="submitting" @click="submitReturn">确认归还</el-button>
      </template>
    </el-dialog>

    <!-- 详情 + 流转记录 -->
    <el-dialog v-model="detailVisible" title="借用预约详情" width="720px">
      <el-descriptions :column="2" border v-if="currentDetail">
        <el-descriptions-item label="预约单号">
          <el-tag type="success" effect="plain">{{ currentDetail.reservationNo }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(currentDetail.status).type">
            {{ statusMeta(currentDetail.status).text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="挡块编号">{{ currentDetail.blockCode }}</el-descriptions-item>
        <el-descriptions-item label="当前产线">{{ currentDetail.currentLineName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="借用班组">{{ currentDetail.teamName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">
          {{ currentDetail.contactPerson || '-' }}<template v-if="currentDetail.contactPhone"> / {{ currentDetail.contactPhone }}</template>
        </el-descriptions-item>
        <el-descriptions-item label="约定取用">{{ formatTime(currentDetail.pickupTime) }}</el-descriptions-item>
        <el-descriptions-item label="计划归还">
          <span :class="{ 'overdue-text': currentDetail.overdueReturn }">
            {{ currentDetail.plannedReturnTime ? formatTime(currentDetail.plannedReturnTime) : '-' }}
          </span>
          <el-tag v-if="currentDetail.overdueReturn" type="danger" size="small" effect="dark" class="point-changed">
            超期未还 {{ currentDetail.overdueReturnDuration }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="约定归还点">{{ currentDetail.returnPoint }}</el-descriptions-item>
        <el-descriptions-item label="实际归还点">
          <span v-if="currentDetail.status === 'RETURNED'">
            {{ currentDetail.actualReturnPoint || currentDetail.returnPoint }}
            <el-tag
              v-if="currentDetail.actualReturnPoint && currentDetail.actualReturnPoint !== currentDetail.returnPoint"
              type="warning"
              size="small"
              effect="plain"
            >归还时改点</el-tag>
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="借用用途" :span="2">{{ currentDetail.purpose || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentDetail.status === 'OVERDUE'" label="逾时提醒" :span="2">
          <el-text type="danger">
            已超时 {{ currentDetail.overdueDuration }}，系统提醒 {{ currentDetail.remindCount }} 次，
            最近 {{ formatTime(currentDetail.lastRemindTime) }}
          </el-text>
        </el-descriptions-item>
        <el-descriptions-item label="实际取走">
          {{ currentDetail.actualPickupTime ? `${formatTime(currentDetail.actualPickupTime)} · ${currentDetail.pickupOperator || ''}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="实际归还">
          {{ currentDetail.actualReturnTime ? `${formatTime(currentDetail.actualReturnTime)} · ${currentDetail.returnOperator || ''}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentDetail.status === 'CANCELLED'" label="取消原因" :span="2">
          {{ currentDetail.cancelReason }}
          <span class="muted">（{{ currentDetail.cancelOperator }} · {{ formatTime(currentDetail.cancelTime) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">流转记录</el-divider>
      <el-timeline>
        <el-timeline-item
          v-for="record in flowRecords"
          :key="record.id"
          :type="flowMeta(record.action).type"
          :timestamp="`${formatTime(record.createTime)} · ${record.operator || '系统'}`"
        >
          <el-tag :type="flowMeta(record.action).type" size="small">{{ flowMeta(record.action).text }}</el-tag>
          <span class="flow-note">{{ record.note }}</span>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getAllBlocks } from '@/api/block'
import { getLeafLines } from '@/api/line'
import {
  queryBorrowReservations,
  getBorrowOverview,
  createBorrowReservation,
  pickupBorrowReservation,
  returnBorrowReservation,
  cancelBorrowReservation,
  getBorrowFlowRecords
} from '@/api/borrow'
import { getHandoverOverview } from '@/api/handover'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const allBlocks = ref([])
const leafLines = ref([])

const overview = ref({ reservedCount: 0, pickedUpCount: 0, overdueCount: 0, overdueReturnCount: 0, activeCount: 0 })
// 班组交班状态：交班未完成时禁止新开预约（后端同样拦截，前后端口径一致）
const handover = ref({ inProgress: false, handoverNo: '', unconfirmedCount: 0, unconfirmedGaugeCount: 0 })

const query = reactive({ status: '', teamName: '', blockCode: '', page: 1, size: 10 })

const formatTime = (t) => (t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-')

const statusMeta = (status) => {
  const map = {
    RESERVED: { text: '已预约', type: 'warning' },
    PICKED_UP: { text: '已取走', type: 'danger' },
    RETURNED: { text: '已归还', type: 'success' },
    CANCELLED: { text: '已取消', type: 'info' },
    OVERDUE: { text: '逾时未取', type: 'danger' }
  }
  return map[status] || { text: status || '-', type: 'info' }
}

const flowMeta = (action) => {
  const map = {
    BOOK: { text: '预约', type: 'primary' },
    PICKUP: { text: '取走', type: 'success' },
    RETURN: { text: '归还', type: 'warning' },
    CANCEL: { text: '取消', type: 'info' },
    OVERDUE_REMIND: { text: '逾时提醒', type: 'danger' }
  }
  return map[action] || { text: action, type: 'info' }
}

const rowClassName = ({ row }) =>
  row.status === 'OVERDUE' || row.overdueReturn ? 'overdue-row' : ''

// 空闲挡块：在用、未挂起、未被借用占用；待移交挡块同样由后端拦截，这里也先过滤
const availableBlocks = computed(() => allBlocks.value.filter(b =>
  b.serviceStatus !== 'SUSPENDED' && !b.borrowedOut && !b.pendingTransfer
))

const selectedBlock = computed(() =>
  allBlocks.value.find(b => b.id === createForm.blockId) || null
)

const loadList = async () => {
  loading.value = true
  try {
    const page = await queryBorrowReservations({
      status: query.status || null,
      teamName: query.teamName || null,
      blockCode: query.blockCode || null,
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
    overview.value = await getBorrowOverview()
  } catch (e) {
    // ignore
  }
}

const loadHandover = async () => {
  try {
    handover.value = await getHandoverOverview()
  } catch (e) {
    // ignore
  }
}

const loadData = async () => {
  await Promise.all([loadList(), loadOverview(), loadHandover()])
}

const handleSearch = () => {
  query.page = 1
  loadList()
}

const handleReset = () => {
  query.status = ''
  query.teamName = ''
  query.blockCode = ''
  query.page = 1
  loadList()
}

// ---------------- 新开预约 ----------------

const createVisible = ref(false)
const createFormRef = ref(null)
const defaultCreateForm = () => ({
  blockId: null,
  teamName: '',
  pickupTime: dayjs().add(1, 'hour').format('YYYY-MM-DD HH:mm:ss'),
  plannedReturnTime: '',
  returnPoint: '',
  contactPerson: '',
  contactPhone: '',
  purpose: '',
  remark: '',
  operator: ''
})
const createForm = reactive(defaultCreateForm())

const createRules = {
  blockId: [{ required: true, message: '请选择空闲挡块', trigger: 'change' }],
  teamName: [{ required: true, message: '请填写借用班组', trigger: 'blur' }],
  pickupTime: [{ required: true, message: '请选择约定取用时间', trigger: 'change' }],
  returnPoint: [{ required: true, message: '请选择或填写归还点', trigger: 'change' }],
  operator: [{ required: true, message: '请填写登记人', trigger: 'blur' }]
}

const openCreateDialog = async () => {
  try {
    allBlocks.value = await getAllBlocks()
  } catch (e) {
    // ignore
  }
  Object.assign(createForm, defaultCreateForm())
  createVisible.value = true
}

const onBlockChange = () => {
  // 选择挡块后若未自定义归还点，带出该挡块当前产线作为默认归还点
  const block = selectedBlock.value
  if (block && block.lineName && !createForm.returnPoint) {
    createForm.returnPoint = block.lineName
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
      const data = { ...createForm }
      data.plannedReturnTime = data.plannedReturnTime || null
      await createBorrowReservation(data)
      ElMessage.success('预约成功，挡块档案已标记为已约出')
      createVisible.value = false
      await loadData()
    } catch (e) {
      // 后端返回明确中文原因（挡块已约出/挂起等），拦截器已弹窗
    } finally {
      submitting.value = false
    }
  })
}

// ---------------- 取消（必填原因） ----------------

const cancelVisible = ref(false)
const cancelTarget = ref(null)
const cancelFormRef = ref(null)
const cancelForm = reactive({ cancelReason: '', operator: '' })
const cancelRules = {
  cancelReason: [{ required: true, message: '取消预约必须填写取消原因', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写取消操作人', trigger: 'blur' }]
}

const openCancelDialog = (row) => {
  cancelTarget.value = row
  cancelForm.cancelReason = ''
  cancelForm.operator = ''
  cancelVisible.value = true
}

const submitCancel = async () => {
  if (!cancelFormRef.value) return
  await cancelFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await cancelBorrowReservation(cancelTarget.value.id, {
        cancelReason: cancelForm.cancelReason,
        operator: cancelForm.operator
      })
      ElMessage.success('预约已取消，挡块恢复空闲')
      cancelVisible.value = false
      await loadData()
    } catch (e) {
      // ignore
    } finally {
      submitting.value = false
    }
  })
}

// ---------------- 取走 / 归还 ----------------

const pickupVisible = ref(false)
const returnVisible = ref(false)
const actionTarget = ref(null)
const pickupFormRef = ref(null)
const returnFormRef = ref(null)
const pickupForm = reactive({ operator: '', note: '' })
const returnForm = reactive({ operator: '', note: '', actualReturnPoint: '' })
const actionRules = {
  operator: [{ required: true, message: '请填写操作人', trigger: 'blur' }]
}

const returnAlertTitle = computed(() => {
  if (!actionTarget.value) return ''
  if (actionTarget.value.overdueReturn) {
    return `该单已超过计划归还时间（超期 ${actionTarget.value.overdueReturnDuration}）仍未归还，登记后占用结束，档案恢复空闲。`
  }
  return `约定归还点：${actionTarget.value.returnPoint}，可在下方修改实际归还点；登记后占用结束，档案恢复空闲。`
})

const handlePickup = (row) => {
  actionTarget.value = row
  pickupForm.operator = ''
  pickupForm.note = ''
  pickupVisible.value = true
}

const submitPickup = async () => {
  if (!pickupFormRef.value) return
  await pickupFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await pickupBorrowReservation(actionTarget.value.id, { ...pickupForm })
      ElMessage.success('取走登记成功')
      pickupVisible.value = false
      await loadData()
    } catch (e) {
      // ignore
    } finally {
      submitting.value = false
    }
  })
}

const handleReturn = (row) => {
  actionTarget.value = row
  returnForm.operator = ''
  returnForm.note = ''
  // 默认沿用约定归还点（空值即后端沿用），需要改点时在下拉中另选/输入
  returnForm.actualReturnPoint = ''
  returnVisible.value = true
}

const submitReturn = async () => {
  if (!returnFormRef.value) return
  await returnFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await returnBorrowReservation(actionTarget.value.id, {
        operator: returnForm.operator,
        note: returnForm.note,
        actualReturnPoint: returnForm.actualReturnPoint || null
      })
      ElMessage.success('归还登记成功，占用结束')
      returnVisible.value = false
      await loadData()
    } catch (e) {
      // ignore
    } finally {
      submitting.value = false
    }
  })
}

// ---------------- 详情 ----------------

const detailVisible = ref(false)
const currentDetail = ref(null)
const flowRecords = ref([])

const viewDetail = async (row) => {
  currentDetail.value = row
  detailVisible.value = true
  flowRecords.value = []
  try {
    flowRecords.value = await getBorrowFlowRecords(row.id)
  } catch (e) {
    // ignore
  }
}

// ---------------- 轮询：到点未取状态与提醒由后端定时更新，前端每 60 秒对齐一次 ----------------
let pollTimer = null

onMounted(async () => {
  try {
    leafLines.value = await getLeafLines()
  } catch (e) {
    // ignore
  }
  await loadData()
  pollTimer = setInterval(() => {
    loadOverview()
    loadHandover()
    // 已取走列表也要周期刷新，以便超期未还标记随计划还期到期自动出现
    if (!query.status || query.status === 'RESERVED' || query.status === 'OVERDUE'
        || query.status === 'PICKED_UP') {
      loadList()
    }
  }, 60_000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.overdue-banner {
  margin-bottom: 16px;
}
.overdue-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
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
  display: flex;
  align-items: center;
  gap: 6px;
}
.mini-value {
  font-size: 24px;
  font-weight: 700;
}
.mini-value.warning { color: #e6a23c; }
.mini-value.danger { color: #f56c6c; }
.mini-value.primary { color: #409eff; }
.tip-bar {
  margin-bottom: 16px;
}
.spec-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.spec-sub {
  color: #909399;
  font-size: 12px;
}
.overdue-text {
  color: #f56c6c;
  font-weight: 600;
}
.overdue-tag {
  animation: overdue-blink 1.6s ease-in-out infinite;
}
.point-changed {
  margin-left: 6px;
}
.return-point-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
@keyframes overdue-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.block-option {
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.block-option-sub {
  color: #909399;
  font-size: 12px;
}
.selected-block-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 8px;
  align-items: center;
}
.cancel-target {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  font-size: 14px;
}
.cancel-alert {
  margin-bottom: 14px;
}
.flow-note {
  margin-left: 8px;
  font-size: 13px;
  color: #606266;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>

<style>
.overdue-row {
  background-color: #fef0f0 !important;
}
</style>
