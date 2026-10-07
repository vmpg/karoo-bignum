package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.format.RaisedTail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class TirePressureFieldTest {

    private val front = TirePressureField(
        "test", "tirePressureFront", null,
        DataType.Type.TIRE_PRESSURE_FRONT, "FRONT", 3670.0,
    )
    private val rear = TirePressureField(
        "test", "tirePressureRear", null,
        DataType.Type.TIRE_PRESSURE_REAR, "REAR", 3810.0,
    )

    @Test fun `front and rear fields map to their native Karoo streams`() {
        assertEquals(DataType.Type.TIRE_PRESSURE_FRONT, front.upstreamTypeId)
        assertEquals(DataType.Type.TIRE_PRESSURE_REAR, rear.upstreamTypeId)
        assertEquals("tirePressureFront", front.typeId)
        assertEquals("tirePressureRear", rear.typeId)
    }

    @Test fun `front and rear expose the requested headers and separate unit`() {
        assertEquals("FRONT", front.label)
        assertEquals("REAR", rear.label)
        assertEquals("bar", front.unitBelow)
        assertEquals("bar", rear.unitBelow)
    }

    @Test fun `TPMS keeps the complete two-decimal value at one size`() {
        assertFalse(front.raisedTailAllowed)
        withLocale(Locale.GERMANY) {
            val text = Formatters.tirePressure(3670.0, null).first
            assertEquals("3,67" to "", RaisedTail.split(text, raised = front.raisedTailAllowed))
        }
    }

    @Test fun `pressure is read by field name instead of multi-value singleValue`() {
        val point = DataPoint(
            DataType.Type.TIRE_PRESSURE_FRONT,
            linkedMapOf(
                // First on purpose: DataPoint.singleValue would choose this alarm setting.
                DataType.Field.TIRE_PRESSURE_ALARM_ENABLED to 1.0,
                DataType.Field.TIRE_PRESSURE_TARGET to 4000.0,
                DataType.Field.TIRE_PRESSURE_RANGE to 300.0,
                DataType.Field.TIRE_PRESSURE to 3670.0,
            ),
        )

        val raw = front.rawValue(
            StreamState.Streaming(point), preview = false, testMode = false,
        )

        assertEquals(3670.0, raw!!, 0.0)
    }

    @Test fun `debug snapshot contains all four native values`() {
        val values = TirePressureField.TpmsValues.from(
            DataPoint(
                DataType.Type.TIRE_PRESSURE_FRONT,
                mapOf(
                    DataType.Field.TIRE_PRESSURE to 3670.0,
                    DataType.Field.TIRE_PRESSURE_TARGET to 4000.0,
                    DataType.Field.TIRE_PRESSURE_RANGE to 300.0,
                    DataType.Field.TIRE_PRESSURE_ALARM_ENABLED to 1.0,
                ),
            ),
        )

        assertEquals(TirePressureField.TpmsValues(3670.0, 4000.0, 300.0, 1.0), values)
    }


    private fun withLocale(locale: Locale, body: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { body() } finally { Locale.setDefault(previous) }
    }
}
