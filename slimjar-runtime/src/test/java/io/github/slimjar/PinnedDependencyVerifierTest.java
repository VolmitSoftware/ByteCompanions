package io.github.slimjar;

import io.github.slimjar.downloader.verify.DependencyVerifier;
import io.github.slimjar.downloader.verify.PinnedDependencyVerifier;
import io.github.slimjar.exceptions.VerificationException;
import io.github.slimjar.resolver.data.Dependency;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.Assert.*;

public class PinnedDependencyVerifierTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final Dependency PINNED = new Dependency("test", "artifact", "1", null, List.of());
    private static final Dependency UNPINNED = new Dependency("test", "other", "1", null, List.of());
    private static final byte[] PAYLOAD = "complete artifact".getBytes(StandardCharsets.UTF_8);

    @Test
    public void onlyTheRecordedHashIsAccepted() throws Exception {
        Path manifest = temporary.newFile().toPath();
        Files.writeString(manifest, "test:artifact:1 " + sha256(PAYLOAD) + "\n");
        DependencyVerifier verifier = PinnedDependencyVerifier.factory(manifest.toUri().toURL()).create(dependency -> java.util.Optional.empty());
        Path artifact = temporary.newFile().toPath();

        assertFalse(verifier.verify(temporary.getRoot().toPath().resolve("missing.jar").toFile(), PINNED));
        Files.write(artifact, PAYLOAD);
        assertTrue(verifier.verify(artifact.toFile(), PINNED));
        Files.writeString(artifact, "tampered artifact");
        assertFalse(verifier.verify(artifact.toFile(), PINNED));
        VerificationException unpinned = assertThrows(VerificationException.class, () -> verifier.verify(artifact.toFile(), UNPINNED));
        assertTrue(unpinned.getMessage().contains("test:other:1"));
        assertTrue(verifier.getChecksumFile(PINNED).isEmpty());
    }

    @Test
    public void missingManifestRejectsEveryLibrary() throws Exception {
        DependencyVerifier verifier = PinnedDependencyVerifier.factory(null).create(dependency -> java.util.Optional.empty());
        Path artifact = temporary.newFile().toPath();
        Files.write(artifact, PAYLOAD);
        assertThrows(VerificationException.class, () -> verifier.verify(artifact.toFile(), PINNED));
    }

    @Test
    public void malformedManifestIsRejected() throws Exception {
        Path manifest = temporary.newFile().toPath();
        for (String content : List.of("test:artifact:1 abc", "test:artifact:1", "test:artifact:1 " + sha256(PAYLOAD) + "\ntest:artifact:1 " + sha256(PAYLOAD))) {
            Files.writeString(manifest, content);
            assertThrows(content, VerificationException.class, () -> PinnedDependencyVerifier.factory(manifest.toUri().toURL()));
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
