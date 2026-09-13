import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 点检提交被“待复检通过挡块”拦截的业务错误码，明细交由调用方弹窗展示 */
export const BIZ_CODE_INSPECTION_PENDING_RECHECK = 4091
/** 班次点检打卡被“到期未校准/校准不合格工装”拦截的业务错误码，明细交由调用方弹窗展示 */
export const BIZ_CODE_GAUGE_CALIBRATION_BLOCKED = 4092

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    } else {
      const err = new Error(res.message || '请求失败')
      err.code = res.code
      // 保留结构化业务明细（如待复检挡块编号清单），供页面逐条展示
      err.detail = res.data
      // 带明细的业务拦截由调用方自行展示，拦截器不再只弹一句笼统失败
      if (res.code !== BIZ_CODE_INSPECTION_PENDING_RECHECK
        && res.code !== BIZ_CODE_GAUGE_CALIBRATION_BLOCKED) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(err)
    }
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
