/**
 * 登录引导的「本次会话已跳过」标记。
 * 用户在弹窗里点「随便逛逛」后，本次会话内不再重复弹（sessionStorage，关标签页即失效）。
 */
const KEY = 'loginGate:dismissed'

export const isGateDismissed = () => sessionStorage.getItem(KEY) === '1'
export const dismissGate = () => sessionStorage.setItem(KEY, '1')
