package com.volmit.bytecompanions.platform.paper.bootstrap;

import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import io.github.slimjar.exceptions.RelocatorException;
import io.github.slimjar.relocation.JarFileRelocatorFactory;
import io.github.slimjar.relocation.RelocationRule;
import io.github.slimjar.relocation.Relocator;
import io.github.slimjar.relocation.RelocatorFactory;
import io.github.slimjar.relocation.facade.ReflectiveJarRelocatorFacadeFactory;
import io.github.slimjar.resolver.data.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

public final class H2ResourceRelocator implements Relocator {
    private static final NestedZipResources.Limits LIMITS = new NestedZipResources.Limits(1024, 8 * 1024 * 1024,
            2 * 1024 * 1024, 16 * 1024 * 1024, 100000, 256 * 1024 * 1024);
    private final Relocator delegate;
    private final String originalPrefix;
    private final String relocatedPrefix;

    public H2ResourceRelocator(Relocator delegate, RelocationRule rule) {
        if (!rule.exclusions().isEmpty() || !rule.inclusions().isEmpty()) {
            throw new IllegalArgumentException("H2 resource relocation requires an unrestricted package rule");
        }
        this.delegate = delegate;
        this.originalPrefix = rule.originalPackagePattern().replace('.', '/') + "/";
        this.relocatedPrefix = rule.relocatedPackagePattern().replace('.', '/') + "/";
    }

    public static SpigotApplicationBuilder configure(SpigotApplicationBuilder builder, Path downloads) {
        RelocatorFactory defaults = new JarFileRelocatorFactory(ReflectiveJarRelocatorFacadeFactory.create(
                downloads, Collections.singleton(Repository.central())));
        return builder.relocatorFactory(rules -> {
            Relocator delegate = defaults.create(rules);
            for (RelocationRule rule : rules) {
                if (rule.originalPackagePattern().equals(String.join(".", "org", "h2"))) {
                    return new H2ResourceRelocator(delegate, rule);
                }
            }
            return delegate;
        });
    }

    @Override
    public void relocate(File input, File output) throws RelocatorException {
        Path temporary = null;
        Throwable primaryFailure = null;
        try {
            Path destination = output.toPath().toAbsolutePath();
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), ".relocating-resources-", ".jar");
            delegate.relocate(input, temporary.toFile());
            NestedZipResources.relocate(temporary, relocatedPrefix + "util/data.zip",
                    Map.of(originalPrefix, relocatedPrefix), LIMITS);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            RelocatorException relocationFailure = new RelocatorException("Could not relocate nested library resources", failure);
            primaryFailure = relocationFailure;
            throw relocationFailure;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(failure);
                    } else {
                        throw new RelocatorException("Could not remove temporary relocation output", failure);
                    }
                }
            }
        }
    }
}
