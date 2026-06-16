package org.openballistics.units

private const val KG_PER_GRAIN = 6.479891e-5
private const val KG_PER_GRAM = 0.001

data class Mass(val kilograms: Double) {

    val grains: Double get() = kilograms / KG_PER_GRAIN
    val grams: Double get() = kilograms / KG_PER_GRAM

    companion object {
        fun fromGrains(grains: Double): Mass = Mass(grains * KG_PER_GRAIN)
        fun fromGrams(grams: Double): Mass = Mass(grams * KG_PER_GRAM)
    }
}
