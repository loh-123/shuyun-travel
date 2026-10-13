import request from '@/api/request'

/**
 * 发送邮箱验证码
 *
 * @param email 邮箱
 * @returns 统一结果，data = {message}
 */
export function sendEmailCode(email: string) {
  return request.post('/auth/send-email-code', { email })
}

/**
 * 校验邮箱验证码
 *
 * @param email 邮箱
 * @param code  验证码
 * @returns 统一结果
 */
export function verifyEmailCode(email: string, code: string) {
  return request.post('/auth/verify-email-code', { email, code })
}
