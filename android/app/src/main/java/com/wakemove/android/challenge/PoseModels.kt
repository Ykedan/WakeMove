package com.wakemove.android.challenge

/** A normalized pose point emitted by a pose landmark detector. */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
)

/** MediaPipe Pose landmark identifiers. */
enum class PoseLandmark {
    NOSE,
    LEFT_EYE_INNER,
    LEFT_EYE,
    LEFT_EYE_OUTER,
    RIGHT_EYE_INNER,
    RIGHT_EYE,
    RIGHT_EYE_OUTER,
    LEFT_EAR,
    RIGHT_EAR,
    MOUTH_LEFT,
    MOUTH_RIGHT,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_PINKY,
    RIGHT_PINKY,
    LEFT_INDEX,
    RIGHT_INDEX,
    LEFT_THUMB,
    RIGHT_THUMB,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_HEEL,
    RIGHT_HEEL,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX,
}

data class PoseFrame(
    val timestampMs: Long,
    val landmarks: Map<PoseLandmark, Landmark>,
)

data class ChallengeProgress(
    val repetitions: Int,
    val targetCount: Int = 0,
    val completed: Boolean = false,
    val guidance: CameraGuidance = CameraGuidance.NONE,
    val landmarks: List<Landmark> = emptyList(),
    val fallbackAvailable: Boolean = false,
)

enum class CameraGuidance {
    NONE,
    LOW_LIGHT,
    NO_PERSON,
}

sealed interface PoseObservation {
    data class Frame(val frame: PoseFrame) : PoseObservation

    data object LowLight : PoseObservation

    data object NoPerson : PoseObservation
}
