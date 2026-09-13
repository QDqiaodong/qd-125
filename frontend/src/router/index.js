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
  },
  {
    path: '/stocktakes',
    name: 'Stocktakes',
    component: () => import('@/views/Stocktakes.vue')
  },
  {
    path: '/borrow',
    name: 'BorrowReservations',
    component: () => import('@/views/BorrowReservations.vue')
  },
  {
    path: '/handovers',
    name: 'ShiftHandovers',
    component: () => import('@/views/ShiftHandovers.vue')
  },
  {
    path: '/inspections',
    name: 'BlockInspections',
    component: () => import('@/views/BlockInspections.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
