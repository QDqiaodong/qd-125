import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/lines',
    name: 'ProductionLines',
    component: () => import('@/views/ProductionLines.vue')
  },
  {
    path: '/blocks',
    name: 'BufferBlocks',
    component: () => import('@/views/BufferBlocks.vue')
  },
  {
    path: '/transfers',
    name: 'BlockTransfers',
    component: () => import('@/views/BlockTransfers.vue')
  },
  {
    path: '/transfer-confirm',
    name: 'TransferConfirm',
    component: () => import('@/views/TransferConfirm.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
