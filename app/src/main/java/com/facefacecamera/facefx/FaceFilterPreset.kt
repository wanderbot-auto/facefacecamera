package com.facefacecamera.facefx

enum class FaceEffectKind {
    SquareGrid,
    PeakPrism,
    BubbleOrb,
    BladeSlice,
}

data class DeformProfile(
    val kind: FaceEffectKind,
    val previewWidthScale: Float,
    val previewHeightScale: Float,
    val previewJawScale: Float = 1f,
    val previewCrownLift: Float = 0f,
    val amount: Float,
    val tension: Float,
    val lift: Float = 0f,
    val defaultGridSize: Int = 5,
    val mirrorOutput: Boolean = true,
)

data class FaceFilterPreset(
    val id: String,
    val name: String,
    val description: String,
    val accentColorHex: Long,
    val deformProfile: DeformProfile,
) {
    companion object {
        fun defaults(): List<FaceFilterPreset> = listOf(
            FaceFilterPreset(
                id = "square",
                name = "方方脸",
                description = "整张脸被整体方化，五官和轮廓都带一点方块感。",
                accentColorHex = 0xFFFF8A5C,
                deformProfile = DeformProfile(
                    kind = FaceEffectKind.SquareGrid,
                    previewWidthScale = 1.24f,
                    previewHeightScale = 1.24f,
                    previewJawScale = 1.22f,
                    amount = 1.08f,
                    tension = 0.98f,
                    defaultGridSize = 5,
                ),
            ),
            FaceFilterPreset(
                id = "peak",
                name = "尖尖头",
                description = "整张脸向上拉伸成棱锥轮廓，夸张又利落。",
                accentColorHex = 0xFF9FE5FF,
                deformProfile = DeformProfile(
                    kind = FaceEffectKind.PeakPrism,
                    previewWidthScale = 0.62f,
                    previewHeightScale = 1.56f,
                    previewJawScale = 0.82f,
                    previewCrownLift = 0.28f,
                    amount = 1.12f,
                    tension = 0.96f,
                    lift = 0.28f,
                ),
            ),
            FaceFilterPreset(
                id = "bubble",
                name = "圆圆脸",
                description = "把整张脸鼓成圆圆软软的一团，轮廓更饱满更夸张。",
                accentColorHex = 0xFFFF769E,
                deformProfile = DeformProfile(
                    kind = FaceEffectKind.BubbleOrb,
                    previewWidthScale = 1.6f,
                    previewHeightScale = 1.46f,
                    previewJawScale = 1.48f,
                    amount = 1.28f,
                    tension = 1.06f,
                    lift = 0.14f,
                ),
            ),
            FaceFilterPreset(
                id = "blade",
                name = "刀锋脸",
                description = "整体纵向压窄，形成锐利纤长的刀锋脸型。",
                accentColorHex = 0xFF91FFD6,
                deformProfile = DeformProfile(
                    kind = FaceEffectKind.BladeSlice,
                    previewWidthScale = 0.52f,
                    previewHeightScale = 1.42f,
                    previewJawScale = 0.48f,
                    previewCrownLift = 0.14f,
                    amount = 1.16f,
                    tension = 0.96f,
                    lift = 0.1f,
                ),
            ),
        )
    }
}
