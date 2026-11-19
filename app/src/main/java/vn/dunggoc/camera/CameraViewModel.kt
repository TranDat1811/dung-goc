package vn.dunggoc.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.dunggoc.camera.data.PhotoRepository
import vn.dunggoc.guidance.PoseStyle
import vn.dunggoc.guidance.Preferences
import vn.dunggoc.guidance.ShotType
import java.io.File

class CameraViewModel(application: Application, private val state: SavedStateHandle) : AndroidViewModel(application) {
    val photos = PhotoRepository(application)
    private val _preferences = MutableStateFlow(Preferences(
        shot = enumValues<ShotType>().firstOrNull { it.name == state.get<String>("shot") } ?: ShotType.FULL_BODY,
        pose = enumValues<PoseStyle>().firstOrNull { it.name == state.get<String>("pose") } ?: PoseStyle.NATURAL,
        coachPose = state["coachPose"] ?: true,
    ))
    val preferences = _preferences.asStateFlow()
    private val _photoPath = MutableStateFlow(photos.resolve(state["photo"])?.path)
    val photoPath = _photoPath.asStateFlow()
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview = _preview.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _saved = MutableStateFlow(state.get<Boolean>("saved") ?: false)
    val saved = _saved.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        photos.cleanOldFiles(_photoPath.value)
        _photoPath.value?.let { loadPreview(File(it)) }
    }

    fun setPreferences(value: Preferences) {
        _preferences.value = value
        state["shot"] = value.shot.name
        state["pose"] = value.pose.name
        state["coachPose"] = value.coachPose
    }

    fun captured(file: File) {
        photos.delete(_photoPath.value)
        _photoPath.value = file.path
        state["photo"] = file.path
        _saved.value = false
        state["saved"] = false
        loadPreview(file)
    }

    private fun loadPreview(file: File) {
        _preview.value = null
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { photos.preview(file) }
                if (_photoPath.value == file.path) _preview.value = bitmap
            } catch (error: Exception) {
                Log.e("DungGoc", "Cannot load preview", error)
                if (_photoPath.value == file.path) notify("Chưa xem được ảnh. Bạn vẫn có thể lưu hoặc chụp lại.")
            }
        }
    }

    fun save(destination: Uri? = null) {
        if (_saving.value || _saved.value) return
        val file = photos.resolve(_photoPath.value) ?: return
        _saving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { photos.save(file, destination) }
                _saved.value = true
                state["saved"] = true
                notify("Đã lưu ảnh của bạn")
            } catch (error: Exception) {
                Log.e("DungGoc", "Cannot save photo", error)
                notify("Chưa lưu được ảnh. Kiểm tra dung lượng rồi thử lại.")
            } finally {
                _saving.value = false
            }
        }
    }

    fun retake() {
        if (_saving.value) return
        photos.delete(_photoPath.value)
        _photoPath.value = null
        state["photo"] = null
        _preview.value = null
        _saved.value = false
        state["saved"] = false
    }

    fun notify(value: String) { _message.value = value }
    fun clearMessage() { _message.value = null }
}
