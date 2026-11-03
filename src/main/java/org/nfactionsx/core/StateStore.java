package org.nfactionsx.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.nfactionsx.core.Model.State;

/** Atomic snapshots with forced file writes. Corrupt/missing live state with an existing backup fails closed. */
public final class StateStore {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    public StateStore(Path file) { this.file=file; }
    public State load() throws IOException {
        if(!Files.exists(file)) {
            if(Files.exists(file.resolveSibling(file.getFileName()+".bak")))
                throw new IOException("Snapshot absent mais sauvegarde presente : restauration manuelle requise");
            return new State();
        }
        if(Files.size(file)>64L*1024*1024) throw new IOException("Snapshot trop volumineux");
        try {
            State state=JSON.fromJson(Files.readString(file),State.class);
            if(state==null||state.schema!=1||state.players==null||state.factions==null||state.claims==null||state.zones==null||state.transfers==null)
                throw new IllegalArgumentException("Schema invalide");
            return state;
        } catch(RuntimeException error) { throw new IOException("Snapshot factions invalide. Ne pas supprimer le fichier.",error); }
    }
    public void save(State state) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        byte[] bytes=JSON.toJson(state).getBytes(StandardCharsets.UTF_8);
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            ByteBuffer buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        if(Files.exists(file)) Files.copy(file,file.resolveSibling(file.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
        // No non-atomic fallback: an unsupported filesystem must not silently weaken persistence.
        Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public static State copy(State state) { return JSON.fromJson(JSON.toJson(state),State.class); }
}
