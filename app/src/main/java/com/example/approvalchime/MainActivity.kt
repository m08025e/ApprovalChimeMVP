package com.example.approvalchime

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(this)
            }
        }
    }
}

@Composable
fun MainScreen(appContext: android.content.Context) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    Column(Modifier.padding(24.dp)) {
        Text("承認音ジェネレーター", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy,
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                scope.launch(Dispatchers.IO) {
                    busy = true
                    try {
                        val pcm = synthEMoneyLike()
                        val name = "Approval_" + System.currentTimeMillis()
                        val uri = savePcmAsWavToMediaStore(appContext, name, pcm)
                        val channelId = "approval_ch"
                        createChannelWithSound(appContext, channelId, "Approval Sound", uri)
                        launch(Dispatchers.Main) { postTestNotification(appContext, channelId) }
                    } finally { busy = false }
                }
            }
        ) {
            Text(if (busy) "生成中…" else "✨ 承認音をつくる")
        }
        Spacer(Modifier.height(8.dp))
        Text("0.4–1.0秒の“電子マネー風”通知音をランダム生成→適用→テスト通知を出します。")
    }
}