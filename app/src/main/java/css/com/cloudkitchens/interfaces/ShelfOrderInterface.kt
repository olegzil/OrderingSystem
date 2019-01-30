package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderDetail

interface ShelfOrderInterface {
    fun isExpired():Boolean
    fun timeStamp():Long
    fun getOrder():KitchenOrderDetail
    fun ageOrderBy(delta:Long)
    fun getDecayRate():Double
    fun setDecayRateMultiplier(multiplier:Double)
}