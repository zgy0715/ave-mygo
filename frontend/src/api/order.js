import request from './request'

/** 订单接口（对应后端 OrderController，全部需登录） */
export const createOrder = (data) => request.post('/orders', data)
export const payOrder = (orderNo) => request.post(`/orders/${orderNo}/pay`)
export const cancelOrder = (orderNo) => request.patch(`/orders/${orderNo}/cancel`)
export const myOrders = () => request.get('/orders/my')
