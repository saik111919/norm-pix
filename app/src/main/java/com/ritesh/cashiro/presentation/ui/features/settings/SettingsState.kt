package com.ritesh.cashiro.presentation.ui.features.settings

data class SettingsUiState(
    val importExportMessage: String? = null,
    val exportedBackupFile: java.io.File? = null,
    val isSeeding: Boolean = false,
    val seedMessage: String? = null
)
