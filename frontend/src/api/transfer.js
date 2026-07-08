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

export function markTransferPrinted(id) {
  return request({
    url: `/transfers/${id}/print`,
    method: 'post'
  })
}

export function getTransfersByBlockId(blockId) {
  return request({
    url: `/transfers/block/${blockId}`,
    method: 'get'
  })
}
