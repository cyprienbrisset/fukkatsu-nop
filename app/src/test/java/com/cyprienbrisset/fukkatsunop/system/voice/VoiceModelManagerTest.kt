package com.cyprienbrisset.fukkatsunop.system.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceModelManagerTest {

    @Test
    fun `downloadProgress est null au demarrage`() {
        VoiceModelManager.resetDownloadProgress()
        assertNull(VoiceModelManager.downloadProgress.value)
    }

    @Test
    fun `setDownloadProgress met a jour le flow`() {
        VoiceModelManager.resetDownloadProgress()
        VoiceModelManager.setDownloadProgressForTest(42)
        assertEquals(42, VoiceModelManager.downloadProgress.value)
    }
}
