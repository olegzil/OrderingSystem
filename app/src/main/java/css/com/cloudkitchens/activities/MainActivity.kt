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
import css.com.cloudkitchens.dataproviders.KitchenOrder
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(css.com.cloudkitchens.R.layout.activity_main)
        setSupportActionBar(toolbar)
        val service = Intent(applicationContext, FoodOrderService::class.java)
        applicationContext.startService(service)
    }
    fun debugTest(){
        kitchenService?.let {service ->
            service.getOrderNotificationChannel()?.let {orderNotifier->
                disposables.add(orderNotifier.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(object : DisposableObserver<KitchenOrder>(){
                        override fun onComplete() {}
                        override fun onNext(order: KitchenOrder) {
                            when(order.temp){
                                "hot" ->    {
                                    val count = count_hot.text.toString().toInt()+1
                                    count_hot.text = count.toString()
                                }
                                "cold" ->   {
                                    val count = count_cold.text.toString().toInt()+1
                                    count_cold.text = count.toString()
                                }
                                "frozen" -> {
                                    val count = count_frozen.text.toString().toInt()+1
                                    count_frozen.text = count.toString()
                                }
                                else-> printLog("unknown temperature: ${order.temp}")
                            }
                        }

                        override fun onError(e: Throwable) {
                            printLog(e.toString())
                            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                        }
                    }))
            }
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
        debugTest()
    }

    /**
     * During a resume event, the service is restarted
     */
    override fun onResume() {
        super.onResume()
        val intent = Intent(this, FoodOrderService::class.java)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
        debugTest()
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
