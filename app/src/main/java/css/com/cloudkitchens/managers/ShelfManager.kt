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
import css.com.cloudkitchens.services.FoodOrderService
import css.com.cloudkitchens.shelves.ShelfCold
import css.com.cloudkitchens.shelves.ShelfFrozen
import css.com.cloudkitchens.shelves.ShelfHot
import css.com.cloudkitchens.shelves.ShelfOverflow
import css.com.cloudkitchens.utilities.printLog
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

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

    private fun processExcessOrder(predicateRemove: () -> List<KitchenOrderDetail>?, shelfSelector: Shelves) {
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

    private fun expiredOrderRemoveHelper(order: KitchenOrderDetail) {
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            if (getOrdersCount() == MAX_OVERFLOW_SHELF_CAPACITY)
                return
        }
        shelves[Shelves.SHELF_OVERFLOW]?.addOrder(order)
    }

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

    @Synchronized
    private fun moveOrdersFromOverflow() {
        val ordersToRemove = mutableListOf<KitchenOrderDetail>()
        shelves[Shelves.SHELF_OVERFLOW]?.run {
            val count = getOrdersCount()
            for (i in count downTo 0) {
                val order = getNewestOrder()
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

    fun initiateDeliveries(): Disposable {
        return service.getDriverNotification()
            .subscribeWith(object : DisposableObserver<String>() {
                override fun onComplete() {
                    //Do nothing
                }

                override fun onNext(selector: String) {
                    when (selector) {
                        "hot" -> {
                            shelves[Shelves.SHELF_HOT]?.run {
                                removeExpiredOrderFromSingleShelf { removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id) }
                            }
                        }
                        "cold" -> {
                            shelves[Shelves.SHELF_COLD]?.run {
                                removeExpiredOrderFromSingleShelf { removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id) }
                            }
                        }
                        "frozen" -> {
                            shelves[Shelves.SHELF_FROZEN]?.run {
                                removeExpiredOrderFromSingleShelf { removeOrder(getKitchenOrderDetailList().sortedByDescending { it.timeStamp }[0].id) }
                            }
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    printLog(e.toString())
                }

            })
    }

    fun initiateOrderAging(): Disposable {
        val start = System.currentTimeMillis()
        return service.getOrderHeartbeat()
            .subscribeOn(Schedulers.io())
            .subscribeWith(object : DisposableObserver<Long>() {
                override fun onComplete() {
                    // Do nothing
                }

                override fun onNext(delta: Long) {
                    shelves[Shelves.SHELF_HOT]?.ageOrder(AGING_DELTAL)
                    shelves[Shelves.SHELF_COLD]?.ageOrder(AGING_DELTAL)
                    shelves[Shelves.SHELF_FROZEN]?.ageOrder(AGING_DELTAL)
                    shelves[Shelves.SHELF_OVERFLOW]?.ageOrder(AGING_DELTAL)
                    removeExpiredOrders()
                    moveOrdersFromOverflow()
                    printLog("order hart beat ${System.currentTimeMillis() - start}")
                }

                override fun onError(e: Throwable) {
                    printLog(e.toString())
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

    fun getKitchenOrdersStatus(): Observable<KitchenOrderShelfStatus>? {
        return service.getOrderNotificationChannel()
            ?.run<PublishSubject<KitchenOrderServerDetail>, Observable<KitchenOrderShelfStatus>?> {
                flatMap { serverOrder ->
                    var taggedOrder = Shelves.SHELF_HOT
                    val orderSelector = serverOrder.temp
                    when (orderSelector) {
                        "hot" -> {
                            taggedOrder = Shelves.SHELF_HOT
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
                            }
                        }
                        "cold" -> {
                            taggedOrder = Shelves.SHELF_COLD
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
                        }
                        "frozen" -> {
                            taggedOrder = Shelves.SHELF_FROZEN
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
                        }
                    }
                    Observable.just(taggedOrder)
                }
                    .map { orderSelector ->
                        var kitchenOrderStatus: KitchenOrderShelfStatus? = null
                        when (orderSelector) {
                            Shelves.SHELF_HOT -> {
                                shelves[Shelves.SHELF_HOT]?.let { theShelf ->
                                    val ordersDetail = copyShelfOrders(theShelf)
                                    kitchenOrderStatus =
                                        KitchenOrderShelfStatus("hot", ordersDetail.first, ordersDetail.second)
                                }
                            }
                            Shelves.SHELF_COLD -> {
                                shelves[Shelves.SHELF_COLD]?.let { theShelf ->
                                    val ordersDetail = copyShelfOrders(theShelf)
                                    kitchenOrderStatus =
                                        KitchenOrderShelfStatus("cold", ordersDetail.first, ordersDetail.second)
                                }
                            }
                            Shelves.SHELF_FROZEN -> {
                                shelves[Shelves.SHELF_FROZEN]?.let { theShelf ->
                                    val ordersDetail = copyShelfOrders(theShelf)
                                    kitchenOrderStatus =
                                        KitchenOrderShelfStatus("frozen", ordersDetail.first, ordersDetail.second)
                                }
                            }
                            Shelves.SHELF_OVERFLOW -> {
                                Observable.empty<KitchenOrderShelfStatus>()
                            } //should never reach here.
                        }
                        kitchenOrderStatus
                    }
            }
    }
}
