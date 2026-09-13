import request from '@/utils/request'

/**
 * 点检台账概览：按产线（车间/产线节点均可）筛选在用挡块，
 * 返回清单与可用/不可用/未点检条数（条数与列表同源）。
 */
export function getInspectionOverview(query = {}) {
  return request({
    url: '/inspections/overview',
    method: 'post',
    data: query
  })
}

/** 某挡块的完整点检记录（最近一次在前） */
export function getBlockInspections(blockId) {
  return request({
    url: `/inspections/block/${blockId}`,
    method: 'get'
  })
}

/** 班次点检打卡：登记点检人、班次与是否可用 */
export function createInspection(data) {
  return request({
    url: '/inspections',
    method: 'post',
    data
  })
}
