package com.sudo_code.ndrenderer;

import android.content.Context;
import android.content.Intent;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;


public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

    private static final String TAG = "MainActivity";
    private static final int BYTES_PER_FLOAT = 4;

    private Vibrator mVibrator;
    private CardboardOverlayView mOverlayView;

    private int mProgram;

    private int mProgramUniformBlockIndex;
    private int mUniformBuffer;
    private final int mUniformBufferkBindingIndex = 0;

    private final float mProjectionConstant = 3.f;
    private float[] mProjectionMatrix;

    private long mPrevTime    = System.nanoTime();
    private long mFrameTime   = 0;

    private String mObjectType;
    private NDShape mObject;    //The thing we're displaying

    private float[]   mModelMatrix; //TODO Stick this into the class probably eventually

    /**
     * Generate the uniform buffer that will store the projection matrix and the projection constant
     */
    private void genUniformBuffer() {
        int[] uniformBufferArray = new int[1];
        GLES30.glGenBuffers(1, uniformBufferArray, 0);
        mUniformBuffer = uniformBufferArray[0];

        mProjectionMatrix = new float[16];
        Matrix.perspectiveM(mProjectionMatrix, 0, 45.f, 1.f, 0.1f, 100.f);  //TODO: change this in onSurfaceChanged
        float[] color = new float[4];
        float[] padding = new float[3]; //It needs to be aligned to vec4 because std140

        FloatBuffer uniformBufferData = ByteBuffer.allocateDirect(
                mProjectionMatrix.length * BYTES_PER_FLOAT + 4 * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        uniformBufferData.put(mProjectionMatrix);
        uniformBufferData.put(mProjectionConstant);
        uniformBufferData.put(padding);
        uniformBufferData.position(0);

        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, mUniformBuffer);

        GLES30.glBufferData(
                GLES30.GL_UNIFORM_BUFFER,
                uniformBufferData.capacity() * BYTES_PER_FLOAT,
                uniformBufferData,
                GLES30.GL_STREAM_DRAW);

        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);

        GLES30.glBindBufferRange(
                GLES30.GL_UNIFORM_BUFFER,
                mUniformBufferkBindingIndex,
                mUniformBuffer,
                0,
                uniformBufferData.capacity());
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);

        //We can't initialize mObject here because the OpenGL context hasn't been created here
        Intent intent = getIntent();
        mObjectType = intent.getStringExtra("objectType");
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
        GLES30.glViewport(0, 0, width, height);
    }

    /**
     * Initialization code
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES30.glClearColor(0.3f, 0.3f, 0.3f, 1.0f);
        GLES30.glClearDepthf(1.f);

        int[] shaders = new int[2];

        if (mObjectType.equals("hypercube")) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

            shaders[0] = Utils.genShader(GLES30.GL_VERTEX_SHADER, R.raw.shape_vert, this);
            shaders[1] = Utils.genShader(GLES30.GL_FRAGMENT_SHADER, R.raw.shape_frag, this);

            mObject = new Hypercube(4, mProjectionConstant, 10.f, 0, 1);
        }

        else if (mObjectType.equals("hypertorus")) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

            shaders[0] = Utils.genShader(GLES30.GL_VERTEX_SHADER, R.raw.shape_vert, this);
            shaders[1] = Utils.genShader(GLES30.GL_FRAGMENT_SHADER, R.raw.shape_frag, this);

            mObject = new Hypertorus(4, mProjectionConstant, 10.f, 0, 1);
        }

        else if (mObjectType.equals("complexGraph")) {
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthMask(true);
            GLES30.glDepthFunc(GLES30.GL_LEQUAL);
            GLES30.glDepthRangef(0.0f, 1.0f);

            shaders[0] = Utils.genShader(GLES30.GL_VERTEX_SHADER, R.raw.c_graph_vert, this);
            shaders[1] = Utils.genShader(GLES30.GL_FRAGMENT_SHADER, R.raw.c_graph_frag, this);

            mObject = new ComplexGraph(50, 1.5f, mProjectionConstant, 10.f, 0, 1);
        }

        mProgram = Utils.genProgram(shaders);
        Utils.delShaders(shaders);

        mProgramUniformBlockIndex = GLES30.glGetUniformBlockIndex(mProgram, "Globals");
        GLES30.glUniformBlockBinding(mProgram, mProgramUniformBlockIndex, mUniformBufferkBindingIndex);

        genUniformBuffer();

        mModelMatrix = new float[16];
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0, 0, -10.f);

        Utils.checkGLError("onSurfaceCreated");
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        mFrameTime = System.nanoTime() - mPrevTime;
        mPrevTime  = System.nanoTime();

        mObject.rotate((float) ((double) mFrameTime / 1000000000.d), new int[] {0, 2});
        mObject.rotate((float) ((double) mFrameTime / 1000000000.d), new int[] {2, 3});
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        float[] eyeProjectionMatrix = new float[16];
        Matrix.multiplyMM(eyeProjectionMatrix, 0, eye.getEyeView(), 0, mModelMatrix, 0);
        Matrix.multiplyMM(eyeProjectionMatrix, 0, mProjectionMatrix, 0, eyeProjectionMatrix, 0);

        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, mUniformBuffer);
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, eyeProjectionMatrix.length * BYTES_PER_FLOAT,
                FloatBuffer.wrap(eyeProjectionMatrix));
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);

        GLES30.glUseProgram(mProgram);
        mObject.draw();
        GLES30.glUseProgram(0);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        // Always give user feedback.
        mVibrator.vibrate(50);
    }
}