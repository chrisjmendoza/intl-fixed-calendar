package io.github.chrisjmendoza.yearal.feature.events.notification

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import javax.inject.Inject

/**
 * Whether the device needs a runtime grant before the app can post a notification (FEATURES E4, P2).
 * Abstracted so [io.github.chrisjmendoza.yearal.feature.events.editor.EventEditorViewModel]'s
 * `POST_NOTIFICATIONS` state machine is testable at the ViewModel level without Robolectric — a fake
 * implementation stands in for [Build.VERSION.SDK_INT] in tests.
 */
public fun interface NotificationPermissionGate {
    /**
     * `true` on API 33 (`Build.VERSION_CODES.TIRAMISU`) and above, where posting a notification needs
     * the runtime `POST_NOTIFICATIONS` permission; `false` below it, where notifications are on by
     * default and nothing should be requested (`docs/security-and-privacy.md`, "Permissions by
     * release").
     */
    public fun needsRuntimePermission(): Boolean
}

/** The real gate, reading [Build.VERSION.SDK_INT]. */
internal class AndroidNotificationPermissionGate
    @Inject
    constructor() : NotificationPermissionGate {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
        override fun needsRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
