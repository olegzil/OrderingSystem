package css.com.cloudkitchens.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import css.com.cloudkitchens.dataproviders.KitchenOrderMetadata
import css.com.cloudkitchens.dataproviders.KitchenOrderShelfStatus
import css.com.cloudkitchens.managers.ShelfManager
import css.com.cloudkitchens.services.FoodOrderService
import css.com.cloudkitchens.utilities.printLog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_main_view.*


class MainActivity : AppCompatActivity(), ServiceConnection {
    private val disposables = CompositeDisposable()
    private var kitchenService: FoodOrderService? = null
    private var shelfManager: ShelfManager?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(css.com.cloudkitchens.R.layout.activity_main)
        setSupportActionBar(toolbar)
        val service = Intent(applicationContext, FoodOrderService::class.java)
        applicationContext.startService(service)
    }

    fun monitorMetaData() {
        kitchenService?.let { service ->
            service.getOrderNotificationChannel()?.let { metaData ->
                metaData.subscribeOn(Schedulers.io())
                metaData.observeOn(AndroidSchedulers.mainThread())
                metaData.subscribeWith(object:DisposableObserver<KitchenOrderMetadata>(){
                    override fun onComplete() {
                        // Do nothing
                    }

                    override fun onNext(orderMetaData: KitchenOrderMetadata) {
                        next_order_arival_time.text =
                            "%.2f/%.2f".format(orderMetaData.nextArrivalTime, orderMetaData.averageArrivalTime)                    }

                    override fun onError(e: Throwable) {
                        printLog(e.toString())
                    }

                })
            }
        }
    }

    private fun monitorOrderCount() {
        shelfManager?.getKitchenOrdersStatus()?.let { orderStatus ->
            disposables.add(
                orderStatus.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(object : DisposableObserver<KitchenOrderShelfStatus>() {
                        override fun onComplete() {
                            //Do nothing
                        }

                        override fun onNext(orders: KitchenOrderShelfStatus) {
                            orders.shelfStatus.forEach { order ->
                                when (order.first) {
                                    "hot" -> count_hot.text = order.second.toString()
                                    "cold" -> count_cold.text = order.second.toString()
                                    "frozen" -> count_frozen.text = order.second.toString()
                                    "overflow" -> count_overflow.text = order.second.toString()
                                    else -> printLog("unknown temperature: ${order.first}")
                                }
                            }
                        }

                        override fun onError(e: Throwable) {
                            printLog(e.toString())
                        }
                    })
            )
        }

    }

    override fun onServiceDisconnected(name: ComponentName?) {
        disposables.clear()
    }

    /**
     * This method is called after we connect to the service.
     * Once connected, we obtain a service refererence so that we can communicate with it
     * */
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val serviceBinder = service as FoodOrderService.OrderSourceBinder
        kitchenService = serviceBinder.getService()
        shelfManager = ShelfManager(serviceBinder.getService())
        monitorOrderCount()
//        monitorMetaData()
    }

    /**
     * During a resume event, the service is restarted
     */
    override fun onResume() {
        super.onResume()
        val intent = Intent(this, FoodOrderService::class.java)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
        monitorOrderCount()
//        monitorMetaData()
    }

    /**
     * During on pause, all disposables are cleared and the service is stopped
     */
    override fun onPause() {
        super.onPause()
        disposables.clear()
        unbindService(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(css.com.cloudkitchens.R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            css.com.cloudkitchens.R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
