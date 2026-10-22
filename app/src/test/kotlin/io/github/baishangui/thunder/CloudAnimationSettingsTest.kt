package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class CloudAnimationSettingsTest {
    @Test fun animationSettingsRoundTripAndReachNativeSchema() {
        val value = CloudAnimationSettings(false, 750, 2, 65)
        val settings = Settings(cloudAnimation = value)
        assertEquals(value, Settings.from(settings.bundle()).cloudAnimation)
        assertEquals(value, Settings.parse(settings.json()).cloudAnimation)
        val runtime = HookConfig(settings.bundle())
        assertFalse(runtime.cloudAnimationEnabled)
        assertEquals(750, runtime.cloudAnimationDuration)
        assertEquals(2, runtime.cloudAnimationCurve)
        assertEquals(65, runtime.cloudAnimationBounce)
        val schema = SystemSettingsCatalog.schema().associateBy { it.getString("key") }
        for (key in CloudAnimationSettings.keys) assertNotNull(key, schema[key])
        val changed = SystemSettingsCatalog.patch(settings, "cloud_animation_duration", 460, 0)
        assertEquals(460, changed.cloudAnimation.duration)
        assertThrows(IllegalArgumentException::class.java) { SystemSettingsCatalog.patch(settings, "cloud_animation_duration", 1001, 0) }
    }
    @Test fun oldConfigurationGetsDefaultsAndNewImportIsStrict() {
        val old = JSONObject(Settings().json()).legacyVersion(23)
        assertEquals(CloudAnimationSettings(), Settings.parse(old.toString()).cloudAnimation)
        for (key in CloudAnimationSettings.keys) {
            val root = JSONObject(Settings().json()); root.getJSONObject("settings").remove(key)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
        for ((key, range) in CloudAnimationSettings.ranges) {
            val root = JSONObject(Settings().json());root.getJSONObject("settings").put(key, range.last + 1)
            assertThrows(IllegalArgumentException::class.java) { Settings.parse(root.toString()) }
        }
    }
    private fun motion(curve: Int, bounce: Int, duration: Int = 500) = CloudMotion(
        CloudMorphHook.Frame(0f, 0f, 100f, 80f, 40f, 0f),
        CloudMorphHook.Frame(100f, 0f, 200f, 80f, 40f, 1f),
        null, duration, curve, bounce
    )

    @Test fun actualMotionHasExactEndpointsAndBoundedBounce() {
        for (curve in 0..3) for (bounce in listOf(0, 30, 100)) {
            val motion = motion(curve, bounce)
            assertEquals(0f, motion.frame(0f).x(), 0f)
            assertEquals(100f, motion.frame(1f).x(), 0f)
            var last = 0f
            for (i in 1..1000) {
                val position = motion.frame(i / 1000f).x() / 100f
                assertTrue("curve=$curve bounce=$bounce position=$position", position.isFinite() && position >= 0f && position <= 1.0401f)
                if (bounce == 0) assertTrue(position >= last - .000001f)
                last = position
            }
        }
    }

    @Test fun reboundChangesExcursionWithoutChangingHalfTravelOrDuration() {
        for (curve in 0..3) {
            var previousPeak = 1f
            var halfTravel = -1
            for (bounce in listOf(0, 30, 65, 100)) {
                val motion = motion(curve, bounce)
                var peak = 0f
                var half = -1
                for (i in 0..10000) {
                    val position = motion.frame(i / 10000f).x() / 100f
                    peak = maxOf(peak, position)
                    if (half < 0 && position >= .5f) half = i
                }
                assertTrue(peak >= previousPeak - .000001f)
                if (bounce == 100) assertTrue(peak > 1f)
                if (halfTravel < 0) halfTravel = half
                assertTrue("half travel curve=$curve bounce=$bounce", kotlin.math.abs(half - halfTravel) <= 1)
                val slower = motion(curve, bounce, 1000)
                assertEquals(motion.frame(.35f).x(), slower.frame(.35f).x(), .0001f)
                assertEquals(motion.velocity(.35f).x() / 2, slower.velocity(.35f).x(), .0001f)
                previousPeak = peak
            }
        }
    }
}
