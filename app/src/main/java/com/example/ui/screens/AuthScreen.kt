package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.sync.GoogleAuthManager
import com.example.data.sync.GoogleSignInResult
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    activity: Activity?,
    authManager: GoogleAuthManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activity) {
        if (activity != null) {
            authManager.attemptAutoSignIn(activity)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auth_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AuthFeatureRow(
                            icon = Icons.Rounded.CloudDone,
                            title = "مزامنة سحابية فورية",
                            subtitle = "حفظ عقود العملاء والأقساط على Firebase Firestore و Google Sheets"
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "مساعد مالي ذكي Gemini",
                            subtitle = "تحليل مخاطر التحصيل وصياغة رسائل تذكير احترافية"
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.Security,
                            title = "حماية وعزل تام للبيانات",
                            subtitle = "كل حساب معزول بالكامل ولا يمكن لأي مستخدم آخر الوصول لسجلاتك"
                        )
                    }
                }

                errorMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (activity == null) {
                            errorMessage = "تعذّر العثور على نافذة التطبيق لتسجيل الدخول."
                            return@Button
                        }
                        errorMessage = null
                        isLoading = true
                        scope.launch {
                            when (val res = authManager.signInWithGoogle(activity)) {
                                is GoogleSignInResult.Success -> {
                                    isLoading = false
                                }
                                is GoogleSignInResult.Cancelled -> {
                                    isLoading = false
                                    errorMessage = res.reason
                                }
                                is GoogleSignInResult.Failed -> {
                                    isLoading = false
                                    errorMessage = res.message
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("auth_google_signin_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جارٍ تسجيل الدخول...")
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_in_with_google))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
