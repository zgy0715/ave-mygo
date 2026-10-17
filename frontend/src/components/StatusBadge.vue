<script setup>
/** 状态徽标：演出/场次/订单状态 → 文案与配色，全站只此一处映射（浅色系） */
const props = defineProps({
  status: { type: String, required: true },
})

const MAP = {
  NOT_OPEN: { text: '即将开售', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  ONSALE: { text: '销售中', cls: 'bg-pink-50 text-pink-600 border-pink-200' },
  SOLDOUT: { text: '已售罄', cls: 'bg-gray-100 text-gray-500 border-gray-200' },
  ENDED: { text: '已结束', cls: 'bg-gray-100 text-gray-400 border-gray-200' },
  PENDING: { text: '待支付', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  PAID: { text: '已支付', cls: 'bg-green-50 text-green-700 border-green-200' },
  CANCELLED: { text: '已取消', cls: 'bg-gray-100 text-gray-500 border-gray-200' },
}
const info = MAP[props.status] || { text: props.status, cls: 'bg-gray-100 text-gray-500 border-gray-200' }
</script>

<template>
  <span class="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium" :class="info.cls">
    {{ info.text }}
  </span>
</template>
