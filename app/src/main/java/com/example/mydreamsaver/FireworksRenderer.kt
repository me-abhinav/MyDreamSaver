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
    private lateinit var tailBuffer: FloatBuffer // NEW Buffer

    private val vertexShaderCode = """
        #version 300 es
        layout(location = 0) in vec4 a_Position; 
        layout(location = 1) in vec4 a_Color;    
        layout(location = 2) in float a_Size;    

        out vec4 v_Color;

        void main() {
            gl_Position = a_Position;
            gl_PointSize = a_Size;
            v_Color = a_Color;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #version 300 es
        precision mediump float;
        
        in vec4 v_Color;
        uniform float u_BrightnessCap; 
        uniform bool u_IsLine; // NEW Flag
        
        out vec4 o_Color;

        void main() {
            vec4 finalColor = v_Color;

            if (!u_IsLine) {
                // POINT LOGIC (Round spark)
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord);
                if (dist > 0.5) discard; 

                float glow = 1.0 - (dist * 2.0);
                glow = pow(glow, 1.5); 
                finalColor.a *= glow;
            } 
            // Else: LINE LOGIC (Draw solid line, rely on vertex alpha gradient)

            vec3 finalRGB = finalColor.rgb * u_BrightnessCap;
            o_Color = vec4(finalRGB, finalColor.a);
        }
    """.trimIndent()

    private var programId: Int = 0
    private var uBrightnessCapHandle: Int = 0
    private var uIsLineHandle: Int = 0 // NEW Handle

    var brightnessCap: Float = 1.0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        programId = ShaderUtils.loadProgram(vertexShaderCode, fragmentShaderCode)
        uBrightnessCapHandle = GLES30.glGetUniformLocation(programId, "u_BrightnessCap")
        uIsLineHandle = GLES30.glGetUniformLocation(programId, "u_IsLine")

        // Point Buffer (Heads)
        val bb = ByteBuffer.allocateDirect(Config.MAX_PARTICLES * 9 * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()

        // Tail Buffer (Lines) - 2x size
        val tb = ByteBuffer.allocateDirect(Config.MAX_PARTICLES * 2 * 9 * 4)
        tb.order(ByteOrder.nativeOrder())
        tailBuffer = tb.asFloatBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
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
        val stride = 9 * 4

        // --- DRAW TAILS (GL_LINES) First ---
        if (tailVertexCount > 0) {
            tailBuffer.clear()
            tailBuffer.put(system.getTailBufferData(), 0, tailVertexCount * 9)
            tailBuffer.position(0)

            // Set Line Mode
            GLES30.glUniform1i(uIsLineHandle, 1) // true

            setupAttributes(tailBuffer, stride)
            GLES30.glLineWidth(3.0f) // Slightly thicker tail
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, tailVertexCount)
        }

        // --- DRAW HEADS (GL_POINTS) Second ---
        vertexBuffer.clear()
        vertexBuffer.put(system.getBufferData(), 0, particleCount * 9)
        vertexBuffer.position(0)

        // Set Point Mode
        GLES30.glUniform1i(uIsLineHandle, 0) // false

        setupAttributes(vertexBuffer, stride)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, particleCount)

        cleanupAttributes()
    }

    private fun setupAttributes(buffer: FloatBuffer, stride: Int) {
        // Position
        buffer.position(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, buffer)
        GLES30.glEnableVertexAttribArray(0)

        // Color
        buffer.position(4)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, buffer)
        GLES30.glEnableVertexAttribArray(1)

        // Size
        buffer.position(8)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, buffer)
        GLES30.glEnableVertexAttribArray(2)
    }

    private fun cleanupAttributes() {
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
    }
}
