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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import java.io.InputStream
import java.lang.Math.random
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Service emulates a server. The service reads JSON data from a resource file in this APK, parser the the data and
 * provides client access to the data
 * */
class FoodOrderService : Service(), KitchenOrderNotification {
    private var kitchenOrders: JSONArray? = null
    private val binder = OrderSourceBinder()
    private var driverArrivalChannel = Channel<String>(Channel.UNLIMITED)
    private var orderNotificationChannel = Channel<KitchenOrderServerDetail>(Channel.UNLIMITED)
    private val heatBeatChannel= Channel<Long>(Channel.UNLIMITED)
    private var continueRunning = true
    private var debugTime = 0L
    private var sampleCount = 1.0
    private var accumulatedTime = 0.0
    private val jobList = mutableListOf<Job>()

    init {
        if (kitchenOrders == null)
            kitchenOrders = readKitchenOrders("orders.json")
        coroutineRestart()
    }

    inner class OrderSourceBinder : Binder() {
        fun getService(): FoodOrderService {
            return this@FoodOrderService
        }
    }

    private fun coroutineRestart() {
        jobList.forEach { it.cancel() }
        jobList.add(GlobalScope.launch {
            while(isActive) {
                delay(AGING_HEART_BEAT)
                heatBeatChannel.offer(AGING_HEART_BEAT)
            }
        })

        jobList.add(GlobalScope.launch {
            while (isActive) {
                delay(orderGenerator())
            }
        })

        jobList.add(GlobalScope.launch {
            while (isActive) {
                debugTime = System.currentTimeMillis()
                val timeOfDirverAriaval = ThreadLocalRandom.current().nextInt(2, 9).toLong()
                printLog("time before next driver arrival: $timeOfDirverAriaval")
                delay(timeOfDirverAriaval * 1000)
                getKitchenOrder()?.let {
                    driverArrivalChannel.offer(it.temp)
                }
            }
        })
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
        coroutineRestart()
        super.onRebind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kitchenOrders = readKitchenOrders("orders.json")
        return Service.START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        continueRunning = false
        jobList.forEach { it.cancel() }
        stopSelf()
        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        continueRunning = true
        printLog("from Service.onBind")
        kitchenOrders = readKitchenOrders("orders.json")
        coroutineRestart()
        return binder
    }

    override fun getOrderNotificationChannel() = orderNotificationChannel
    override fun getDriverNotification() = driverArrivalChannel
    override fun getOrderHeartbeat() = heatBeatChannel

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
            orderNotificationChannel.offer(it)
        }
        return sleepTime.toLong() * 1000
    }
}