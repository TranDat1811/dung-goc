package vn.dunggoc.guidance

import kotlin.math.abs
import kotlin.math.hypot

enum class CaptureMode(val label: String) { AUTO("Tự động"), PERSON("Chụp người"), SCENE("Chụp cảnh") }
enum class Subject(val label: String) { UNKNOWN("Đang quan sát"), PERSON("Chụp người"), SCENE("Chụp cảnh") }
enum class Composition(val label: String, val targetX: Float) {
    AUTO("Tự chọn", .5f), CENTER("Chính giữa", .5f), LEFT_THIRD("Một phần ba trái", 1f / 3f), RIGHT_THIRD("Một phần ba phải", 2f / 3f),
}

data class SceneFeatures(
    val brightness: Float = 1f,
    val focalPoint: Point? = null,
    val horizontalLineY: Float? = null,
)

data class CompositionPlan(
    val subject: Subject,
    val composition: Composition,
    val targetX: Float,
    val targetY: Float = .5f,
    val horizontalLineY: Float? = null,
    val aspectRatio: Float = .75f,
) {
    val title: String get() = when (subject) {
        Subject.UNKNOWN -> "Đang tìm bố cục"
        Subject.PERSON -> "Dáng người · ${composition.label.lowercase()}"
        Subject.SCENE -> "Bố cục cảnh · ${composition.label.lowercase()}"
    }
}

data class CameraAnalysis(val guidance: Guidance, val plan: CompositionPlan)

/** Time-based switching prevents a missed pose frame from changing shooting mode. */
class SubjectSelector {
    private var current = Subject.UNKNOWN
    private var candidate = Subject.UNKNOWN
    private var since = -1L
    private var lastTime = -1L

    fun update(mode: CaptureMode, seesPerson: Boolean, nowMs: Long): Subject {
        if (lastTime >= 0 && (nowMs < lastTime || nowMs - lastTime > 1_500)) {
            current = Subject.UNKNOWN
            candidate = Subject.UNKNOWN
            since = -1L
        }
        lastTime = nowMs
        if (mode != CaptureMode.AUTO) {
            current = if (mode == CaptureMode.PERSON) Subject.PERSON else Subject.SCENE
            candidate = current
            since = nowMs
            return current
        }
        val observed = if (seesPerson) Subject.PERSON else Subject.SCENE
        if (candidate != observed) {
            candidate = observed
            since = nowMs
        }
        val wait = if (observed == Subject.PERSON) 500 else if (current == Subject.PERSON) 1_800 else 900
        if (nowMs - since >= wait) current = observed
        return current
    }
}

/** Heuristics for visual emphasis and strong horizontal edges, not semantic scene recognition. */
object SceneComposition {
    fun analyze(pixels: IntArray, width: Int, height: Int): SceneFeatures {
        require(width >= 3 && height >= 3 && pixels.size == width * height)
        val light = FloatArray(pixels.size)
        val saturation = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 255) / 255f
            val g = ((pixels[i] shr 8) and 255) / 255f
            val b = (pixels[i] and 255) / 255f
            light[i] = .2126f * r + .7152f * g + .0722f * b
            saturation[i] = maxOf(r, g, b) - minOf(r, g, b)
        }
        val mean = light.average().toFloat()
        val weights = FloatArray(36)
        val counts = IntArray(36)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val gradient = abs(light[i + 1] - light[i - 1]) + abs(light[i + width] - light[i - width])
            val weight = gradient * .65f + abs(light[i] - mean) * .25f + saturation[i] * .1f
            val tile = (y * 6 / height).coerceAtMost(5) * 6 + (x * 6 / width).coerceAtMost(5)
            weights[tile] += weight
            counts[tile]++
        }
        for (i in weights.indices) weights[i] /= counts[i].coerceAtLeast(1)
        val best = weights.indices.maxBy { weights[it] }
        val peak = weights[best]
        val contrast = if (peak > 0f) (peak - weights.average().toFloat()) / peak else 0f
        val focal = if (peak > .04f && contrast > .3f) Point((best % 6 + .5f) / 6f, (best / 6 + .5f) / 6f, contrast) else null
        var bestRow: Int? = null
        var bestScore = .06f
        for (y in maxOf(1, height / 5) until minOf(height - 1, height * 4 / 5)) {
            var strength = 0f
            var coherent = 0
            var samples = 0
            for (x in width / 8 until width * 7 / 8) {
                val edge = abs(light[(y + 1) * width + x] - light[(y - 1) * width + x])
                strength += edge
                if (edge > .08f) coherent++
                samples++
            }
            val score = strength / samples.coerceAtLeast(1)
            if (coherent > samples * .55f && score > bestScore) {
                bestScore = score
                bestRow = y
            }
        }
        return SceneFeatures(mean, focal, bestRow?.let { it.toFloat() / height })
    }
}

