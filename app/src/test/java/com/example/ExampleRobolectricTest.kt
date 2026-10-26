package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ArAr PDF", appName)
    }

    @Test
    fun `format file size test`() {
        assertEquals("0 B", FileUtils.formatFileSize(0))
        assertTrue(FileUtils.formatFileSize(1024).contains("KB"))
        assertTrue(FileUtils.formatFileSize(1024 * 1024 * 2).contains("MB"))
    }
}
