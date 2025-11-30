package com.example.mydreamsaver

import kotlin.math.*
import kotlin.random.Random

class FireworksSystem {

    // --- Object Pool ---
    private val particles = Array(Config.MAX_PARTICLES) { Particle() }

    // Buffer for Heads (GL_POINTS)
    private val renderBuffer = FloatArray(Config.MAX_PARTICLES * 9)

    // Buffer for Tails (GL_TRIANGLES)
    private val tailBuffer = FloatArray(Config.MAX_PARTICLES * 6 * 9)

    var activeParticleCount = 0
    var activeTailVertexCount = 0

    // --- Choreography Variables ---
    private val shooters = FloatArray(6)
    private val shooterColors =  Array(6) { OledPalette[0] }
    private val shooterStateTimer = IntArray(6)
    private val shooterShotsFired = IntArray(6)

    private var volleyState = "setup"
    private var volleyTimer = 0
    private var currentVolleyShots = 12

    private fun spawnParticle(): Particle? {
        return particles.firstOrNull { !it.active }
    }

    fun update() {
        processChoreography()
        updateParticles()
    }

    private fun processChoreography() {
        if (volleyState == "setup") {
            val margin = Config.VIRTUAL_WIDTH * 0.15f
            val width = Config.VIRTUAL_WIDTH - (margin * 2)
            val step = width / 5
            for (i in 0 until 6) {
                val jitter = (Random.nextFloat() * step * 0.6f) - (step * 0.3f)
                shooters[i] = margin + (step * i) + jitter
                shooterColors[i] = OledPalette.random()
                shooterStateTimer[i] = Random.nextInt(40)
                shooterShotsFired[i] = 0
            }
            currentVolleyShots = Random.nextInt(8, 16)
            volleyState = "firing"
        } else if (volleyState == "firing") {
            var activeShooters = 0
            for (i in 0 until 6) {
                if (shooterShotsFired[i] < currentVolleyShots) {
                    activeShooters++
                    shooterStateTimer[i]--
                    if (shooterStateTimer[i] <= 0) {
                        launchRocket(i)
                        shooterShotsFired[i]++
                        shooterStateTimer[i] = 90 + Random.nextInt(-15, 15)
                    }
                }
            }
            if (activeShooters == 0) {
                volleyState = "waiting"
                volleyTimer = 0
            }
        } else if (volleyState == "waiting") {
            volleyTimer++
            if (volleyTimer > 280) volleyState = "setup"
        }
    }

    private fun launchRocket(shooterIndex: Int) {
        val p = spawnParticle() ?: return
        p.active = true; p.isSpark = false
        p.x = shooters[shooterIndex]; p.y = Config.VIRTUAL_HEIGHT
        val color = shooterColors[shooterIndex]
        p.r = color.r; p.g = color.g; p.b = color.b

        // Rocket size (Small)
        p.size = 8f

        val targetY = (Config.VIRTUAL_HEIGHT * 0.1f) + (Random.nextFloat() * Config.VIRTUAL_HEIGHT * 0.3f)
        p.targetY = targetY
        val divergence = (Random.nextFloat() * 80f - 40f)
        val targetX = p.x + divergence
        val angle = atan2(targetY - p.y, targetX - p.x)
        val height = p.y - targetY
        val speed = (sqrt(2 * Config.GRAVITY_ROCKET * height) + 1.2f) * 2.2f
        p.vx = cos(angle) * speed; p.vy = sin(angle) * speed
        p.windOffset = Random.nextFloat() * 100f
        p.windDirection = if (Random.nextBoolean()) 1f else -1f
    }

    private fun explode(parent: Particle) {
        val sizeMod = 0.8f + (Random.nextFloat() * 2.0f)
        var densityMod = 1.0f
        val densityRoll = Random.nextFloat()
        if (densityRoll < 0.3f) densityMod = 0.4f
        else if (densityRoll > 0.7f) densityMod = 1.6f
        val particleCount = ((100 + Random.nextInt(80)) * sizeMod * densityMod).toInt().coerceAtMost(400)
        val baseExpansionSpeed = (1.8f + Random.nextFloat() * 1.2f) * sizeMod

        repeat(particleCount) {
            val spark = spawnParticle() ?: return@repeat
            spark.active = true
            spark.isSpark = true
            spark.x = parent.x
            spark.y = parent.y
            spark.r = parent.r
            spark.g = parent.g
            spark.b = parent.b
            spark.size = 12f // Base size

            val theta = Random.nextFloat() * PI.toFloat() * 2f
            val phi = acos(Random.nextFloat() * 2f - 1f)
            val xDir = sin(phi) * cos(theta)
            val yDir = sin(phi) * sin(theta)
            val irregularity = 0.95f + (Random.nextFloat() * 0.1f)
            val finalSpeed = baseExpansionSpeed * irregularity
            spark.vx = xDir * finalSpeed
            spark.vy = yDir * finalSpeed
            spark.baseDecay = Random.nextFloat() * 0.004f + 0.002f
            spark.decay = spark.baseDecay
        }
    }

