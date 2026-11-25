package com.s25fe.pdfviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingPdfUri = extractPdfUri(intent)

        setContent {
            val useDark = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (useDark) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                PdfReadAndShareScreen(
                    initialUri = incomingPdfUri,
                    onUriChanged = { incomingPdfUri = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractPdfUri(intent)?.let { incomingPdfUri = it }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReadAndShareScreen(
    initialUri: Uri?,
    onUriChanged: (Uri?) -> Unit
) {
    val context = LocalContext.current
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var fileName by remember { mutableStateOf("Файл не выбран") }
    var isLoading by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    fun setZoomClamped(targetScale: Float) {
        val next = targetScale.coerceIn(1f, 4f)
        zoomScale = next
        if (next <= 1.01f) {
            zoomScale = 1f
            panX = 0f
            panY = 0f
        } else {
            val maxX = (viewportSize.width * (next - 1f)) / 2f
            val maxY = (viewportSize.height * (next - 1f)) / 2f
            panX = panX.coerceIn(-maxX, maxX)
            panY = panY.coerceIn(-maxY, maxY)
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUriChanged(uri)
        }
    }

    LaunchedEffect(initialUri) {
        val uri = initialUri ?: return@LaunchedEffect
        isLoading = true
        zoomScale = 1f
        panX = 0f
        panY = 0f
        fileName = resolveDisplayName(context, uri)
        pdfPages = renderPdfPages(context, uri)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (initialUri == null) "PDF Лист" else fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (pdfPages.isNotEmpty()) {
                            Text(
                                text = "Только чтение · ${pdfPages.size} стр. · ${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (pdfPages.isNotEmpty()) {
                        IconButton(onClick = { setZoomClamped(zoomScale - 0.5f) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Уменьшить")
                        }
                        TextButton(onClick = { setZoomClamped(1f) }) {
                            Text("${(zoomScale * 100).roundToInt()}%")
                        }
                        IconButton(onClick = { setZoomClamped(zoomScale + 0.5f) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Увеличить")
                        }
                    }
                    IconButton(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = "Открыть PDF")
                    }
                }
            )
        },
        bottomBar = {
            if (initialUri != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Другой PDF")
                        }
                        Button(
                            onClick = {
                                initialUri?.let { sharePdfToApps(context, it, fileName) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Переслать PDF")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                initialUri == null || pdfPages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Откройте PDF для чтения",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Безопасный просмотр без редактирования и быстрая пересылка в Telegram, WhatsApp или по почте.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Выбрать PDF-файл")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.1f) {
                                            setZoomClamped(1f)
                                        } else {
                                            setZoomClamped(2.2f)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.count { it.pressed }
                                        if (pointerCount >= 2 || zoomScale > 1.01f) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            if (zoomChange != 1f || (zoomScale > 1.01f && (panChange.x != 0f || panChange.y != 0f))) {
                                                val nextScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                                zoomScale = nextScale
                                                if (nextScale <= 1.01f) {
                                                    zoomScale = 1f
                                                    panX = 0f
                                                    panY = 0f
                                                } else {
                                                    val maxX = (viewportSize.width * (nextScale - 1f)) / 2f
                                                    val maxY = (viewportSize.height * (nextScale - 1f)) / 2f
                                                    panX = (panX + panChange.x).coerceIn(-maxX, maxX)
                                                    panY = (panY + panChange.y).coerceIn(-maxY, maxY)
                                                }
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panX,
                                translationY = panY
                            ),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(pdfPages) { index, bitmap ->
                            Card(
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Страница ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            return cursor.getString(nameIndex) ?: "document.pdf"
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
}

private suspend fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
    val result = mutableListOf<Bitmap>()
    val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext result
    pfd.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val maxWidth = (context.resources.displayMetrics.widthPixels * 1.75f).toInt().coerceIn(1080, 2400)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = maxWidth.toFloat() / page.width.toFloat()
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(maxWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bitmap)
                }
            }
        }
    }
    result
}

private fun sharePdfToApps(context: Context, sourceUri: Uri, fileName: String) {
    try {
        val shareDir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        shareDir.listFiles()?.forEach { it.delete() }

        val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val cachedFile = File(shareDir, safeName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(cachedFile).use { output ->
                input.copyTo(output)
            }
        }

        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, safeName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(sendIntent, "Переслать PDF через"))
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось подготовить файл для отправки", Toast.LENGTH_SHORT).show()
    }
}
