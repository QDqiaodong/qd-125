import dayjs from 'dayjs'

/**
 * 等待时长口径（与后端 BlockTransferService 保持一致）：
 * - 待确认单：从登记时刻到当前时刻的实时时长，刷新页面后随当前时间增长；
 * - 已确认/已驳回单：停在办理时刻（handleTime）的固定时长，不随当前时间继续增长。
 * 返回 null 表示无法计算（缺少必要时间字段）。
 */
export function transferDuration(row, now = dayjs()) {
  if (!row || !row.createTime) return null
  const start = dayjs(row.createTime)
  if (row.status === 'PENDING') {
    return start.isValid() ? now.diff(start, 'minute') : null
  }
  if (row.handleTime) {
    const end = dayjs(row.handleTime)
    return end.isValid() ? end.diff(start, 'minute') : null
  }
  // 历史数据兜底：无办理时刻时用最后更新时刻
  if (row.updateTime) {
    const end = dayjs(row.updateTime)
    return end.isValid() ? end.diff(start, 'minute') : null
  }
  return null
}

/** 将分钟数格式化为“X天X小时X分钟”，无法计算时返回 '-' */
export function formatDurationMinutes(mins) {
  if (mins == null || Number.isNaN(mins) || mins < 0) return '-'
  const days = Math.floor(mins / 1440)
  const hours = Math.floor((mins % 1440) / 60)
  const leftMins = mins % 60
  let s = ''
  if (days > 0) s += `${days}天`
  if (hours > 0) s += `${hours}小时`
  if (leftMins > 0 || !s) s += `${leftMins}分钟`
  return s
}

/** 等待时长展示文本：优先使用后端按统一口径算好的 waitingDuration，缺省时前端兜底计算 */
export function waitingDurationText(row, now) {
  if (!row) return '-'
  if (row.waitingDuration) return row.waitingDuration
  return formatDurationMinutes(transferDuration(row, now))
}
