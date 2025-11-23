package com.example.mydreamsaver

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FireworksRenderer : GLSurfaceView.Renderer {

    // Logic System
    private val system = FireworksSystem()

    // OpenGL Buffers
    private lateinit var vertexBuffer: FloatBuffer

    // Shader Source (Same as before)
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
        
        out vec4 o_Color;

        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord);

            if (dist > 0.5) {
                discard; 
            }

            float glow = 1.0 - (dist * 2.0);
            glow = pow(glow, 1.5); 

            vec3 finalRGB = v_Color.rgb * u_BrightnessCap;
            o_Color = vec4(finalRGB, v_Color.a * glow);
        }
    """.trimIndent()

    private var programId: Int = 0
    private var uBrightnessCapHandle: Int = 0
    var brightnessCap: Float = 1.0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        programId = ShaderUtils.loadProgram(vertexShaderCode, fragmentShaderCode)
        uBrightnessCapHandle = GLES30.glGetUniformLocation(programId, "u_BrightnessCap")

        // Initialize Buffer (Max capacity)
        // 9 floats per particle * 4 bytes per float
        val bb = ByteBuffer.allocateDirect(Config.MAX_PARTICLES * 9 * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        // 1. Update Physics
        system.update()
        val count = system.getParticleCount()
        if (count == 0) return

        // 2. Update Buffer
        vertexBuffer.clear()
        vertexBuffer.put(system.getBufferData(), 0, count * 9)
        vertexBuffer.position(0)

        // 3. Draw
        GLES30.glUseProgram(programId)
        GLES30.glUniform1f(uBrightnessCapHandle, brightnessCap)

        // Stride = 9 * 4 bytes = 36 bytes total per particle
        val stride = 9 * 4

        // a_Position (Index 0, 4 floats)
        vertexBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)
        GLES30.glEnableVertexAttribArray(0)

        // a_Color (Index 1, 4 floats) -> starts at float index 4
        vertexBuffer.position(4)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, vertexBuffer)
        GLES30.glEnableVertexAttribArray(1)

        // a_Size (Index 2, 1 float) -> starts at float index 8
        vertexBuffer.position(8)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, vertexBuffer)
        GLES30.glEnableVertexAttribArray(2)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)

        // Cleanup attributes
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
    }
}
