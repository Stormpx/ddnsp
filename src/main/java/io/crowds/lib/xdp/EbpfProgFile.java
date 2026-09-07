package io.crowds.lib.xdp;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class EbpfProgFile {
    private final static String EBPF_PROG_HOME ="META-INF/ebpf/";

    private static void close(Closeable closeable){
        if (closeable!=null){
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static Path getProgFilepath(ClassLoader cl,String name) throws IOException {

        String path = EBPF_PROG_HOME + name;
        URL url = cl.getResource(path);
        if (url==null){
            throw new IOException("no such file "+path);
        }
        int indexOf = name.lastIndexOf(".");
        String prefix = name.substring(0,indexOf);
        String suffix = name.substring(indexOf);

        InputStream in = url.openStream();
        Path tempPath = Files.createTempFile(prefix, suffix);
        OutputStream out = Files.newOutputStream(tempPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        in.transferTo(out);
        out.flush();
        close(out);
        tempPath.toFile().deleteOnExit();
        return tempPath;

    }

}
