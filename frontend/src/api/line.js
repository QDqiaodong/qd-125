import request from '@/utils/request'

export function getLineTree() {
  return request({
    url: '/lines/tree',
    method: 'get'
  })
}

export function getLeafLines() {
  return request({
    url: '/lines/leaf',
    method: 'get'
  })
}

export function getAllLines() {
  return request({
    url: '/lines',
    method: 'get'
  })
}

export function getLineById(id) {
  return request({
    url: `/lines/${id}`,
    method: 'get'
  })
}
