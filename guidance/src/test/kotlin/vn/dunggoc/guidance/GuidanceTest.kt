package vn.dunggoc.guidance

import org.junit.Assert.*
import org.junit.Test

class GuidanceTest {
    private val preferences = Preferences()

    private fun body(): Body {
        val points = MutableList(33) { Point(0f, 0f, 0f) }
        mapOf(
            Joint.NOSE to Point(.5f, .17f),
            Joint.LEFT_EAR to Point(.45f, .16f), Joint.RIGHT_EAR to Point(.55f, .16f),
            Joint.LEFT_SHOULDER to Point(.4f, .3f), Joint.RIGHT_SHOULDER to Point(.6f, .3f),
            Joint.LEFT_ELBOW to Point(.38f, .47f), Joint.RIGHT_ELBOW to Point(.62f, .47f),
            Joint.LEFT_WRIST to Point(.39f, .64f), Joint.RIGHT_WRIST to Point(.61f, .64f),
            Joint.LEFT_HIP to Point(.43f, .58f), Joint.RIGHT_HIP to Point(.57f, .58f),
            Joint.LEFT_ANKLE to Point(.44f, .88f), Joint.RIGHT_ANKLE to Point(.56f, .88f),
            Joint.LEFT_FOOT to Point(.43f, .90f), Joint.RIGHT_FOOT to Point(.58f, .90f),
        ).forEach { (index, point) -> points[index] = point }
        return Body(points)
    }

    private fun Body.change(vararg changes: Pair<Int, Point>): Body {
        val updated = points.toMutableList()
        changes.forEach { (index, point) -> updated[index] = point }
        return Body(updated)
    }

    private fun evaluate(body: Body = body(), prefs: Preferences = preferences, roll: Float? = 0f,
                         previous: Code? = null): Guidance = GuidanceRules.evaluate(Observation(listOf(body), rollDegrees = roll), prefs, previous)

    @Test fun `balanced natural pose is ready`() {
        assertEquals(Code.READY, evaluate().code)
    }

    @Test fun `no person and multiple people have separate actionable instructions`() {
        assertEquals(Code.NO_PERSON, GuidanceRules.evaluate(Observation(emptyList()), preferences).code)
        assertEquals(Code.MULTIPLE_PEOPLE, GuidanceRules.evaluate(Observation(listOf(body(), body())), preferences).code)
    }

    @Test fun `missing shoulders cannot produce geometry advice`() {
        assertEquals(Code.UNCLEAR, evaluate(body().change(Joint.LEFT_SHOULDER to Point(.4f, .3f, .2f))).code)
    }

    @Test fun `cropped and low-confidence ankles ask for full body`() {
        assertEquals(Code.STEP_BACK, evaluate(body().change(Joint.LEFT_ANKLE to Point(.44f, 1.1f))).code)
        assertEquals(Code.STEP_BACK, evaluate(body().change(Joint.RIGHT_ANKLE to Point(.56f, .88f, .2f))).code)
    }

    @Test fun `head clearance accounts for space above nose`() {
        assertEquals(Code.STEP_BACK, evaluate(body().change(Joint.NOSE to Point(.5f, .08f))).code)
    }

    @Test fun `half-body ignores invisible legs`() {
        val half = Body(body().points.map {
            if (!it.visible()) it else it.copy(x = .5f + (it.x - .5f) * 1.5f, y = .05f + (it.y - .0725f) * 1.5f)
        })
        assertEquals(Code.READY, evaluate(half, preferences.copy(shot = ShotType.HALF_BODY)).code)
    }

    @Test fun `small subject requests a closer framing`() {
        val small = Body(body().points.map { it.copy(x = .5f + (it.x - .5f) * .5f, y = .5f + (it.y - .5f) * .5f) })
        assertEquals(Code.STEP_CLOSER, evaluate(small).code)
    }

    @Test fun `pan instruction points toward person on the screen`() {
        val left = Body(body().points.map { it.copy(x = it.x - .18f) })
        val right = Body(body().points.map { it.copy(x = it.x + .18f) })
        assertEquals(Cue.LEFT, evaluate(left).cue)
        assertEquals(Cue.RIGHT, evaluate(right).cue)
    }

    @Test fun `roll hysteresis asks to straighten until within three degrees`() {
        assertEquals(Code.LEVEL, evaluate(roll = 8f).code)
        assertEquals(Code.LEVEL, evaluate(roll = 4f, previous = Code.LEVEL).code)
        assertEquals(Code.READY, evaluate(roll = 2f, previous = Code.LEVEL).code)
    }

    @Test fun `absent level sensor does not block taking photos`() {
        assertEquals(Code.READY, evaluate(roll = null).code)
    }

    @Test fun `poor light never signals ready`() {
        assertEquals(Code.DARK, GuidanceRules.evaluate(Observation(listOf(body()), luminance = .03f), preferences).code)
        assertEquals(Code.DARK, GuidanceRules.evaluate(Observation(emptyList(), luminance = .03f), preferences).code)
    }

