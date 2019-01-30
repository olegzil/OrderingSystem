package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderServerDetail
import kotlinx.coroutines.channels.Channel

interface KitchenOrderNotification {
    fun getOrderNotificationChannel() : Channel<KitchenOrderServerDetail>
    fun getOrderHeartbeat(): Channel<Long>
    fun getDriverNotification():Channel<String>
}