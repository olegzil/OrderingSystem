package css.com.cloudkitchens.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import css.com.cloudkitchens.dataproviders.KitchenOrder
import css.com.cloudkitchens.interfaces.KitchenOrderNotification
import css.com.cloudkitchens.utilities.printLog
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Job
import org.json.JSONArray
import java.io.InputStream
import java.lang.Math.random
import java.lang.Thread.sleep
import java.nio.charset.Charset
import java.util.concurrent.ThreadLocalRandom

/**
 * Service emulates a server. The service reads JSON data from a resource file in this APK, parser the the data and
 * provides client access to the data
 * */
class FoodOrderService : Service(), KitchenOrderNotification {
    private var kitchenOrders: JSONArray? = null
    private val binder = OrderSourceBinder()
    private var orderNotification: PublishSubject<KitchenOrder>? = null
    private var continueRunning = true
    private var request:Job? = null
    init {
        if (kitchenOrders == null)
            kitchenOrders = readKitchenOrders("orders.json")
        if (orderNotification == null)
            orderNotification = PublishSubject.create()
        OrderThread{
            orderGenerator()
        }.start()
    }
    internal class OrderThread( private var predicate:()->Unit) : Thread() {
        override fun run(){
            predicate()
        }
    }
    inner class OrderSourceBinder : Binder() {
        fun getService() : FoodOrderService{
            return this@FoodOrderService
        }
    }
    /**
     * This method reads a file provided by [path]. If an exception is thrown
     * it is consumed and null is returned.
     * The return value is null or a [JSONArray]
     * */
    private fun readKitchenOrders(path: String): JSONArray? {
        val buffer: ByteArray?
        var retVal: JSONArray? = null
        val `is`: InputStream
        try {
            `is` = assets.open(path)
            val size = `is`.available()
            buffer = ByteArray(size)
            `is`.read(buffer)
            `is`.close()
            val str = String(buffer, Charset.defaultCharset())
            retVal = JSONArray(str)
        } finally {
            return retVal
        }
    }

    /**
     * This method returns a time value using a Poisson distribution given a lambda of [rateParameter], which is defaulted to 3.5
     * The time value is in seconds and it is used to determine when to publish the next order to the kitchen
     *
     * */
    private fun timeOfNextOrder(rateParameter: Float = 3.25f): Double {
        return -Math.log(1.0f - random() / (Int.MAX_VALUE.toDouble() + 1.0)) / rateParameter
    }

    /**
    * This method returns an [Observable] that emits a random kitchen order based on a Poisson distribution with a specified lambda.
     * The distribution value determines the rate of emision
    * */
    private fun getKitchenOrder() : KitchenOrder? {
        kitchenOrders?.let { orderArray ->
            val index = ThreadLocalRandom.current().nextInt(0, orderArray.length())
            val retVal = orderArray.getJSONObject(index)
            return KitchenOrder(
                retVal.getString("name"),
                retVal.getString("temp"),
                retVal.getInt("shelfLife"),
                retVal.getDouble("decayRate")
            )
        }
    }

    override fun onRebind(intent: Intent?) {
        continueRunning = true
        printLog("from Service.onBind")
        OrderThread{
            orderGenerator()
        }.start()
        super.onRebind(intent)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kitchenOrders = readKitchenOrders("orders.json")
        return Service.START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        continueRunning = false
        request?.cancel()
        stopSelf()
        return super.onUnbind(intent)
    }
    override fun onBind(intent: Intent?): IBinder? {
        continueRunning = true
        printLog("from Service.onBind")
        kitchenOrders = readKitchenOrders("orders.json")
        OrderThread{
            orderGenerator()
        }.start()
        return binder
    }
    override fun getOrderNotificationChannel() = orderNotification

    /**
     * This method generates random kitchen orders.
     * The coroutine is interruptable. If the [continueRunning] flag is set to false the while loop will terminate
     * A publish/subscriber notification method is used to send out notifications of type KitchenOrder
     */
    private fun orderGenerator() {
        while (continueRunning) {
            val delayTime = timeOfNextOrder()
            printLog("delay time: $delayTime")
            val order = getKitchenOrder()
            order?.let {
                orderNotification?.onNext(it)
            }
            sleep(3000)
        }
    }
}