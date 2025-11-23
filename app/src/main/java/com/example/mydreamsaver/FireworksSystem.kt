package com.example.mydreamsaver

import kotlin.math.*
import kotlin.random.Random

class FireworksSystem {

    // --- Object Pool ---
    private val particles = Array(Config.MAX_PARTICLES) { Particle() }
    private val renderBuffer = FloatArray(Config.MAX_PARTICLES * 9) // 9 floats per particle (x,y,z,w, r,g,b,a, size)
    var activeParticleCount = 0

    // --- Choreography State ---
    private val shooters = FloatArray(6)
    private val shooterColors =  Array(6) { OledPalette[0] }
    private val shooterStateTimer = IntArray(6)
    private val shooterShotsFired = IntArray(6)

    private var volleyState = "setup" // setup, firing, waiting
    private var volleyTimer = 0
    private var currentVolleyShots = 12

    // --- Utils ---
    private fun spawnParticle(): Particle? {
        // Linear search for an inactive particle (Simple and fast enough for <5000)
        return particles.firstOrNull { !it.active }
    }

    // --- The Main Loop (Called every frame) ---
    fun update() {
        processChoreography()
        updateParticles()
    }

    private fun processChoreography() {
        if (volleyState == "setup") {
            // Generate Shooters logic
            val margin = Config.VIRTUAL_WIDTH * 0.15f
            val width = Config.VIRTUAL_WIDTH - (margin * 2)
            val step = width / 5 // 6 shooters

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
                        shooterStateTimer[i] = 90 + Random.nextInt(-15, 15) // Firing Delay
                    }
                }
            }

            if (activeShooters == 0) {
                volleyState = "waiting"
                volleyTimer = 0
            }

        } else if (volleyState == "waiting") {
            volleyTimer++
            if (volleyTimer > 280) { // Pause Duration
                volleyState = "setup"
            }
        }
    }

    private fun launchRocket(shooterIndex: Int) {
        val p = spawnParticle() ?: return

        p.active = true
        p.isSpark = false
        p.x = shooters[shooterIndex]
        p.y = Config.VIRTUAL_HEIGHT

        val color = shooterColors[shooterIndex]
        p.r = color.r; p.g = color.g; p.b = color.b
        p.size = 18f

        val targetY = (Config.VIRTUAL_HEIGHT * 0.1f) + (Random.nextFloat() * Config.VIRTUAL_HEIGHT * 0.3f)
        p.targetY = targetY

        val divergence = (Random.nextFloat() * 80f - 40f)
        val targetX = p.x + divergence

        val angle = atan2(targetY - p.y, targetX - p.x)
        val height = p.y - targetY

        // REVERTED SPEED MULTIPLIER: Matches JS (2.2) closer now
        val speed = (sqrt(2 * Config.GRAVITY_ROCKET * height) + 1.2f) * 2.2f

        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed

        p.windOffset = Random.nextFloat() * 100f
        p.windDirection = if (Random.nextBoolean()) 1f else -1f
    }

    private fun explode(parent: Particle) {
        val sizeMod = 0.8f + Random.nextFloat()

        // Density logic from JS
        var densityMod = 1.0f
        val densityRoll = Random.nextFloat()
        if (densityRoll < 0.3f) densityMod = 0.4f
        else if (densityRoll > 0.7f) densityMod = 1.6f

        val particleCount = ((100 + Random.nextInt(80)) * sizeMod * densityMod).toInt()

        // REDUCED EXPANSION SPEED: Matches JS (1.6 + rnd)
        val expansionSpeed = (1.6f + Random.nextFloat() * 1.0f) * sizeMod

        for (i in 0 until particleCount) {
            val spark = spawnParticle() ?: break

            spark.active = true
            spark.isSpark = true
            spark.x = parent.x
            spark.y = parent.y

            spark.r = parent.r
            spark.g = parent.g
            spark.b = parent.b
            spark.size = 12f

            val theta = Random.nextFloat() * PI.toFloat() * 2f
            val phi = acos(Random.nextFloat() * 2f - 1f)

            val xDir = sin(phi) * cos(theta)
            val yDir = sin(phi) * sin(theta)

            spark.vx = xDir * expansionSpeed
            spark.vy = yDir * expansionSpeed

            spark.baseDecay = Random.nextFloat() * 0.004f + 0.002f
            spark.decay = spark.baseDecay
        }
    }

    private fun updateParticles() {
        activeParticleCount = 0
        var bufIdx = 0

        for (p in particles) {
            if (!p.active) continue

            // --- Physics Update ---
            if (p.isSpark) {
                p.vx *= Config.DRAG_SPARK
                p.vy *= Config.DRAG_SPARK
                p.vy += Config.GRAVITY_SPARK // Gravity goes down (positive Y in screen coords)

                p.age++
                if (p.age > 45) p.decay *= 1.03f
                p.alpha -= p.decay

                if (p.alpha <= 0) {
                    p.reset()
                    continue
                }
            } else {
                // Rocket Logic
                val wind = sin((p.y * 0.01f) + p.windOffset) * Config.WIND_FORCE * p.windDirection
                val helix = cos((p.y * Config.HELIX_FREQ) + p.windOffset) * Config.HELIX_FORCE

                p.vx += wind + helix
                p.vx *= Config.DRAG_ROCKET
                p.vy *= Config.DRAG_ROCKET
                p.vy -= Config.GRAVITY_ROCKET * 0.5f // Rockets fight gravity slightly less than sparks

                // Check Explosion
                // Note: In screen coords, "up" implies Y getting smaller
                if ((p.vy > -0.5f || p.y <= p.targetY) && !p.exploded) {
                    p.exploded = true
                    explode(p)
                    p.reset() // Remove rocket, replaced by sparks
                    continue
                }
            }

            p.x += p.vx
            p.y += p.vy // In Screen Coords, +y is Down. Wait...
            // CORRECTION: Standard physics usually y+ is up.
            // In JS Canvas: y=0 is top, y=1080 is bottom.
            // If Gravity is Positive, things fall DOWN (increasing Y).
            // Let's stick to: 0 is Top, 1080 is Bottom.

            // --- Fill Buffer (Mapping to OpenGL NDC) ---
            // OpenGL: x[-1, 1], y[-1, 1]. Center is 0,0.
            // Screen: x[0, 1920], y[0, 1080]. Center is 960, 540.

            val ndcX = (p.x / Config.VIRTUAL_WIDTH) * 2f - 1f
            val ndcY = 1f - (p.y / Config.VIRTUAL_HEIGHT) * 2f // Invert Y because GL Y+ is up

            renderBuffer[bufIdx++] = ndcX
            renderBuffer[bufIdx++] = ndcY
            renderBuffer[bufIdx++] = 0f // z
            renderBuffer[bufIdx++] = 1f // w

            renderBuffer[bufIdx++] = p.r
            renderBuffer[bufIdx++] = p.g
            renderBuffer[bufIdx++] = p.b
            renderBuffer[bufIdx++] = p.alpha

            renderBuffer[bufIdx++] = p.size

            activeParticleCount++
        }
    }

    // Helper to get buffer for Renderer
    fun getBufferData(): FloatArray = renderBuffer
    fun getParticleCount(): Int = activeParticleCount
}
