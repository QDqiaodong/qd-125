<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="28" color="#409EFF"><Goods /></el-icon>
        <h1>输送设备缓冲挡块跨产线移交划转系统</h1>
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" class="app-aside">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#ffffffa6"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/">
            <el-icon><HomeFilled /></el-icon>
            <span>首页概览</span>
          </el-menu-item>
          <el-menu-item index="/lines">
            <el-icon><OfficeBuilding /></el-icon>
            <span>产线分组视图</span>
          </el-menu-item>
          <el-menu-item index="/blocks">
            <el-icon><Grid /></el-icon>
            <span>挡块档案管理</span>
          </el-menu-item>
          <el-menu-item index="/transfers">
            <el-icon><Switch /></el-icon>
            <span>移交台账管理</span>
          </el-menu-item>
          <el-menu-item index="/transfer-confirm">
            <el-icon><CircleCheck /></el-icon>
            <span>移交确认</span>
          </el-menu-item>
          <el-menu-item index="/stocktakes">
            <el-icon><DocumentChecked /></el-icon>
            <span>挡块盘点差异</span>
          </el-menu-item>
          <el-menu-item index="/borrow">
            <el-icon><Calendar /></el-icon>
            <span>挡块借用预约</span>
          </el-menu-item>
          <el-menu-item index="/handovers">
            <el-icon><SwitchButton /></el-icon>
            <span>班组交班</span>
          </el-menu-item>
          <el-menu-item index="/inspections">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>挡块班次点检</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.app-container {
  height: 100vh;
}
.app-header {
  background: linear-gradient(135deg, #1e3a8a 0%, #1e40af 100%);
  display: flex;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-left h1 {
  color: white;
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 1px;
}
.app-aside {
  background: #001529;
}
.app-aside :deep(.el-menu) {
  border-right: none;
}
.app-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
