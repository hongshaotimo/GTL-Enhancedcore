package com.gtl.enhancedcore.client.cloud;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Small client view preferences only. No stacks, permissions, network or world objects. */
public final class CloudTerminalViewState {
    public record View(String library,String input,String output) {
        public View {
            library=library==null?"":library;
            input=bounded(input);output=bounded(output);
        }
        private static String bounded(String value) {
            if(value==null)return "";value=value.replace('\n',' ').replace('\r',' ');
            return value.substring(0,Math.min(96,value.length()));
        }
    }
    private final Path file;
    private JsonObject values;
    public CloudTerminalViewState(Path file) { this.file=file; }
    private JsonObject values() {
        if(values==null) {
            values=new JsonObject();
            try { if(Files.isRegularFile(file)&&Files.size(file)<=262144) values=JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
            catch(Exception ignored) { values=new JsonObject(); }
        }
        return values;
    }
    public synchronized View load(String scope) { return read(key(scope)); }
    public synchronized View loadForLibrary(String scope,String library) { return read(key(scope+"\n"+library)); }
    private View read(String key) {
        try { var row=values().getAsJsonObject(key);if(row!=null)return new View(row.get("library").getAsString(),
                row.get("input").getAsString(),row.get("output").getAsString()); }
        catch(RuntimeException ignored) { }
        return new View("","","");
    }
    public synchronized void save(String scope,View view) {
        var row=new Gson().toJsonTree(view).getAsJsonObject();
        if(row.equals(values().get(key(scope))) && (view.library.isEmpty() || row.equals(values.get(key(scope+"\n"+view.library)))))return;
        values().add(key(scope),row);
        if(!view.library.isEmpty())values().add(key(scope+"\n"+view.library),row.deepCopy());
        while(values.size()>128)values.remove(values.keySet().iterator().next());
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,new Gson().toJson(values),StandardCharsets.UTF_8);
            try { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } catch(java.io.IOException ignored) { }
    }
    private static String key(String scope) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
