import request from '@/utils/request'

export function getAllBlocks() {
  return request({
    url: '/blocks',
    method: 'get'
  })
}

export function getBlockById(id) {
  return request({
    url: `/blocks/${id}`,
    method: 'get'
  })
}

export function createBlock(data) {
  return request({
    url: '/blocks',
    method: 'post',
    data
  })
}

export function updateBlock(id, data) {
  return request({
    url: `/blocks/${id}`,
    method: 'put',
    data
  })
}

export function getSpecTemplates() {
  return request({
    url: '/blocks/spec-templates',
    method: 'get'
  })
}

export function getBlocksBySpecTemplate(template) {
  return request({
    url: `/blocks/spec-template/${template}`,
    method: 'get'
  })
}

export function getBlockBindings(id) {
  return request({
    url: `/blocks/${id}/bindings`,
    method: 'get'
  })
}

export function getBlocksByLineIds(lineIds) {
  return request({
    url: '/blocks/by-line-ids',
    method: 'post',
    data: lineIds
  })
}
