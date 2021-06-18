package io.github.glassmc.kiln.common;

import java.util.Locale;

public class SystemUtil {

    public static OSType getOSType() {
        String osName = System.getProperty("os.name").toLowerCase();
        if(osName.contains("win")) {
            return OSType.WINDOWS;
        }
        if(osName.contains("mac")) {
            return OSType.MAC;
        }
        if(osName.contains("nux")) {
            return OSType.LINUX;
        }
        return OSType.UNKNOWN;
    }

    public static Architecture getArchitecture() {
        String osArch = System.getProperty("os.arch");
        if(osArch.equals("32")) {
            return Architecture.X32;
        }
        if(osArch.equals("64")) {
            return Architecture.X64;
        }
        return Architecture.UNKNOWN;
    }

    public enum OSType {
        WINDOWS,
        MAC,
        LINUX,
        UNKNOWN
    }

    public enum Architecture {
        X32,
        X64,
        UNKNOWN
    }

}
