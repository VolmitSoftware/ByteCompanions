package io.github.slimjar.downloader.verify;

import io.github.slimjar.exceptions.VerificationException;
import io.github.slimjar.resolver.data.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Accepts a library only when its SHA-256 matches the value recorded at build time.
 * The repository's own checksum files are never consulted, so a compromised or downgraded
 * repository cannot supply a matching artifact and checksum together.
 */
public final class PinnedDependencyVerifier implements DependencyVerifier {
    @NotNull public static final String RESOURCE = "slimjar-checksums.txt";
    @NotNull private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    @NotNull private final Map<String, String> pins;
    @NotNull private final ChecksumCalculator checksumCalculator;

    public PinnedDependencyVerifier(@NotNull final Map<String, String> pins, @NotNull final ChecksumCalculator checksumCalculator) {
        this.pins = Map.copyOf(pins);
        this.checksumCalculator = checksumCalculator;
    }

    public static @NotNull DependencyVerifierFactory factory(@Nullable final URL manifest) {
        final Map<String, String> pins = read(manifest);
        final ChecksumCalculator calculator = new FileChecksumCalculator("SHA-256");
        return resolver -> new PinnedDependencyVerifier(pins, calculator);
    }

    public static @NotNull String key(@NotNull final Dependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId() + ":" + dependency.version();
    }

    // Lines are "group:artifact:version sha256". A missing manifest yields no pins, which rejects every library.
    static @NotNull Map<String, String> read(@Nullable final URL manifest) {
        if (manifest == null) return Map.of();
        final String text;
        try (InputStream input = manifest.openStream()) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException err) {
            throw new VerificationException("Unable to read library checksums from " + manifest, err);
        }
        final Map<String, String> pins = new HashMap<>();
        for (final String line : text.split("\n")) {
            final String entry = line.trim();
            if (entry.isEmpty()) continue;
            final String[] parts = entry.split(" ");
            if (parts.length != 2 || !SHA_256.matcher(parts[1]).matches() || pins.put(parts[0], parts[1]) != null) {
                throw new VerificationException("Malformed library checksum entry: " + entry);
            }
        }
        return pins;
    }

    @Override
    public boolean verify(@NotNull final File file, @NotNull final Dependency dependency) throws VerificationException {
        if (!file.exists()) return false;
        final String expected = pins.get(key(dependency));
        if (expected == null) {
            throw new VerificationException("No pinned SHA-256 for library " + key(dependency));
        }
        return expected.equals(checksumCalculator.calculate(file).toLowerCase(Locale.ROOT));
    }

    @Override
    public @NotNull Optional<File> getChecksumFile(@NotNull final Dependency dependency) {
        return Optional.empty();
    }
}
