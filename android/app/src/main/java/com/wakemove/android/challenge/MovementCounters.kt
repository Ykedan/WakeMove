package com.wakemove.android.challenge

import kotlin.math.acos
import kotlin.math.sqrt

private const val MINIMUM_VISIBILITY = 0.65f
private const val STABLE_FRAME_COUNT = 3
private const val COOLDOWN_MS = 350L
private const val STANDING_KNEE_ANGLE = 160.0
private const val SQUAT_BOTTOM_KNEE_ANGLE = 120.0
private const val CLOSED_ANKLE_DISTANCE = 0.25f
private const val OPEN_ANKLE_DISTANCE = 0.45f
private const val OPEN_ARM_ANGLE = 110.0
private const val HANDS_UP_HOLD_MS = 1_000L
private const val HANDS_UP_STABLE_FRAME_COUNT = 2
private const val HANDS_UP_MINIMUM_VISIBILITY = 0.50f
private const val HANDS_UP_SHOULDER_TOLERANCE = 0.04f

interface MovementCounter {
    fun update(frame: PoseFrame): ChallengeProgress
}

/** Returns the angle in degrees formed by [a], [vertex], and [c]. */
fun angle(a: Landmark, vertex: Landmark, c: Landmark): Double {
    val firstX = a.x - vertex.x
    val firstY = a.y - vertex.y
    val secondX = c.x - vertex.x
    val secondY = c.y - vertex.y
    val firstLength = sqrt(firstX * firstX + firstY * firstY)
    val secondLength = sqrt(secondX * secondX + secondY * secondY)

    if (firstLength == 0f || secondLength == 0f) return 0.0

    val cosine = ((firstX * secondX + firstY * secondY) / (firstLength * secondLength))
        .coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cosine.toDouble()))
}

class SquatCounter : MovementCounter {
    private var phase = SquatPhase.WAITING_FOR_STANDING
    private var stableFrames = 0
    private var repetitions = 0
    private var lastRepetitionAtMs: Long? = null

    override fun update(frame: PoseFrame): ChallengeProgress {
        val observedPhase = frame.squatPhase()
        stableFrames = if (observedPhase == phase.expectedPose) stableFrames + 1 else 0

        if (stableFrames >= STABLE_FRAME_COUNT) {
            when (phase) {
                SquatPhase.WAITING_FOR_STANDING -> transitionTo(SquatPhase.WAITING_FOR_BOTTOM)
                SquatPhase.WAITING_FOR_BOTTOM -> transitionTo(SquatPhase.WAITING_FOR_RETURN)
                SquatPhase.WAITING_FOR_RETURN -> {
                    if (lastRepetitionAtMs == null || frame.timestampMs - lastRepetitionAtMs!! >= COOLDOWN_MS) {
                        repetitions += 1
                        lastRepetitionAtMs = frame.timestampMs
                    }
                    transitionTo(SquatPhase.WAITING_FOR_BOTTOM)
                }
            }
        }

        return ChallengeProgress(repetitions)
    }

    private fun transitionTo(nextPhase: SquatPhase) {
        phase = nextPhase
        stableFrames = 0
    }

    private enum class SquatPhase(val expectedPose: PosePosition) {
        WAITING_FOR_STANDING(PosePosition.STANDING),
        WAITING_FOR_BOTTOM(PosePosition.BOTTOM),
        WAITING_FOR_RETURN(PosePosition.STANDING),
    }
}

class JumpingJackCounter : MovementCounter {
    private var phase = JackPhase.WAITING_FOR_CLOSED
    private var stableFrames = 0
    private var repetitions = 0
    private var lastRepetitionAtMs: Long? = null

    override fun update(frame: PoseFrame): ChallengeProgress {
        val observedPhase = frame.jackPhase()
        stableFrames = if (observedPhase == phase.expectedPose) stableFrames + 1 else 0

        if (stableFrames >= STABLE_FRAME_COUNT) {
            when (phase) {
                JackPhase.WAITING_FOR_CLOSED -> transitionTo(JackPhase.WAITING_FOR_OPEN)
                JackPhase.WAITING_FOR_OPEN -> transitionTo(JackPhase.WAITING_FOR_RETURN)
                JackPhase.WAITING_FOR_RETURN -> {
                    if (lastRepetitionAtMs == null || frame.timestampMs - lastRepetitionAtMs!! >= COOLDOWN_MS) {
                        repetitions += 1
                        lastRepetitionAtMs = frame.timestampMs
                    }
                    transitionTo(JackPhase.WAITING_FOR_OPEN)
                }
            }
        }

        return ChallengeProgress(repetitions)
    }

    private fun transitionTo(nextPhase: JackPhase) {
        phase = nextPhase
        stableFrames = 0
    }

    private enum class JackPhase(val expectedPose: PosePosition) {
        WAITING_FOR_CLOSED(PosePosition.CLOSED),
        WAITING_FOR_OPEN(PosePosition.OPEN),
        WAITING_FOR_RETURN(PosePosition.CLOSED),
    }
}

class HandsUpCounter : MovementCounter {
    private var stableFrames = 0
    private var handsUpStartedAtMs: Long? = null
    private var countedCurrentHold = false
    private var repetitions = 0

