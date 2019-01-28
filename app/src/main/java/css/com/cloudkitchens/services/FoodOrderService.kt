package css.com.cloudkitchens.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import css.com.cloudkitchens.constants.Constants.AGING_HEART_BEAT
import css.com.cloudkitchens.constants.Constants.POISSON_LAMBDA
import css.com.cloudkitchens.dataproviders.KitchenOrderServerDetail
import css.com.cloudkitchens.interfaces.KitchenOrderNotification
import css.com.cloudkitchens.utilities.printLog
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.InputStream
import java.lang.Math.random
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * Service emulates a server. The service reads JSON data from a resource file in this APK, parser the the data and
 * provides client access to the data
 * */
class FoodOrderService : Service(), KitchenOrderNotification {
    private var kitchenOrders: JSONArray? = null
    private val binder = OrderSourceBinder()
    private var orderNotification: PublishSubject<KitchenOrderServerDetail>? = null
    private var driverArivalNotification = PublishSubject.create<String>()
    private var continueRunning = true
    private var request: Job? = null
    private var debugTime = 0L
    private var sampleCount = 1.0
    private var accumulatedTime = 0.0
    private lateinit var coroutineScopeCancelation: Job

    init {
        if (kitchenOrders == null)
            kitchenOrders = readKitchenOrders("orders.json")
        if (orderNotification == null)
            orderNotification = PublishSubject.create()
        coroutineRestart()
    }

    inner class OrderSourceBinder : Binder() {
        fun getService(): FoodOrderService {
            return this@FoodOrderService
        }
    }

    private fun coroutineRestart() {
        var async1Result: Deferred<Unit>? = null
        var async2Result: Deferred<Unit>? = null
        var driverLoop = true
        var orderLoop = true
        coroutineScopeCancelation = GlobalScope.launch {
            while (isActive) {

                if (driverLoop) {
                    driverLoop = false
                    async1Result = async {
                        val timeToDelayDriver = driverGenerator()
                        printLog("next driver ariving in ${timeToDelayDriver / 1000} seconds ")
                        delay(timeToDelayDriver)
                        driverLoop = true
                    }
                }
                if (orderLoop) {
                    orderLoop = false
                    async2Result = async {
                        val timeToDelayOrder = orderGenerator()
                        delay(timeToDelayOrder)
                        orderLoop = true
                    }
                }
            }
            async1Result?.cancel()
            async2Result?.cancel()
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
     * This method returns a time value using a Poisson distribution given a lambda of [rateParameter], which is defaulted to 3.25
     * Time unit is assumed to be one second
     * */
    private fun sampleTime(rateParameter: Double = 3.25): Double {
        val adjustedRate = 1.0 / rateParameter
        return (-Math.log(1.0f - random()) / adjustedRate)
    }

    /**
     * This method returns an [Observable] that emits a random kitchen orderDetail based on a Poisson distribution with a specified lambda.
     * The distribution value determines the rate of emision
     * */
    private fun getKitchenOrder(): KitchenOrderServerDetail? {
        kitchenOrders?.let { orderArray ->
            val index = ThreadLocalRandom.current().nextInt(0, orderArray.length())
            val retVal = orderArray.getJSONObject(index)
            return KitchenOrderServerDetail(
                UUID.randomUUID().toString(),
                retVal.getString("name"),
                retVal.getString("temp"),
                retVal.getInt("shelfLife"),
                retVal.getDouble("decayRate"),
                System.currentTimeMillis()
            )
        }
        return null
    }

    override fun onRebind(intent: Intent?) {
        continueRunning = true
        printLog("from Service.onBind")
        coroutineScopeCancelation.cancel()
        coroutineRestart()
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kitchenOrders = readKitchenOrders("orders.json")
        return Service.START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        continueRunning = false
        coroutineScopeCancelation.cancel()
        request?.cancel()
        stopSelf()
        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        continueRunning = true
        printLog("from Service.onBind")
        kitchenOrders = readKitchenOrders("orders.json")
        coroutineScopeCancelation.cancel()
        coroutineRestart()
        return binder
    }

    override fun getOrderNotificationChannel() = orderNotification
    override fun getDriverNotification() = driverArivalNotification

    override fun getOrderHeartbeat(): Observable<Long> {
        return Observable.interval(AGING_HEART_BEAT, TimeUnit.MILLISECONDS)
    }

    /**
     * Generate a random value between 2 and 8. Sleep for that many milliseconds
     * Once awaken, notify the listener of a random order pickup
     */
    private fun driverGenerator(): Long {
        debugTime = System.currentTimeMillis()
        val timeOfDirverAriaval = ThreadLocalRandom.current().nextInt(2, 9).toLong()
        printLog("time before next driver arrival: $timeOfDirverAriaval")
        getKitchenOrder()?.let {
            driverArivalNotification.onNext(it.temp)

        }
        return timeOfDirverAriaval * 1000
    }

    /**
     * This method generates random kitchen orders.
     * The thread is interruptable. If the [continueRunning] flag is set to false the while loop will terminate
     * A publish/subscriber notification method is used to send out notifications of type KitchenOrderServerDetail
     */
    private fun orderGenerator(): Long {
        debugTime = System.currentTimeMillis()
        val sleepTime = sampleTime(POISSON_LAMBDA)
        val time1 = "%.2f".format(sleepTime)
        val time2 = "%.2f".format(accumulatedTime / sampleCount)
        printLog("time before next orderDetail: $time1 average orderDetail time $time2")
        getKitchenOrder()?.let {
            accumulatedTime += sleepTime
            sampleCount += 1
            orderNotification?.run {
                onNext(it)
            }
        }
        return sleepTime.toLong() * 1000
    }
}