package css.com.cloudkitchens.managers

import css.com.cloudkitchens.constants.Constants
import css.com.cloudkitchens.constants.Constants.AGING_DELTAL
import css.com.cloudkitchens.constants.Constants.MAX_COLD_SHELF_CAPCITY
import css.com.cloudkitchens.constants.Constants.MAX_FROZEN_SHELF_CAPCITY
import css.com.cloudkitchens.constants.Constants.MAX_HOT_SHELF_CAPACITY
import css.com.cloudkitchens.constants.Constants.MAX_OVERFLOW_SHELF_CAPACITY
import css.com.cloudkitchens.dataproviders.KitchenOrderDetail
import css.com.cloudkitchens.dataproviders.KitchenOrderServerDetail
import css.com.cloudkitchens.dataproviders.KitchenOrderShelfStatus
import css.com.cloudkitchens.interfaces.ShelfInterface
import css.com.cloudkitchens.interfaces.ShelfManagerInterface
import css.com.cloudkitchens.services.FoodOrderService
import css.com.cloudkitchens.shelves.ShelfCold
import css.com.cloudkitchens.shelves.ShelfFrozen
import css.com.cloudkitchens.shelves.ShelfHot
import css.com.cloudkitchens.shelves.ShelfOverflow
import css.com.cloudkitchens.utilities.printLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * This class is responsible for managing the various shelves and driver pickup.
 */
class ShelfManager(private val service: FoodOrderService) : ShelfManagerInterface {
    private enum class Shelves {
        SHELF_HOT,
        SHELF_COLD,
        SHELF_FROZEN,
        SHELF_OVERFLOW
    }

    private val shelves = mutableMapOf<Shelves, ShelfInterface>()
    private val jobsList = mutableListOf<Job>()
    private val shelfStatusChannel = Channel<KitchenOrderShelfStatus>(Channel.UNLIMITED)

    //Initialize shelves
    init {
        shelves[Shelves.SHELF_HOT] = ShelfHot()
        shelves[Shelves.SHELF_COLD] = ShelfCold()
        shelves[Shelves.SHELF_FROZEN] = ShelfFrozen()
        shelves[Shelves.SHELF_OVERFLOW] = ShelfOverflow()
        initiateDeliveries()
        kitchenOrderDispatcher()
        initiateOrderAging()
    }

    override fun getOrderArrivalChannel() = shelfStatusChannel
    override fun cleanup() {
        jobsList.forEach { it.cancel() }
    }

