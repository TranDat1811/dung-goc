package vn.dunggoc.guidance

import org.junit.Assert.*
import org.junit.Test

class CompositionTest {
    private val scenePrefs = Preferences(captureMode = CaptureMode.SCENE, composition = Composition.LEFT_THIRD)
    private val blank = Observation(emptyList(), rollDegrees = 0f)

    @Test fun `automatic selection waits before choosing a scene`() {
        val selector = SubjectSelector()
        assertEquals(Subject.UNKNOWN, selector.update(CaptureMode.AUTO, false, 0))
        assertEquals(Subject.UNKNOWN, selector.update(CaptureMode.AUTO, false, 600))
        assertEquals(Subject.SCENE, selector.update(CaptureMode.AUTO, false, 1_000))
    }

    @Test fun `briefly missing a person does not change mode`() {
        val selector = SubjectSelector()
        selector.update(CaptureMode.AUTO, true, 0)
        assertEquals(Subject.PERSON, selector.update(CaptureMode.AUTO, true, 600))
        assertEquals(Subject.PERSON, selector.update(CaptureMode.AUTO, false, 800))
        assertEquals(Subject.PERSON, selector.update(CaptureMode.AUTO, true, 1_200))
    }

    @Test fun `persistent absence eventually chooses scene`() {
        val selector = SubjectSelector()
        selector.update(CaptureMode.AUTO, true, 0)
        selector.update(CaptureMode.AUTO, true, 600)
        selector.update(CaptureMode.AUTO, false, 800)
        selector.update(CaptureMode.AUTO, false, 1_400)
        selector.update(CaptureMode.AUTO, false, 2_000)
        assertEquals(Subject.SCENE, selector.update(CaptureMode.AUTO, false, 2_600))
    }

    @Test fun `manual mode overrides detection immediately`() {
        val selector = SubjectSelector()
        assertEquals(Subject.SCENE, selector.update(CaptureMode.SCENE, true, 0))
        assertEquals(Subject.PERSON, selector.update(CaptureMode.PERSON, false, 100))
    }

    @Test fun `long interruption restarts auto observation`() {
        val selector = SubjectSelector()
        selector.update(CaptureMode.AUTO, true, 0)
        selector.update(CaptureMode.AUTO, true, 600)
        assertEquals(Subject.UNKNOWN, selector.update(CaptureMode.AUTO, true, 3_000))
    }

    @Test fun `a flat image does not invent focal points or a horizontal edge`() {
        val scene = SceneComposition.analyze(IntArray(48 * 48) { 0xFFFFFFFF.toInt() }, 48, 48)
        assertEquals(1f, scene.brightness, .001f)
        assertNull(scene.focalPoint)
        assertNull(scene.horizontalLineY)
    }

    @Test fun `a broad horizontal edge is measured in normalized crop coordinates`() {
        val pixels = IntArray(48 * 48) { index -> if (index / 48 < 16) 0xFFFFFFFF.toInt() else 0xFF222222.toInt() }
        val scene = SceneComposition.analyze(pixels, 48, 48)
        assertNotNull(scene.horizontalLineY)
        assertEquals(1f / 3f, scene.horizontalLineY!!, .05f)
    }

    @Test fun `localized contrast identifies the side with visual emphasis`() {
        val pixels = IntArray(48 * 48) { index ->
            val x = index % 48
            val y = index / 48
            if (x in 32..40 && y in 12..22) 0xFFFFFFFF.toInt() else 0xFF333333.toInt()
        }
        val scene = SceneComposition.analyze(pixels, 48, 48)
        assertNotNull(scene.focalPoint)
        assertTrue(scene.focalPoint!!.x > .5f)
    }

    @Test fun `featureless scene stays manual rather than falsely ready`() {
        val coach = CompositionCoach()
        for (t in 0L..3_000L step 300L) {
            val result = coach.update(blank, SceneFeatures(), scenePrefs, t)
            assertEquals(Subject.SCENE, result.plan.subject)
            assertEquals(Code.COMPOSE, result.guidance.code)
        }
    }

    @Test fun `framed focal point requires stability before ready`() {
        val coach = CompositionCoach()
        val scene = SceneFeatures(focalPoint = Point(1f / 3f, 1f / 3f))
        assertEquals(Code.HOLD, coach.update(blank, scene, scenePrefs, 0).guidance.code)
        assertEquals(Code.HOLD, coach.update(blank, scene, scenePrefs, 600).guidance.code)
        assertEquals(Code.READY, coach.update(blank, scene, scenePrefs, 1_200).guidance.code)
        assertEquals(Code.COMPOSE, coach.update(blank, SceneFeatures(), scenePrefs, 1_300).guidance.code)
    }

    @Test fun `moving the focal point revokes readiness`() {
        val coach = CompositionCoach()
        val scene = SceneFeatures(focalPoint = Point(1f / 3f, 1f / 3f))
        for (t in 0L..1_200L step 300L) coach.update(blank, scene, scenePrefs, t)
        assertEquals(Code.PAN_RIGHT, coach.update(blank, scene.copy(focalPoint = Point(.8f, 1f / 3f)), scenePrefs, 1_300).guidance.code)
    }

