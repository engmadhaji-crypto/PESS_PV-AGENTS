package com.example

import android.app.Application
import com.example.data.ai.GeminiInstallmentAssistant
import com.example.data.firebase.FirestoreInstallmentDataSource
import com.example.data.local.AppDatabase
import com.example.data.network.NetworkMonitor
import com.example.data.pdf.PdfInvoiceGenerator
import com.example.data.repository.InstallmentRepositoryImpl
import com.example.data.sync.GoogleAuthManager
import com.example.data.sync.GoogleSheetsSyncManager
import com.example.domain.repository.InstallmentRepository
import com.google.firebase.firestore.FirebaseFirestore

class InstallmentApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }
    val googleAuthManager: GoogleAuthManager by lazy { GoogleAuthManager(this) }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    val firestoreDataSource: FirestoreInstallmentDataSource by lazy {
        FirestoreInstallmentDataSource(firestore)
    }

    val sheetsSyncManager: GoogleSheetsSyncManager by lazy {
        GoogleSheetsSyncManager(
            networkMonitor = networkMonitor,
            appName = getString(R.string.app_name)
        )
    }

    val repository: InstallmentRepository by lazy {
        InstallmentRepositoryImpl(
            database = database,
            networkMonitor = networkMonitor,
            sheetsSyncManager = sheetsSyncManager,
            firestoreDataSource = firestoreDataSource
        )
    }

    val geminiAssistant: GeminiInstallmentAssistant by lazy { GeminiInstallmentAssistant() }
    val pdfInvoiceGenerator: PdfInvoiceGenerator by lazy { PdfInvoiceGenerator(this) }
}
