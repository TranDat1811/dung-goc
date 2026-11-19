package vn.dunggoc.guidance

import kotlin.math.abs
import kotlin.math.hypot

enum class ShotType(val label: String) { FULL_BODY("Toàn thân"), HALF_BODY("Bán thân") }

enum class PoseStyle(val label: String, val instruction: String) {
    NATURAL("Tự nhiên", "Thả hai tay thoải mái bên người"),
    HAND_ON_HIP("Chống hông", "Đặt một tay lên hông, khuỷu tay mở nhẹ"),
    HAND_AT_WAIST("Tay ngang eo", "Đặt một tay ngang eo, tay còn lại thả nhẹ"),
    TOUCH_HAIR("Chạm tóc", "Đưa một tay chạm nhẹ tóc bên tai"),
    OPEN_ARMS("Thả tay nhẹ", "Thả hai tay, giữ một khoảng nhỏ với thân");
}

data class Point(val x: Float, val y: Float, val confidence: Float = 1f) {
    fun visible(margin: Float = 0f): Boolean =
        confidence >= 0.6f && x.isFinite() && y.isFinite() &&
            x in margin..(1f - margin) && y in margin..(1f - margin)
}

// MediaPipe's 33-point indices. Coordinates are normalized to the displayed crop.
object Joint {
    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_FOOT = 31
    const val RIGHT_FOOT = 32
}

data class Body(val points: List<Point>) {
    operator fun get(index: Int): Point = points.getOrElse(index) { Point(0f, 0f, 0f) }
}

data class Observation(
    val bodies: List<Body>,
    val aspectRatio: Float = 0.75f,
    val rollDegrees: Float? = null,
    val luminance: Float = 1f,
)

data class Preferences(
    val shot: ShotType = ShotType.FULL_BODY,
    val pose: PoseStyle = PoseStyle.NATURAL,
    val coachPose: Boolean = true,
)

enum class Actor(val label: String) { CAMERA("Người chụp"), SUBJECT("Người tạo dáng"), STATUS("Trợ lý chụp ảnh") }
enum class Cue { NONE, LEFT, RIGHT, BACK, FORWARD, LEVEL, POSE, CHECK }
enum class Code {
    NO_PERSON, MULTIPLE_PEOPLE, UNCLEAR, DARK, STEP_BACK, STEP_CLOSER,
    PAN_LEFT, PAN_RIGHT, LEVEL, SHOW_HANDS, ADJUST_POSE, HOLD, READY,
}

data class Guidance(val code: Code, val actor: Actor, val text: String, val cue: Cue = Cue.NONE) {
    val ready: Boolean get() = code == Code.READY
}

