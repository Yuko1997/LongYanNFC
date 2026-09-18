package com.longyannfc.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmiiboScreen(
    viewModel: AmiiboViewModel = hiltViewModel()
) {
    val amiiboList by viewModel.amiiboList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloading by viewModel.downloading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val cacheDir = context.cacheDir

    LaunchedEffect(Unit) {
        viewModel.loadAmiiboList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amiibo Manager") },
                actions = {
                    IconButton(onClick = { viewModel.loadAmiiboList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (amiiboList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("暂无本地 Amiibo")
                    Button(onClick = { viewModel.loadAmiiboList() }) {
                        Text("刷新")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(amiiboList) { amiibo ->
                        AmiiboCard(
                            amiibo = amiibo,
                            isDownloading = downloading.contains(amiibo.id),
                            onDownload = { viewModel.downloadAmiibo(amiibo, cacheDir) },
                            onWrite = { viewModel.writeAmiibo(amiibo.id, cacheDir) }
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun AmiiboCard(
    amiibo: com.longyannfc.app.data.remote.AmiiboInfo,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onWrite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(amiibo.image)
                    .crossfade(true)
                    .build(),
                contentDescription = amiibo.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Fit
            )
            Text(
                text = amiibo.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(
                    onClick = onWrite,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = "Write", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
