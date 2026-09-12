import request from '@/utils/request'

/** 分页查询班组交班记录 */
export function queryShiftHandovers(data) {
  return request({
    url: '/shift-handovers/query',
    method: 'post',
    data
  })
}

/** 当前进行中交班概览（未确认条数等，无进行中交班时 inProgress=false） */
export function getHandoverOverview() {
  return request({
    url: '/shift-handovers/overview',
    method: 'get'
  })
}

/** 登记前预览：当前将被一次性登记的三类未结事项 */
export function getHandoverPreview() {
  return request({
    url: '/shift-handovers/preview',
    method: 'get'
  })
}

/** 登记交班：一次性快照全部未结事项 */
export function createShiftHandover(data) {
  return request({
    url: '/shift-handovers',
    method: 'post',
    data
  })
}

export function getShiftHandoverById(id) {
  return request({
    url: `/shift-handovers/${id}`,
    method: 'get'
  })
}

/** 交班事项清单（登记时快照 + 源单据当前状态） */
export function getHandoverItems(id) {
  return request({
    url: `/shift-handovers/${id}/items`,
    method: 'get'
  })
}

/** 接班人逐条确认；全部确认后交班自动完成 */
export function confirmHandoverItem(id, itemId, data) {
  return request({
    url: `/shift-handovers/${id}/items/${itemId}/confirm`,
    method: 'post',
    data
  })
}
