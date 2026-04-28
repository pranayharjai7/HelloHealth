package com.hellohealth.ui.profile

data class ProfileEditorState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
