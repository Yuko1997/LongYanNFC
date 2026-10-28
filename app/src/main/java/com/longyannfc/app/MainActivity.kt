package com.longyannfc.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.longyannfc.app.ui.LongYanNavHost
import com.longyannfc.app.ui.theme.LongYanNFCTheme
import com.longyannfc.app.utils.BlePermissions
import com.longyannfc.app.utils.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边布局：系统栏与手势区由各页面的 WindowInsets 消费，
        // 避免写死顶部 padding 导致内容被状态栏遮挡。
        enableEdgeToEdge()
        if (!BlePermissions.hasPermissions(this)) {
            permissionLauncher.launch(BlePermissions.requiredPermissions)
        }
        setContent {
            LongYanNFCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    LongYanNavHost()
                }
            }
        }
    }
}
