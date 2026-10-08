package com.gtl.enhancedcore.client.renderer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Native block-entity poses already contain the camera; do not multiply its view a second time. */
public final class StellarForgeFrame {
    private StellarForgeFrame() {}
    public static Matrix4f view(Matrix4fc controllerPose,Vector3f right,Vector3f up,Vector3f front) {
        var displacement=new Vector3f(up).mul(30).fma(-106,front).add(.5F,.5F,.5F);
        var basis=new Matrix4f().setColumn(0,new Vector4f(right,0))
                .setColumn(1,new Vector4f(up,0)).setColumn(2,new Vector4f(front,0));
        return new Matrix4f(controllerPose).translate(displacement).mul(basis);
    }
}
