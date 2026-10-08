package com.gtl.enhancedcore.client.renderer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** World BER poses already contain the camera view; LDLib previews keep it separate. */
public final class BlackHoleFrame {
    private BlackHoleFrame() {}

    public static Matrix4f view(Matrix4fc blockPose, Matrix4fc previewView, boolean preview, float radius) {
        Matrix4f result = preview ? new Matrix4f(previewView).mul(blockPose) : new Matrix4f(blockPose);
        return result.scale(radius);
    }
}