/** Chooses a stable target layout first, then coaches toward that target. */
class CompositionCoach {
    private var selector = SubjectSelector()
    private val portrait = GuidanceCoach()
    private var lastPreferences: Preferences? = null
    private var plan: CompositionPlan? = null
    private var stableSince: Long? = null
    private var lastTime = -1L
    private var anchor: Point? = null
    private var lineAnchor: Float? = null
    private var plannedWithDetails = false

    fun update(frame: Observation, scene: SceneFeatures, preferences: Preferences, nowMs: Long): CameraAnalysis {
        val changed = lastPreferences != preferences
        val modeChanged = lastPreferences?.captureMode != preferences.captureMode
        val interrupted = lastTime >= 0 && (nowMs < lastTime || nowMs - lastTime > 1_500)
        if (changed || interrupted) {
            portrait.reset()
            stableSince = null
            anchor = null
            lineAnchor = null
            plan = null
            plannedWithDetails = false
            if (modeChanged) selector = SubjectSelector()
        }
        lastTime = nowMs
        lastPreferences = preferences
        val seesPerson = frame.bodies.any { body ->
            listOf(Joint.NOSE, Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP)
                .count { body[it].visible() } >= 3
        }
        val subject = selector.update(preferences.captureMode, seesPerson, nowMs)
        val hasDetails = scene.focalPoint != null || scene.horizontalLineY != null
        val currentPlan = plan?.takeIf { it.subject == subject && !(subject == Subject.SCENE && !plannedWithDetails && hasDetails) }
            ?: makePlan(subject, frame, scene, preferences).also {
            plan = it
            plannedWithDetails = hasDetails
            portrait.reset()
            stableSince = null
            anchor = null
            lineAnchor = null
        }
        val guide = when (subject) {
            Subject.UNKNOWN -> Guidance(Code.SEARCHING, Actor.STATUS, "Giữ máy một chút để chọn bố cục…")
            Subject.PERSON -> portrait.update(frame, preferences, nowMs, currentPlan.targetX,
                if (preferences.shot == ShotType.FULL_BODY) .83f else .76f)
            Subject.SCENE -> sceneGuidance(frame, scene, currentPlan, nowMs)
        }
        return CameraAnalysis(guide, currentPlan)
    }

    private fun makePlan(subject: Subject, frame: Observation, scene: SceneFeatures, prefs: Preferences): CompositionPlan {
        val person = frame.bodies.firstOrNull()
        val personX = person?.let { (it[Joint.LEFT_SHOULDER].x + it[Joint.RIGHT_SHOULDER].x) / 2f } ?: .5f
        val suggestedX = if (subject == Subject.PERSON) personX else scene.focalPoint?.x ?: .5f
        val composition = if (prefs.composition != Composition.AUTO) prefs.composition else when {
            suggestedX < .43f -> Composition.LEFT_THIRD
            suggestedX > .57f -> Composition.RIGHT_THIRD
            else -> Composition.CENTER
        }
        val line = if (subject == Subject.SCENE) scene.horizontalLineY?.let { if (it < .5f) 1f / 3f else 2f / 3f } else null
        val y = scene.focalPoint?.y?.let { if (it < .5f) 1f / 3f else 2f / 3f } ?: .5f
        return CompositionPlan(subject, composition, composition.targetX, if (subject == Subject.SCENE) y else .5f, line,
            frame.aspectRatio)
    }

