package io.smartycoder.bignum.fields

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.smartycoder.bignum.BuildConfig
import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.render.ZoneColors
import io.smartycoder.bignum.render.ZoneKind
import java.util.concurrent.atomic.AtomicReference

/** A native Karoo TPMS stream rendered in bar. */
class TirePressureField(
    extension: String,
    typeId: String,
    karoo: KarooSystemService?,
    override val upstreamTypeId: String,
    override val label: String,
    override val previewValue: Double,
) : BaseNumericField(extension, typeId, karoo) {

    override val iconRes = R.drawable.ic_tire_pressure
    override val zoneKind: ZoneKind? = null
    override val format: (Double, PreferredUnit?) -> Pair<String, String> = Formatters.tirePressure
    override val widthTemplate = "0.00"
    override val raisedTailAllowed = false
    override val unitBelow = "bar"

    // A TPMS point has four values, so singleValue is not a safe way to select the pressure.
    override val valueField = DataType.Field.TIRE_PRESSURE

    private val lastDebugState = AtomicReference<TpmsDebugState?>()

    override fun alertColor(point: DataPoint): Int? =
        if (TpmsValues.from(point).isLowPressure()) ZoneColors.warningRed() else null

    override fun onDataPoint(point: DataPoint) {
        if (!BuildConfig.DEBUG) return
        val current = TpmsValues.from(point)
        val lowLimit = current.lowLimitRaw()
        val isLow = current.isLowPressure()
        val debugState = TpmsDebugState(
            current.targetRaw, current.rangeRaw, current.alarmEnabled, isLow,
        )
        if (lastDebugState.getAndSet(debugState) != debugState) {
            // Pressure changes frequently, so log only the first sample, alarm/threshold changes,
            // and transitions into or out of low pressure. Debug builds only.
            Log.d(
                TAG,
                "$label pressureRaw=${current.pressureRaw} " +
                    "pressureBar=${current.pressureRaw?.let { Formatters.tirePressure(it, null).first }} " +
                    "target=${current.targetRaw} range=${current.rangeRaw} " +
                    "lowLimit=$lowLimit alarmEnabled=${current.alarmEnabled} " +
                    "isLowPressure=$isLow",
            )
        }
    }

    internal data class TpmsValues(
        val pressureRaw: Double?,
        val targetRaw: Double?,
        val rangeRaw: Double?,
        val alarmEnabled: Double?,
    ) {
        fun lowLimitRaw(): Double? {
            val target = targetRaw?.takeIf { it.isFinite() } ?: return null
            val range = rangeRaw?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            return target - range
        }

        fun isLowPressure(): Boolean {
            val pressure = pressureRaw?.takeIf { it.isFinite() } ?: return false
            val lowLimit = lowLimitRaw() ?: return false
            return alarmEnabled?.let { it.isFinite() && it > 0.0 } == true && pressure < lowLimit
        }

        companion object {
            fun from(point: DataPoint) = TpmsValues(
                pressureRaw = point.values[DataType.Field.TIRE_PRESSURE],
                targetRaw = point.values[DataType.Field.TIRE_PRESSURE_TARGET],
                rangeRaw = point.values[DataType.Field.TIRE_PRESSURE_RANGE],
                alarmEnabled = point.values[DataType.Field.TIRE_PRESSURE_ALARM_ENABLED],
            )
        }
    }

    private data class TpmsDebugState(
        val targetRaw: Double?,
        val rangeRaw: Double?,
        val alarmEnabled: Double?,
        val isLowPressure: Boolean,
    )

    private companion object {
        const val TAG = "BigNumTpms"
    }
}
