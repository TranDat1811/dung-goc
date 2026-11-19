package vn.dunggoc.camera.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vn.dunggoc.camera.CameraViewModel
import vn.dunggoc.camera.camera.CameraSession
import vn.dunggoc.guidance.*

val Lime = Color(0xFFC9F27B)
private val Ink = Color(0xFF101411)
private val Panel = Color(0xFF1C231E)
private val Muted = Color(0xFFA7B2A8)
private val Paper = Color(0xFFF3F5EC)

@Composable
fun DungGocApp(model: CameraViewModel = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink,
        surface = Panel, onSurface = Paper, onBackground = Paper, secondary = Lime)) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        var asked by rememberSaveable { mutableStateOf(false) }
        var help by rememberSaveable { mutableStateOf(false) }
        val preferences by model.preferences.collectAsStateWithLifecycle()
        val photo by model.photoPath.collectAsStateWithLifecycle()
        val message by model.message.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
        val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
            if (uri != null) model.save(uri)
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    active = true
                } else if (event == Lifecycle.Event.ON_PAUSE) active = false
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(message) {
            message?.let { snackbar.showSnackbar(it); model.clearMessage() }
        }
        Scaffold(containerColor = Ink, snackbarHost = { SnackbarHost(snackbar) }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrandMark(Modifier.size(30.dp))
                        Text("đúng góc", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                    }
                    TextButton(onClick = { help = true }) { Text("Cách dùng  ↗", color = Muted) }
                }
                when {
                    photo != null -> PhotoReview(model, onSave = {
                        if (Build.VERSION.SDK_INT >= 29) model.save()
                        else documentLauncher.launch(model.photos.suggestedName())
                    }, modifier = Modifier.weight(1f))
                    !permission -> PermissionGate(asked, onRequest = {
                        val permanentlyDenied = asked && !ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, Manifest.permission.CAMERA)
                        if (permanentlyDenied) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        } else {
                            asked = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }, modifier = Modifier.weight(1f))
                    else -> ShootingScreen(preferences, active, model, Modifier.weight(1f))
                }
            }
        }
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Một chút hướng dẫn") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("1. Chọn ảnh toàn thân hoặc bán thân, rồi chọn dáng bạn thích.")
                Text("2. Làm theo từng câu nhắc. Nhãn “Người chụp” dành cho người cầm máy; “Người tạo dáng” dành cho người trong ảnh.")
                Text("3. Giữ yên khi khung chuyển xanh, rồi bấm chụp. Bạn cũng có thể chụp bất kỳ lúc nào.")
                Text("Khung xanh xác nhận đã căn chỉnh theo gợi ý. Hãy chọn khoảnh khắc bạn thích nhất.", color = Muted)
                Text("Ảnh được xử lý trên điện thoại và chỉ lưu vào thư viện khi bạn chọn lưu.", color = Muted)
            } }, confirmButton = { TextButton(onClick = { help = false }) { Text("Mình hiểu rồi") } })
    }
}

