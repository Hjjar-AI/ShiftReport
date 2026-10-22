package com.hos.rushdpatients.ui.demo

import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** Injected demo repository: one temporary session, with no disk/database/network dependencies. */
@ViewModelScoped
class InMemoryDemoWardRepository @Inject constructor() {
    internal val state = MutableStateFlow(DemoWardState())
}
