package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudPatternAccessHandles;
import com.gtl.enhancedcore.client.cloud.CloudTerminalViewState;

public final class CloudPatternAccessRegression {
    public static void main(String[] args) { System.out.println("Cloud terminal assertions: "+run()); }
    static int run() {
        int checks=0;var first=new CloudPatternAccessHandles();var second=new CloudPatternAccessHandles();
        first.issue(Long.MIN_VALUE);second.issue(Long.MIN_VALUE+1);
        if(!first.owns(Long.MIN_VALUE)||first.owns(Long.MIN_VALUE+1)||second.owns(Long.MIN_VALUE))throw new AssertionError("Foreign menu handle intercepted");checks++;
        for(int i=1;i<=129;i++)first.issue(Long.MIN_VALUE+2L*i);
        if(first.owns(Long.MIN_VALUE)||first.owns(Long.MIN_VALUE+2)||!first.owns(Long.MIN_VALUE+4))throw new AssertionError("History is not exactly bounded to the latest 128 handles");checks++;
        if(!first.owns(Long.MIN_VALUE+258)||first.owns(0)||first.owns(Long.MAX_VALUE))throw new AssertionError("Current/foreign handle isolation");checks++;
        first.issue(Long.MIN_VALUE+258);
        if(!first.owns(Long.MIN_VALUE+4))throw new AssertionError("Duplicate issuance evicts an unrelated handle");checks++;
        try{first.issue(0);throw new AssertionError("Positive upstream domain accepted");}catch(IllegalArgumentException expected){checks++;}
        try {
            var directory=java.nio.file.Files.createTempDirectory("cloud-terminal-view-");
            var file=directory.resolve("views.json");
            try {
                var preferences=new CloudTerminalViewState(file);
                var view=new CloudTerminalViewState.View("library-a","铁 锭","gold");
                preferences.save("server-a/player-a/terminal-a",view);
                var reopened=new CloudTerminalViewState(file);
                if(!reopened.load("server-a/player-a/terminal-a").equals(view))throw new AssertionError("Close/reopen lost IO queries");checks++;
                if(!reopened.loadForLibrary("server-a/player-a/terminal-a","library-a").equals(view))throw new AssertionError("Library-specific view lost");checks++;
                if(!reopened.load("server-b/player-a/terminal-a").input().isEmpty()||!reopened.load("server-a/player-b/terminal-a").input().isEmpty())throw new AssertionError("Server/player view leaked");checks++;
                var other=new CloudTerminalViewState.View("library-b","copper","tin");
                reopened.save("server-a/player-a/terminal-a",other);
                if(!new CloudTerminalViewState(file).loadForLibrary("server-a/player-a/terminal-a","library-a").equals(view))throw new AssertionError("Changing linked library overwrote its prior view");checks++;
                java.nio.file.Files.writeString(file,"broken json");
                if(!new CloudTerminalViewState(file).load("scope").input().isEmpty())throw new AssertionError("Corrupt preference file blocked defaults");checks++;
                if(new CloudTerminalViewState.View(null,"x".repeat(200),null).input().length()!=96)throw new AssertionError("Unbounded view query");checks++;
            } finally { java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory.resolve("views.json.tmp"));java.nio.file.Files.deleteIfExists(directory); }
        } catch(java.io.IOException failure) { throw new AssertionError("View persistence test failed",failure); }
        return checks+sharedCatalogue();
    }
    @SuppressWarnings("unchecked")
    private static int sharedCatalogue() {
        try {
            var service=com.gtl.enhancedcore.common.cloud.CloudLibraryService.class;
            var libraryType=com.gtl.enhancedcore.common.cloud.store.CloudPatternStore.Library.class;
            var constructor=libraryType.getDeclaredConstructors()[0];constructor.setAccessible(true);
            var library=(com.gtl.enhancedcore.common.cloud.store.CloudPatternStore.Library)constructor.newInstance(
                    null,new java.util.UUID(1,1),new java.util.UUID(2,2));
            var catalogType=Class.forName(service.getName()+"$Catalog");
            var catalogConstructor=catalogType.getDeclaredConstructor();catalogConstructor.setAccessible(true);var catalog=catalogConstructor.newInstance();
            var revision=catalogType.getDeclaredField("revision");revision.setAccessible(true);revision.setLong(catalog,library.catalogRevision());
            var rowsField=catalogType.getDeclaredField("rows");rowsField.setAccessible(true);
            var rows=(java.util.Map<java.util.UUID,Object>)rowsField.get(catalog);
            var sideType=Class.forName(service.getName()+"$TerminalSide");var side=sideType.getDeclaredConstructors()[0];side.setAccessible(true);
            var input=side.newInstance("minecraft:iron_ingot\n",new int[]{7},new int[0]);
            var output=side.newInstance("minecraft:gold_ingot\n",new int[]{9},new int[0]);
            var rowType=Class.forName(service.getName()+"$CatalogRow");var row=rowType.getDeclaredConstructors()[0];row.setAccessible(true);
            for(int i=0;i<2664;i++){var id=new java.util.UUID(0,i+1);rows.put(id,row.newInstance(id,"",java.util.Set.of(),
                    new int[]{7,9},new int[0],false,true,input,output));}
            var indexes=service.getDeclaredField("CATALOGS");indexes.setAccessible(true);
            var catalogs=(java.util.Map<Object,Object>)indexes.get(null);catalogs.put(library,catalog);
            try {
                var bits=new java.util.BitSet();bits.set(7);var outs=new java.util.BitSet();outs.set(9);var empty=new java.util.BitSet();
                var all=com.gtl.enhancedcore.common.cloud.CloudLibraryService.terminalCatalogue(library,null,"iron","gold",empty,empty,empty,empty);
                if(all.size()!=2664||!all.get(2663).equals(new java.util.UUID(0,2664)))throw new AssertionError("Complete shared query omitted last page");
                if(all!=com.gtl.enhancedcore.common.cloud.CloudLibraryService.terminalCatalogue(library,null,"iron","gold",empty,empty,empty,empty))throw new AssertionError("Reopen rescans unchanged catalogue query");
                var localized=com.gtl.enhancedcore.common.cloud.CloudLibraryService.terminalCatalogue(library,null,"铁","金",bits,empty,outs,empty);
                if(localized.size()!=2664)throw new AssertionError("Localized IO must search the whole catalogue");
                bits.clear();
                if(!com.gtl.enhancedcore.common.cloud.CloudLibraryService.terminalCatalogue(library,null,"铁","金",bits,empty,outs,empty).isEmpty())throw new AssertionError("Query cache retained mutable alias keys");
                var version=libraryType.getDeclaredField("catalogRevision");version.setAccessible(true);version.setLong(library,1);
                if(!com.gtl.enhancedcore.common.cloud.CloudLibraryService.terminalCatalogue(library,null,"iron","gold",empty,empty,empty,empty).isEmpty())throw new AssertionError("Catalogue revision did not invalidate old matches");
                return 5;
            } finally { catalogs.remove(library); }
        } catch(ReflectiveOperationException failure) { throw new AssertionError("Shared terminal catalogue fixture",failure); }
    }
}