    override fun update(frame: PoseFrame): ChallengeProgress {
        if (!frame.hasHandsUp()) {
            stableFrames = 0
            handsUpStartedAtMs = null
            countedCurrentHold = false
            return ChallengeProgress(repetitions)
        }

        if (stableFrames == 0) handsUpStartedAtMs = frame.timestampMs
        stableFrames += 1
        val holdStartedAtMs = handsUpStartedAtMs!!
        if (
            !countedCurrentHold &&
            stableFrames >= HANDS_UP_STABLE_FRAME_COUNT &&
            frame.timestampMs - holdStartedAtMs >= HANDS_UP_HOLD_MS
        ) {
            repetitions += 1
            countedCurrentHold = true
        }

        return ChallengeProgress(repetitions)
    }
}

private enum class PosePosition {
    STANDING,
    BOTTOM,
    CLOSED,
    OPEN,
}

private fun PoseFrame.squatPhase(): PosePosition? {
    val leftHip = visible(PoseLandmark.LEFT_HIP) ?: return null
    val rightHip = visible(PoseLandmark.RIGHT_HIP) ?: return null
    val leftKnee = visible(PoseLandmark.LEFT_KNEE) ?: return null
    val rightKnee = visible(PoseLandmark.RIGHT_KNEE) ?: return null
    val leftAnkle = visible(PoseLandmark.LEFT_ANKLE) ?: return null
    val rightAnkle = visible(PoseLandmark.RIGHT_ANKLE) ?: return null
    val averageKneeAngle = (
        angle(leftHip, leftKnee, leftAnkle) + angle(rightHip, rightKnee, rightAnkle)
        ) / 2.0

    return when {
        averageKneeAngle >= STANDING_KNEE_ANGLE -> PosePosition.STANDING
        averageKneeAngle <= SQUAT_BOTTOM_KNEE_ANGLE -> PosePosition.BOTTOM
        else -> null
    }
}

private fun PoseFrame.jackPhase(): PosePosition? {
    val leftWrist = visible(PoseLandmark.LEFT_WRIST) ?: return null
    val rightWrist = visible(PoseLandmark.RIGHT_WRIST) ?: return null
    val leftShoulder = visible(PoseLandmark.LEFT_SHOULDER) ?: return null
    val rightShoulder = visible(PoseLandmark.RIGHT_SHOULDER) ?: return null
    val leftHip = visible(PoseLandmark.LEFT_HIP) ?: return null
    val rightHip = visible(PoseLandmark.RIGHT_HIP) ?: return null
    val leftKnee = visible(PoseLandmark.LEFT_KNEE) ?: return null
    val rightKnee = visible(PoseLandmark.RIGHT_KNEE) ?: return null
    val leftAnkle = visible(PoseLandmark.LEFT_ANKLE) ?: return null
    val rightAnkle = visible(PoseLandmark.RIGHT_ANKLE) ?: return null
    val ankleDistance = kotlin.math.abs(leftAnkle.x - rightAnkle.x)
    val legsStraight = (
        angle(leftHip, leftKnee, leftAnkle) + angle(rightHip, rightKnee, rightAnkle)
        ) / 2.0 >= STANDING_KNEE_ANGLE
    val armsRaised = leftWrist.y < leftShoulder.y && rightWrist.y < rightShoulder.y
    val armsOpen =
        angle(leftWrist, leftShoulder, rightShoulder) >= OPEN_ARM_ANGLE &&
            angle(rightWrist, rightShoulder, leftShoulder) >= OPEN_ARM_ANGLE

    return when {
        legsStraight && armsRaised && armsOpen && ankleDistance >= OPEN_ANKLE_DISTANCE -> PosePosition.OPEN
        legsStraight && leftWrist.y > leftShoulder.y && rightWrist.y > rightShoulder.y &&
            ankleDistance <= CLOSED_ANKLE_DISTANCE -> PosePosition.CLOSED
        else -> null
    }
}

private fun PoseFrame.hasHandsUp(): Boolean {
    val leftWrist = visible(
        PoseLandmark.LEFT_WRIST,
        HANDS_UP_MINIMUM_VISIBILITY,
    ) ?: return false
    val rightWrist = visible(
        PoseLandmark.RIGHT_WRIST,
        HANDS_UP_MINIMUM_VISIBILITY,
    ) ?: return false
    val leftShoulder = visible(
        PoseLandmark.LEFT_SHOULDER,
        HANDS_UP_MINIMUM_VISIBILITY,
    ) ?: return false
    val rightShoulder = visible(
        PoseLandmark.RIGHT_SHOULDER,
        HANDS_UP_MINIMUM_VISIBILITY,
    ) ?: return false
    return leftWrist.y <= leftShoulder.y + HANDS_UP_SHOULDER_TOLERANCE &&
        rightWrist.y <= rightShoulder.y + HANDS_UP_SHOULDER_TOLERANCE
}

private fun PoseFrame.visible(
    landmark: PoseLandmark,
    minimumVisibility: Float = MINIMUM_VISIBILITY,
): Landmark? =
    landmarks[landmark]?.takeIf { it.visibility >= minimumVisibility }
