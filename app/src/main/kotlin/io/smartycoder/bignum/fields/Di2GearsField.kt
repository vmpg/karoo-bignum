package io.smartycoder.bignum.fields

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile.PreferredUnit
import io.smartycoder.bignum.BuildConfig
import io.smartycoder.bignum.R
import io.smartycoder.bignum.render.ZoneColors
import io.smartycoder.bignum.render.ZoneKind
import io.smartycoder.bignum.streamDataFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicReference

/** Front and rear Di2 teeth from Karoo, with Ki2's already-derived upcoming-shift warning. */
class Di2GearsField(
    extension: String,
    karoo: KarooSystemService?,
) : BaseNumericField(extension, "di2Gears", karoo) {

    // The live flow below combines both shifting streams; this identifies the primary one for
    // callers that inspect field metadata without starting the view.
    override val upstreamTypeId = DataType.Type.SHIFTING_FRONT_GEAR
    override val label = "DI2"
    override val iconRes = R.drawable.ic_bignum
    override val zoneKind: ZoneKind? = null
    override val widthTemplate = "00-00"
    override val raisedTailAllowed = false
    override val previewValue = GearValues.pack(36, 17)
    override val valueField = FIELD_GEAR_PAIR
    override val format: (Double, PreferredUnit?) -> Pair<String, String> = { raw, _ ->
        (GearValues.format(raw) ?: "--") to ""
    }

    private val lastDebugState = AtomicReference<GearValues?>()

    override fun dataFlow(): Flow<StreamState> {
        val service = requireNotNull(karoo)
        return combine(
            service.streamDataFlow(DataType.Type.SHIFTING_FRONT_GEAR),
            service.streamDataFlow(DataType.Type.SHIFTING_REAR_GEAR),
            service.streamDataFlow(KI2_DI2_TYPE_ID),
        ) { front, rear, upcoming ->
            StreamState.Streaming(GearValues.from(front, rear, upcoming).toDataPoint())
        }.distinctUntilChanged()
    }

    override fun alertColor(point: DataPoint): Int? =
        if (GearValues.from(point).isUpcoming) ZoneColors.warningRed() else null

    override fun onDataPoint(point: DataPoint) {
        if (!BuildConfig.DEBUG) return
        val current = GearValues.from(point)
        if (lastDebugState.getAndSet(current) != current) {
            Log.d(
                TAG,
                "frontTeeth=${current.frontTeeth} rearTeeth=${current.rearTeeth} " +
                    "upcomingRaw=${current.upcomingRaw} " +
                    "upcomingState=${current.upcomingState} isUpcoming=${current.isUpcoming}",
            )
        }
    }

    internal enum class UpcomingState {
        NONE,
        UPCOMING_UP,
        UPCOMING_DOWN;

        companion object {
            fun from(raw: Double?): UpcomingState = when (raw) {
                1.0 -> UPCOMING_UP
                2.0 -> UPCOMING_DOWN
                else -> NONE
            }
        }
    }

    internal data class GearValues(
        val frontTeeth: Int?,
        val rearTeeth: Int?,
        val upcomingRaw: Double?,
        val upcomingState: UpcomingState = UpcomingState.from(upcomingRaw),
    ) {
        val isUpcoming: Boolean
            get() = upcomingState == UpcomingState.UPCOMING_UP ||
                upcomingState == UpcomingState.UPCOMING_DOWN

        val packedPair: Double?
            get() = if (frontTeeth != null && rearTeeth != null) {
                pack(frontTeeth, rearTeeth)
            } else {
                null
            }

        fun toDataPoint(): DataPoint {
            val values = mutableMapOf<String, Double>()
            frontTeeth?.let { values[DataType.Field.SHIFTING_FRONT_GEAR_TEETH] = it.toDouble() }
            rearTeeth?.let { values[DataType.Field.SHIFTING_REAR_GEAR_TEETH] = it.toDouble() }
            upcomingRaw?.let { values[FIELD_DI2_UPCOMING_SYNCHRO_SHIFT] = it }
            packedPair?.let { values[FIELD_GEAR_PAIR] = it }
            return DataPoint(INTERNAL_DATA_TYPE_ID, values)
        }

        companion object {
            fun from(front: StreamState, rear: StreamState, upcoming: StreamState) = GearValues(
                frontTeeth = teeth(front, DataType.Field.SHIFTING_FRONT_GEAR_TEETH),
                rearTeeth = teeth(rear, DataType.Field.SHIFTING_REAR_GEAR_TEETH),
                upcomingRaw = value(upcoming, FIELD_DI2_UPCOMING_SYNCHRO_SHIFT),
            )

            fun from(point: DataPoint) = GearValues(
                frontTeeth = validTeeth(point.values[DataType.Field.SHIFTING_FRONT_GEAR_TEETH]),
                rearTeeth = validTeeth(point.values[DataType.Field.SHIFTING_REAR_GEAR_TEETH]),
                upcomingRaw = point.values[FIELD_DI2_UPCOMING_SYNCHRO_SHIFT],
            )

            fun pack(frontTeeth: Int, rearTeeth: Int): Double =
                (frontTeeth * PACK_BASE + rearTeeth).toDouble()

            fun format(raw: Double): String? {
                if (!raw.isFinite() || raw % 1.0 != 0.0) return null
                val packed = raw.toInt()
                val front = packed / PACK_BASE
                val rear = packed % PACK_BASE
                if (front !in VALID_TEETH || rear !in VALID_TEETH) return null
                return "$front-$rear"
            }

            private fun teeth(state: StreamState, field: String): Int? =
                validTeeth(value(state, field))

            private fun value(state: StreamState, field: String): Double? =
                (state as? StreamState.Streaming)?.dataPoint?.values?.get(field)

            private fun validTeeth(raw: Double?): Int? = raw
                ?.takeIf { it.isFinite() && it % 1.0 == 0.0 }
                ?.toInt()
                ?.takeIf { it in VALID_TEETH }
        }
    }

    companion object {
        val KI2_DI2_TYPE_ID = DataType.dataTypeId("ki2", "TYPE_DI2")
        const val FIELD_DI2_UPCOMING_SYNCHRO_SHIFT = "FIELD_DI2_UPCOMING_SYNCHRO_SHIFT"

        private const val INTERNAL_DATA_TYPE_ID = "TYPE_EXT::bignum::DI2_GEARS_COMBINED"
        private const val FIELD_GEAR_PAIR = "FIELD_DI2_GEAR_PAIR_INTERNAL"
        private const val PACK_BASE = 1_000
        private val VALID_TEETH = 1 until PACK_BASE
        private const val TAG = "BigNumDi2"
    }
}
