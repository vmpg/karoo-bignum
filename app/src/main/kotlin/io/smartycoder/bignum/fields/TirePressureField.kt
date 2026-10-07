package io.smartycoder.bignum.fields

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.smartycoder.bignum.BuildConfig
import io.smartycoder.bignum.R
import io.smartycoder.bignum.format.Formatters
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

    private val lastDebugValues = AtomicReference<TpmsValues?>()

    override fun onDataPoint(point: DataPoint) {
        if (!BuildConfig.DEBUG) return
        val current = TpmsValues.from(point)
        if (lastDebugValues.getAndSet(current) != current) {
            // This deliberately logs only changes and only in debug builds. karoo-ext 1.1.9 does
            // not expose an active low-pressure flag, so one normal/low/normal device capture is
            // needed before warning colour can be implemented without guessing.
            Log.d(
                TAG,
                "$label pressureRaw=${current.pressureRaw} " +
                    "pressureBar=${current.pressureRaw?.let { Formatters.tirePressure(it, null).first }} " +
                    "target=${current.targetRaw} range=${current.rangeRaw} " +
                    "alarmEnabled=${current.alarmEnabled}",
            )
        }
    }

    internal data class TpmsValues(
        val pressureRaw: Double?,
        val targetRaw: Double?,
        val rangeRaw: Double?,
        val alarmEnabled: Double?,
    ) {
        companion object {
            fun from(point: DataPoint) = TpmsValues(
                pressureRaw = point.values[DataType.Field.TIRE_PRESSURE],
                targetRaw = point.values[DataType.Field.TIRE_PRESSURE_TARGET],
                rangeRaw = point.values[DataType.Field.TIRE_PRESSURE_RANGE],
                alarmEnabled = point.values[DataType.Field.TIRE_PRESSURE_ALARM_ENABLED],
            )
        }
    }

    private companion object {
        const val TAG = "BigNumTpms"
    }
}
