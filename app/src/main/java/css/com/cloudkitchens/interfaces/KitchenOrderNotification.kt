package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderMetadata
import io.reactivex.subjects.PublishSubject

interface KitchenOrderNotification {
    fun getOrderNotificationChannel() : PublishSubject<KitchenOrderMetadata>?
}