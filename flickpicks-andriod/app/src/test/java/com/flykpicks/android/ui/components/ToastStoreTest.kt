package com.flykpicks.android.ui.components

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastStoreTest {

    @Test
    fun toast_clearsAfterTwoSeconds() = runTest {
        val store = ToastStore(backgroundScope)

        store.show("Password changed successfully")
        assertEquals(ToastMessage("Password changed successfully"), store.current.value)

        advanceTimeBy(1_999)
        assertEquals(ToastMessage("Password changed successfully"), store.current.value)
        advanceTimeBy(2)
        assertNull(store.current.value)
    }

    @Test
    fun newToast_replacesTheCurrentOne_andRestartsTheTimer() = runTest {
        val store = ToastStore(backgroundScope)

        store.show("Session signed out")
        advanceTimeBy(1_500)
        store.show("All other sessions revoked")
        assertEquals(ToastMessage("All other sessions revoked"), store.current.value)

        // The first toast's timer would have fired at 2 s.
        advanceTimeBy(1_000)
        assertEquals(ToastMessage("All other sessions revoked"), store.current.value)
        advanceTimeBy(1_001)
        assertNull(store.current.value)
    }
}
