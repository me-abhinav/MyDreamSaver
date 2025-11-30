package com.example.mydreamsaver

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FireworksRenderer : GLSurfaceView.Renderer {

    private val system = FireworksSystem()

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var tailBuffer: FloatBuffer

    // Added u_ScaleFactor to scale points based on screen resolution
    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec4 a_Position; 
        layout(location = 1) in vec4 a_Color;    
        layout(location = 2) in float a_Size;    
        
        uniform float u_ScaleFactor;

        out vec4 v_Color;

        void main() {
            gl_Position = a_Position;
            // Apply scale factor. 
            // On 1080p, this is 1.0. On 4K, this is 2.0.
            gl_PointSize = a_Size * u_ScaleFactor;
            v_Color = a_Color;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        
        in vec4 v_Color;
        uniform float u_BrightnessCap; 
        uniform bool u_IsLine; 
        
        out vec4 o_Color;

        void main() {
            vec4 finalColor = v_Color;

            if (!u_IsLine) {
                // POINT LOGIC (Heads)
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord);
                if (dist > 0.5) discard; 

                float core = 1.0 - smoothstep(0.15, 0.18, dist);
                float glow = 1.0 - (dist * 2.0);
                glow = pow(glow, 2.0); 
                
                float combinedAlpha = clamp(core + (glow * 0.5), 0.0, 1.0);
                finalColor.a *= combinedAlpha;
            } 
            // Else: TAIL LOGIC

            vec3 finalRGB = finalColor.rgb * u_BrightnessCap;
            o_Color = vec4(finalRGB, finalColor.a);
        }
    """.trimIndent()

    private var programId: Int = 0
    private var uBrightnessCapHandle: Int = 0
    private var uIsLineHandle: Int = 0
    private var uScaleFactorHandle: Int = 0 // NEW Handle

    var brightnessCap: Float = 1.0f
    private var scaleFactor: Float = 1.0f // NEW Variable

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)

        programId = ShaderUtils.loadProgram(vertexShaderCode, fragmentShaderCode)
        uBrightnessCapHandle = GLES30.glGetUniformLocation(programId, "u_BrightnessCap")
        uIsLineHandle = GLES30.glGetUniformLocation(programId, "u_IsLine")
        uScaleFactorHandle = GLES30.glGetUniformLocation(programId, "u_ScaleFactor")

        val bb = ByteBuffer.allocateDirect(Config.MAX_PARTICLES * 9 * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()

        val tb = ByteBuffer.allocateDirect(Config.MAX_PARTICLES * 6 * 9 * 4)
        tb.order(ByteOrder.nativeOrder())
        tailBuffer = tb.asFloatBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)

        // Calculate how much larger the actual screen is compared to our design baseline (1080p).
        // On a 4K emulator, height is 2160, so scaleFactor becomes 2.0.
        // This makes points 2x larger in pixels, keeping them the same physical size.
        scaleFactor = height.toFloat() / Config.VIRTUAL_HEIGHT
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // 1. Update Physics
        system.update()
        val particleCount = system.getParticleCount()
        val tailVertexCount = system.getTailVertexCount()

        if (particleCount == 0) return

        GLES30.glUseProgram(programId)
        GLES30.glUniform1f(uBrightnessCapHandle, brightnessCap)

        // Pass the calculated scale factor to the shader
        GLES30.glUniform1f(uScaleFactorHandle, scaleFactor)

        val stride = 9 * 4

        // --- 1. DRAW TAILS (GL_TRIANGLES) ---
        if (tailVertexCount > 0) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

            tailBuffer.clear()
            tailBuffer.put(system.getTailBufferData(), 0, tailVertexCount * 9)
            tailBuffer.position(0)

            GLES30.glUniform1i(uIsLineHandle, 1)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glEnableVertexAttribArray(1)

            tailBuffer.position(0)
            GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, tailBuffer)

            tailBuffer.position(4)
            GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, tailBuffer)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, tailVertexCount)

            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
        }

        // --- 2. DRAW HEADS (GL_POINTS) ---
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        vertexBuffer.clear()
        vertexBuffer.put(system.getBufferData(), 0, particleCount * 9)
        vertexBuffer.position(0)

        GLES30.glUniform1i(uIsLineHandle, 0)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glEnableVertexAttribArray(2)

        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(4)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(8)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, particleCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
    }
}
