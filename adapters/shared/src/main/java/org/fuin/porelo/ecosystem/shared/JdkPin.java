package org.fuin.porelo.ecosystem.shared;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JDK a checkout pins by its archive's address and digest, read from the archive's own file name.
 *
 * @param vendor whose JDK it is, {@code GraalVM} or {@code Temurin}
 * @param major its major version
 * @param download the archive, checked as a vendor's answer is
 */
public record JdkPin(String vendor, int major, ToolDownload download) {
    private static final Pattern TEMURIN =
            Pattern.compile(
                    "OpenJDK(\\d{1,4})U-jdk_x64_linux_hotspot_([0-9][0-9A-Za-z._-]{0,39})\\.tar\\.gz");

    /**
     * Whether an address names a JDK archive for Linux on x64, of any vendor.
     *
     * @param url the address as written
     * @return true when its file name says so
     */
    public static boolean isLinuxJdk(String url) {
        String name = fileName(url).toLowerCase(Locale.ROOT);
        return name.contains("jdk")
                && name.contains("linux")
                && (name.contains("x64") || name.contains("x86_64") || name.contains("amd64"))
                && (name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".zip"));
    }

    /**
     * Reads a pin.
     *
     * @param url the archive's address
     * @param digest its hex digest as written, trimmed and in either case
     * @return the pin
     * @throws ToolUnavailable when the file name is no known vendor's, or the address or digest
     *     would not be taken from a vendor either
     */
    public static JdkPin of(String url, String digest) {
        String hex = digest.strip().toLowerCase(Locale.ROOT);
        String name = fileName(url);
        Matcher graalvm = JdkDownloads.GRAALVM_ASSET.matcher(name);
        if (graalvm.matches()) {
            int major =
                    Integer.parseInt(
                            graalvm.group(1) != null ? graalvm.group(1) : graalvm.group(3));
            return new JdkPin("GraalVM", major, new ToolDownload(graalvm.group(2), url, hex));
        }
        Matcher temurin = TEMURIN.matcher(name);
        if (temurin.matches()) {
            return new JdkPin(
                    "Temurin",
                    Integer.parseInt(temurin.group(1)),
                    new ToolDownload(temurin.group(2), url, hex));
        }
        throw new ToolUnavailable("its file name " + name + " is no GraalVM CE or Temurin archive");
    }

    private static String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
