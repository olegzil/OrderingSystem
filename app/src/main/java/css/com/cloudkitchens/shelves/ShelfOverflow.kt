package css.com.cloudkitchens.shelves

class ShelfOverflow : BaseShelf(2.0){
    override fun setDecayRateMultiplier(multiplier: Double) {
        super.setDecayRateMultiplier(multiplier)
    }
    override fun getOrderTemp() = "overflow"
}

