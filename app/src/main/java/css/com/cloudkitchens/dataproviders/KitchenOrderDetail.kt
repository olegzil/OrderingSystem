package css.com.cloudkitchens.dataproviders

data class KitchenOrderDetail(val id:String, val name:String, val temp:String, val shelfLife:Int, val decayRate:Double, val timeStamp:Long, val normalizedDecay:Double, val orderDecay:Double)
data class KitchenOrderMetadata(val orderDetail:KitchenOrderDetail, val nextArrivalTime:Double, val averageArrivalTime:Double)