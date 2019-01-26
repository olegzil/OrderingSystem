package css.com.cloudkitchens.shelves

import css.com.cloudkitchens.dataproviders.KitchenOrder
import css.com.cloudkitchens.interfaces.ShelfInterface
import css.com.cloudkitchens.interfaces.ShelfOrderInterface

abstract class BaseShelf : ShelfInterface {
    private class ShelfOrder(private var order: KitchenOrder) : ShelfOrderInterface {
        private var decayRateMultiplier=1.0
        var orderAge = 0L
        override fun getDecayRate() = decayRateMultiplier

        override fun setDecayRateMultiplier(multiplier:Double){
            decayRateMultiplier = multiplier
        }

        override fun ageOrderBy(delta: Long) {
            orderAge = Math.min(order.shelfLife.toLong(), orderAge+delta)
        }

        override fun isExpired(): Boolean {
            val age = (order.shelfLife - orderAge) - (order.decayRate*decayRateMultiplier*orderAge)
            return Math.max(0.0, age) == 0.0
        }

        override fun timeStamp(): Long {
            return order.timeStamp
        }

        override fun getOrder(): KitchenOrder {
            return order
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
    override fun getOldestOrder(): KitchenOrder? {
        if (orderList.isEmpty())
            return null
        var prevOrderAge = 0L
        var requestedOrder: KitchenOrder? = null
        orderList.forEach { order ->
            if (order.value.timeStamp() >= prevOrderAge) {
                requestedOrder = order.value.getOrder()
                prevOrderAge = order.value.timeStamp()
            }
        }
        return requestedOrder
    }

    override fun getNewestOrder(): KitchenOrder? {
        if (orderList.isEmpty())
            return null
        var prevOrderAge = Long.MAX_VALUE
        var requestedOrder: KitchenOrder? = null
        orderList.forEach { order ->
            if (order.value.timeStamp() < prevOrderAge) {
                requestedOrder = order.value.getOrder()
                prevOrderAge = order.value.timeStamp()
            }
        }
        return requestedOrder
    }

    @Synchronized
    override fun removeOrder(id: String): KitchenOrder? {
        if (orderList.isEmpty() || !orderList.containsKey(id))
            return null
        return orderList.remove(id)?.getOrder()
    }

    @Synchronized
    override fun addOrder(order: KitchenOrder) {
        orderList[order.id] = ShelfOrder(order)
    }

    @Synchronized
    override fun removeOrder(): List<KitchenOrder> {
        if (orderList.isEmpty())
            return listOf()
        val orders = mutableListOf<KitchenOrder>()
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