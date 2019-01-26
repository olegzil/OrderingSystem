package css.com.cloudkitchens.dataproviders

data class KitchenOrder(val id:String, val name:String, val temp:String, val shelfLife:Int, val decayRate:Double, val timeStamp:Long)
data class KitchenOrderMetadata(val order:KitchenOrder, val nextArrivalTime:Double, val averageArrivalTime:Double)