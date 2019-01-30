package css.com.cloudkitchens.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import css.com.cloudkitchens.R
import css.com.cloudkitchens.adapters.RecyclerViewAdapter
import css.com.cloudkitchens.interfaces.RecyclerViewAdapterInterface
import css.com.cloudkitchens.managers.ShelfManager
import css.com.cloudkitchens.services.FoodOrderService
import css.com.cloudkitchens.utilities.printLog
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_main_view.*
import kotlinx.coroutines.*

/**
 * The one and only main activity. The activity consists of three recycler views with their respective detail items
 * A shelf is modeled by a recycler view
 */
class MainActivity : AppCompatActivity(), ServiceConnection {
    private val jobList = mutableListOf<Job>()
    private var kitchenService: FoodOrderService? = null
    private var shelfManager: ShelfManager? = null
    private lateinit var recyclerViewHot: RecyclerView
    private lateinit var recyclerViewCold: RecyclerView
    private lateinit var recyclerViewFrozen: RecyclerView
    private lateinit var recyclerViewOverFlow: RecyclerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(css.com.cloudkitchens.R.layout.activity_main)
        setSupportActionBar(toolbar)
        initializeRecyclerViews()
        val service = Intent(applicationContext, FoodOrderService::class.java)
        applicationContext.startService(service)
    }

    private fun initializeRecyclerViews() {
        recyclerViewHot = recycler_hot_list
        recyclerViewCold = recycler_cold_list
        recyclerViewFrozen = recycler_frozen_list
        recyclerViewOverFlow = recycler_overflow_list

        recyclerViewHot.adapter = RecyclerViewAdapter()
        recyclerViewCold.adapter = RecyclerViewAdapter()
        recyclerViewFrozen.adapter = RecyclerViewAdapter()
        recyclerViewOverFlow.adapter = RecyclerViewAdapter()

        recyclerViewHot.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewCold.layoutManager = LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewFrozen.layoutManager =
            LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
        recyclerViewOverFlow.layoutManager =
            LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)

    }

    /**
     * The method listens for order arrival emitted by the [ShelfManager]. It them updates the appropriate recycler view
     */
    private fun monitorOrderArrival() {
        shelfManager?.let { shelfManager ->
            jobList.add(GlobalScope.launch(Dispatchers.Main) {
                while (isActive) {
                    val orders = shelfManager.getOrderArrivalChannel().receive()
                    when (orders.orderSelector) {
                        "hot" -> {
                            val adapter: RecyclerViewAdapterInterface =
                                recyclerViewHot.adapter as RecyclerViewAdapterInterface
                            adapter.update(orders.shelfStatus)
                            findViewById<TextView>(R.id.count_hot_id)?.let { count->
                                count.text = orders.shelfStatus.size.toString()
                            }
                        }

                        "cold" -> {
                            val adapter: RecyclerViewAdapterInterface =
                                recyclerViewCold.adapter as RecyclerViewAdapterInterface
                            adapter.update(orders.shelfStatus)
                            findViewById<TextView>(R.id.count_cold_id)?.let {count->
                                count.text = orders.shelfStatus.size.toString()
                            }
                        }
                        "frozen" -> {
                            val adapter: RecyclerViewAdapterInterface =
                                recyclerViewFrozen.adapter as RecyclerViewAdapterInterface
                            adapter.update(orders.shelfStatus)
                            findViewById<TextView>(R.id.count_frozen_id)?.let {count->
                                count.text = orders.shelfStatus.size.toString()
                            }
                        }
                        else -> printLog("unknown temperature: ${orders.orderSelector}")
                    }
                    if (orders.overFlow.isNotEmpty()) {
                        val adapter: RecyclerViewAdapterInterface =
                            recyclerViewOverFlow.adapter as RecyclerViewAdapterInterface
                        adapter.update(orders.overFlow)
                        findViewById<TextView>(R.id.count_overflow_id)?.let { count->
                            count.text = orders.shelfStatus.size.toString()
                        }
                    }
                }
            })
        }

    }

    override fun onServiceDisconnected(name: ComponentName?) {
        jobList.forEach { it.cancel() }
        shelfManager?.cleanup()
    }

    /**
     * This method is called after we connect to the service.
     * Once connected, we obtain a service reference so that we can communicate with it
     * */
    override fun onServiceConnected(name: ComponentName?, dispatchSerivice: IBinder?) {
        val serviceBinder = dispatchSerivice as FoodOrderService.OrderSourceBinder
        kitchenService = serviceBinder.getService()
        kitchenService?.let { service ->
            shelfManager = ShelfManager(service)
            monitorOrderArrival()
        }
    }

    /**
     * During a resume event, the service is restarted
     */
    override fun onResume() {
        super.onResume()
        val intent = Intent(this, FoodOrderService::class.java)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
        jobList.forEach { it.cancel() }
        monitorOrderArrival()
    }

    /**
     * During on pause, all disposables are cleared and the service is stopped
     */
    override fun onPause() {
        super.onPause()
        shelfManager?.cleanup()
        jobList.forEach { it.cancel() }
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
