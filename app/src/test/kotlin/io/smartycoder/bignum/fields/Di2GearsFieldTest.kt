package io.smartycoder.bignum.fields

import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.smartycoder.bignum.ZoneColorMode
import io.smartycoder.bignum.render.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Di2GearsFieldTest {

    private val field = Di2GearsField("test", null)
    private val defaultColor = 0xFF123456.toInt()

    @Test fun `field uses exact Karoo and Ki2 public stream ids`() {
        assertEquals(DataType.Type.SHIFTING_FRONT_GEAR, field.upstreamTypeId)
        assertEquals("TYPE_EXT::ki2::TYPE_DI2", Di2GearsField.KI2_DI2_TYPE_ID)
        assertEquals(
            "FIELD_DI2_UPCOMING_SYNCHRO_SHIFT",
            Di2GearsField.FIELD_DI2_UPCOMING_SYNCHRO_SHIFT,
        )
        assertEquals("di2Gears", field.typeId)
        assertEquals("DI2", field.label)
    }

    @Test fun `valid teeth pairs format with one hyphen and no spaces`() {
        assertEquals("36-17", format(36, 17))
        assertEquals("52-17", format(52, 17))
        assertEquals("36-11", format(36, 11))
    }

    @Test fun `missing or invalid teeth never invent a complete pair`() {
        assertEquals("--", frame(front = null, rear = 17, upcoming = 0.0).visual.text)
        assertEquals("--", frame(front = 36, rear = null, upcoming = 0.0).visual.text)

        val invalidFront = states(front = 0.0, rear = 17.0, upcoming = 0.0)
        val invalidRear = states(front = 36.0, rear = Double.NaN, upcoming = 0.0)
        assertNull(combine(invalidFront).packedPair)
        assertNull(combine(invalidRear).packedPair)
    }

    @Test fun `upcoming mapping preserves none up and down`() {
        assertEquals(Di2GearsField.UpcomingState.NONE, state(0.0))
        assertEquals(Di2GearsField.UpcomingState.UPCOMING_UP, state(1.0))
        assertEquals(Di2GearsField.UpcomingState.UPCOMING_DOWN, state(2.0))
        assertFalse(Di2GearsField.GearValues(36, 17, 0.0).isUpcoming)
        assertTrue(Di2GearsField.GearValues(36, 17, 1.0).isUpcoming)
        assertTrue(Di2GearsField.GearValues(36, 17, 2.0).isUpcoming)
    }

    @Test fun `none is normal and both upcoming directions are red in number mode`() {
        assertEquals(defaultColor, frame(36, 17, 0.0).visual.color)
        assertEquals(ZoneColors.warningRed(), frame(36, 17, 1.0).visual.color)
        assertEquals(ZoneColors.warningRed(), frame(36, 17, 2.0).visual.color)
    }

    @Test fun `upcoming follows field background and off modes`() {
        val fill = frame(36, 17, 1.0, ZoneColorMode.FILL)
        assertEquals(ZoneColors.warningRed(), fill.visual.background)
        assertEquals(ZoneColors.onColor(ZoneColors.warningRed()), fill.visual.color)

        val off = frame(36, 17, 2.0, ZoneColorMode.OFF)
        assertEquals(defaultColor, off.visual.color)
        assertNull(off.visual.background)
    }

    @Test fun `up and down transitions return from red to normal only when Ki2 returns none`() {
        fun colors(vararg upcoming: Double) = upcoming.map {
            frame(36, 17, it).visual.color
        }

        assertEquals(
            listOf(defaultColor, ZoneColors.warningRed(), defaultColor),
            colors(0.0, 1.0, 0.0),
        )
        assertEquals(
            listOf(defaultColor, ZoneColors.warningRed(), defaultColor),
            colors(0.0, 2.0, 0.0),
        )
    }

    @Test fun `front and rear changes update the displayed teeth while warning is normal`() {
        assertEquals("36-17", frame(36, 17, 0.0).visual.text)
        assertEquals("36-16", frame(36, 16, 0.0).visual.text)
        assertEquals("52-16", frame(52, 16, 0.0).visual.text)
    }

    private fun format(front: Int, rear: Int): String =
        Di2GearsField.GearValues.format(Di2GearsField.GearValues.pack(front, rear))!!

    private fun state(raw: Double) = Di2GearsField.UpcomingState.from(raw)

    private fun frame(
        front: Int?,
        rear: Int?,
        upcoming: Double?,
        mode: ZoneColorMode = ZoneColorMode.TEXT,
    ) = field.compute(
        StreamState.Streaming(Di2GearsField.GearValues(front, rear, upcoming).toDataPoint()),
        profile = null,
        preview = false,
        testMode = false,
        mode = mode,
        defaultColor = defaultColor,
    )

    private fun states(
        front: Double,
        rear: Double,
        upcoming: Double,
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
        StreamState.Streaming(
            DataPoint(
                Di2GearsField.KI2_DI2_TYPE_ID,
                mapOf(Di2GearsField.FIELD_DI2_UPCOMING_SYNCHRO_SHIFT to upcoming),
            ),
        ),
    )

    private fun combine(states: Array<StreamState>) =
        Di2GearsField.GearValues.from(states[0], states[1], states[2])
}
