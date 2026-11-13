package com.example.data.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface AuthTokenResult {
    data class Authorized(val accessToken: String) : AuthTokenResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthTokenResult
    data class Failed(val message: String) : AuthTokenResult
}

sealed interface GoogleSignInResult {
    data class Success(val uid: String, val email: String, val displayName: String) : GoogleSignInResult
    data class Cancelled(val reason: String) : GoogleSignInResult
    data class Failed(val message: String) : GoogleSignInResult
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class GoogleAuthManager(private val appContext: Context) {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(appContext)
    }

    // Ephemeral in-memory access token only; never persisted to disk or database.
    @Volatile
    private var cachedAccessToken: String? = null

    val requiredScopes: List<Scope> = listOf(
        Scope(SheetsScopes.SPREADSHEETS),
        Scope(DriveScopes.DRIVE_FILE)
    )

    suspend fun attemptAutoSignIn(activity: Activity): Boolean {
        if (Firebase.auth.currentUser != null) return true
        val clientId = try {
            appContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return false
        }
        if (clientId.isBlank()) return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                authResult.user != null
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
            false
        }
    }

    suspend fun signInWithGoogle(activity: Activity): GoogleSignInResult {
        return try {
            val serverClientId = try {
                appContext.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                return GoogleSignInResult.Failed("إعدادات Google Sign-In غير مكتملة (default_web_client_id).")
            }
            if (serverClientId.isBlank()) {
                return GoogleSignInResult.Failed("معرّف عميل OAuth غير متوفر في إعدادات التطبيق.")
            }

            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
                val firebaseResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = firebaseResult.user
                    ?: return GoogleSignInResult.Failed("تعذّر إتمام المصادقة مع Firebase.")
                GoogleSignInResult.Success(
                    uid = user.uid,
                    email = user.email ?: googleIdCredential.id,
                    displayName = user.displayName ?: googleIdCredential.displayName ?: googleIdCredential.id
                )
            } else {
                GoogleSignInResult.Failed("نوع بيانات الاعتماد المستلم غير مدعوم.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled("تم إلغاء تسجيل الدخول.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In CredentialManager error", e)
            GoogleSignInResult.Failed(e.localizedMessage ?: "تعذّر تسجيل الدخول بحساب Google.")
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInResult.Failed(e.localizedMessage ?: "حدث خطأ غير متوقع أثناء تسجيل الدخول.")
        }
    }

    suspend fun authorizeSheetsAndDrive(activity: Activity): AuthTokenResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(requiredScopes)
                    .build()

                Identity.getAuthorizationClient(activity)
                    .authorize(authRequest)
                    .addOnSuccessListener { authorizationResult ->
                        if (authorizationResult.hasResolution()) {
                            val pendingIntent = authorizationResult.pendingIntent
                            if (pendingIntent != null) {
                                cont.resume(AuthTokenResult.NeedsConsent(pendingIntent))
                            } else {
                                cont.resume(AuthTokenResult.Failed("تعذّر فتح شاشة منح الصلاحيات."))
                            }
                        } else {
                            val token = authorizationResult.accessToken
                            if (!token.isNullOrBlank()) {
                                cachedAccessToken = token
                                cont.resume(AuthTokenResult.Authorized(token))
                            } else {
                                cont.resume(AuthTokenResult.Failed("لم يتم إرجاع رمز وصول صالح."))
                            }
                        }
                    }
                    .addOnFailureListener { ex ->
                        cont.resume(
                            AuthTokenResult.Failed(
                                ex.localizedMessage ?: "فشل التحقق من صلاحيات Google Sheets و Drive."
                            )
                        )
                    }
            }
        }

    fun extractTokenFromConsentResult(activity: Activity, data: Intent?): AuthTokenResult {
        return try {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                cachedAccessToken = token
                AuthTokenResult.Authorized(token)
            } else {
                AuthTokenResult.Failed("لم يتم منح صلاحية الوصول إلى Google Sheets.")
            }
        } catch (e: ApiException) {
            AuthTokenResult.Failed(e.localizedMessage ?: "فشل إتمام التفويض.")
        } catch (e: Exception) {
            AuthTokenResult.Failed(e.localizedMessage ?: "تعذّر استخراج رمز التفويض.")
        }
    }

    fun getCachedToken(): String? = cachedAccessToken

    fun invalidateCachedToken() {
        cachedAccessToken = null
    }

    suspend fun signOut() {
        cachedAccessToken = null
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { Firebase.auth.signOut() }
            }
        }
    }

    private companion object {
        const val TAG = "Auth"
    }
}
