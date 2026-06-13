package com.kuroshimira.nyanpasu

import android.os.Build
import com.kuroshimira.nyanpasu.network.isNetworkUsable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {

    @Test
    fun usableWhenValidatedOnM() {
        assertTrue(
            isNetworkUsable(
                hasInternet = true,
                hasValidated = true,
                sdkInt = Build.VERSION_CODES.M,
            ),
        )
    }

    @Test
    fun usableWhenInternetOnlyOnM() {
        assertTrue(
            isNetworkUsable(
                hasInternet = true,
                hasValidated = false,
                sdkInt = Build.VERSION_CODES.M,
            ),
        )
    }

    @Test
    fun notUsableWithoutInternet() {
        assertFalse(
            isNetworkUsable(
                hasInternet = false,
                hasValidated = false,
                sdkInt = Build.VERSION_CODES.M,
            ),
        )
    }

    @Test
    fun preM_withInternet() {
        assertTrue(
            isNetworkUsable(
                hasInternet = true,
                hasValidated = false,
                sdkInt = Build.VERSION_CODES.LOLLIPOP,
            ),
        )
    }
}
