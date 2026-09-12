import request from '@/utils/request'

/** 校准临期概览：临期条数与清单（应校日/所属产线/最近一次校准结论） */
export function getDueSoonCalibrations() {
  return request({
    url: '/calibrations/due-soon',
    method: 'get'
  })
}

/** 某挡块当前校准状态 */
export function getBlockCalibrationStatus(blockId) {
  return request({
    url: `/calibrations/block/${blockId}/status`,
    method: 'get'
  })
}

/** 某挡块的完整校准记录 */
export function getBlockCalibrations(blockId) {
  return request({
    url: `/calibrations/block/${blockId}`,
    method: 'get'
  })
}
