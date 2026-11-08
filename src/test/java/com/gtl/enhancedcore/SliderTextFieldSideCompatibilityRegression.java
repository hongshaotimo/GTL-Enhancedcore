package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.integration.SliderTextFieldSideCompatibility;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import javax.tools.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes the unmodified ADD widget bodies against narrow LDLib/MC ABI fixtures, without Forge or worlds. */
public final class SliderTextFieldSideCompatibilityRegression {
    private static final String TARGET = "com.gtladd.gtladditions.api.gui.widget.SliderTextFieldWidget";
    private static final String WIDGET = "com.lowdragmc.lowdraglib.gui.widget.";
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        byte[] original;
        try (ZipFile zip = new ZipFile("libs/gtladditions-3.3.1Custom.jar");
                InputStream input = zip.getInputStream(zip.getEntry(TARGET.replace('.', '/') + ".class"))) { original = input.readAllBytes(); }
        ClassNode node = new ClassNode(); new ClassReader(original).accept(node, 0);
        Map<String, Integer> before = new HashMap<>();
        node.methods.forEach(method -> before.put(method.name + method.desc, method.instructions.size()));
        check(SliderTextFieldSideCompatibility.transform(node) == 1, "Exactly one unsafe side predicate is replaced");
        for (var method : node.methods) {
            check(method.instructions.size() == before.get(method.name + method.desc), "Instruction count is preserved for " + method.name);
            check(java.util.Arrays.stream(method.instructions.toArray()).noneMatch(value -> value instanceof MethodInsnNode call
                    && call.owner.startsWith("net/minecraft/client/")), "No executable client calls remain in " + method.name);
        }
        ClassWriter writer = new ClassWriter(0); node.accept(writer); byte[] fixed = writer.toByteArray();
        boolean duplicateRejected = false;
        try { SliderTextFieldSideCompatibility.transform(node); } catch (IllegalStateException expected) { duplicateRejected = true; }
        check(duplicateRejected, "Duplicate transformation is rejected");
        ClassNode drift = new ClassNode(); new ClassReader(original).accept(drift, 0);
        for (var method : drift.methods) if (method.name.equals("setCurrentValue"))
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("m_18695_")) call.name = "changedThreadCheck";
        boolean driftRejected = false;
        try { SliderTextFieldSideCompatibility.transform(drift); } catch (IllegalStateException expected) { driftRejected = true; }
        check(driftRejected, "Changed side-predicate ABI fails visibly");
        var fixtures = compileFixtures();
        for (String operation : List.of("set", "detect", "action")) {
            try (var f = new Fixture(fixtures, original, false, true)) {
                boolean rejected = false;
                try { f.operation(operation, 31); } catch (InvocationTargetException failure) {
                    rejected = failure.getCause() instanceof NoClassDefFoundError;
                }
                check(rejected && f.loader.clientAttempts > 0, "Original real ADD " + operation + " reproduces dedicated-side class loading failure");
            }
        }
        try (var f = new Fixture(fixtures, fixed, false, true)) {
            f.operation("set", 25); f.verify(25, 1);
            check(f.updates().equals(List.of(25)), "Server setter retains update255 payload");
            f.operation("detect", 37); f.verify(37, 2);
            check(f.updates().equals(List.of(25, 37, 37)), "Server detect retains both upstream update writes");
            int count = f.updates().size(); f.call("detectAndSendChanges");
            check(f.updates().size() == count && f.responded.size() == 2, "Unchanged supplier does not rerun setter or emit an update");
            f.operation("action", 49); f.verify(49, 3);
            check(f.updates().getLast() == 49 && f.loader.clientAttempts == 0, "Server client-action applies and synchronizes without attempting client loading");
        }
        try (var f = new Fixture(fixtures, fixed, true, false)) {
            f.operation("initial", 20); f.verify(20, 1); f.knob(19);
            f.operation("update", 60); f.verify(60, 2); f.knob(57);
            check(f.updates().isEmpty(), "Client incoming initial/update data updates knob without server broadcasts");
            f.call("mouseDragged", new Class<?>[]{double.class, double.class, int.class, double.class, double.class}, 80d, 16d, 0, 1d, 0d);
            f.verify(80, 3); f.knob(76);
            check(f.actions().equals(List.of(80)) && f.loader.clientAttempts == 0, "Client drag preserves original action255 payload and never consults physical-client singleton");
            Object output = f.buffer(0); f.call("writeInitialData", new Class<?>[]{f.bufferClass}, output);
            check(f.bufferClass.getField("value").getInt(output) == 80, "Original initial-data writer retains current value");
        }
        // The physical client singleton reports the render thread, but the widget belongs to the integrated server.
        try (var f = new Fixture(fixtures, fixed, false, false)) {
            f.operation("set", 70); f.verify(70, 1); f.knob(9);
            check(f.updates().equals(List.of(70)) && f.loader.clientAttempts == 0,
                    "Integrated-server logical side synchronizes instead of moving the knob despite physical CLIENT");
        }
        Path evidence = Path.of("_work/add-slider-side-20261108"); Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("offline-widget-regression.txt"), "PASS: " + assertions
                + " assertions. Actual ADD SliderTextFieldWidget constructor and all tested method bodies executed unchanged except the production side predicate. Narrow ABI fixtures replace external LDLib/MC/Kotlin classes; dedicated child loader rejects every net.minecraft.client.* class. No Forge client/server/world started; this is not game acceptance or network transport testing.\n");
        return assertions;
    }

    private static final class Fixture implements AutoCloseable {
        final FixtureLoader loader;
        final Object widget;
        final Class<?> widgetClass, bufferClass;
        final AtomicInteger supplied = new AtomicInteger(10);
        final List<Integer> responded = new ArrayList<>(), changed = new ArrayList<>();
        Fixture(Map<String, byte[]> fixtures, byte[] target, boolean remote, boolean rejectClient) throws Exception {
            var classes = new HashMap<>(fixtures); classes.put(TARGET, target);
            loader = new FixtureLoader(classes, rejectClient);
            loader.loadClass(WIDGET + "Widget").getField("remoteDefault").setBoolean(null, remote);
            widgetClass = loader.loadClass(TARGET); bufferClass = loader.loadClass("net.minecraft.network.FriendlyByteBuf");
            widget = widgetClass.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class, Supplier.class, Consumer.class, Consumer.class)
                    .newInstance(0, 0, 100, 24, 0, 100, (Supplier<Integer>) supplied::get, (Consumer<Integer>) responded::add, (Consumer<Integer>) changed::add);
        }
        Object buffer(int value) throws Exception { Object buffer = bufferClass.getConstructor().newInstance(); bufferClass.getField("value").setInt(buffer, value); return buffer; }
        void operation(String name, int value) throws Exception {
            switch (name) {
                case "set" -> call("setCurrentValue", new Class<?>[]{int.class}, value);
                case "detect" -> { supplied.set(value); call("detectAndSendChanges"); }
                case "action" -> call("handleClientAction", new Class<?>[]{int.class, bufferClass}, 255, buffer(value));
                case "initial" -> call("readInitialData", new Class<?>[]{bufferClass}, buffer(value));
                case "update" -> call("readUpdateInfo", new Class<?>[]{int.class, bufferClass}, 255, buffer(value));
                default -> throw new AssertionError(name);
            }
        }
        Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
        Object call(String name, Class<?>[] types, Object... args) throws Exception { return widgetClass.getMethod(name, types).invoke(widget, args); }
        void verify(int value, int callbacks) throws Exception {
            var field = widgetClass.getDeclaredField("currentValue"); field.setAccessible(true);
            check(field.getInt(widget) == value && responded.size() == callbacks && changed.size() == callbacks
                    && responded.getLast() == value && changed.getLast() == value, "Real setter retains current value and both callback sequences at " + value);
            var text = widgetClass.getDeclaredField("textField"); text.setAccessible(true);
            check(text.get(widget).getClass().getField("current").get(text.get(widget)).equals(String.valueOf(value)), "Text child retains value " + value);
        }
        @SuppressWarnings("unchecked") List<Integer> updates() throws Exception { return (List<Integer>) loader.loadClass(WIDGET + "Widget").getField("updates").get(widget); }
        @SuppressWarnings("unchecked") List<Integer> actions() throws Exception { return (List<Integer>) loader.loadClass(WIDGET + "Widget").getField("actions").get(widget); }
        void knob(int expected) throws Exception {
            var field = widgetClass.getDeclaredField("slider"); field.setAccessible(true); Object slider = field.get(widget);
            var widgets = (List<?>) slider.getClass().getField("widgets").get(slider); Object knob = widgets.getFirst();
            Object position = knob.getClass().getMethod("getPosition").invoke(knob);
            check(position.getClass().getField("x").getInt(position) == expected, "Original knob position math remains " + expected);
        }
        @Override public void close() {}
    }

    private static final class FixtureLoader extends ClassLoader {
        final Map<String, byte[]> classes; final boolean rejectClient; int clientAttempts;
        FixtureLoader(Map<String, byte[]> classes, boolean rejectClient) { super(null); this.classes = classes; this.rejectClient = rejectClient; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.")) { clientAttempts++; if (rejectClient) throw new ClassNotFoundException("Dedicated-side guard: " + name); }
            Class<?> found = findLoadedClass(name);
            if (found == null) { byte[] bytes = classes.get(name); found = bytes == null ? super.loadClass(name, false) : defineClass(name, bytes, 0, bytes.length); }
            if (resolve) resolveClass(found); return found;
        }
    }

    private static Map<String, byte[]> compileFixtures() throws Exception {
        Map<String, String> sources = new HashMap<>();
        sources.put("com.lowdragmc.lowdraglib.utils.Position", "public class Position { public int x,y; public Position(int x,int y){this.x=x;this.y=y;} }");
        sources.put("com.lowdragmc.lowdraglib.utils.Size", "public class Size { public int width,height; public Size(int w,int h){width=w;height=h;} }");
        sources.put("io.netty.buffer.ByteBuf", "public class ByteBuf {}");
        sources.put("net.minecraft.network.FriendlyByteBuf", "public class FriendlyByteBuf extends io.netty.buffer.ByteBuf { public int value; public int readInt(){return value;} public io.netty.buffer.ByteBuf writeInt(int v){value=v;return this;} }");
        sources.put("net.minecraft.util.Mth", "public class Mth { public static double m_14008_(double v,double a,double b){return Math.max(a,Math.min(b,v));} }");
        sources.put("net.minecraft.client.Minecraft", "public class Minecraft { public static Minecraft m_91087_(){return new Minecraft();} public boolean m_18695_(){return true;} }");
        sources.put("kotlin.jvm.internal.Intrinsics", "public class Intrinsics { public static void checkNotNullParameter(Object o,String n){java.util.Objects.requireNonNull(o,n);} public static void checkNotNullExpressionValue(Object o,String n){checkNotNullParameter(o,n);} public static void checkNotNull(Object o){java.util.Objects.requireNonNull(o);} }");
        sources.put("com.lowdragmc.lowdraglib.gui.texture.IGuiTexture", "public interface IGuiTexture {}");
        sources.put("com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture", "public class ColorRectTexture implements IGuiTexture {}");
        sources.put("com.lowdragmc.lowdraglib.gui.editor.ColorPattern", "public class ColorPattern { public static final ColorPattern GREEN=new ColorPattern(),WHITE=new ColorPattern(); public com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture rectTexture(){return new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture();} }");
        sources.put(WIDGET + "Widget", """
                import com.lowdragmc.lowdraglib.utils.*;
                import java.util.*; import java.util.function.*;
                import net.minecraft.network.FriendlyByteBuf;
                public class Widget {
                    public static boolean remoteDefault; public final boolean remote=remoteDefault;
                    public final List<Integer> updates=new ArrayList<>(), actions=new ArrayList<>();
                    protected Position position; protected Size size;
                    public Widget(Position p,Size s){position=p;size=s;}
                    public boolean isRemote(){return remote;} public Size getSize(){return size;}
                    public int getSizeWidth(){return size.width;} public Position getPosition(){return position;}
                    public void setSelfPosition(Position p){position=p;}
                    public Widget setBackground(com.lowdragmc.lowdraglib.gui.texture.IGuiTexture[] t){return this;}
                    public boolean isMouseOverElement(double x,double y){return true;}
                    public void writeUpdateInfo(int id,Consumer<FriendlyByteBuf> writer){if(id!=255)throw new AssertionError(id);var b=new FriendlyByteBuf();writer.accept(b);updates.add(b.value);}
                    public void writeClientAction(int id,Consumer<FriendlyByteBuf> writer){if(id!=255)throw new AssertionError(id);var b=new FriendlyByteBuf();writer.accept(b);actions.add(b.value);}
                }
                """);
        sources.put(WIDGET + "WidgetGroup", """
                import com.lowdragmc.lowdraglib.utils.*; import java.util.*; import net.minecraft.network.FriendlyByteBuf;
                public class WidgetGroup extends Widget {
                    public final List<Widget> widgets=new ArrayList<>();
                    public WidgetGroup(Position p,Size s){super(p,s);}
                    public WidgetGroup(int x,int y,int w,int h){this(new Position(x,y),new Size(w,h));}
                    public WidgetGroup addWidget(Widget w){widgets.add(w);return this;}
                    public void detectAndSendChanges(){} public void handleClientAction(int i,FriendlyByteBuf b){}
                    public void readUpdateInfo(int i,FriendlyByteBuf b){} public void readInitialData(FriendlyByteBuf b){} public void writeInitialData(FriendlyByteBuf b){}
                    public boolean mouseClicked(double x,double y,int b){return false;}
                    public boolean mouseDragged(double x,double y,int b,double dx,double dy){return false;}
                }
                """);
        sources.put(WIDGET + "TextFieldWidget", "public class TextFieldWidget extends Widget { public String current; public TextFieldWidget(int x,int y,int w,int h,java.util.function.Supplier<String>s,java.util.function.Consumer<String> c){super(new com.lowdragmc.lowdraglib.utils.Position(x,y),new com.lowdragmc.lowdraglib.utils.Size(w,h));} public TextFieldWidget setNumbersOnly(int a,int b){return this;} public TextFieldWidget setCurrentString(Object o){current=String.valueOf(o);return this;} }");
        sources.put(WIDGET + "DraggableScrollableWidgetGroup", "public class DraggableScrollableWidgetGroup extends WidgetGroup { public DraggableScrollableWidgetGroup(int x,int y,int w,int h){super(x,y,w,h);} public DraggableScrollableWidgetGroup setScrollable(boolean v){return this;} public DraggableScrollableWidgetGroup setDraggable(boolean v){return this;} public DraggableScrollableWidgetGroup setUseScissor(boolean v){return this;} }");
        sources.put(WIDGET + "ImageWidget", "public class ImageWidget extends Widget { public ImageWidget(int x,int y,int w,int h,com.lowdragmc.lowdraglib.gui.texture.IGuiTexture t){super(new com.lowdragmc.lowdraglib.utils.Position(x,y),new com.lowdragmc.lowdraglib.utils.Size(w,h));} }");
        var compiler = ToolProvider.getSystemJavaCompiler();
        check(compiler != null, "Java21 compiler is available for isolated ABI fixtures");
        Map<String, byte[]> result = new HashMap<>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var standard = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8);
                var manager = new ForwardingJavaFileManager<StandardJavaFileManager>(standard) {
                    @Override public JavaFileObject getJavaFileForOutput(Location location,String name,JavaFileObject.Kind kind,FileObject sibling){
                        return new SimpleJavaFileObject(URI.create("memory:///"+name.replace('.','/')+kind.extension),kind){
                            @Override public OutputStream openOutputStream(){return new ByteArrayOutputStream(){@Override public void close(){result.put(name,toByteArray());}};}
                        };
                    }
                }) {
            List<JavaFileObject> inputs = new ArrayList<>();
            sources.forEach((name, body) -> inputs.add(new SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignored){return "package "+name.substring(0,name.lastIndexOf('.'))+";\n"+body;}
            }));
            boolean success = compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--release","21"),null,inputs).call();
            check(success, "Narrow external ABI fixtures compile: " + diagnostics.getDiagnostics());
        }
        return result;
    }

    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
