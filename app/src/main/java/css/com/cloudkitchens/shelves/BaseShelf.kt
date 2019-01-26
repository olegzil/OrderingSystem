package css.com.cloudkitchens.shelves

import css.com.cloudkitchens.dataproviders.KitchenOrder
import css.com.cloudkitchens.interfaces.ShelfInterface
import css.com.cloudkitchens.interfaces.ShelfOrderInterface

abstract class BaseShelf : ShelfInterface {
    private class ShelfOrder(private var order: KitchenOrder) : ShelfOrderInterface {
        override fun isExpired(): Boolean {
            return false
        }

        override fun timeStamp(): Long {
            return order.timeStamp
        }

        override fun getOrder(): KitchenOrder {
            return order
        }
    }

    private val orderList = mutableMapOf<String, ShelfOrder>()

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

    override fun getNewstOrder(): KitchenOrder? {
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

    override fun removeOrder(id: String): KitchenOrder? {
        if (orderList.isEmpty() || !orderList.containsKey(id))
            return null
        return orderList.remove(id)?.getOrder()
    }

    override fun addOrder(order: KitchenOrder) {
        orderList[order.id] = ShelfOrder(order)
    }

    override fun removeOrder(): List<KitchenOrder> {
        if (orderList.isEmpty())
            return listOf()
        val orders = mutableListOf<KitchenOrder>()
        orderList.forEach { order ->
            if (order.value.isExpired()) {
                orders.add(order.value.getOrder())
            }
        }
        return orders
    }
}