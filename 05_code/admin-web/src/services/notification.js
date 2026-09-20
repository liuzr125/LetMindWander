import { reactive } from 'vue'

export const notification = reactive({ open: false, title: '', message: '', code: '', help: '' })

export function showError(error, fallback = '操作失败，请稍后重试') {
  const code = error?.code || ''
  notification.title = '操作未完成'
  notification.message = error?.message || fallback
  notification.code = code
  notification.help = code === 'CREDENTIAL_ENCRYPTION_NOT_CONFIGURED'
    ? '启动后端前设置环境变量 AI_CREDENTIAL_ENCRYPTION_KEY（至少 32 位随机字符串），然后重启后端并重新保存本页密钥。不要使用示例值。'
    : ''
  notification.open = true
}

export function closeNotification() {
  notification.open = false
}
