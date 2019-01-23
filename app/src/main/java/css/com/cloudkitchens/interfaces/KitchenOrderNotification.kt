package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrder
import io.reactivex.subjects.PublishSubject

interface KitchenOrderNotification {
    fun getOrderNotificationChannel() : PublishSubject<KitchenOrder>?
}