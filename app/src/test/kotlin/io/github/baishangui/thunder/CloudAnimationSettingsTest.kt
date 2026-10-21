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
    @Test fun allCurvesHaveExactEndpointsAndBounceIsBounded() {
        for (curve in 0..3) for (bounce in listOf(0,30,100)) {
            assertEquals(0f, CloudMorphTiming.progress(0f,curve,bounce),0f)
            assertEquals(1f, CloudMorphTiming.progress(1f,curve,bounce),0f)
            var last=0f
            for(i in 1..100) {
                val p=CloudMorphTiming.progress(i/100f,curve,bounce)
                assertTrue(p>=0f&&p<1.2f)
                if(bounce==0)assertTrue(p>=last)
                last=p
            }
        }
        assertNotEquals(CloudMorphTiming.progress(.5f,0,0),CloudMorphTiming.progress(.5f,0,100))
    }
    @Test fun reboundHasOnePeakAndNeverOscillates() {
        for(curve in 0..3) for(bounce in listOf(1,30,65,100)) {
            val values=(0..10000).map { CloudMorphTiming.progress(it/10000f,curve,bounce) }
            val peak=values.indices.maxBy { values[it] }
            assertTrue(values[peak]>1f)
            assertTrue(peak in 1..9999)
            for(i in 1..peak)assertTrue(values[i]>=values[i-1]-0.000001f)
            for(i in peak+1..10000)assertTrue("second bounce curve=$curve bounce=$bounce", values[i]<=values[i-1]+0.000001f)
        }
    }

}
