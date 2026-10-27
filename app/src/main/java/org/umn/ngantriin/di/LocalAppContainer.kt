package org.umn.ngantriin.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Memberikan [AppContainer] ke composable supaya layar bisa membuat
 * ViewModel sendiri tanpa framework DI atau factory tingkat Activity.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided. Wrap the tree in CompositionLocalProvider.")
}

/**
 * Membuat ViewModel dengan scope nav entry saat ini, dibangun dari container.
 *
 * `key` penting kalau layar yang sama muncul lebih dari sekali di back
 * stack dengan argumen berbeda. Kirim argumennya supaya masing-masing
 * dapat instance sendiri.
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
