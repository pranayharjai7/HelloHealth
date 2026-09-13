package com.hellohealth.ui.profile

import com.hellohealth.domain.model.UserProfile

/**
 * UX state for the profile editor. [profile] is the full row loaded when the editor opens, so the
 * new full-screen vitals editor (P0.5 Step 10b) can seed its fields from what's already stored;
 * [isLoading] guards the brief fetch and [loaded] marks that the fetch SUCCEEDED (distinct from a
 * brand-new user whose [profile] is legitimately null). The editor refuses to save until [loaded]
 * is true, so a failed load can't let an empty draft clobber a live row. The name-only [AlertDialog]
 * path ignores these and just uses [isSaving]/[error]/[successMessage] as before.
 */
data class ProfileEditorState(
    val isLoading: Boolean = false,
    val loaded: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val profile: UserProfile? = null
)