object GuidanceRules {
    fun evaluate(frame: Observation, preferences: Preferences, previous: Code? = null): Guidance {
        fun camera(code: Code, text: String, cue: Cue = Cue.NONE) = Guidance(code, Actor.CAMERA, text, cue)
        if (frame.luminance < 0.08f) return camera(Code.DARK, "Tìm nơi sáng hơn để nhận diện rõ")
        if (frame.bodies.isEmpty()) return camera(Code.NO_PERSON, "Đưa một người vào khung hình")
        if (frame.bodies.size > 1) return camera(Code.MULTIPLE_PEOPLE, "Chỉ giữ một người trong khung hình")
        val body = frame.bodies.single()
        val nose = body[Joint.NOSE]
        val left = body[Joint.LEFT_SHOULDER]
        val right = body[Joint.RIGHT_SHOULDER]
        if (!nose.visible() || !left.visible() || !right.visible()) {
            return camera(Code.UNCLEAR, "Để mặt và hai vai hiện rõ trong khung")
        }
        val span = abs(left.x - right.x) * frame.aspectRatio
        if (span < 0.025f) return camera(Code.UNCLEAR, "Quay người về phía máy để nhận diện rõ")
        val top = nose.y - span * 0.65f
        val hips = listOf(body[Joint.LEFT_HIP], body[Joint.RIGHT_HIP])
        val bottom: Float
        if (preferences.shot == ShotType.FULL_BODY) {
            val ankles = listOf(body[Joint.LEFT_ANKLE], body[Joint.RIGHT_ANKLE])
            if (ankles.any { !it.visible(0.015f) }) {
                return camera(Code.STEP_BACK, "Lùi lại để thấy rõ cả hai chân", Cue.BACK)
            }
            bottom = (ankles + listOf(body[Joint.LEFT_FOOT], body[Joint.RIGHT_FOOT]).filter { it.visible() }).maxOf { it.y }
        } else {
            if (hips.any { !it.visible() }) return camera(Code.STEP_BACK, "Lùi nhẹ để lấy khung từ đầu đến hông", Cue.BACK)
            bottom = hips.maxOf { it.y }
        }
        val margin = if (previous == Code.STEP_BACK) 0.055f else 0.025f
        if (top < margin || bottom > 1f - margin || minOf(left.x, right.x) < margin || maxOf(left.x, right.x) > 1f - margin) {
            return camera(Code.STEP_BACK, "Lùi một chút, chừa khoảng trống quanh người", Cue.BACK)
        }
        val center = (left.x + right.x) / 2f
        val centerTolerance = if (previous == Code.PAN_LEFT || previous == Code.PAN_RIGHT) 0.07f else 0.11f
        if (center < 0.5f - centerTolerance) return camera(Code.PAN_LEFT, "Hướng máy sang trái một chút", Cue.LEFT)
        if (center > 0.5f + centerTolerance) return camera(Code.PAN_RIGHT, "Hướng máy sang phải một chút", Cue.RIGHT)
        val minHeight = if (previous == Code.STEP_CLOSER) 0.69f else 0.62f
        if (bottom - top < minHeight) return camera(Code.STEP_CLOSER, "Tiến gần hơn một chút", Cue.FORWARD)
        val maxRoll = if (previous == Code.LEVEL) 3f else 6f
        if (frame.rollDegrees?.let { abs(it) > maxRoll } == true) return camera(Code.LEVEL, "Giữ máy thẳng theo vạch ngang", Cue.LEVEL)

        if (preferences.coachPose) {
            val arms = listOf(Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW, Joint.LEFT_WRIST, Joint.RIGHT_WRIST)
            if (arms.any { !body[it].visible() } || hips.any { !it.visible() }) {
                return Guidance(Code.SHOW_HANDS, Actor.SUBJECT, "Để hai tay và phần eo hiện rõ", Cue.POSE)
            }
            if (!matchesPose(body, preferences.pose, frame.aspectRatio)) {
                return Guidance(Code.ADJUST_POSE, Actor.SUBJECT, preferences.pose.instruction, Cue.POSE)
            }
        }
        return Guidance(Code.READY, Actor.STATUS, "Có thể chụp", Cue.CHECK)
    }

    private fun matchesPose(body: Body, pose: PoseStyle, aspect: Float): Boolean {
        val ls = body[Joint.LEFT_SHOULDER]
        val rs = body[Joint.RIGHT_SHOULDER]
        val lh = body[Joint.LEFT_HIP]
        val rh = body[Joint.RIGHT_HIP]
        val lw = body[Joint.LEFT_WRIST]
        val rw = body[Joint.RIGHT_WRIST]
        val torso = ((lh.y + rh.y - ls.y - rs.y) / 2f).coerceAtLeast(0.08f)
        fun distance(a: Point, b: Point) = hypot((a.x - b.x) * aspect, a.y - b.y)
        fun relaxed(wrist: Point, hip: Point) = wrist.y > hip.y - torso * 0.15f
        fun atHip(wrist: Point, hip: Point, elbow: Point) = distance(wrist, hip) < torso * 0.45f &&
            abs(elbow.x - hip.x) * aspect > torso * 0.22f
        return when (pose) {
            PoseStyle.NATURAL -> relaxed(lw, lh) && relaxed(rw, rh)
            PoseStyle.HAND_ON_HIP -> atHip(lw, lh, body[Joint.LEFT_ELBOW]) || atHip(rw, rh, body[Joint.RIGHT_ELBOW])
            PoseStyle.HAND_AT_WAIST -> {
                val waistY = (lh.y + rh.y) / 2f - torso * 0.25f
                val centerX = (lh.x + rh.x) / 2f
                fun atWaist(w: Point) = abs(w.y - waistY) < torso * 0.2f && abs(w.x - centerX) * aspect < torso * 0.45f
                (atWaist(lw) && relaxed(rw, rh)) || (atWaist(rw) && relaxed(lw, lh))
            }
            PoseStyle.TOUCH_HAIR -> listOf(Joint.LEFT_EAR, Joint.RIGHT_EAR).map { body[it] }
                .filter { it.visible() }.any { distance(lw, it) < torso * 0.45f || distance(rw, it) < torso * 0.45f }
            PoseStyle.OPEN_ARMS -> relaxed(lw, lh) && relaxed(rw, rh) &&
                distance(lw, lh) in (torso * 0.25f)..(torso * 0.8f) &&
                distance(rw, rh) in (torso * 0.25f)..(torso * 0.8f)
        }
    }
}