    private fun sceneGuidance(frame: Observation, scene: SceneFeatures, target: CompositionPlan, nowMs: Long): Guidance {
        fun advice(code: Code, text: String, cue: Cue = Cue.NONE): Guidance {
            stableSince = null
            anchor = null
            lineAnchor = null
            return Guidance(code, Actor.CAMERA, text, cue)
        }
        if (scene.brightness < .08f) return advice(Code.DARK, "Tìm vùng sáng hơn để nhìn rõ bố cục")
        if (frame.rollDegrees?.let { abs(it) > 5f } == true) return advice(Code.LEVEL, "Giữ máy thẳng theo đường ngang", Cue.LEVEL)
        val lineY = scene.horizontalLineY
        val targetLine = target.horizontalLineY
        if (targetLine != null && lineY == null) return advice(Code.COMPOSE, "Chưa thấy rõ đường ngang. Căn theo nét đứt hoặc đổi bố cục")
        if (targetLine != null && lineY != null && abs(lineY - targetLine) > .06f) {
            return advice(if (lineY < targetLine) Code.PAN_UP else Code.PAN_DOWN,
                if (lineY < targetLine) "Hướng máy lên để đường ngang khớp nét đứt" else "Hướng máy xuống để đường ngang khớp nét đứt",
                if (lineY < targetLine) Cue.UP else Cue.DOWN)
        }
        val focal = scene.focalPoint
        if (focal != null && abs(focal.x - target.targetX) > .10f) {
            return advice(if (focal.x < target.targetX) Code.PAN_LEFT else Code.PAN_RIGHT,
                if (focal.x < target.targetX) "Dịch máy sang trái, đưa điểm nhấn vào vòng mờ" else "Dịch máy sang phải, đưa điểm nhấn vào vòng mờ",
                if (focal.x < target.targetX) Cue.LEFT else Cue.RIGHT)
        }
        if (focal != null && abs(focal.y - target.targetY) > .10f) {
            return advice(if (focal.y < target.targetY) Code.PAN_UP else Code.PAN_DOWN,
                if (focal.y < target.targetY) "Hướng máy lên để điểm nhấn khớp vòng mờ" else "Hướng máy xuống để điểm nhấn khớp vòng mờ",
                if (focal.y < target.targetY) Cue.UP else Cue.DOWN)
        }
        // Never certify a scene based solely on absence of people or a level phone.
        if (focal == null && (lineY == null || targetLine == null)) {
            return advice(Code.COMPOSE, "Đặt điểm bạn muốn nhấn vào vòng mờ, rồi chọn khoảnh khắc")
        }
        val moved = stableSince == null ||
            (focal != null && anchor == null) || (focal == null && anchor != null) ||
            (focal != null && anchor?.let { hypot((focal.x - it.x) * frame.aspectRatio, focal.y - it.y) > .025f } == true) ||
            (lineY != null && lineAnchor?.let { abs(lineY - it) > .025f } == true)
        if (moved) {
            stableSince = nowMs
            anchor = focal
            lineAnchor = lineY
        }
        return if (nowMs - (stableSince ?: nowMs) >= 1_000) Guidance(Code.READY, Actor.STATUS, "Bố cục đã khớp gợi ý · Có thể chụp", Cue.CHECK)
            else Guidance(Code.HOLD, Actor.STATUS, "Giữ bố cục một chút…")
    }
}

/** Shared normalized targets for overlay drawing, independent of the detected body. */
object PoseTemplate {
    fun body(pose: PoseStyle, shot: ShotType, centerX: Float, aspectRatio: Float): Body {
        val points = MutableList(33) { Point(0f, 0f, 0f) }
        fun set(joint: Int, x: Float, y: Float) { points[joint] = Point(x, y) }
        set(Joint.NOSE, .5f, .17f)
        set(Joint.LEFT_EAR, .45f, .16f); set(Joint.RIGHT_EAR, .55f, .16f)
        set(Joint.LEFT_SHOULDER, .4f, .30f); set(Joint.RIGHT_SHOULDER, .6f, .30f)
        set(Joint.LEFT_ELBOW, .38f, .47f); set(Joint.RIGHT_ELBOW, .62f, .47f)
        set(Joint.LEFT_WRIST, .39f, .64f); set(Joint.RIGHT_WRIST, .61f, .64f)
        set(Joint.LEFT_HIP, .43f, .58f); set(Joint.RIGHT_HIP, .57f, .58f)
        set(Joint.LEFT_ANKLE, .44f, .88f); set(Joint.RIGHT_ANKLE, .56f, .88f)
        set(Joint.LEFT_FOOT, .43f, .90f); set(Joint.RIGHT_FOOT, .58f, .90f)
        when (pose) {
            PoseStyle.HAND_ON_HIP -> { set(Joint.LEFT_ELBOW, .30f, .44f); set(Joint.LEFT_WRIST, .43f, .58f) }
            PoseStyle.HAND_AT_WAIST -> set(Joint.LEFT_WRIST, .50f, .51f)
            PoseStyle.TOUCH_HAIR -> { set(Joint.LEFT_ELBOW, .30f, .28f); set(Joint.LEFT_WRIST, .44f, .16f) }
            PoseStyle.OPEN_ARMS -> { set(Joint.LEFT_WRIST, .30f, .64f); set(Joint.RIGHT_WRIST, .70f, .64f) }
            PoseStyle.NATURAL -> Unit
        }
        return Body(points.map { point ->
            if (point.confidence == 0f) point else {
                val scale = if (shot == ShotType.HALF_BODY) 1.5f else 1f
                point.copy(x = centerX + (point.x - .5f) * .75f / aspectRatio.coerceIn(.3f, 3f) * scale,
                    y = if (shot == ShotType.HALF_BODY) .05f + (point.y - .0725f) * scale else point.y)
            }
        })
    }
}
