package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderDetail
import css.com.cloudkitchens.dataproviders.KitchenOrderShelfStatus
import kotlinx.coroutines.channels.Channel

interface ShelfManagerInterface {
    fun getOrderArrivalChannel(): Channel<KitchenOrderShelfStatus>
    fun getOrderAgeUpdateChannel():Channel<List<KitchenOrderDetail>>
    fun cleanup()
}