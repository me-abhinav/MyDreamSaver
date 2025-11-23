package com.example.mydreamsaver

import kotlin.math.*
import kotlin.random.Random

class FireworksSystem {

    // --- Object Pool ---
    private val particles = Array(Config.MAX_PARTICLES) { Particle() }

    // Buffer for Heads (GL_POINTS)
    private val renderBuffer = FloatArray(Config.MAX_PARTICLES * 9)

    // NEW: Buffer for Tails (GL_LINES)
    // 2 vertices per tail * 9 floats per vertex * Max Particles
    private val tailBuffer = FloatArray(Config.MAX_PARTICLES * 2 * 9)

    var activeParticleCount = 0
    var activeTailVertexCount = 0 // Tracks how many vertices (not lines) are in the buffer

    // ... (Choreography variables remain the same) ...
    private val shooters = FloatArray(6)
    private val shooterColors =  Array(6) { OledPalette[0] }
    private val shooterStateTimer = IntArray(6)
    private val shooterShotsFired = IntArray(6)

    private var volleyState = "setup"
    private var volleyTimer = 0
    private var currentVolleyShots = 12

    // ... (spawnParticle and processChoreography remain the same) ...
    private fun spawnParticle(): Particle? {
        return particles.firstOrNull { !it.active }
    }

    fun update() {
        processChoreography()
        updateParticles()
    }

    private fun processChoreography() {
        // ... (Keep your existing Choreography logic here) ...
        // Copy-paste the logic from the previous step if you replaced the file
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

    // ... (launchRocket and explode remain the same) ...
    private fun launchRocket(shooterIndex: Int) {
        val p = spawnParticle() ?: return
        p.active = true; p.isSpark = false
        p.x = shooters[shooterIndex]; p.y = Config.VIRTUAL_HEIGHT
        val color = shooterColors[shooterIndex]
        p.r = color.r; p.g = color.g; p.b = color.b
        p.size = 18f
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
        // Size Modifier (Big explosions logic from previous step)
        val sizeMod = 0.8f + (Random.nextFloat() * 2.0f)

        var densityMod = 1.0f
        val densityRoll = Random.nextFloat()
        if (densityRoll < 0.3f) densityMod = 0.4f
        else if (densityRoll > 0.7f) densityMod = 1.6f

        // Cap particle count for performance
        val particleCount = ((100 + Random.nextInt(80)) * sizeMod * densityMod).toInt().coerceAtMost(400)

        // Base Expansion Speed
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
            spark.size = 12f

            // Direction Calculation
            val theta = Random.nextFloat() * PI.toFloat() * 2f
            val phi = acos(Random.nextFloat() * 2f - 1f)

            val xDir = sin(phi) * cos(theta)
            val yDir = sin(phi) * sin(theta)

            // NEW LOGIC: Irregularity
            // Instead of uniform speed, we vary each particle by +/- 25%
            // This breaks the "Perfect Circle" look.
            val irregularity = 0.95f + (Random.nextFloat() * 0.1f) // 0.95 to 1.05
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

            // --- Physics Update ---
            if (p.isSpark) {
                p.vx *= Config.DRAG_SPARK
                p.vy *= Config.DRAG_SPARK
                p.vy += Config.GRAVITY_SPARK

                p.age++
                if (p.age > 45) p.decay *= 1.03f
                p.alpha -= p.decay
                if (p.alpha <= 0) { p.reset(); continue }

                // --- TAIL LOGIC (Sparks only) ---
                // CHANGE 1: Increase multiplier from 4.0f to 40.0f
                // Because velocity is low (slow motion), we need a huge multiplier
                // to make the tail visible from behind the large particle head.
                val tailX = p.x - p.vx * 6.0f
                val tailY = p.y - p.vy * 6.0f

                val ndcHeadX = (p.x / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcHeadY = 1f - (p.y / Config.VIRTUAL_HEIGHT) * 2f
                val ndcTailX = (tailX / Config.VIRTUAL_WIDTH) * 2f - 1f
                val ndcTailY = 1f - (tailY / Config.VIRTUAL_HEIGHT) * 2f

                // Vertex 1: Head of Tail
                tailBuffer[tailIdx++] = ndcHeadX; tailBuffer[tailIdx++] = ndcHeadY
                tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                // CHANGE 2: Increase Alpha from 0.6f to 1.0f for visibility
                tailBuffer[tailIdx++] = p.r; tailBuffer[tailIdx++] = p.g; tailBuffer[tailIdx++] = p.b; tailBuffer[tailIdx++] = p.alpha * 1.0f
                tailBuffer[tailIdx++] = 0f

                // Vertex 2: End of Tail
                tailBuffer[tailIdx++] = ndcTailX; tailBuffer[tailIdx++] = ndcTailY
                tailBuffer[tailIdx++] = 0f; tailBuffer[tailIdx++] = 1f
                tailBuffer[tailIdx++] = p.r; tailBuffer[tailIdx++] = p.g; tailBuffer[tailIdx++] = p.b; tailBuffer[tailIdx++] = 0f
                tailBuffer[tailIdx++] = 0f

                activeTailVertexCount += 2

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

            p.x += p.vx
            p.y += p.vy

            // --- Fill Point Buffer ---
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

    // New Getters for Tail
    fun getTailBufferData(): FloatArray = tailBuffer
    fun getTailVertexCount(): Int = activeTailVertexCount
}
