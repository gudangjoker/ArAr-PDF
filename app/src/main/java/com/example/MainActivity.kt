package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ScreenTab
import com.example.ui.components.OperationDialog
import com.example.ui.screens.CompressExportScreen
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.PageManagerScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PdfStudioApp()
            }
        }
    }
}

@Composable
fun PdfStudioApp(viewModel: PdfViewModel = viewModel()) {
    val currentTab by viewModel.currentTab.collectAsState()
    val operationState by viewModel.operationState.collectAsState()

    // Determine if bottom navigation bar should be visible
    val showBottomNav = currentTab != ScreenTab.READER

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("app_bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.HOME,
                        onClick = { viewModel.navigateTo(ScreenTab.HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Beranda", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_item_home")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.READER,
                        onClick = { viewModel.navigateTo(ScreenTab.READER) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        label = { Text("Pembaca", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_item_reader")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.PAGE_MANAGER,
                        onClick = { viewModel.navigateTo(ScreenTab.PAGE_MANAGER) },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                        label = { Text("Halaman", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_item_pages")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.MERGE,
                        onClick = { viewModel.navigateTo(ScreenTab.MERGE) },
                        icon = { Icon(Icons.Default.MergeType, contentDescription = null) },
                        label = { Text("Gabung", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_item_merge")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.CONVERT || currentTab == ScreenTab.COMPRESS_EXPORT,
                        onClick = { viewModel.navigateTo(ScreenTab.CONVERT) },
                        icon = { Icon(Icons.Default.Transform, contentDescription = null) },
                        label = { Text("Konversi", fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_item_convert")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showBottomNav) innerPadding else androidx.compose.foundation.layout.PaddingValues())
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { tab ->
                when (tab) {
                    ScreenTab.HOME -> HomeScreen(viewModel)
                    ScreenTab.READER -> ReaderScreen(viewModel)
                    ScreenTab.PAGE_MANAGER -> PageManagerScreen(viewModel)
                    ScreenTab.MERGE -> MergePdfScreen(viewModel)
                    ScreenTab.CONVERT -> ConvertScreen(viewModel)
                    ScreenTab.COMPRESS_EXPORT -> CompressExportScreen(viewModel)
                }
            }
        }

        // Global Operations Progress & Result Dialog
        OperationDialog(
            state = operationState,
            onDismiss = { viewModel.dismissOperationDialog() },
            onOpenPdf = { doc -> viewModel.openInReader(doc) }
        )
    }
}