@Composable
private fun ShootingScreen(preferences: Preferences, active: Boolean, model: CameraViewModel, modifier: Modifier) {
    var choosePose by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    // Invalidate prior instructions immediately after a preference change or resume.
    var guide by remember(preferences, active, retry) { mutableStateOf(Guidance(Code.NO_PERSON, Actor.CAMERA, "Đưa một người vào khung hình")) }
    var lastGuideAt by remember(preferences, active, retry) { mutableLongStateOf(0L) }
    var session by remember { mutableStateOf<CameraSession?>(null) }
    var ready by remember(active) { mutableStateOf(false) }
    var capturing by remember(active) { mutableStateOf(false) }
    var cameraError by remember(active) { mutableStateOf<String?>(null) }
    var analysisError by remember(active) { mutableStateOf(false) }
    LaunchedEffect(preferences, active, retry) {
        while (active) {
            delay(300)
            if (lastGuideAt != 0L && SystemClock.elapsedRealtime() - lastGuideAt > 1_500) {
                guide = Guidance(Code.UNCLEAR, Actor.CAMERA, "Chưa nhận diện rõ. Giữ máy yên một chút.")
                lastGuideAt = 0L
            }
        }
    }
    val takePhoto: () -> Unit = {
        if (!capturing && ready) {
            try {
                val file = model.photos.newCapture()
                capturing = true
                session?.takePhoto(file, {
                    capturing = false
                    model.captured(it)
                }, {
                    capturing = false
                    model.notify("Chưa chụp được ảnh. Hãy thử lại.")
                }) ?: run { file.delete(); capturing = false }
            } catch (_: Exception) {
                capturing = false
                model.notify("Không đủ bộ nhớ để chụp ảnh. Hãy giải phóng dung lượng.")
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val landscape = maxWidth > maxHeight
        val cameraContent: @Composable (Modifier) -> Unit = { cameraModifier ->
            Box(cameraModifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                if (active) CameraPreview(preferences, retry,
                    onSession = { session = it; ready = false }, onGuidance = { guide = it; lastGuideAt = SystemClock.elapsedRealtime() },
                    onReady = { ready = true }, onError = { cameraError = it },
                    onAnalysisError = { analysisError = true }, modifier = Modifier.fillMaxSize())
                if (ready && cameraError == null && !analysisError) CameraOverlay(guide, preferences.shot, Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.TopStart).padding(14.dp), color = Ink.copy(alpha = 0.72f), shape = CircleShape) {
                    Text("●  ${preferences.shot.label}", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Paper, fontSize = 12.sp)
                }
                if (!ready && cameraError == null) Text(if (active) "Đang mở camera…" else "Camera đang nghỉ",
                    Modifier.align(Alignment.Center).padding(20.dp), color = Paper)
                if (cameraError != null || analysisError) {
                    Column(Modifier.align(Alignment.Center).padding(20.dp).background(Ink.copy(alpha = 0.92f), RoundedCornerShape(16.dp)).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(cameraError ?: "Chưa bật được hướng dẫn. Bạn vẫn có thể chụp ảnh.", textAlign = TextAlign.Center)
                        Button(onClick = { cameraError = null; analysisError = false; ready = false; retry++ }) { Text("Thử lại") }
                    }
                }
            }
        }
        val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
            Column(controlsModifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShotType.entries.forEach { shot ->
                        FilterChip(selected = preferences.shot == shot,
                            onClick = { model.setPreferences(preferences.copy(shot = shot)) }, label = { Text(shot.label) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lime, selectedLabelColor = Ink))
                    }
                }
                GuideCard(if (analysisError || cameraError != null) null else guide)
                Surface(onClick = { choosePose = true }, color = Panel, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PoseIllustration(preferences.pose, preferences.shot, Modifier.width(40.dp).height(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (preferences.coachPose) "DÁNG ĐANG CHỌN" else "CHỈ CĂN BỐ CỤC", fontSize = 10.sp, letterSpacing = 1.4.sp, color = Muted)
                            Text(if (preferences.coachPose) preferences.pose.label else "Tạo dáng theo ý bạn", fontWeight = FontWeight.Medium)
                        }
                        Text("Đổi  ›", color = Lime, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Bạn chọn khoảnh khắc.", fontSize = 13.sp)
                        Text("Mình giúp bạn căn máy.", color = Muted, fontSize = 12.sp)
                    }
                    Shutter(enabled = ready && active && !capturing && cameraError == null, capturing = capturing, onClick = takePhoto)
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                cameraContent(Modifier.weight(1f).fillMaxHeight())
                controls(Modifier.widthIn(max = 320.dp).weight(0.85f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cameraContent(Modifier.fillMaxWidth().weight(1f))
                // A fixed control region keeps new instruction text from resizing
                // the shared camera ViewPort and restarting the camera session.
                controls(Modifier.fillMaxWidth().height(maxHeight * 0.5f))
            }
        }
    }
    if (choosePose) PosePicker(preferences, onSelect = { model.setPreferences(it); choosePose = false }, onDismiss = { choosePose = false })
}

@Composable
private fun GuideCard(guide: Guidance?) {
    val color = if (guide?.ready == true) Lime else Paper
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(guide?.actor?.label?.uppercase() ?: "BẠN CHỌN KHOẢNH KHẮC",
            fontSize = 10.sp, letterSpacing = 1.8.sp, color = if (guide?.ready == true) Lime else Muted)
        Text(guide?.text ?: "Chụp theo cách bạn thích", fontSize = 20.sp, fontWeight = FontWeight.Medium,
            color = color, lineHeight = 25.sp)
    }
}

@Composable
private fun CameraOverlay(guide: Guidance, shot: ShotType, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val color = if (guide.ready) Lime else Paper.copy(alpha = 0.65f)
        for (i in 1..2) {
            drawLine(Paper.copy(alpha = 0.13f), Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), 1.dp.toPx())
            drawLine(Paper.copy(alpha = 0.13f), Offset(0f, h * i / 3f), Offset(w, h * i / 3f), 1.dp.toPx())
        }
        val left = w * if (shot == ShotType.FULL_BODY) 0.18f else 0.12f
        val right = w - left
        val top = h * 0.08f
        val bottom = h * 0.93f
        val corner = 24.dp.toPx().coerceAtMost(w * 0.1f)
        for ((x, dx) in listOf(left to 1f, right to -1f)) {
            for ((y, dy) in listOf(top to 1f, bottom to -1f)) {
                drawLine(color, Offset(x, y), Offset(x + corner * dx, y), 2.dp.toPx())
                drawLine(color, Offset(x, y), Offset(x, y + corner * dy), 2.dp.toPx())
            }
        }
        if (guide.cue == Cue.LEVEL) {
            drawLine(Lime, Offset(w * 0.25f, h / 2), Offset(w * 0.75f, h / 2), 2.dp.toPx())
            drawCircle(Lime, 5.dp.toPx(), Offset(w / 2, h / 2))
        }
        if (guide.cue in listOf(Cue.LEFT, Cue.RIGHT, Cue.FORWARD, Cue.BACK)) {
            val cx = w / 2
            val cy = h * 0.76f
            val d = 18.dp.toPx()
            drawCircle(Ink.copy(alpha = 0.7f), d * 1.7f, Offset(cx, cy))
            val horizontal = guide.cue == Cue.LEFT || guide.cue == Cue.RIGHT
            val sign = if (guide.cue == Cue.LEFT || guide.cue == Cue.FORWARD) -1 else 1
            val start = if (horizontal) Offset(cx - d * sign, cy) else Offset(cx, cy - d * sign)
            val end = if (horizontal) Offset(cx + d * sign, cy) else Offset(cx, cy + d * sign)
            drawLine(Lime, start, end, 3.dp.toPx())
            val path = Path().apply {
                if (horizontal) {
                    moveTo(end.x - d * sign * 0.55f, end.y - d * 0.55f)
                    lineTo(end.x, end.y)
                    lineTo(end.x - d * sign * 0.55f, end.y + d * 0.55f)
                } else {
                    moveTo(end.x - d * 0.55f, end.y - d * sign * 0.55f)
                    lineTo(end.x, end.y)
                    lineTo(end.x + d * 0.55f, end.y - d * sign * 0.55f)
                }
            }
            drawPath(path, Lime, style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, capturing: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).border(2.dp, if (enabled) Paper else Muted, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (capturing) "Đang chụp ảnh" else "Chụp ảnh" }, contentAlignment = Alignment.Center) {
        if (capturing) CircularProgressIndicator(Modifier.size(32.dp), color = Lime)
        else Box(Modifier.size(62.dp).background(if (enabled) Lime else Muted.copy(alpha = 0.3f), CircleShape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosePicker(preferences: Preferences, onSelect: (Preferences) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Một dáng thật thoải mái", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text("Chọn một dáng để mình hướng dẫn từng bước.", color = Muted)
            PoseStyle.entries.forEach { pose ->
                val selected = preferences.coachPose && preferences.pose == pose
                Surface(onClick = { onSelect(preferences.copy(pose = pose, coachPose = true)) },
                    color = if (selected) Lime.copy(alpha = 0.1f) else Ink,
                    border = if (selected) BorderStroke(1.dp, Lime) else null, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PoseIllustration(pose, preferences.shot, Modifier.width(48.dp).height(76.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pose.label, color = if (selected) Lime else Paper, fontWeight = FontWeight.Medium)
                            Text(pose.instruction, color = Muted, fontSize = 12.sp)
                        }
                        if (selected) Text("✓", color = Lime)
                    }
                }
            }
            OutlinedButton(onClick = { onSelect(preferences.copy(coachPose = false)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Tự tạo dáng, chỉ giúp mình căn máy")
            }
        }
    }
}

@Composable
private fun PermissionGate(asked: Boolean, onRequest: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
        Surface(color = Panel, shape = RoundedCornerShape(32.dp)) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                BrandMark(Modifier.size(118.dp))
                Text("MỘT GÓC NHÌN MỚI", Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Muted, fontSize = 10.sp, letterSpacing = 3.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Ảnh đẹp hơn.\nBắt đầu từ một góc.", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
        Spacer(Modifier.height(14.dp))
        Text(if (asked) "App cần quyền camera để giúp bạn căn khung và tạo dáng. Nếu đã từ chối quyền, bạn có thể bật lại trong cài đặt."
            else "Không cần biết chụp ảnh. Mình sẽ giúp bạn căn khung và gợi ý dáng, ngay trên camera.", color = Muted, lineHeight = 23.sp)
        Spacer(Modifier.height(26.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (asked) "Bật quyền camera  ↗" else "Mở camera  →", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Text("Xử lý trên máy · Dùng được ngoại tuyến", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhotoReview(model: CameraViewModel, onSave: () -> Unit, modifier: Modifier) {
    val bitmap by model.preview.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    BackHandler { if (!saving) model.retake() }
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (saved) "Khoảnh khắc đã được lưu." else "Khoảnh khắc của bạn.", fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text(if (saved) "Tiếp tục tìm một góc nhìn mới nhé." else "Thích bức này? Lưu lại, hoặc thử thêm một dáng.", color = Muted, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Panel), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), "Ảnh vừa chụp", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                ?: Text("Ảnh đã chụp · Có thể lưu ảnh bên dưới", Modifier.padding(24.dp), color = Muted, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { model.retake() }, enabled = !saving, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (saved) "Chụp tiếp" else "Chụp lại")
            }
            Button(onClick = onSave, enabled = !saving && !saved, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (saved) "Đã lưu  ✓" else "Lưu ảnh  ↓")
            }
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.045f).coerceAtLeast(2f)
        drawRoundRect(Lime, Offset(w * 0.05f, h * 0.12f), Size(w * 0.9f, h * 0.76f), CornerRadius(w * 0.17f), style = Stroke(stroke))
        drawCircle(Lime, w * 0.19f, Offset(w * 0.5f, h * 0.5f), style = Stroke(stroke))
        drawCircle(Lime, w * 0.035f, Offset(w * 0.78f, h * 0.32f))
    }
}
