package com.waveheaven.lambda.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.waveheaven.lambda.feature.about.AboutKey
import com.waveheaven.lambda.feature.about.AboutScreen
import com.waveheaven.lambda.feature.exposure.DoseExplorerKey
import com.waveheaven.lambda.feature.exposure.DoseExplorerRoute
import com.waveheaven.lambda.feature.settings.SettingsKey
import com.waveheaven.lambda.feature.settings.SettingsRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The app's navigation (Navigation 3, ADR-0008). The back stack is a plain list of [NavKey]s that this
 * function owns: navigating means adding or removing keys. Each entry gets its own saved state and
 * ViewModel store, cleared when the entry leaves the back stack.
 */
@Composable
internal fun AppNavigation(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(NavigationConfig, DoseExplorerKey)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<DoseExplorerKey> {
                    DoseExplorerRoute(
                        onOpenAbout = { backStack.add(AboutKey) },
                        onOpenSettings = { backStack.add(SettingsKey) },
                    )
                }
                entry<SettingsKey> {
                    SettingsRoute(onBack = { backStack.removeLastOrNull() })
                }
                entry<AboutKey> {
                    AboutScreen(appVersion = appVersion, onBack = { backStack.removeLastOrNull() })
                }
            },
    )
}

/**
 * Outside Android, saving the back stack needs every destination registered explicitly.
 * Add new feature keys here.
 */
private val NavigationConfig =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(DoseExplorerKey::class, DoseExplorerKey.serializer())
                    subclass(AboutKey::class, AboutKey.serializer())
                    subclass(SettingsKey::class, SettingsKey.serializer())
                }
            }
    }
