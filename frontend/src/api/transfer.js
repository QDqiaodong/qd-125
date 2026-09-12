import request from '@/utils/request'
import axios from 'axios'

export function queryTransfers(data) {
  return request({
    url: '/transfers/query',
    method: 'post',
    data
  })
}

/** 导出当前筛选结果（CSV 二进制流，不走统一 JSON 拦截器） */
export async function exportTransfers(query) {
  const response = await axios.post('/api/transfers/export', query, {
    responseType: 'blob'
  })
  // 业务异常时后端返回的是 JSON Result（非文件流），读出中文提示后抛出
  const contentType = response.headers['content-type'] || ''
  if (contentType.includes('application/json')) {
    const text = await response.data.text()
    let message = '导出失败'
    try {
      message = JSON.parse(text).message || message
    } catch (e) {
      // 保留默认提示
    }
    throw new Error(message)
  }
  const disposition = response.headers['content-disposition'] || ''
  let fileName = 'transfer-confirm.csv'
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match) {
    fileName = decodeURIComponent(utf8Match[1])
  } else {
    const asciiMatch = disposition.match(/filename="?([^";]+)"?/i)
    if (asciiMatch) fileName = asciiMatch[1]
  }
  const url = window.URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8;' }))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', fileName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
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
