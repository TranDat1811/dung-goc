package vn.dunggoc.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.dunggoc.guidance.*
import kotlin.math.atan2
import kotlin.math.hypot

/** Target outlines stay in the chosen composition, rather than following the detected person. */
@Composable
fun CompositionGhost(plan: CompositionPlan, prefs: Preferences, ready: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = if (ready) Lime.copy(alpha = .8f) else Color.White.copy(alpha = .55f)
        val thin = 1.3.dp.toPx()
        val dashed = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = .15f), Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), thin)
            drawLine(Color.White.copy(alpha = .15f), Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), thin)
        }
        when (plan.subject) {
            Subject.UNKNOWN -> Unit
            Subject.SCENE -> {
                val center = Offset(plan.targetX * size.width, plan.targetY * size.height)
                val radius = minOf(26.dp.toPx(), size.width * .09f, size.height * .09f)
                drawCircle(color.copy(alpha = .04f), radius, center)
                drawCircle(color, radius, center, style = Stroke(thin, pathEffect = dashed))
                drawLine(color, center - Offset(radius * .35f, 0f), center + Offset(radius * .35f, 0f), thin)
                drawLine(color, center - Offset(0f, radius * .35f), center + Offset(0f, radius * .35f), thin)
                plan.horizontalLineY?.let { y ->
                    drawLine(color, Offset(size.width * .06f, y * size.height), Offset(size.width * .94f, y * size.height),
                        2.dp.toPx(), pathEffect = dashed)
                }
            }
            Subject.PERSON -> {
                val pose = if (prefs.coachPose) prefs.pose else PoseStyle.NATURAL
                val target = PoseTemplate.body(pose, prefs.shot, plan.targetX, size.width / size.height)
                fun p(joint: Int) = Offset(target[joint].x * size.width, target[joint].y * size.height)
                val scale = if (prefs.shot == ShotType.HALF_BODY) 1.5f else 1f
                val headRadius = size.height * .055f * scale
                val nose = p(Joint.NOSE)
                val head = nose - Offset(0f, size.height * .035f * scale)
                drawCircle(color.copy(alpha = .03f), headRadius, head)
                drawCircle(color, headRadius, head, style = Stroke(thin))
                val ls = p(Joint.LEFT_SHOULDER)
                val rs = p(Joint.RIGHT_SHOULDER)
                val lh = p(Joint.LEFT_HIP)
                val rh = p(Joint.RIGHT_HIP)
                val neck = headRadius * .38f
                val torso = Path().apply {
                    moveTo(head.x - neck, head.y + headRadius)
                    lineTo(head.x - neck, ls.y - headRadius * .4f)
                    quadraticBezierTo(ls.x, ls.y - headRadius * .4f, ls.x, ls.y)
                    quadraticBezierTo(ls.x + headRadius * .15f, lh.y - headRadius, lh.x - headRadius * .3f, lh.y)
                    quadraticBezierTo(head.x, lh.y + headRadius * .25f, rh.x + headRadius * .3f, rh.y)
                    quadraticBezierTo(rs.x - headRadius * .15f, rh.y - headRadius, rs.x, rs.y)
                    quadraticBezierTo(rs.x, rs.y - headRadius * .4f, head.x + neck, rs.y - headRadius * .4f)
                    lineTo(head.x + neck, head.y + headRadius)
                }
                drawPath(torso, color.copy(alpha = .025f))
                drawPath(torso, color, style = Stroke(thin))
                fun limb(start: Offset, end: Offset, width: Float) {
                    val delta = end - start
                    val length = hypot(delta.x, delta.y)
                    val angle = Math.toDegrees(atan2(delta.y, delta.x).toDouble()).toFloat() - 90f
                    rotate(angle, pivot = start) {
                        val topLeft = Offset(start.x - width / 2f, start.y - width / 2f)
                        val dimensions = Size(width, length + width)
                        val corner = CornerRadius(width / 2f)
                        drawRoundRect(color.copy(alpha = .025f), topLeft, dimensions, corner)
                        drawRoundRect(color, topLeft, dimensions, corner, style = Stroke(thin))
                    }
                }
                val armWidth = headRadius * .65f
                for ((shoulder, elbow, wrist) in listOf(
                    Triple(Joint.LEFT_SHOULDER, Joint.LEFT_ELBOW, Joint.LEFT_WRIST),
                    Triple(Joint.RIGHT_SHOULDER, Joint.RIGHT_ELBOW, Joint.RIGHT_WRIST),
                )) {
                    limb(p(shoulder), p(elbow), armWidth)
                    limb(p(elbow), p(wrist), armWidth * .85f)
                }
                if (prefs.shot == ShotType.FULL_BODY) {
                    for ((hip, ankle) in listOf(Joint.LEFT_HIP to Joint.LEFT_ANKLE, Joint.RIGHT_HIP to Joint.RIGHT_ANKLE)) {
                        val knee = p(hip) + (p(ankle) - p(hip)) * .52f
                        limb(p(hip), knee, headRadius * 1.05f)
                        limb(knee, p(ankle), headRadius * .75f)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompositionPicker(prefs: Preferences, onSelect: (Preferences) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Chọn điểm đặt chủ thể", fontSize = 24.sp)
            Text("Nét mờ sẽ chuyển vị trí. Căn người hoặc chi tiết bạn muốn nhấn vào đó.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Composition.entries.forEach { style ->
                val selected = prefs.composition == style
                Surface(onClick = { onSelect(prefs.copy(composition = style)) }, shape = RoundedCornerShape(16.dp),
                    color = if (selected) Lime.copy(alpha = .1f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) Lime else Color.White.copy(alpha = .12f))) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Canvas(Modifier.width(60.dp).height(42.dp)) {
                            drawRoundRect(Color.White.copy(alpha = .3f), cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
                            for (i in 1..2) drawLine(Color.White.copy(alpha = .2f), Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                            drawCircle(Lime, 5.dp.toPx(), Offset(size.width * style.targetX, size.height * .5f))
                        }
                        Text(style.label, Modifier.weight(1f), color = if (selected) Lime else MaterialTheme.colorScheme.onSurface)
                        if (selected) Text("✓", color = Lime)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
