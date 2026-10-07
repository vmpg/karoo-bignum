package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.format.Formatters
import io.smartycoder.bignum.format.RaisedTail
import io.smartycoder.bignum.render.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        withLocale(Locale.US) {
            val text = Formatters.tirePressure(3670.0, null).first
            assertEquals("3.67" to "", RaisedTail.split(text, raised = front.raisedTailAllowed))
        }
    }

    @Test fun `low pressure uses a strict target minus range boundary`() {
        fun values(pressureBar: Double) = TirePressureField.TpmsValues(
            pressureRaw = pressureBar * 1000.0,
            targetRaw = 3800.0,
            rangeRaw = 400.0,
            alarmEnabled = 1.0,
        )

        assertFalse(values(3.82).isLowPressure())
        assertFalse(values(3.45).isLowPressure())
        assertFalse(values(3.40).isLowPressure())
        assertTrue(values(3.39).isLowPressure())
        assertTrue(values(3.00).isLowPressure())
        assertEquals(3400.0, values(3.00).lowLimitRaw()!!, 0.0)
    }

    @Test fun `disabled native alarm suppresses low pressure warning`() {
        val values = TirePressureField.TpmsValues(3000.0, 3800.0, 400.0, 0.0)
        assertFalse(values.isLowPressure())
    }

    @Test fun `low pressure follows existing number fill and off colour modes`() {
        val state = streaming(front, pressureRaw = 3000.0, alarmEnabled = 1.0)
        val defaultColor = 0xFF123456.toInt()
        val red = ZoneColors.warningRed()

        val number = front.compute(
            state, null, preview = false, testMode = false,
            mode = ZoneColorMode.TEXT, defaultColor = defaultColor,
        )
        assertEquals(red, number.visual.color)
        assertNull(number.visual.background)

        val fill = front.compute(
            state, null, preview = false, testMode = false,
            mode = ZoneColorMode.FILL, defaultColor = defaultColor,
        )
        assertEquals(red, fill.visual.background)
        assertEquals(ZoneColors.onColor(red), fill.visual.color)

        val off = front.compute(
            state, null, preview = false, testMode = false,
            mode = ZoneColorMode.OFF, defaultColor = defaultColor,
        )
        assertEquals(defaultColor, off.visual.color)
        assertNull(off.visual.background)
    }

    @Test fun `front and rear low pressure states are independent`() {
        val defaultColor = 0xFF123456.toInt()
        val frontFrame = front.compute(
            streaming(front, pressureRaw = 3390.0, alarmEnabled = 1.0),
            null, false, false, ZoneColorMode.TEXT, defaultColor,
        )
        val rearFrame = rear.compute(
            streaming(rear, pressureRaw = 3820.0, alarmEnabled = 1.0),
            null, false, false, ZoneColorMode.TEXT, defaultColor,
        )

        assertEquals(ZoneColors.warningRed(), frontFrame.visual.color)
        assertEquals(defaultColor, rearFrame.visual.color)
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

    private fun streaming(
        field: TirePressureField,
        pressureRaw: Double,
        alarmEnabled: Double,
    ) = StreamState.Streaming(
        DataPoint(
            field.upstreamTypeId,
            mapOf(
                DataType.Field.TIRE_PRESSURE to pressureRaw,
                DataType.Field.TIRE_PRESSURE_TARGET to 3800.0,
                DataType.Field.TIRE_PRESSURE_RANGE to 400.0,
                DataType.Field.TIRE_PRESSURE_ALARM_ENABLED to alarmEnabled,
            ),
        ),
    )

    private fun withLocale(locale: Locale, body: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try { body() } finally { Locale.setDefault(previous) }
    }
}