    private fun updateParticles() {
        activeParticleCount = 0
        activeTailVertexCount = 0

        var bufIdx = 0
        var tailIdx = 0

        for (p in particles) {
            if (!p.active) continue

            // --- 1. Physics Update ---
            if (p.isSpark) {
                p.vx *= Config.DRAG_SPARK
                p.vy *= Config.DRAG_SPARK
                p.vy += Config.GRAVITY_SPARK
                p.age++

                if (p.age > 45) p.decay *= 1.03f
                p.alpha -= p.decay

                // Update size based on alpha (shrinking effect)
                p.size = 12f * p.alpha.coerceAtLeast(0f)

                if (p.alpha <= 0) { p.reset(); continue }

            } else {
                // Rocket Logic
                val wind = sin((p.y * 0.01f) + p.windOffset) * Config.WIND_FORCE * p.windDirection
                val helix = cos((p.y * Config.HELIX_FREQ) + p.windOffset) * Config.HELIX_FORCE
                p.vx += wind + helix
                p.vx *= Config.DRAG_ROCKET
                p.vy *= Config.DRAG_ROCKET
                p.vy -= Config.GRAVITY_ROCKET * 0.5f

                if ((p.vy > -0.5f || p.y <= p.targetY) && !p.exploded) {
                    p.exploded = true
                    explode(p)
                    p.reset(); continue
                }
            }

            // --- 2. Position Integration ---
            p.x += p.vx
            p.y += p.vy

            // --- 3. Tail Logic ---

            // Thinner tails for Rockets
            val widthMult = if (p.isSpark) 0.25f else 0.15f
            val halfWidth = p.size * widthMult

            // Shorter tails for Rockets
            // Rockets get a 3.0f length multiplier, Sparks keep 6.0f
            val lengthMult = if (p.isSpark) 6.0f else 3.0f

            val headX = p.x
            val headY = p.y
            val tailX = headX - p.vx * lengthMult
            val tailY = headY - p.vy * lengthMult

            var dx = headX - tailX
            var dy = headY - tailY
            val len = sqrt(dx*dx + dy*dy)

            if (len > 0.1f) {
                dx /= len
                dy /= len

                // Tapered Ribbon Logic (Head has width, Tail has 0 width)
                val pxHead = -dy * halfWidth
                val pyHead = dx * halfWidth

                val pxTail = 0f
                val pyTail = 0f

                val x1 = headX + pxHead; val y1 = headY + pyHead
                val x2 = headX - pxHead; val y2 = headY - pyHead
                val x3 = tailX + pxTail; val y3 = tailY + pyTail
                val x4 = tailX - pxTail; val y4 = tailY - pyTail

                val ndcX1 = (x1 / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcY1 = 1f - (y1 / Config.VIRTUAL_HEIGHT) * 2f
                val ndcX2 = (x2 / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcY2 = 1f - (y2 / Config.VIRTUAL_HEIGHT) * 2f
                val ndcX3 = (x3 / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcY3 = 1f - (y3 / Config.VIRTUAL_HEIGHT) * 2f
                val ndcX4 = (x4 / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcY4 = 1f - (y4 / Config.VIRTUAL_HEIGHT) * 2f

                val r = p.r; val g = p.g; val b = p.b; val aHead = p.alpha; val aTail = 0f

                // Push Triangles
                tailBuffer[tailIdx++] = ndcX1; tailBuffer[tailIdx++] = ndcY1; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aHead; tailBuffer[tailIdx++] = 0f
                tailBuffer[tailIdx++] = ndcX2; tailBuffer[tailIdx++] = ndcY2; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aHead; tailBuffer[tailIdx++] = 0f
                tailBuffer[tailIdx++] = ndcX3; tailBuffer[tailIdx++] = ndcY3; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aTail; tailBuffer[tailIdx++] = 0f

                tailBuffer[tailIdx++] = ndcX2; tailBuffer[tailIdx++] = ndcY2; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aHead; tailBuffer[tailIdx++] = 0f
                tailBuffer[tailIdx++] = ndcX4; tailBuffer[tailIdx++] = ndcY4; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aTail; tailBuffer[tailIdx++] = 0f
                tailBuffer[tailIdx++] = ndcX3; tailBuffer[tailIdx++] = ndcY3; tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = r; tailBuffer[tailIdx++] = g; tailBuffer[tailIdx++] = b; tailBuffer[tailIdx++] = aTail; tailBuffer[tailIdx++] = 0f

                activeTailVertexCount += 6
            }

            // --- 4. Render Buffer (Heads) ---
            val ndcX = (p.x / Config.VIRTUAL_WIDTH) * 2f - 1f
            val ndcY = 1f - (p.y / Config.VIRTUAL_HEIGHT) * 2f

            renderBuffer[bufIdx++] = ndcX; renderBuffer[bufIdx++] = ndcY
            renderBuffer[bufIdx++] = 0f; renderBuffer[bufIdx++] = 1f
            renderBuffer[bufIdx++] = p.r; renderBuffer[bufIdx++] = p.g; renderBuffer[bufIdx++] = p.b; renderBuffer[bufIdx++] = p.alpha
            renderBuffer[bufIdx++] = p.size

            activeParticleCount++
        }
    }

    fun getBufferData(): FloatArray = renderBuffer
    fun getParticleCount(): Int = activeParticleCount
    fun getTailBufferData(): FloatArray = tailBuffer
    fun getTailVertexCount(): Int = activeTailVertexCount
}
