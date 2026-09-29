package org.umn.ngantriin.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Hands the [AppContainer] to composables so screens can build their own
 * ViewModel without a DI framework or an Activity-wide factory.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap the tree in CompositionLocalProvider.")
}

/**
 * Creates a ViewModel scoped to the current nav entry, built from the
 * container.
 *
 * `key` matters when the same screen appears more than once in the back
 * stack with different arguments — pass the argument so each gets its own
 * instance.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } }
    )
}
