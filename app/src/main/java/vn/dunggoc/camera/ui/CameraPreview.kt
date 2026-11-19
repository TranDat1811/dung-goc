package vn.dunggoc.camera.ui

import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import vn.dunggoc.camera.camera.CameraSession
import vn.dunggoc.camera.camera.LevelSensor
import vn.dunggoc.guidance.CameraAnalysis
import vn.dunggoc.guidance.Preferences
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CameraPreview(
    preferences: Preferences,
    retry: Int,
    onSession: (CameraSession?) -> Unit,
    onGuidance: (CameraAnalysis) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onAnalysisError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val prefs = remember { AtomicReference(preferences) }
    val roll = remember { AtomicReference<Float?>(null) }
    val sessionCallback by rememberUpdatedState(onSession)
    val guidanceCallback by rememberUpdatedState(onGuidance)
    val readyCallback by rememberUpdatedState(onReady)
    val errorCallback by rememberUpdatedState(onError)
    val analysisErrorCallback by rememberUpdatedState(onAnalysisError)
    LaunchedEffect(preferences) { prefs.set(preferences) }

    AndroidView(factory = { view }, modifier = modifier.onSizeChanged { size = it })
    DisposableEffect(lifecycleOwner, view, size, retry) {
        if (size.width > 0 && size.height > 0) {
            val rotation = view.display?.rotation ?: Surface.ROTATION_0
            val level = LevelSensor(context, rotation, roll)
            val session = CameraSession(context, lifecycleOwner, view, size.width, size.height, rotation,
                prefs, roll, { guidanceCallback(it) }, { readyCallback() }, { errorCallback(it) }, { analysisErrorCallback() })
            sessionCallback(session)
            onDispose {
                sessionCallback(null)
                level.close()
                session.close()
            }
        } else {
            onDispose { }
        }
    }
}

