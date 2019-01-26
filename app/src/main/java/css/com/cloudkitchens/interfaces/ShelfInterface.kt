package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderDetail

interface ShelfInterface {
    fun getOrdersCount():Int
    fun getOldestOrder():KitchenOrderDetail?
    fun getNewestOrder():KitchenOrderDetail?
    fun removeOrder(id:String): KitchenOrderDetail?
    fun removeOrder() : List<KitchenOrderDetail>
    fun addOrder(orderDetail:KitchenOrderDetail)
    fun getOrderTemp():String
    fun ageOrder(delta:Long)
    fun setDecayRateMultiplier(multiplier:Double)
}