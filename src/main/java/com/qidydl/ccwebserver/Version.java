package com.qidydl.ccwebserver;

import java.util.Properties;

public class Version {
    private static String major;
    private static String minor;
    private static String rev;
    private static String build;
    @SuppressWarnings("unused")
    private static String mcversion;

    static void init(Properties properties)
    {
        if (properties != null)
        {
            major = properties.getProperty("ccwebserver.build.major.number");
            minor = properties.getProperty("ccwebserver.build.minor.number");
            rev = properties.getProperty("ccwebserver.build.revision.number");
            build = properties.getProperty("ccwebserver.build.number");
            mcversion = properties.getProperty("ccwebserver.build.mcversion");
        }
    }

    public static String fullVersionString()
    {
        return String.format("%s.%s.%s build %s", major, minor, rev, build);
    }
}
