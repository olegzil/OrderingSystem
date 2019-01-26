package css.com.cloudkitchens.managers

import css.com.cloudkitchens.constants.Constants
import css.com.cloudkitchens.constants.Constants.MAX_OVERFLOW_SHELF_CAPACITY
import css.com.cloudkitchens.dataproviders.KitchenOrder
import css.com.cloudkitchens.dataproviders.KitchenOrderShelfStatus
import css.com.cloudkitchens.interfaces.ShelfInterface
import css.com.cloudkitchens.services.FoodOrderService
import css.com.cloudkitchens.shelves.ShelfCold
import css.com.cloudkitchens.shelves.ShelfFrozen
import css.com.cloudkitchens.shelves.ShelfHot
import css.com.cloudkitchens.shelves.ShelfOverflow
import io.reactivex.Observable

class ShelfManager(private val service: FoodOrderService) {
    private enum class Shelves {
        SHELF_HOT,
        SHELF_COLD,
        SHELF_FROZEN,
        SHELF_OVERFLOW
    }

    private val shelves = mutableMapOf<Shelves, ShelfInterface>()

    init {
        shelves[Shelves.SHELF_HOT] = ShelfHot()
        shelves[Shelves.SHELF_COLD] = ShelfCold()
        shelves[Shelves.SHELF_FROZEN] = ShelfFrozen()
        shelves[Shelves.SHELF_OVERFLOW] = ShelfOverflow()
    }

    private fun processExcessOrder(predicateRemove: () -> List<KitchenOrder>?, shelfSelector: Shelves) {
        shelves[shelfSelector]?.getOrdersCount()?.let { count ->
            if (count < MAX_OVERFLOW_SHELF_CAPACITY) {
                predicateRemove()?.let { orderList ->
                    orderList.forEach { order ->
                        shelves[shelfSelector]?.addOrder(order)
                    }
                }
            } else {
                shelves[shelfSelector]?.removeOrder()
            }
        }
    }

    fun getKitchenOrdersStatus(): Observable<KitchenOrderShelfStatus>? {
        val subscription = service.getOrderNotificationChannel()?.run {
            doOnNext { order ->
                when (order.order.temp) {
                    "hot" -> {
                        shelves[Shelves.SHELF_HOT]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_HOT_SHELF_CAPACITY) {
                                processExcessOrder({ shelves[Shelves.SHELF_HOT]?.removeOrder() }, Shelves.SHELF_HOT)
                            } else
                                shelves[Shelves.SHELF_HOT]?.addOrder(order.order)
                        }
                    }
                    "cold" -> {
                        shelves[Shelves.SHELF_COLD]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_HOT_SHELF_CAPACITY) {
                                processExcessOrder({ shelves[Shelves.SHELF_COLD]?.removeOrder() }, Shelves.SHELF_COLD)
                            } else
                                shelves[Shelves.SHELF_COLD]?.addOrder(order.order)
                        }
                    }
                    "frozen" -> {
                        shelves[Shelves.SHELF_FROZEN]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_FROZEN_SHELF_CAPCITY) {
                                processExcessOrder(
                                    { shelves[Shelves.SHELF_FROZEN]?.removeOrder() },
                                    Shelves.SHELF_FROZEN
                                )
                            } else
                                shelves[Shelves.SHELF_FROZEN]?.addOrder(order.order)
                        }
                    }
                }
            }
            .map {
                val retVal = mutableListOf<Pair<String, Int>>()
                shelves.forEach { order ->
                    retVal.add(Pair(order.value.getOrderTemp(), order.value.getOrdersCount()))
                }
                KitchenOrderShelfStatus(retVal)
            }
        }
        return subscription
    }
}
