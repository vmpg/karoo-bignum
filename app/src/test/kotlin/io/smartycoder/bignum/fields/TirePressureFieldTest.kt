package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Test

class TirePressureFieldTest {

    private val front = TirePressureField(
        "test", "tirePressureFront", null,
        DataType.Type.TIRE_PRESSURE_FRONT, "TPMS F", 520.0,
    )
    private val rear = TirePressureField(
        "test", "tirePressureRear", null,
        DataType.Type.TIRE_PRESSURE_REAR, "TPMS R", 540.0,
    )

    @Test fun `front and rear fields map to their native Karoo streams`() {
        assertEquals(DataType.Type.TIRE_PRESSURE_FRONT, front.upstreamTypeId)
        assertEquals(DataType.Type.TIRE_PRESSURE_REAR, rear.upstreamTypeId)
        assertEquals("tirePressureFront", front.typeId)
        assertEquals("tirePressureRear", rear.typeId)
    }

    @Test fun `pressure is read by field name instead of multi-value singleValue`() {
        val point = DataPoint(
            DataType.Type.TIRE_PRESSURE_FRONT,
            linkedMapOf(
                // First on purpose: DataPoint.singleValue would choose this alarm setting.
                DataType.Field.TIRE_PRESSURE_ALARM_ENABLED to 1.0,
                DataType.Field.TIRE_PRESSURE_TARGET to 550.0,
                DataType.Field.TIRE_PRESSURE_RANGE to 50.0,
                DataType.Field.TIRE_PRESSURE to 520.0,
            ),
        )

        val raw = front.rawValue(
            StreamState.Streaming(point), preview = false, testMode = false,
        )

        assertEquals(520.0, raw!!, 0.0)
    }

    @Test fun `debug snapshot contains all four native values`() {
        val values = TirePressureField.TpmsValues.from(
            DataPoint(
                DataType.Type.TIRE_PRESSURE_FRONT,
                mapOf(
                    DataType.Field.TIRE_PRESSURE to 520.0,
                    DataType.Field.TIRE_PRESSURE_TARGET to 550.0,
                    DataType.Field.TIRE_PRESSURE_RANGE to 50.0,
                    DataType.Field.TIRE_PRESSURE_ALARM_ENABLED to 1.0,
                ),
            ),
        )

        assertEquals(TirePressureField.TpmsValues(520.0, 550.0, 50.0, 1.0), values)
    }
}
