package vn.dunggoc.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import vn.dunggoc.guidance.PoseStyle
import vn.dunggoc.guidance.ShotType

@Composable
fun PoseIllustration(pose: PoseStyle, shot: ShotType, modifier: Modifier = Modifier, color: Color = Lime) {
    Canvas(modifier) {
        val full = shot == ShotType.FULL_BODY
        // The half-body illustration enlarges the same pose and crops below the hips.
        val w = size.width
        val h = if (full) size.height else size.height * 1.5f
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        val lineWidth = (w * 0.038f).coerceIn(2f, 7f)
        fun line(a: Offset, b: Offset) = drawLine(color, a, b, lineWidth, StrokeCap.Round)
        drawCircle(color, w * 0.09f, p(0.5f, 0.13f), style = Stroke(lineWidth))
        line(p(0.5f, 0.23f), p(0.5f, 0.28f))
        line(p(0.32f, 0.29f), p(0.68f, 0.29f))
        line(p(0.32f, 0.29f), p(0.37f, 0.58f))
        line(p(0.68f, 0.29f), p(0.63f, 0.58f))
        line(p(0.37f, 0.58f), p(0.63f, 0.58f))
        val leftElbow: Offset
        val leftHand: Offset
        when (pose) {
            PoseStyle.HAND_ON_HIP -> { leftElbow = p(0.15f, 0.43f); leftHand = p(0.37f, 0.57f) }
            PoseStyle.HAND_AT_WAIST -> { leftElbow = p(0.26f, 0.46f); leftHand = p(0.52f, 0.50f) }
            PoseStyle.TOUCH_HAIR -> { leftElbow = p(0.16f, 0.28f); leftHand = p(0.38f, 0.13f) }
            PoseStyle.OPEN_ARMS -> { leftElbow = p(0.23f, 0.45f); leftHand = p(0.20f, 0.62f) }
            PoseStyle.NATURAL -> { leftElbow = p(0.30f, 0.45f); leftHand = p(0.31f, 0.65f) }
        }
        line(p(0.32f, 0.29f), leftElbow)
        line(leftElbow, leftHand)
        val rightElbow = if (pose == PoseStyle.OPEN_ARMS) p(0.77f, 0.45f) else p(0.70f, 0.45f)
        val rightHand = if (pose == PoseStyle.OPEN_ARMS) p(0.80f, 0.62f) else p(0.69f, 0.65f)
        line(p(0.68f, 0.29f), rightElbow)
        line(rightElbow, rightHand)
        if (full) {
            line(p(0.39f, 0.59f), p(0.37f, 0.91f))
            line(p(0.61f, 0.59f), p(0.65f, 0.91f))
            line(p(0.37f, 0.91f), p(0.28f, 0.92f))
            line(p(0.65f, 0.91f), p(0.73f, 0.92f))
        }
    }
}
