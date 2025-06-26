package eu.kanade.tachiyomi.extension.repository.exception

import java.io.IOException

/**
 * Exception related to saving Extension Repositories.
 * Wraps underlying IOExceptions or database constraint issues.
 *
 * @param throwable the source throwable to include for tracing.
 */
class SaveExtensionRepoException(
    throwable: Throwable,
) : IOException("Error Saving Repository to Database", throwable)
