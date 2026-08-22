package com.wellconverge.membership.adapters;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * This module has no Spring Boot application class of its own (that lives in {@code bootstrap},
 * and adapters shouldn't depend on it — dependency direction runs the other way). Test-only
 * anchor so {@code @WebMvcTest} has a {@code @SpringBootConfiguration} to bootstrap from; the
 * slice mechanism still narrows instantiated beans to web-layer ones regardless of what else
 * this component-scans.
 */
@SpringBootApplication
public class TestApplication {
}
