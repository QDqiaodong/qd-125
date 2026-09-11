import request from '@/utils/request'
import axios from 'axios'

/** 盘点批次分页查询 */
export function queryStocktakeBatches(data) {
  return request({
    url: '/stocktakes/query',
    method: 'post',
    data
  })
}

/** 盘点中批次/待处理差异概览 */
export function getStocktakeOverview() {
  return request({
    url: '/stocktakes/overview',
    method: 'get'
  })
}

export function getStocktakeBatch(id) {
  return request({
    url: `/stocktakes/${id}`,
    method: 'get'
  })
}

export function createStocktakeBatch(data) {
  return request({
    url: '/stocktakes',
    method: 'post',
    data
  })
}

/** 结束盘点：待盘项转缺失，存在待处理差异时拒绝封账 */
export function finishStocktakeBatch(id) {
  return request({
    url: `/stocktakes/${id}/finish`,
    method: 'post'
  })
}

/** 重新打开已完成批次补盘 */
export function reopenStocktakeBatch(id) {
  return request({
    url: `/stocktakes/${id}/reopen`,
    method: 'post'
  })
}

/** 盘点明细分页查询（差异状态/类型/编号筛选） */
export function queryStocktakeItems(batchId, data) {
  return request({
    url: `/stocktakes/${batchId}/items/query`,
    method: 'post',
    data
  })
}

/** 逐项录入实物状态与现场产线 */
export function countStocktakeItem(batchId, data) {
  return request({
    url: `/stocktakes/${batchId}/items/count`,
    method: 'post',
    data
  })
}

/** 差异确认/忽略 */
export function handleStocktakeItem(batchId, itemId, data) {
  return request({
    url: `/stocktakes/${batchId}/items/${itemId}/handle`,
    method: 'post',
    data
  })
}

/** 删除盘盈误录记录 */
export function deleteStocktakeItem(batchId, itemId) {
  return request({
    url: `/stocktakes/${batchId}/items/${itemId}`,
    method: 'delete'
  })
}

/** 差异详情追溯 */
export function getStocktakeTrace(batchId, itemId) {
  return request({
    url: `/stocktakes/${batchId}/items/${itemId}/trace`,
    method: 'get'
  })
}

/** 导出盘点差异结果（CSV 二进制流，不走统一 JSON 拦截器） */
export async function exportStocktake(batchId) {
  const response = await axios.get(`/api/stocktakes/${batchId}/export`, {
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
  let fileName = `stocktake-${batchId}.csv`
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
