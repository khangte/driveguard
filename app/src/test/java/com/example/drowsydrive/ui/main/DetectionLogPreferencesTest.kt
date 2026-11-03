package com.example.drowsydrive.ui.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionLogPreferencesTest {
    @Test
    fun recreationRetainsIntentButNewProcessStartsStopped() {
        val retainedStore = ViewModelStore()
        val first = ViewModelProvider(retainedStore, ViewModelProvider.NewInstanceFactory()).get(DetectionLogPreferences::class.java)
        first.wanted = true
        first.scenario = "eyes_closed_2s"

        val recreated = ViewModelProvider(retainedStore, ViewModelProvider.NewInstanceFactory()).get(DetectionLogPreferences::class.java)
        assertSame(first, recreated)
        assertTrue(recreated.wanted)
        assertEquals("eyes_closed_2s", recreated.scenario)

        val freshStore = ViewModelStore()
        val fresh = ViewModelProvider(freshStore, ViewModelProvider.NewInstanceFactory()).get(DetectionLogPreferences::class.java)
        assertFalse(fresh.wanted)
        assertEquals("", fresh.scenario)
        recreated.wanted = false
        assertFalse(ViewModelProvider(retainedStore, ViewModelProvider.NewInstanceFactory()).get(DetectionLogPreferences::class.java).wanted)
        retainedStore.clear()
        freshStore.clear()
    }
}
