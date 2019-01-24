package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrder

interface ShelfeInterface {
    fun getOrdersCount():Int
    fun getOldestOrder():KitchenOrder
    fun getNewstOrder():KitchenOrder
    fun removeOrder()
    fun addOrder(item:KitchenOrder)
}