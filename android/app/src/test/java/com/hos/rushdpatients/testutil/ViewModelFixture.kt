package com.hos.rushdpatients.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.hos.rushdpatients.ui.components.UiStrings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** Each case owns its dispatcher and cancels all ViewModel collectors on teardown. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ViewModelFixture {
    private val store = ViewModelStore()
    protected val text = mockk<UiStrings> {
        every { this@mockk.invoke(any(), *anyVararg()) } returns "رسالة تجريبية"
    }
    @Before fun installMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun releaseViewModels() {
        try { store.clear() } finally { Dispatchers.resetMain() }
    }
    protected fun <T : ViewModel> keep(model: T): T = model.also {
        store.put(model.javaClass.name, it)
    }
    protected fun clearViewModels() = store.clear()
}
