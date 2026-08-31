package com.ssafy.upcycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PackageStructureTests {

    private static final Path MAIN_PACKAGE = Path.of(
            System.getProperty("user.dir"),
            "src", "main", "java", "com", "ssafy", "upcycle");

    @ParameterizedTest
    @MethodSource("domainPackages")
    void requestedDomainPackageExists(String domainPackage) {
        assertThat(MAIN_PACKAGE.resolve(domainPackage).resolve("package-info.java"))
                .isRegularFile();
    }

    private static Stream<String> domainPackages() {
        return Stream.of(
                "user",
                "material",
                "trade",
                "inventory",
                "processing",
                "production",
                "product",
                "traceability",
                "blockchain",
                "common");
    }
}
