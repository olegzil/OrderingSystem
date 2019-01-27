package css.com.cloudkitchens.dataproviders

data class KitchenOrderDetail(
    val id: String,
    val name: String,
    val temp: String,
    val shelfLife: Int,
    val decayRate: Double,
    val timeStamp: Long,
    var normalizedShelfLife: Double,
    var orderDecay: Double,
    var orderRemainingLife: Long
)

data class KitchenOrderServerDetail(
    val id: String,
    val name: String,
    val temp: String,
    val shelfLife: Int,
    val decayRate: Double,
    val timeStamp: Long
)
