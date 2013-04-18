/* Copyright (c) 2007-2009 Timothy Wall, All Rights Reserved
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.  
 */
package com.sun.jna;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import junit.framework.TestCase;

public class LibraryLoadTest extends TestCase {
    
    private static final String BUILDDIR =
        System.getProperty("jna.builddir", "build"
                           + (Platform.is64Bit() ? "-d64" : ""));

    public void testLoadJNALibrary() {
        assertTrue("Pointer size should never be zero", Pointer.SIZE > 0);
    }
    
    public void testLoadJAWT() {
        if (!Platform.HAS_AWT) return;

        if (GraphicsEnvironment.isHeadless()) return;

        // Encapsulate in a separate class to avoid class loading issues where
        // AWT is unavailable
        AWT.loadJAWT(getName());
    }
    
    public void testLoadAWTAfterJNA() {
        if (!Platform.HAS_AWT) return;

        if (GraphicsEnvironment.isHeadless()) return;

        if (Pointer.SIZE > 0) {
            Toolkit.getDefaultToolkit();
        }
    }
    
    public interface TestLibrary extends Library {
    }

    public void testLoadFromJNALibraryPath() {
        NativeLibrary.getInstance("testlib");
    }

    public void testLoadFromClasspath() {
        NativeLibrary.getInstance("testlib-path");
    }

    public void testLoadFromClasspathAbsolute() {
        String name = System.mapLibraryName("testlib-path").replace(".jnilib", ".dylib");
        NativeLibrary.getInstance("/" + Platform.RESOURCE_PREFIX + "/" + name);
    }

    public void testLoadFromJar() {
        NativeLibrary.getInstance("testlib-jar");
    }

    public void testLoadFromJarAbsolute() {
        String name = System.mapLibraryName("testlib-jar").replace(".jnilib", ".dylib");
        NativeLibrary.getInstance("/" + Platform.RESOURCE_PREFIX + "/" + name);
    }

    public void testLoadExplicitAbsolutePath() {
        NativeLibrary.getInstance(new File(BUILDDIR + "/native/testlib-truncated").getAbsolutePath());
    }

    public static interface CLibrary extends Library {
        int wcslen(WString wstr);
        int strlen(String str);
        int atol(String str);

        Pointer getpwuid(int uid);
        int geteuid();
    }

    private Object load() {
        return Native.loadLibrary(Platform.C_LIBRARY_NAME, CLibrary.class);
    }
    
    public void testLoadCLibrary() {
        load();
    }
    
    private static final String UNICODE = "\u0444\u043b\u0441\u0432\u0443";
    private void copy(File src, File dst) throws Exception {
        FileInputStream is = new FileInputStream(src);
        FileOutputStream os = new FileOutputStream(dst);
        int count;
        byte[] buf = new byte[1024];
        try {
            while ((count = is.read(buf, 0, buf.length)) > 0) {
                os.write(buf, 0, count);
            }
        }
        finally {
            try { is.close(); } catch(IOException e) { }
            try { os.close(); } catch(IOException e) { } 
        }
    }

    public void testLoadLibraryWithUnicodeName() throws Exception {
        String tmp = System.getProperty("java.io.tmpdir");
        String libName = System.mapLibraryName("jnidispatch");
        File src = new File(BUILDDIR + "/native", libName);
        if (Platform.isWindowsCE()) {
            src = new File("/Storage Card", libName);
        }
        assertTrue("Expected JNA native library at " + src + " is missing", src.exists());

        String newLibName = UNICODE;
        if (libName.startsWith("lib"))
            newLibName = "lib" + newLibName;
        int dot = libName.lastIndexOf(".");
        if (dot != -1) {
            if (Platform.isMac()) {
                newLibName += ".dylib";
            }
            else {
                newLibName += libName.substring(dot, libName.length());
            }
        }
        File dst = new File(tmp, newLibName);
        dst.deleteOnExit();
        copy(src, dst);
        NativeLibrary.addSearchPath(UNICODE, tmp);
        NativeLibrary nl = NativeLibrary.getInstance(UNICODE);
        nl.dispose();
    }
    
    public void testHandleObjectMethods() {
        CLibrary lib = (CLibrary)load();
        String method = "toString";
        try {
            lib.toString();
            method = "hashCode";
            lib.hashCode();
            method = "equals";
            lib.equals(null);
        }
        catch(UnsatisfiedLinkError e) {
            fail("Object method '" + method + "' not handled");
        }
    }

    public interface TestLib2 extends Library {
        int dependentReturnFalse();
    }

    // Only desktop windows provides an altered search path, looking for
    // dependent libraries in the same directory as the original
    public void testLoadDependentLibraryWithAlteredSearchPath() {
        try {
            TestLib2 lib = (TestLib2)Native.loadLibrary("testlib2", TestLib2.class);
            lib.dependentReturnFalse();
        }
        catch(UnsatisfiedLinkError e) {
            // failure expected on anything but windows
            if (Platform.isWindows() && !Platform.isWindowsCE()) {
                fail("Failed to load dependent libraries: " + e);
            }
        }
    }

    // Ubuntu bug when arch-specific libc is active
    // Only fails on *some* functions
    public void testLoadProperCLibraryVersion() {
        if (Platform.isWindows()) return;

        CLibrary lib = (CLibrary)Native.loadLibrary("c", CLibrary.class);
        assertNotNull("Couldn't get current user",
                      lib.getpwuid(lib.geteuid()));
    }
    
    private static class AWT {
        public static void loadJAWT(String name) {
            Frame f = new Frame(name);
            f.pack();
            try {
                // FIXME: this works as a test, but fails in ShapedWindowDemo
                // if the JAWT load workaround is not used
                Native.getWindowPointer(f);
            }
            finally {
                f.dispose();
            }
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(LibraryLoadTest.class);
    }
}
