package org.openballistics.engine

import org.openballistics.model.BallisticCoefficient
import org.openballistics.model.DragModel

object DragTables {

    private val G1_TABLE = doubleArrayOf(
        0.000, 0.2629, 0.050, 0.2558, 0.100, 0.2487, 0.150, 0.2413,
        0.200, 0.2344, 0.250, 0.2278, 0.300, 0.2214, 0.350, 0.2155,
        0.400, 0.2104, 0.450, 0.2061, 0.500, 0.2032, 0.550, 0.2020,
        0.600, 0.2034, 0.700, 0.2165, 0.725, 0.2230, 0.750, 0.2313,
        0.775, 0.2417, 0.800, 0.2546, 0.825, 0.2706, 0.850, 0.2901,
        0.875, 0.3136, 0.900, 0.3415, 0.925, 0.3734, 0.950, 0.4084,
        0.975, 0.4448, 1.000, 0.4805, 1.025, 0.5136, 1.050, 0.5427,
        1.075, 0.5677, 1.100, 0.5883, 1.125, 0.6053, 1.150, 0.6191,
        1.200, 0.6393, 1.250, 0.6518, 1.300, 0.6589, 1.350, 0.6621,
        1.400, 0.6625, 1.450, 0.6607, 1.500, 0.6573, 1.550, 0.6528,
        1.600, 0.6474, 1.650, 0.6413, 1.700, 0.6347, 1.750, 0.6280,
        1.800, 0.6210, 1.850, 0.6141, 1.900, 0.6072, 1.950, 0.6003,
        2.000, 0.5934, 2.050, 0.5867, 2.100, 0.5804, 2.150, 0.5743,
        2.200, 0.5685, 2.250, 0.5630, 2.300, 0.5577, 2.350, 0.5527,
        2.400, 0.5481, 2.450, 0.5438, 2.500, 0.5397, 2.600, 0.5325,
        2.700, 0.5264, 2.800, 0.5211, 2.900, 0.5168, 3.000, 0.5133,
        3.100, 0.5105, 3.200, 0.5084, 3.300, 0.5067, 3.400, 0.5054,
        3.500, 0.5040, 3.600, 0.5030, 3.700, 0.5022, 3.800, 0.5016,
        3.900, 0.5010, 4.000, 0.5006, 4.200, 0.4998, 4.400, 0.4995,
        4.600, 0.4992, 4.800, 0.4990, 5.000, 0.4988
    )

    private val G7_TABLE = doubleArrayOf(
        0.000, 0.1198, 0.050, 0.1197, 0.100, 0.1196, 0.150, 0.1194,
        0.200, 0.1193, 0.250, 0.1194, 0.300, 0.1194, 0.350, 0.1194,
        0.400, 0.1193, 0.450, 0.1193, 0.500, 0.1194, 0.550, 0.1193,
        0.600, 0.1194, 0.650, 0.1197, 0.700, 0.1202, 0.725, 0.1207,
        0.750, 0.1215, 0.775, 0.1226, 0.800, 0.1242, 0.825, 0.1266,
        0.850, 0.1306, 0.875, 0.1368, 0.900, 0.1464, 0.925, 0.1660,
        0.950, 0.2054, 0.975, 0.2993, 1.000, 0.3803, 1.025, 0.4015,
        1.050, 0.4043, 1.075, 0.4034, 1.100, 0.4014, 1.125, 0.3987,
        1.150, 0.3955, 1.200, 0.3884, 1.250, 0.3810, 1.300, 0.3732,
        1.350, 0.3657, 1.400, 0.3580, 1.500, 0.3440, 1.550, 0.3376,
        1.600, 0.3315, 1.650, 0.3260, 1.700, 0.3209, 1.750, 0.3160,
        1.800, 0.3117, 1.850, 0.3078, 1.900, 0.3042, 1.950, 0.3010,
        2.000, 0.2980, 2.050, 0.2951, 2.100, 0.2922, 2.150, 0.2892,
        2.200, 0.2864, 2.250, 0.2835, 2.300, 0.2807, 2.350, 0.2779,
        2.400, 0.2752, 2.450, 0.2725, 2.500, 0.2697, 2.550, 0.2670,
        2.600, 0.2643, 2.650, 0.2615, 2.700, 0.2588, 2.750, 0.2561,
        2.800, 0.2533, 2.850, 0.2506, 2.900, 0.2479, 2.950, 0.2451,
        3.000, 0.2424, 3.100, 0.2368, 3.200, 0.2313, 3.300, 0.2258,
        3.400, 0.2205, 3.500, 0.2154, 3.600, 0.2106, 3.700, 0.2060,
        3.800, 0.2017, 3.900, 0.1975, 4.000, 0.1935, 4.200, 0.1861,
        4.400, 0.1793, 4.600, 0.1730, 4.800, 0.1672, 5.000, 0.1618
    )

