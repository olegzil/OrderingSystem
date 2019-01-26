package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrder

interface ShelfOrderInterface {
    fun isExpired():Boolean
    fun timeStamp():Long
    fun getOrder():KitchenOrder
}