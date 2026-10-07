package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.render.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Di2GearsFieldTest {

    private val field = Di2GearsField("test", null)
    private val defaultColor = 0xFF123456.toInt()

    @Test fun `field uses the Karoo front teeth stream as its primary source`() {
        assertEquals(DataType.Type.SHIFTING_FRONT_GEAR, field.upstreamTypeId)
        assertEquals("di2Gears", field.typeId)
        assertEquals("DI2", field.label)
    }

    @Test fun `valid teeth pairs format with one hyphen and no spaces`() {
        assertEquals("36-17", format(36, 17))
        assertEquals("52-17", format(52, 17))
        assertEquals("36-11", format(36, 11))
    }

    @Test fun `missing or invalid teeth never invent a complete pair`() {
        assertEquals("--", frame(front = null, rear = 17).visual.text)
        assertEquals("--", frame(front = 36, rear = null).visual.text)

        val invalidFront = states(front = 0.0, rear = 17.0)
        val invalidRear = states(front = 36.0, rear = Double.NaN)
        assertNull(combine(invalidFront).packedPair)
        assertNull(combine(invalidRear).packedPair)
    }

    @Test fun `only the two fixed tooth combinations are red in number mode`() {
        assertEquals(ZoneColors.warningRed(), frame(36, 15).visual.color)
        assertEquals(ZoneColors.warningRed(), frame(46, 24).visual.color)

        listOf(36 to 17, 36 to 16, 36 to 14, 46 to 22, 46 to 23, 46 to 25).forEach {
            (front, rear) -> assertEquals(defaultColor, frame(front, rear).visual.color)
        }
    }

    @Test fun `fixed red shift points follow field background and off modes`() {
        val fill = frame(36, 15, ZoneColorMode.FILL)
        assertEquals(ZoneColors.warningRed(), fill.visual.background)
        assertEquals(ZoneColors.onColor(ZoneColors.warningRed()), fill.visual.color)

        val off = frame(46, 24, ZoneColorMode.OFF)
        assertEquals(defaultColor, off.visual.color)
        assertNull(off.visual.background)
    }

    @Test fun `changing away from either fixed shift point returns to normal`() {
        assertEquals(
            listOf(defaultColor, ZoneColors.warningRed(), defaultColor),
            listOf(17, 15, 14).map { rear -> frame(36, rear).visual.color },
        )
        assertEquals(
            listOf(defaultColor, ZoneColors.warningRed(), defaultColor),
            listOf(23, 24, 25).map { rear -> frame(46, rear).visual.color },
        )
    }

    @Test fun `front and rear changes keep the existing displayed teeth format`() {
        assertEquals("36-17", frame(36, 17).visual.text)
        assertEquals("36-16", frame(36, 16).visual.text)
        assertEquals("52-16", frame(52, 16).visual.text)
    }

    private fun format(front: Int, rear: Int): String =
        Di2GearsField.GearValues.format(Di2GearsField.GearValues.pack(front, rear))!!

    private fun frame(
        front: Int?,
        rear: Int?,
        mode: ZoneColorMode = ZoneColorMode.TEXT,
    ) = field.compute(
        StreamState.Streaming(Di2GearsField.GearValues(front, rear).toDataPoint()),
        profile = null,
        preview = false,
        testMode = false,
        mode = mode,
        defaultColor = defaultColor,
    )

    private fun states(
        front: Double,
        rear: Double,
    ): Array<StreamState> = arrayOf(
        StreamState.Streaming(
            DataPoint(
                DataType.Type.SHIFTING_FRONT_GEAR,
                mapOf(DataType.Field.SHIFTING_FRONT_GEAR_TEETH to front),
            ),
        ),
        StreamState.Streaming(
            DataPoint(
                DataType.Type.SHIFTING_REAR_GEAR,
                mapOf(DataType.Field.SHIFTING_REAR_GEAR_TEETH to rear),
            ),
        ),
    )

    private fun combine(states: Array<StreamState>) =
        Di2GearsField.GearValues.from(states[0], states[1])
}
