package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderDetail

interface RecyclerViewAdapterInterface {
    fun addOrder(order: KitchenOrderDetail)
    fun removeOrder(order: KitchenOrderDetail)
    fun update(newItems: List<KitchenOrderDetail>)
}