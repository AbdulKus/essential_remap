package com.abdulkus.essentialremap

import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellKeyEventSenderPolicyTest {
    @Test
    fun unavailableAndroid16SenderIsAcceptedAfterManifestPermissionCheck() {
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(-1))
    }

    @Test
    fun shellAndPreAndroid14UnavailableSenderAreAccepted() {
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(Process.SHELL_UID))
        assertTrue(ShellKeyEventSenderPolicy.isAllowed(null))
    }

    @Test
    fun concreteAppUidIsRejected() {
        assertFalse(ShellKeyEventSenderPolicy.isAllowed(10_123))
    }
}
