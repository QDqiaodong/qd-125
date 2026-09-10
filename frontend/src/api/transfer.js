import request from '@/utils/request'

export function queryTransfers(data) {
  return request({
    url: '/transfers/query',
    method: 'post',
    data
  })
}

export function getTransfersByDateRange(startDate, endDate) {
  const params = {}
  if (startDate) params.startDate = startDate
  if (endDate) params.endDate = endDate
  return request({
    url: '/transfers/range',
    method: 'get',
    params
  })
}

export function getTransferById(id) {
  return request({
    url: `/transfers/${id}`,
    method: 'get'
  })
}

export function createTransfer(data) {
  return request({
    url: '/transfers',
    method: 'post',
    data
  })
}

/** 接收方确认接收 */
export function confirmTransfer(id, data) {
  return request({
    url: `/transfers/${id}/confirm`,
    method: 'post',
    data
  })
}

/** 接收方驳回 */
export function rejectTransfer(id, data) {
  return request({
    url: `/transfers/${id}/reject`,
    method: 'post',
    data
  })
}

/** 完整流转记录 */
export function getTransferFlowRecords(id) {
  return request({
    url: `/transfers/${id}/flow-records`,
    method: 'get'
  })
}

export function markTransferPrinted(id) {
  return request({
    url: `/transfers/${id}/print`,
    method: 'post'
  })
}

/** 打印确认回执 */
export function markReceiptPrinted(id) {
  return request({
    url: `/transfers/${id}/receipt-print`,
    method: 'post',
  })
}

export function getTransfersByBlockId(blockId) {
  return request({
    url: `/transfers/block/${blockId}`,
    method: 'get'
  })
}
