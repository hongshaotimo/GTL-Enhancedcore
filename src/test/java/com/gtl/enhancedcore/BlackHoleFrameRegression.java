package com.gtl.enhancedcore;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.renderer.BlackHoleFrame;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercises the production world/preview frame before any shader-only validation. */
final class BlackHoleFrameRegression {
    private BlackHoleFrameRegression() {}

    static int run() throws Exception {
        int assertions = 0;
        var frames = new ArrayList<Map<String, Object>>();
        Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(65), 16F/9F, .1F, 1500F);
        Vector3f anchorRelativeToCamera = new Vector3f(5, 65, -190);
        for (int yaw : new int[]{-25,-15,0,15,25}) {
            for (int pitch : new int[]{-35,-25,-15,-5}) {
                Matrix4f cameraView = new Matrix4f().rotateX((float)Math.toRadians(pitch))
                        .rotateY((float)Math.toRadians(yaw));
                Matrix4f blockPose = new Matrix4f(cameraView).translate(anchorRelativeToCamera);
                Matrix4f unrelatedGlobalView = new Matrix4f().translate(11,-7,19)
                        .rotateXYZ(.18F, (float)Math.toRadians(yaw+60), .12F);
                Matrix4f original = new Matrix4f(blockPose);
                Matrix4f actual = BlackHoleFrame.view(blockPose, unrelatedGlobalView, false, 6.5F);
                Vector3f expectedCenter = cameraView.transformPosition(new Vector3f(anchorRelativeToCamera));
                require(actual.transformPosition(new Vector3f()).distance(expectedCenter)<.0001F,
                        "World field must stay on the native BER anchor while turning");
                assertions++;
                require(blockPose.equals(original), "Frame calculation mutates the caller's pose");
                assertions++;
                Matrix4f preview = BlackHoleFrame.view(new Matrix4f().translation(anchorRelativeToCamera),
                        cameraView, true, 6.5F);
                require(actual.equals(preview,.0001F), "LDLib split view differs from world view");
                assertions++;
                for (Vector3f point : new Vector3f[]{new Vector3f(),new Vector3f(2,1,-3),new Vector3f(-4,-2,1)}) {
                    Vector3f expected = blockPose.transformPosition(new Vector3f(point).mul(6.5F));
                    require(actual.transformPosition(new Vector3f(point)).distance(expected)<.0002F,
                            "Field surface detached from native world coordinates");
                    assertions++;
                }
                Vector4f clip = projection.transform(new Vector4f(expectedCenter,1));
                var frame = new LinkedHashMap<String,Object>();
                frame.put("yaw",yaw);
                frame.put("pitch",pitch);
                frame.put("field_view_column_major",actual.get(new float[16]));
                frame.put("projection_column_major",projection.get(new float[16]));
                frame.put("expected_center_ndc",new float[]{clip.x/clip.w,clip.y/clip.w});
                frames.add(frame);
            }
        }
        Matrix4f ui = new Matrix4f().translation(0,0,-350).rotateY(.3F);
        Matrix4f mirrored = new Matrix4f().translate(20,5,0).scale(-.7F,.7F,.7F);
        Matrix4f actual = BlackHoleFrame.view(mirrored,ui,true,6.5F);
        require(actual.determinant()<0, "Mirrored preview winding not detected");
        assertions++;
        require(actual.transformPosition(new Vector3f()).distance(
                ui.transformPosition(mirrored.transformPosition(new Vector3f())))<.0001F,
                "Preview scale or mirror moves the anchor");
        assertions++;
        Path report=Path.of("build/reports/black-hole-camera-frames.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(frames));
        return assertions;
    }

    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
}
