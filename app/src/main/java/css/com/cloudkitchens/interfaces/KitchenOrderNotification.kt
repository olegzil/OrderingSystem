package css.com.cloudkitchens.interfaces

import css.com.cloudkitchens.dataproviders.KitchenOrderServerDetail
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

interface KitchenOrderNotification {
    fun getOrderNotificationChannel() : PublishSubject<KitchenOrderServerDetail>?
    fun getOrderHeartbeat(): Observable<Long>
}