    /**
     * a helper method to remove expired orders. The [order] parameter is added to the overflow shelf if it below capacity
     */
    private fun expiredOrderRemoveHelper(order: KitchenOrderDetail) {
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            if (getOrdersCount() == MAX_OVERFLOW_SHELF_CAPACITY)
                return
        }
        shelves[Shelves.SHELF_OVERFLOW]?.addOrder(order)
    }

    /**
     * a helper method that removes lists of expired orders. The parameter [orderDetails] contains
     * a list of orders to be removed. If the [Shelves.SHELF_OVERFLOW] shelf is up to capacity, the method returns. Otherwise,
     * the target list is sorted using the order time stamp as key. The oldes orders are removed first. The removed orders are
     * added to the overflow shelve or destroyed, depending on the overflow shelf capacity
     */
    private fun expiredOrdersRemoveHelper(orderDetails: List<KitchenOrderDetail>) {
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            if (getOrdersCount() == MAX_OVERFLOW_SHELF_CAPACITY)
                return
        }

        orderDetails.sortedByDescending {
            it.timeStamp
        }
        orderDetails.forEach { order ->
            shelves[Shelves.SHELF_OVERFLOW]?.addOrder(order)
            shelves[Shelves.SHELF_OVERFLOW]?.run {
                if (getOrdersCount() == MAX_OVERFLOW_SHELF_CAPACITY)
                    return@forEach
            }
        }
    }

    /**
     * Same as expiredOrdersRemoveHelper, but for a single order
     */
    @Synchronized
    private fun removeExpiredOrdersFromSingleShelf(predicateRemove: () -> List<KitchenOrderDetail>?) {
        predicateRemove()?.let { orderList ->
            shelves[Shelves.SHELF_OVERFLOW]?.run {
                expiredOrdersRemoveHelper(orderList)
            }
        }
    }

    @Synchronized
    private fun removeExpiredOrderFromSingleShelf(predicateRemove: () -> KitchenOrderDetail?) {
        predicateRemove()?.let { orderList ->
            shelves[Shelves.SHELF_OVERFLOW]?.run {
                expiredOrderRemoveHelper(orderList)
            }
        }
    }

    /**
     * This is the root method that triggers shelf cleanup. Expired orders are collected into lists.
     * If the overflow shelf is below capacity, orders are moved from the removal lists to the overflow shelf.
     */
    @Synchronized
    private fun removeExpiredOrders() {
        val hotOrders = shelves[Shelves.SHELF_HOT]?.removeOrder()
        val coldOrders = shelves[Shelves.SHELF_COLD]?.removeOrder()
        val frozenOrders = shelves[Shelves.SHELF_FROZEN]?.removeOrder()
        shelves[Shelves.SHELF_OVERFLOW]?.removeOrder() //Once removed, overflow orders are considered waste
        //If there is space on the overflow shelf, move the expired orders there
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            if (getOrdersCount() < MAX_OVERFLOW_SHELF_CAPACITY) {
                hotOrders?.let { expiredOrdersRemoveHelper(hotOrders) }          //now move any expired orders to the overflow shelf, if possible.
                coldOrders?.let { expiredOrdersRemoveHelper(coldOrders) }
                frozenOrders?.let { expiredOrdersRemoveHelper(frozenOrders) }
            }
        }
    }

    /**
     * This method will move orders from the overflow shelf to an appropriate shelf (hot, cold, etc)
     * The oldest orders are moved first
     */
    @Synchronized
    private fun moveOrdersFromOverflow() {
        val ordersToRemove = mutableListOf<KitchenOrderDetail>()
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            val count = getOrdersCount()
            for (i in count downTo 0) {
                val order = getOldestOrder()
                order?.run {
                    when (temp) {
                        "hot" -> {
                            shelves[Shelves.SHELF_HOT]?.run {
                                if (getOrdersCount() < MAX_HOT_SHELF_CAPACITY) {
                                    addOrder(order)
                                    ordersToRemove.add(order)
                                }
                            }
                        }
                        "cold" -> {
                            shelves[Shelves.SHELF_COLD]?.run {
                                if (getOrdersCount() < MAX_COLD_SHELF_CAPCITY) {
                                    addOrder(order)
                                    ordersToRemove.add(order)
                                }
                            }
                        }
                        "frozen" -> {
                            shelves[Shelves.SHELF_FROZEN]?.run {
                                if (getOrdersCount() < MAX_FROZEN_SHELF_CAPCITY) {
                                    addOrder(order)
                                    ordersToRemove.add(order)
                                }
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        }
        // clean up.
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            ordersToRemove.forEach {
                removeOrder(it.id)
            }
        }
    }

    private fun shelfUpdateSelector(selector: String) {
        when (selector) {
            "hot" -> {
                shelves[Shelves.SHELF_HOT]?.run {
                    if (getKitchenOrderDetailList().isNotEmpty())
                        removeExpiredOrderFromSingleShelf {
                            removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id)
                        }
                }
            }
            "cold" -> {
                shelves[Shelves.SHELF_COLD]?.run {
                    if (getKitchenOrderDetailList().isNotEmpty())
                        removeExpiredOrderFromSingleShelf { removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id) }
                }
            }
            "frozen" -> {
                shelves[Shelves.SHELF_FROZEN]?.run {
                    if (getKitchenOrderDetailList().isNotEmpty())
                        removeExpiredOrderFromSingleShelf { removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id) }
                }
            }
        }
    }

    /**
     * This method initiates the process of listening for driver ariaval. Driver arival is random along with the orders the driver will pickup
     * Each arival also results in an order being removed from the shelf
     */
    fun initiateDeliveries() {
        jobsList.add(GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                shelfUpdateSelector(service.getDriverNotification().receive())
            }
        })
    }

    /**
     * This method is executed in one second intervals. It advances the order age. After advancing the order age
     * it interogates each shelf and removes any expired orders.
     */
    private fun initiateOrderAging() {
        val start = System.currentTimeMillis()
        jobsList.add(GlobalScope.launch {
            while(isActive) {
                val timeDelay = service.getOrderHeartbeat().receive()
                delay(timeDelay)
                shelves[Shelves.SHELF_HOT]?.ageOrder(AGING_DELTAL)
                shelves[Shelves.SHELF_COLD]?.ageOrder(AGING_DELTAL)
                shelves[Shelves.SHELF_FROZEN]?.ageOrder(AGING_DELTAL)
                shelves[Shelves.SHELF_OVERFLOW]?.ageOrder(AGING_DELTAL)
                removeExpiredOrders()
                moveOrdersFromOverflow()
                printLog("order hart beat ${System.currentTimeMillis() - start} time delay = $timeDelay")
            }
        })
    }

    private fun copyShelfOrders(targetShelf: ShelfInterface?): Pair<List<KitchenOrderDetail>, List<KitchenOrderDetail>> {
        val orders = mutableListOf<KitchenOrderDetail>()
        targetShelf?.let {
            orders.addAll(it.getKitchenOrderDetailList().toMutableList()) //make a copy of the data so that we're thread-friendly
        }
        val overflowOrders = mutableListOf<KitchenOrderDetail>()
        shelves[Shelves.SHELF_OVERFLOW]?.let {
            overflowOrders.addAll(it.getKitchenOrderDetailList().toMutableList()) //same here
        }
        return Pair(orders, overflowOrders)
    }

    private fun generateKitchenOrder(selector: Shelves, orderTemp: String): KitchenOrderShelfStatus? {
        return shelves[selector]?.let { theShelf ->
            val ordersDetail = copyShelfOrders(theShelf)
            KitchenOrderShelfStatus(orderTemp, ordersDetail.first, ordersDetail.second)
        }
    }

    /**
     * This is the method that is called from the main loop (U.I. loop) to monitor order arival. Each time an order arives it is added
     *  to the appopriate shelf. The orders arive from the server as [KitchenOrderServerDetail] orders. These are then flatMaped into
     *  a [KitchenOrderShelfStatus] class that is consumed by the U.I.
     */
    private fun kitchenOrderDispatcher() {
        jobsList.add(GlobalScope.launch {
            while (isActive) {
                val serverOrder = service.getOrderNotificationChannel().receive()
                when (serverOrder.temp) {
                    "hot" -> {
                        shelves[Shelves.SHELF_HOT]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_HOT_SHELF_CAPACITY) {
                                removeExpiredOrdersFromSingleShelf(
                                    { shelves[Shelves.SHELF_HOT]?.removeOrder() }
                                )
                            } else {
                                shelves[Shelves.SHELF_HOT]?.addOrder(
                                    KitchenOrderDetail(
                                        serverOrder.id,
                                        serverOrder.name,
                                        serverOrder.temp,
                                        serverOrder.shelfLife,
                                        serverOrder.decayRate,
                                        serverOrder.timeStamp,
                                        1.0,
                                        0.0,
                                        serverOrder.shelfLife.toLong()
                                    )
                                )
                            }
                            generateKitchenOrder(Shelves.SHELF_HOT, "hot")?.let { order ->
                                shelfStatusChannel.offer(order)
                            }
                        }
                    }
                    "cold" -> {
                        shelves[Shelves.SHELF_COLD]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_COLD_SHELF_CAPCITY) {
                                removeExpiredOrdersFromSingleShelf(
                                    { shelves[Shelves.SHELF_COLD]?.removeOrder() }
                                )
                            } else
                                shelves[Shelves.SHELF_COLD]?.addOrder(
                                    KitchenOrderDetail(
                                        serverOrder.id,
                                        serverOrder.name,
                                        serverOrder.temp,
                                        serverOrder.shelfLife,
                                        serverOrder.decayRate,
                                        serverOrder.timeStamp,
                                        1.0,
                                        0.0,
                                        serverOrder.shelfLife.toLong()
                                    )
                                )
                        }
                        generateKitchenOrder(Shelves.SHELF_COLD, "cold")?.let { order ->
                            shelfStatusChannel.offer(order)
                        }
                    }
                    "frozen" -> {
                        shelves[Shelves.SHELF_FROZEN]?.let { shelf ->
                            if (shelf.getOrdersCount() >= Constants.MAX_FROZEN_SHELF_CAPCITY) {
                                removeExpiredOrdersFromSingleShelf(
                                    { shelves[Shelves.SHELF_FROZEN]?.removeOrder() }
                                )
                            } else
                                shelves[Shelves.SHELF_FROZEN]?.addOrder(
                                    KitchenOrderDetail(
                                        serverOrder.id,
                                        serverOrder.name,
                                        serverOrder.temp,
                                        serverOrder.shelfLife,
                                        serverOrder.decayRate,
                                        serverOrder.timeStamp,
                                        1.0,
                                        0.0,
                                        serverOrder.shelfLife.toLong()
                                    )
                                )
                        }
                        generateKitchenOrder(Shelves.SHELF_FROZEN, "frozen")?.let { order ->
                            shelfStatusChannel.offer(order)
                        }
                    }
                }
            }
        })
    }
}
