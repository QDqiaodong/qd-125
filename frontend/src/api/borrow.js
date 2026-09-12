import request from '@/utils/request'

/** 分页查询借用预约 */
export function queryBorrowReservations(data) {
  return request({
    url: '/borrow-reservations/query',
    method: 'post',
    data
  })
}

/** 概览计数（已预约/已取走/逾时未取/占用合计） */
export function getBorrowOverview() {
  return request({
    url: '/borrow-reservations/overview',
    method: 'get'
  })
}

export function getBorrowReservationById(id) {
  return request({
    url: `/borrow-reservations/${id}`,
    method: 'get'
  })
}

/** 新建借用预约 */
export function createBorrowReservation(data) {
  return request({
    url: '/borrow-reservations',
    method: 'post',
    data
  })
}

/** 班组取走 */
export function pickupBorrowReservation(id, data) {
  return request({
    url: `/borrow-reservations/${id}/pickup`,
    method: 'post',
    data
  })
}

/** 归还 */
export function returnBorrowReservation(id, data) {
  return request({
    url: `/borrow-reservations/${id}/return`,
    method: 'post',
    data
  })
}

/** 取消预约（必须填写取消原因） */
export function cancelBorrowReservation(id, data) {
  return request({
    url: `/borrow-reservations/${id}/cancel`,
    method: 'post',
    data
  })
}

/** 完整流转记录（含逾时提醒） */
export function getBorrowFlowRecords(id) {
  return request({
    url: `/borrow-reservations/${id}/flow-records`,
    method: 'get'
  })
}

/** 某挡块的借用记录（挡块详情用） */
export function getBorrowReservationsByBlock(blockId) {
  return request({
    url: `/borrow-reservations/block/${blockId}`,
    method: 'get'
  })
}
