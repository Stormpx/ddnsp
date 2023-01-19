package io.crowds.util;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SimpleNativeLibLoader {
    private final static String NATIVE_RESOURCE_HOME ="META-INF/native/";

    public static void close(Closeable closeable){
        if (closeable!=null){
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void loadLib(String lib,ClassLoader cl) {

        String name = System.mapLibraryName(lib);

        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError e) {
            String path = NATIVE_RESOURCE_HOME + name;
            URL url = cl.getResource(path);
            if (url==null){
                throw new UnsatisfiedLinkError("no such file "+path);
            }
            int indexOf = name.lastIndexOf(".");
            String prefix = name.substring(0,indexOf);
            String suffix = name.substring(indexOf);

            InputStream in;
            OutputStream out;
            Path tempPath;
            try {
                in = url.openStream();
                tempPath = Files.createTempFile(prefix, suffix);
                out = Files.newOutputStream(tempPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                in.transferTo(out);
                out.flush();
                close(out);
            } catch (IOException ex) {
                throw new UnsatisfiedLinkError("could not load a native library: "+lib);
            }

            try {
                System.load(tempPath.toString());
                tempPath.toFile().deleteOnExit();
            } finally {
                close(in);
                close(out);
            }
        }

    }
}
