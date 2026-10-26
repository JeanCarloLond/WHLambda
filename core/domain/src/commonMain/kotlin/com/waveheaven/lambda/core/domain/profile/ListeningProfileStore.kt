package com.waveheaven.lambda.core.domain.profile

import kotlinx.coroutines.flow.Flow

/**
 * Port: the user's listening profiles, persisted on the device. The built-in ones ([ListeningProfile.BUILT_IN]) are
 * always present and read-only.
 */
public interface ListeningProfileStore {
    /** Built-in profiles first, then the user's, in creation order. */
    public fun profiles(): Flow<List<ListeningProfile>>

    /** The selected profile; [ListeningProfile.DEFAULT] until the user picks one or after the selected one is deleted. */
    public fun activeProfile(): Flow<ListeningProfile>

    /** Creates or updates a user profile. */
    public suspend fun save(profile: ListeningProfile)

    /** Deletes a user profile. */
    public suspend fun delete(id: String)

    /** Selects the profile [id], which must exist. */
    public suspend fun setActive(id: String)
}

/** A built-in profile cannot be changed or deleted. */
public class BuiltInProfileException(
    id: String,
) : IllegalArgumentException("Built-in profile '$id' cannot be changed")
