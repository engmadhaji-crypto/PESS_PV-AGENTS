package com.example.domain.model

data class DatabaseBackupPreview(
    val rawJson: String,
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val customerCount: Int,
    val planCount: Int,
    val installmentCount: Int
)

data class DatabaseBackupSummary(
    val customerCount: Int,
    val planCount: Int,
    val installmentCount: Int,
    val exportedAtMillis: Long
)
