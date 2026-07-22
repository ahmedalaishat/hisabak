package com.hisabak.testutil

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Multiplatform successor to the JUnit4 `MainDispatcherRule`: swaps `Dispatchers.Main` for a
 * [TestDispatcher] around every test so `viewModelScope.launch` is controllable and deterministic.
 * `runTest` picks up the dispatcher's scheduler automatically once Main is a [TestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) {
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}
