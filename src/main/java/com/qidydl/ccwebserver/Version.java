package com.qidydl.ccwebserver;

import java.util.Properties;

public class Version {
	private static String major;
	private static String medium;
	private static String minor;
	private static String rev;
	private static String mcversion;

	static void init(Properties properties)
	{
		if (properties != null)
		{
			major = properties.getProperty("qidydlCCWebServer.build.major.number");
			medium = properties.getProperty("qidydlCCWebServer.build.medium.number");
			minor = properties.getProperty("qidydlCCWebServer.build.minor.number");
			rev = properties.getProperty("qidydlCCWebServer.build.revision.number");
			mcversion = properties.getProperty("qidydlCCWebServer.build.mcversion");
		}
	}

	public static String fullVersionString()
	{
		return String.format("%s.%s.%s rev %s", major, medium, minor, rev);
	}
}
