package com.example.luaplayground

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigatorTest {
    @Test fun `navigate emits its route`() = runTest {
        val event = async(UnconfinedTestDispatcher(testScheduler)) { AppNavigator.events.first() }

        AppNavigator.navigate(CONSOLE_ROUTE)

        assertEquals(NavigationEvent.Navigate(CONSOLE_ROUTE), event.await())
    }

    @Test fun `popBackStack emits a back event`() = runTest {
        val event = async(UnconfinedTestDispatcher(testScheduler)) { AppNavigator.events.first() }

        AppNavigator.popBackStack()

        assertEquals(NavigationEvent.PopBackStack, event.await())
    }

    @Test fun `two events buffer while a subscriber is busy`() = runTest {
        val firstReceived = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            AppNavigator.events.collect {
                firstReceived.complete(Unit)
                release.await()
            }
        }

        AppNavigator.navigate("active")
        firstReceived.await()
        AppNavigator.navigate("buffered-one")
        AppNavigator.navigate("buffered-two")
        val overflow = backgroundScope.async { AppNavigator.navigate("overflow") }
        runCurrent()

        assertFalse(overflow.isCompleted)
        release.complete(Unit)
        runCurrent()
        assertTrue(overflow.isCompleted)
    }
}