    @Test fun `occluded hand is not assessed as a wrong pose`() {
        val hidden = body().change(Joint.LEFT_WRIST to Point(.4f, .5f, .1f))
        assertEquals(Code.SHOW_HANDS, evaluate(hidden).code)
        assertEquals(Code.READY, evaluate(hidden, preferences.copy(coachPose = false)).code)
    }

    @Test fun `each selected pose has a reachable ready configuration`() {
        val poses = mapOf(
            PoseStyle.NATURAL to body(),
            PoseStyle.HAND_ON_HIP to body().change(Joint.LEFT_WRIST to Point(.43f, .58f), Joint.LEFT_ELBOW to Point(.25f, .44f)),
            PoseStyle.HAND_AT_WAIST to body().change(Joint.LEFT_WRIST to Point(.5f, .51f)),
            PoseStyle.TOUCH_HAIR to body().change(Joint.LEFT_WRIST to Point(.45f, .16f)),
            PoseStyle.OPEN_ARMS to body().change(Joint.LEFT_WRIST to Point(.30f, .64f), Joint.RIGHT_WRIST to Point(.70f, .64f)),
        )
        poses.forEach { (pose, person) -> assertEquals(pose.name, Code.READY, evaluate(person, preferences.copy(pose = pose)).code) }
    }

    @Test fun `pose instruction is for subject`() {
        val advice = evaluate(prefs = preferences.copy(pose = PoseStyle.TOUCH_HAIR))
        assertEquals(Code.ADJUST_POSE, advice.code)
        assertEquals(Actor.SUBJECT, advice.actor)
    }

    @Test fun `invalid coordinates and absent joints do not throw`() {
        assertEquals(Code.UNCLEAR, evaluate(Body(emptyList())).code)
        assertEquals(Code.UNCLEAR, evaluate(body().change(Joint.NOSE to Point(Float.NaN, .2f))).code)
    }

    @Test fun `ready requires a continuous second of stability`() {
        val coach = GuidanceCoach()
        val frame = Observation(listOf(body()))
        for (t in 0L..900L step 100L) assertFalse(coach.update(frame, preferences, t).ready)
        assertTrue(coach.update(frame, preferences, 1_000).ready)
    }

    private fun readyCoach(): GuidanceCoach = GuidanceCoach().also { coach ->
        for (t in 0L..1_000L step 100L) coach.update(Observation(listOf(body())), preferences, t)
    }

    @Test fun `loss of person immediately revokes green frame`() {
        assertEquals(Code.NO_PERSON, readyCoach().update(Observation(emptyList()), preferences, 1_100).code)
    }

    @Test fun `newly cropped foot immediately revokes green despite smoothing`() {
        val cropped = body().change(Joint.LEFT_ANKLE to Point(.44f, 1.03f))
        assertEquals(Code.STEP_BACK, readyCoach().update(Observation(listOf(cropped)), preferences, 1_100).code)
    }

    @Test fun `moving hands restarts stability timer even in framing-only mode`() {
        val prefs = preferences.copy(coachPose = false)
        val coach = GuidanceCoach()
        for (t in 0L..1_000L step 100L) coach.update(Observation(listOf(body())), prefs, t)
        val moved = body().change(Joint.LEFT_WRIST to Point(.3f, .65f))
        assertEquals(Code.HOLD, coach.update(Observation(listOf(moved)), prefs, 1_100).code)
    }

    @Test fun `changing preferences cannot reuse readiness`() {
        val result = readyCoach().update(Observation(listOf(body())), preferences.copy(coachPose = false), 1_100)
        assertEquals(Code.HOLD, result.code)
    }

    @Test fun `resuming after frame gap restarts stability`() {
        assertEquals(Code.HOLD, readyCoach().update(Observation(listOf(body())), preferences, 3_000).code)
    }

    @Test fun `slow analysis cadence can still become ready`() {
        val coach = GuidanceCoach()
        val frame = Observation(listOf(body()))
        assertEquals(Code.HOLD, coach.update(frame, preferences, 0).code)
        assertEquals(Code.HOLD, coach.update(frame, preferences, 600).code)
        assertEquals(Code.READY, coach.update(frame, preferences, 1_200).code)
    }

    @Test fun `second person immediately revokes readiness`() {
        assertEquals(Code.MULTIPLE_PEOPLE, readyCoach().update(Observation(listOf(body(), body())), preferences, 1_100).code)
    }

    @Test fun `camera corrections keep minimum display time to avoid flicker`() {
        val coach = GuidanceCoach()
        val frame = Observation(listOf(body()))
        coach.update(frame, preferences, 0)
        val tilted = frame.copy(rollDegrees = 10f)
        assertEquals(Code.HOLD, coach.update(tilted, preferences, 100).code)
        for (t in 200L..1_000L step 100L) coach.update(tilted, preferences, t)
        assertEquals(Code.LEVEL, coach.update(tilted, preferences, 1_100).code)
    }
}
