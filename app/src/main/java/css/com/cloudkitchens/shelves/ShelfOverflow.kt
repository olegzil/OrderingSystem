package css.com.cloudkitchens.shelves

class ShelfOverflow : BaseShelf(){
    init {
        super.setDecayRateMultiplier(2.0)
    }
    override fun getOrderTemp() = "overflow"
}