    private data class DragTableData(val mach: DoubleArray, val cd: DoubleArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DragTableData) return false
            return mach.contentEquals(other.mach) && cd.contentEquals(other.cd)
        }
        override fun hashCode(): Int = 31 * mach.contentHashCode() + cd.contentHashCode()
    }

    private val g1Data: DragTableData by lazy { parseTable(G1_TABLE) }
    private val g7Data: DragTableData by lazy { parseTable(G7_TABLE) }

    private fun parseTable(raw: DoubleArray): DragTableData {
        val n = raw.size / 2
        val mach = DoubleArray(n)
        val cd = DoubleArray(n)
        for (i in 0 until n) {
            mach[i] = raw[i * 2]
            cd[i] = raw[i * 2 + 1]
        }
        return DragTableData(mach, cd)
    }

    fun standardCd(model: DragModel, mach: Double): Double {
        val pchip = when (model) {
            DragModel.G1 -> g1Pchip
            DragModel.G7 -> g7Pchip
        }
        return pchip.evaluate(mach)
    }

    fun dragRetardation(
        velocity: Double,
        mach: Double,
        bc: Double,
        dragModel: DragModel,
        densityRatio: Double
    ): Double {
        val cdStd = standardCd(dragModel, mach)
        return densityRatio * cdStd * velocity * velocity / (bc * DRAG_CONSTANT)
    }

    // 0.3048 / (ρ_std_imp × π / (8 × 144)), ρ_std_imp = 0.076474 lb/ft³
    internal const val DRAG_CONSTANT = 1461.5130112059

    fun effectiveBc(bc: BallisticCoefficient, velocityMps: Double): Double {
        return when (bc) {
            is BallisticCoefficient.Single -> bc.value
            is BallisticCoefficient.Segmented -> {
                val segments = bc.segments.sortedByDescending { it.velocity.metersPerSecond }
                for (seg in segments) {
                    if (velocityMps >= seg.velocity.metersPerSecond) {
                        return seg.bc
                    }
                }
                segments.last().bc
            }
        }
    }

    private class PchipInterpolator(private val xs: DoubleArray, private val ys: DoubleArray) {
        private val slopes: DoubleArray = computeSlopes()

        private fun computeSlopes(): DoubleArray {
            val n = xs.size
            val d = DoubleArray(n)
            val deltas = DoubleArray(n - 1)
            val h = DoubleArray(n - 1)

            for (i in 0 until n - 1) {
                h[i] = xs[i + 1] - xs[i]
                deltas[i] = (ys[i + 1] - ys[i]) / h[i]
            }

            for (i in 1 until n - 1) {
                val d0 = deltas[i - 1]
                val d1 = deltas[i]
                if (d0 * d1 <= 0.0) {
                    d[i] = 0.0
                } else {
                    val w1 = 2.0 * h[i] + h[i - 1]
                    val w2 = h[i] + 2.0 * h[i - 1]
                    d[i] = (w1 + w2) / (w1 / d0 + w2 / d1)
                }
            }

            d[0] = endpointSlope(h[0], h[1], deltas[0], deltas[1])
            d[n - 1] = endpointSlope(h[n - 2], h[n - 3], deltas[n - 2], deltas[n - 3])

            return d
        }

        private fun endpointSlope(h0: Double, h1: Double, d0: Double, d1: Double): Double {
            val s = ((2.0 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
            return if (s * d0 <= 0.0) {
                0.0
            } else if (kotlin.math.abs(s) > 3.0 * kotlin.math.abs(d0)) {
                3.0 * d0
            } else {
                s
            }
        }

        fun evaluate(x: Double): Double {
            if (x <= xs.first()) return ys.first()
            if (x >= xs.last()) return ys.last()

            var lo = 0
            var hi = xs.size - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (xs[mid] <= x) lo = mid else hi = mid
            }

            val h = xs[hi] - xs[lo]
            val t = (x - xs[lo]) / h
            val t2 = t * t
            val t3 = t2 * t

            val h00 = 2 * t3 - 3 * t2 + 1
            val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2
            val h11 = t3 - t2

            return h00 * ys[lo] + h10 * h * slopes[lo] + h01 * ys[hi] + h11 * h * slopes[hi]
        }
    }

    private val g1Pchip: PchipInterpolator by lazy { PchipInterpolator(g1Data.mach, g1Data.cd) }
    private val g7Pchip: PchipInterpolator by lazy { PchipInterpolator(g7Data.mach, g7Data.cd) }
}
