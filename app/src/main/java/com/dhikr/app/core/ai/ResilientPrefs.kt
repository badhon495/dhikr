package com.dhikr.app.core.ai

import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Opens an [android.security.crypto.EncryptedSharedPreferences]-style store,
 * recovering from an unreadable on-disk state.
 *
 * Hardware-backed keystore material does not survive a device-to-device
 * transfer, but Android auto-backup still restores the encrypted prefs file. On
 * the new device the file can no longer be decrypted, and the first read throws
 * [GeneralSecurityException] (an `AEADBadTagException` / `KeyStoreException`) or,
 * when the keyset prefs are what's corrupt, [IOException]. Left unhandled that
 * crashes the app on the screen that touches the store.
 *
 * On either failure this wipes the stored files via [reset] and retries [open]
 * once. The persisted secret is unrecoverable and is lost — the user re-enters
 * it — but the app opens cleanly instead of crashing. A second failure is
 * rethrown; unrelated exceptions are never swallowed and trigger no wipe.
 */
internal inline fun <T> openResettingOnCorruption(reset: () -> Unit, open: () -> T): T =
    try {
        open()
    } catch (first: GeneralSecurityException) {
        reset()
        open()
    } catch (first: IOException) {
        reset()
        open()
    }