/** Stateful only on the analysis thread. Safety messages immediately revoke a green frame. */
class GuidanceCoach {
    private var lastPreferences: Preferences? = null
    private var smoothed: Body? = null
    private var previousTime = -1L
    private var displayed = Guidance(Code.NO_PERSON, Actor.CAMERA, "Đưa một người vào khung hình")
    private var displayedAt = 0L
    private var candidate: Code? = null
    private var candidateSince = 0L
    private var stableSince: Long? = null
    private var stableAnchor: Body? = null

    fun reset() {
        lastPreferences = null
        smoothed = null
        previousTime = -1L
        stableSince = null
        stableAnchor = null
        candidate = null
        displayed = Guidance(Code.NO_PERSON, Actor.CAMERA, "Đưa một người vào khung hình")
        displayedAt = 0L
    }

    fun update(frame: Observation, preferences: Preferences, nowMs: Long): Guidance {
        if (preferences != lastPreferences || previousTime < 0 || nowMs - previousTime > 1_500 || nowMs < previousTime) {
            reset()
            lastPreferences = preferences
        }
        previousTime = nowMs
        val current = frame.bodies.singleOrNull()
        val old = smoothed
        smoothed = if (current == null || old == null) current else Body(current.points.mapIndexed { index, point ->
            val past = old[index]
            if (point.visible() && past.visible()) point.copy(x = past.x * 0.55f + point.x * 0.45f, y = past.y * 0.55f + point.y * 0.45f) else point
        })
        // Raw geometry prevents smoothing from hiding a newly cropped limb or a sudden movement.
        val raw = GuidanceRules.evaluate(frame, preferences, displayed.code)
        val filtered = GuidanceRules.evaluate(frame.copy(bodies = smoothed?.let(::listOf) ?: frame.bodies), preferences, displayed.code)
        val invalid = setOf(Code.NO_PERSON, Code.MULTIPLE_PEOPLE, Code.UNCLEAR, Code.DARK, Code.STEP_BACK, Code.SHOW_HANDS)
        var next = if (raw.code in invalid || raw.code != Code.READY && filtered.code == Code.READY) raw else filtered
        if (next.ready) {
            val anchor = stableAnchor
            val trackingPoints = listOf(Joint.NOSE, Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP,
                Joint.LEFT_ELBOW, Joint.RIGHT_ELBOW, Joint.LEFT_WRIST, Joint.RIGHT_WRIST) +
                if (preferences.shot == ShotType.FULL_BODY) listOf(Joint.LEFT_ANKLE, Joint.RIGHT_ANKLE) else emptyList()
            val moved = anchor == null || current == null || trackingPoints.any {
                current[it].visible() && anchor[it].visible() &&
                    hypot((current[it].x - anchor[it].x) * frame.aspectRatio, current[it].y - anchor[it].y) > 0.018f
            }
            if (moved) {
                stableSince = nowMs
                stableAnchor = current
            }
            if (nowMs - (stableSince ?: nowMs) < 1_000) next = Guidance(Code.HOLD, Actor.STATUS, "Giữ yên một chút…")
        } else {
            stableSince = null
            stableAnchor = null
        }
        // A failure or motion must never leave a stale READY visible for the dwell period.
        if (next.code in invalid || displayed.ready || next.code == Code.HOLD || next.ready) {
            display(next, nowMs)
        } else if (next.code == displayed.code) {
            candidate = null
        } else {
            if (candidate != next.code) {
                candidate = next.code
                candidateSince = nowMs
            }
            if (nowMs - candidateSince >= 250 && nowMs - displayedAt >= 1_000) display(next, nowMs)
        }
        return displayed
    }

    private fun display(value: Guidance, nowMs: Long) {
        if (displayed.code != value.code) displayedAt = nowMs
        displayed = value
        candidate = null
    }
}
