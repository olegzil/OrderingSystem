package css.com.cloudkitchens.adapters

import android.support.v7.widget.CardView
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import css.com.cloudkitchens.R
import css.com.cloudkitchens.dataproviders.KitchenOrderDetail

class RecyclerViewAdapter : RecyclerView.Adapter<RecyclerViewAdapter.ImageHolder>(){
    private val itemList = ArrayList<KitchenOrderDetail>()
    inner class ImageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(itemDetail: KitchenOrderDetail, pos: Int) {
            val cardView = itemView.findViewById<CardView>(R.id.kitch_detail_card_id)
            cardView.findViewById<TextView>(R.id.title_name_id)
            cardView.findViewById<TextView>(R.id.title_temp_id)
            cardView.findViewById<TextView>(R.id.title_shelf_max_life_id)
            cardView.findViewById<TextView>(R.id.title_decay_rate_id)
            cardView.findViewById<TextView>(R.id.title_normalized_life_id)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewAdapter.ImageHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.kitchen_order_item_detail, parent, false)

        return RecyclerViewAdapter().ImageHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecyclerViewAdapter.ImageHolder, position: Int) {
        holder.bind(itemList[position], position)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    fun update(newItems: List<KitchenOrderDetail>) {
        scrollHelper.update(newItems)
        notifyDataSetChanged()
    }
}