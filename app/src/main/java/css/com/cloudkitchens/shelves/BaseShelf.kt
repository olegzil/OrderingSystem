package css.com.cloudkitchens.shelves

import css.com.cloudkitchens.dataproviders.KitchenOrderDetail
import css.com.cloudkitchens.interfaces.ShelfInterface
import css.com.cloudkitchens.interfaces.ShelfOrderInterface

abstract class BaseShelf : ShelfInterface {
    private class ShelfOrder(private var orderDetail: KitchenOrderDetail) : ShelfOrderInterface {
        private var decayRateMultiplier=1.0
        var orderAge = 0L
        override fun getDecayRate() = decayRateMultiplier

        override fun setDecayRateMultiplier(multiplier:Double){
            decayRateMultiplier = multiplier
        }

        override fun ageOrderBy(delta: Long) {
            orderAge = Math.min(orderDetail.shelfLife.toLong(), orderAge+delta)
        }

        override fun isExpired(): Boolean {
            val age = (orderDetail.shelfLife - orderAge) - (orderDetail.decayRate*decayRateMultiplier*orderAge)
            return Math.max(0.0, age) == 0.0
        }

        override fun timeStamp(): Long {
            return orderDetail.timeStamp
        }

        override fun getOrder(): KitchenOrderDetail {
            return orderDetail
        }
    }

    private val orderList = mutableMapOf<String, ShelfOrder>()

    @Synchronized
    override fun ageOrder(delta: Long) {
        orderList.forEach{order->
            order.value.ageOrderBy(delta)
        }
    }

    override fun getOrdersCount() = orderList.size
    override fun getOldestOrder(): KitchenOrderDetail? {
        if (orderList.isEmpty())
            return null
        var prevOrderAge = 0L
        var requestedOrderDetail: KitchenOrderDetail? = null
        orderList.forEach { order ->
            if (order.value.timeStamp() >= prevOrderAge) {
                requestedOrderDetail = order.value.getOrder()
                prevOrderAge = order.value.timeStamp()
            }
        }
        return requestedOrderDetail
    }

    override fun getNewestOrder(): KitchenOrderDetail? {
        if (orderList.isEmpty())
            return null
        var prevOrderAge = Long.MAX_VALUE
        var requestedOrderDetail: KitchenOrderDetail? = null
        orderList.forEach { order ->
            if (order.value.timeStamp() < prevOrderAge) {
                requestedOrderDetail = order.value.getOrder()
                prevOrderAge = order.value.timeStamp()
            }
        }
        return requestedOrderDetail
    }

    @Synchronized
    override fun removeOrder(id: String): KitchenOrderDetail? {
        if (orderList.isEmpty() || !orderList.containsKey(id))
            return null
        return orderList.remove(id)?.getOrder()
    }

    @Synchronized
    override fun addOrder(orderDetail: KitchenOrderDetail) {
        orderList[orderDetail.id] = ShelfOrder(orderDetail)
    }

    @Synchronized
    override fun removeOrder(): List<KitchenOrderDetail> {
        if (orderList.isEmpty())
            return listOf()
        val orders = mutableListOf<KitchenOrderDetail>()
        orderList.forEach { order ->
            if (order.value.isExpired()) {
                orders.add(order.value.getOrder())
            }
        }
        orders.forEach{
            orderList.remove(it.id)
        }
        return orders
    }

    override fun setDecayRateMultiplier(multiplier: Double) {
        orderList.forEach {
            it.value.setDecayRateMultiplier(multiplier)
        }
    }
}