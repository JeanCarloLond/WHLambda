package com.waveheaven.lambda.shared.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.core.domain.consent.NotificationAccess
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.feature.about.AboutKey
import com.waveheaven.lambda.feature.about.AboutScreen
import com.waveheaven.lambda.feature.dashboard.DashboardKey
import com.waveheaven.lambda.feature.dashboard.DashboardRoute
import com.waveheaven.lambda.feature.exposure.DoseExplorerKey
import com.waveheaven.lambda.feature.exposure.DoseExplorerRoute
import com.waveheaven.lambda.feature.hearingcheck.HearingCheckKey
import com.waveheaven.lambda.feature.hearingcheck.HearingCheckRoute
import com.waveheaven.lambda.feature.onboarding.OnboardingKey
import com.waveheaven.lambda.feature.onboarding.OnboardingRoute
import com.waveheaven.lambda.feature.profiles.ProfilesKey
import com.waveheaven.lambda.feature.profiles.ProfilesRoute
import com.waveheaven.lambda.feature.settings.SettingsKey
import com.waveheaven.lambda.feature.settings.SettingsRoute
import kotlinx.coroutines.flow.first
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.koinInject

/**
 * The app's navigation (Navigation 3, ADR-0008). The back stack is a plain list of [NavKey]s that this
 * function owns: navigating means adding or removing keys. Each entry gets its own saved state and
 * ViewModel store, cleared when the entry leaves the back stack.
 */
@Composable
internal fun AppNavigation(
    appVersion: String,
    microphoneAccess: MicrophoneAccess,
    notificationAccess: NotificationAccess,
    modifier: Modifier = Modifier,
) {
    // The first screen depends on a stored preference, so nothing is shown until it has been read (a few ms).
    val preferences = koinInject<UserPreferences>()
    val onboardingCompleted by produceState<Boolean?>(initialValue = null) { value = preferences.onboardingCompleted.first() }
    when (onboardingCompleted) {
        null -> Box(modifier.fillMaxSize())
        else -> AppNavDisplay(appVersion, microphoneAccess, notificationAccess, startAtOnboarding = onboardingCompleted == false, modifier)
    }
}

@Composable
private fun AppNavDisplay(
    appVersion: String,
    microphoneAccess: MicrophoneAccess,
    notificationAccess: NotificationAccess,
    startAtOnboarding: Boolean,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(NavigationConfig, if (startAtOnboarding) OnboardingKey() else DashboardKey)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = appEntries(backStack, appVersion, microphoneAccess, notificationAccess),
    )
}

/** Every destination and how it leads to the others. */
private fun appEntries(
    backStack: NavBackStack<NavKey>,
    appVersion: String,
    microphoneAccess: MicrophoneAccess,
    notificationAccess: NotificationAccess,
): (NavKey) -> NavEntry<NavKey> =
    entryProvider {
        entry<OnboardingKey> { key ->
            OnboardingRoute(
                reviewConsentOnly = key.reviewConsentOnly,
                microphoneAccess = microphoneAccess,
                onFinished = {
                    if (key.reviewConsentOnly) {
                        backStack.removeLastOrNull()
                    } else {
                        // Onboarding is not part of the history: Back from the dashboard leaves the app.
                        backStack.clear()
                        backStack.add(DashboardKey)
                    }
                },
            )
        }
        entry<DashboardKey> {
            DashboardRoute(
                microphoneAccess = microphoneAccess,
                notificationAccess = notificationAccess,
                onOpenExplorer = { backStack.add(DoseExplorerKey) },
                onOpenProfiles = { backStack.add(ProfilesKey) },
                onOpenSettings = { backStack.add(SettingsKey) },
                onOpenAbout = { backStack.add(AboutKey) },
                onReviewConsent = { backStack.add(OnboardingKey(reviewConsentOnly = true)) },
            )
        }
        entry<DoseExplorerKey> {
            DoseExplorerRoute(onBack = { backStack.removeLastOrNull() })
        }
        entry<ProfilesKey> {
            ProfilesRoute(onBack = { backStack.removeLastOrNull() })
        }
        entry<SettingsKey> {
            SettingsRoute(
                onBack = { backStack.removeLastOrNull() },
                onReviewConsent = { backStack.add(OnboardingKey(reviewConsentOnly = true)) },
                onOpenHearingCheck = { backStack.add(HearingCheckKey) },
            )
        }
        entry<HearingCheckKey> {
            HearingCheckRoute(onBack = { backStack.removeLastOrNull() })
        }
        entry<AboutKey> {
            AboutScreen(appVersion = appVersion, onBack = { backStack.removeLastOrNull() })
        }
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
                    subclass(OnboardingKey::class, OnboardingKey.serializer())
                    subclass(DashboardKey::class, DashboardKey.serializer())
                    subclass(DoseExplorerKey::class, DoseExplorerKey.serializer())
                    subclass(AboutKey::class, AboutKey.serializer())
                    subclass(SettingsKey::class, SettingsKey.serializer())
                    subclass(HearingCheckKey::class, HearingCheckKey.serializer())
                    subclass(ProfilesKey::class, ProfilesKey.serializer())
                }
            }
    }
