import request from '@/utils/request'

/**
 * 点检工装校准台概览：卡尺/塞尺/百分表台账清单与各口径条数。
 * 超期条数 blockedCount 与点检打卡拦截清单同源，刷新后一致。
 */
export function getGaugeOverview(query = {}) {
  return request({
    url: '/gauge-tools/overview',
    method: 'post',
    data: query
  })
}

/** 工装建账：登记编号、类型、校准到期日与保管班组 */
export function createGaugeTool(data) {
  return request({
    url: '/gauge-tools',
    method: 'post',
    data
  })
}

/** 编辑工装台账（编号/类型/到期日/保管班组/停用） */
export function updateGaugeTool(data) {
  return request({
    url: '/gauge-tools',
    method: 'put',
    data
  })
}

/** 某工装的完整校准记录（最近一次在前） */
export function getGaugeCalibrations(toolId) {
  return request({
    url: `/gauge-tools/${toolId}/calibrations`,
    method: 'get'
  })
}

/** 工装校准登记：合格时到期日同步为下次应校日，工装移出拦截清单 */
export function createGaugeCalibration(data) {
  return request({
    url: '/gauge-tools/calibrations',
    method: 'post',
    data
  })
}
