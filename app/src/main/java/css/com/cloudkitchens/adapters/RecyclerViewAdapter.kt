package css.com.cloudkitchens.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import css.com.cloudkitchens.R
import css.com.cloudkitchens.dataproviders.KitchenOrderDetail
import css.com.cloudkitchens.interfaces.RecyclerViewAdapterInterface

class RecyclerViewAdapter : RecyclerViewAdapterInterface, RecyclerView.Adapter<RecyclerViewAdapter.CardViewHolder>() {
    private val itemList = ArrayList<KitchenOrderDetail>()

    inner class CardViewHolder(holder: View) : RecyclerView.ViewHolder(holder) {
        fun bind(itemDetail: KitchenOrderDetail, pos: Int) {
            val name = itemView.findViewById<TextView>(R.id.order_name_id)
            val temp = itemView.findViewById<TextView>(R.id.order_temperture)
            val maxLife = itemView.findViewById<TextView>(R.id.order_shelf_life)
            val decayRate = itemView.findViewById<TextView>(R.id.order_decay_rate)
            val normalizedLife = itemView.findViewById<TextView>(R.id.order_normalized_life)
            val remainingLife = itemView.findViewById<TextView>(R.id.order_life_remaining)
            name.text = itemDetail.name
            temp.text = itemDetail.temp
            maxLife.text = itemDetail.shelfLife.toString()
            decayRate.text = String.format("%2.3f", itemDetail.decayRate)
            normalizedLife.text = String.format("%2.3f", itemDetail.normalizedShelfLife)
            remainingLife.text = itemDetail.orderRemainingLife.toString()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewAdapter.CardViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.kitchen_order_item_detail, parent, false)

        return RecyclerViewAdapter().CardViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecyclerViewAdapter.CardViewHolder, position: Int) {
        holder.bind(itemList[position], position)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    override fun addOrder(order: KitchenOrderDetail) {
        itemList.add(order)
        notifyItemInserted(itemList.size - 1)
    }

    override fun removeOrder(order: KitchenOrderDetail) {
        itemList.find { order.id == it.id }?.let {
            itemList.remove(it)
        }
        notifyDataSetChanged()
    }

    override fun updateSingleOrder(order:KitchenOrderDetail){
        var index=0
        itemList.first { order.id == it.id }.let {targetOrder->
            itemList[index] = order
            ++index
            notifyItemChanged(index)
        }
    }

    override fun update(newItems: List<KitchenOrderDetail>) {
        itemList.clear()
        itemList.addAll(newItems)
        notifyDataSetChanged()
    }
}