    @Test fun `losing a targeted horizontal edge does not certify the layout`() {
        val coach = CompositionCoach()
        val scene = SceneFeatures(horizontalLineY = 1f / 3f)
        for (t in 0L..1_200L step 300L) coach.update(blank, scene, scenePrefs, t)
        assertEquals(Code.COMPOSE, coach.update(blank, SceneFeatures(), scenePrefs, 1_300).guidance.code)
    }

    @Test fun `scene with a tilted camera asks to level it`() {
        assertEquals(Code.LEVEL, CompositionCoach().update(blank.copy(rollDegrees = 12f), SceneFeatures(), scenePrefs, 0).guidance.code)
    }

    @Test fun `scene remains scene when user explicitly chooses it despite people`() {
        val body = PoseTemplate.body(PoseStyle.NATURAL, ShotType.FULL_BODY, .5f, .75f)
        val result = CompositionCoach().update(blank.copy(bodies = listOf(body)), SceneFeatures(), scenePrefs, 0)
        assertEquals(Subject.SCENE, result.plan.subject)
    }

    @Test fun `person mode does not silently fall back to scene when empty`() {
        val result = CompositionCoach().update(blank, SceneFeatures(), Preferences(captureMode = CaptureMode.PERSON), 0)
        assertEquals(Subject.PERSON, result.plan.subject)
        assertEquals(Code.NO_PERSON, result.guidance.code)
    }

    @Test fun `person guidance and ghost agree for every pose shot and composition`() {
        for (shot in ShotType.entries) for (pose in PoseStyle.entries) {
            for (style in listOf(Composition.CENTER, Composition.LEFT_THIRD, Composition.RIGHT_THIRD)) {
                val body = PoseTemplate.body(pose, shot, style.targetX, .75f)
                val prefs = Preferences(shot = shot, pose = pose, captureMode = CaptureMode.PERSON, composition = style)
                val frame = blank.copy(bodies = listOf(body))
                val coach = CompositionCoach()
                var result = coach.update(frame, SceneFeatures(), prefs, 0)
                for (t in 300L..1_200L step 300L) result = coach.update(frame, SceneFeatures(), prefs, t)
                assertEquals("$shot $pose $style", Code.READY, result.guidance.code)
                assertEquals(style.targetX, result.plan.targetX, .001f)
            }
        }
    }

    @Test fun `thirds composition does not accept a person in the center`() {
        val body = PoseTemplate.body(PoseStyle.NATURAL, ShotType.FULL_BODY, .5f, .75f)
        val guide = GuidanceRules.evaluate(blank.copy(bodies = listOf(body)), Preferences(), targetX = 1f / 3f, targetHeight = .83f)
        assertEquals(Code.PAN_RIGHT, guide.code)
    }

    @Test fun `changing layout restarts readiness and moves the target`() {
        val coach = CompositionCoach()
        val scene = SceneFeatures(focalPoint = Point(1f / 3f, 1f / 3f))
        for (t in 0L..1_200L step 300L) coach.update(blank, scene, scenePrefs, t)
        val changed = coach.update(blank, scene, scenePrefs.copy(composition = Composition.RIGHT_THIRD), 1_300)
        assertEquals(2f / 3f, changed.plan.targetX, .001f)
        assertFalse(changed.guidance.ready)
    }

    @Test fun `new details can replace an initially featureless suggestion`() {
        val coach = CompositionCoach()
        coach.update(blank, SceneFeatures(), scenePrefs, 0)
        val detailed = coach.update(blank, SceneFeatures(horizontalLineY = .3f), scenePrefs, 300)
        assertEquals(1f / 3f, detailed.plan.horizontalLineY!!, .001f)
    }

    @Test fun `ghost proportions stay constant in pixels across preview aspect ratios`() {
        val portrait = PoseTemplate.body(PoseStyle.NATURAL, ShotType.FULL_BODY, .5f, .75f)
        val wide = PoseTemplate.body(PoseStyle.NATURAL, ShotType.FULL_BODY, .5f, 1.5f)
        val portraitSpan = (portrait[Joint.RIGHT_SHOULDER].x - portrait[Joint.LEFT_SHOULDER].x) * .75f
        val wideSpan = (wide[Joint.RIGHT_SHOULDER].x - wide[Joint.LEFT_SHOULDER].x) * 1.5f
        assertEquals(portraitSpan, wideSpan, .001f)
    }

    @Test fun `changing pose in auto mode retains detected subject`() {
        val coach = CompositionCoach()
        val body = PoseTemplate.body(PoseStyle.NATURAL, ShotType.FULL_BODY, .5f, .75f)
        val frame = blank.copy(bodies = listOf(body))
        val prefs = Preferences()
        coach.update(frame, SceneFeatures(), prefs, 0)
        assertEquals(Subject.PERSON, coach.update(frame, SceneFeatures(), prefs, 600).plan.subject)
        val changed = coach.update(frame, SceneFeatures(), prefs.copy(pose = PoseStyle.TOUCH_HAIR), 800)
        assertEquals(Subject.PERSON, changed.plan.subject)
        assertFalse(changed.guidance.ready)
    }
}
