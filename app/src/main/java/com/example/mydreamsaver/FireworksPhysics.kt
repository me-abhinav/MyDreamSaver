package com.example.mydreamsaver

object Config {
    const val VIRTUAL_WIDTH = 1920f
    const val VIRTUAL_HEIGHT = 1080f

    // --- SLOW MOTION TUNING ---
    // Drastically reduced from 0.05f to 0.006f to match JS feel
    const val GRAVITY_ROCKET = 0.006f
    const val DRAG_ROCKET = 0.992f

    // Reduced from 0.15f to 0.025f
    const val GRAVITY_SPARK = 0.025f
    const val DRAG_SPARK = 0.975f

    // Softer wind
    const val WIND_FORCE = 0.001f
    const val HELIX_FORCE = 0.05f
    const val HELIX_FREQ = 0.15f

    const val MAX_PARTICLES = 4000
}

data class RgbColor(val r: Float, val g: Float, val b: Float)

//val OledPalette = listOf(
//    RgbColor(1.0f, 0.3f, 0.3f),
//    RgbColor(1.0f, 0.4f, 0.2f),
//    RgbColor(1.0f, 0.7f, 0.2f),
//    RgbColor(1.0f, 0.9f, 0.3f),
//    RgbColor(1.0f, 0.95f, 0.5f),
////    RgbColor(1.0f, 1.0f, 0.4f)
//)

val OledPalette = listOf(
    // --- REALISTIC CHEMICAL COLORS ---
    RgbColor(1.0f, 0.1f, 0.1f),    // Strontium Red (Deep, pure red)
    RgbColor(1.0f, 0.4f, 0.0f),    // Calcium Orange (Vibrant sunset orange)
    RgbColor(1.0f, 0.4f, 0.0f),    // Calcium Orange (Vibrant sunset orange)
    RgbColor(1.0f, 0.8f, 0.1f),    // Sodium Gold (Rich golden yellow)
    RgbColor(1.0f, 0.8f, 0.1f),    // Sodium Gold (Rich golden yellow)
    RgbColor(0.2f, 1.0f, 0.2f),    // Barium Green (Electric lime green)
//    RgbColor(0.1f, 0.5f, 1.0f),    // Copper Blue (Rich deep blue)
    RgbColor(0.7f, 0.2f, 1.0f),    // Potassium Violet (Bright purple)

    // --- MODERN VIBRANT ADDITIONS ---
    RgbColor(0.0f, 1.0f, 0.9f),    // Cyan (Turquoise)
    RgbColor(1.0f, 0.2f, 0.6f),    // Hot Pink / Magenta
    RgbColor(0.9f, 0.95f, 1.0f),    // Titanium White (Blinding white with slight blue tint)
    RgbColor(0.9f, 0.95f, 1.0f)    // Titanium White (Blinding white with slight blue tint)
)

class Particle {
    var active: Boolean = false
    var isSpark: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var r: Float = 1f
    var g: Float = 1f
    var b: Float = 1f
    var alpha: Float = 1f
    var size: Float = 10f
    var age: Int = 0
    var decay: Float = 0f
    var baseDecay: Float = 0f
    var targetY: Float = 0f
    var windOffset: Float = 0f
    var windDirection: Float = 1f
    var exploded: Boolean = false

    fun reset() {
        active = false
        age = 0
        alpha = 1f
        exploded = false
        baseDecay = 0f
    }
}
