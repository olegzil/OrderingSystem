package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrder

interface ShelfInterface {
    fun getOrdersCount():Int
    fun getOldestOrder():KitchenOrder?
    fun getNewestOrder():KitchenOrder?
    fun removeOrder(id:String): KitchenOrder?
    fun removeOrder() : List<KitchenOrder>
    fun addOrder(order:KitchenOrder)
    fun getOrderTemp():String
    fun ageOrder(delta:Long)
    fun setDecayRateMultiplier(multiplier:Double)
}