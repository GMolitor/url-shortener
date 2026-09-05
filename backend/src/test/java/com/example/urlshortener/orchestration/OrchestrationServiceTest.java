package com.example.urlshortener.orchestration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrchestrationServiceTest {
    private final OrchestrationRepository repository = mock(OrchestrationRepository.class);
    private final OrchestrationService service = new OrchestrationService(repository);

    @Test
    void rejectsCyclicDependencyGraphBeforePersistence() {
        List<OrchestrationTaskSpec> tasks = List.of(
                task("a", List.of("b")),
                task("b", List.of("a")));

        assertThatThrownBy(() -> service.create("cycle", tasks))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("cycle");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsTasksOutsideApprovedLocalPolicy() {
        OrchestrationTaskSpec task = new OrchestrationTaskSpec(
                "deploy", "publish to cloud", "DEPLOY", List.of(), false, 1, null);

        assertThatThrownBy(() -> service.create("policy", List.of(task)))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("policy boundary");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingDependencyBeforePersistence() {
        assertThatThrownBy(() -> service.create("missing", List.of(task("a", List.of("does-not-exist")))) )
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("dependency does not exist");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDuplicateAndSelfReferentialTaskKeysBeforePersistence() {
        assertThatThrownBy(() -> service.create("duplicate", List.of(task("a", List.of()), task("a", List.of()))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(() -> service.create("self", List.of(task("a", List.of("a")))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("cycle");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsFallbackSelfReferenceAndCyclesBeforePersistence() {
        assertThatThrownBy(() -> service.create(
                        "fallback-self",
                        List.of(new OrchestrationTaskSpec("a", "Run a", "TEST", List.of(), false, 1, "a"))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Fallback graph contains a cycle");
        assertThatThrownBy(() -> service.create(
                        "fallback-cycle",
                        List.of(
                                new OrchestrationTaskSpec("a", "Run a", "TEST", List.of(), false, 1, "b"),
                                new OrchestrationTaskSpec("b", "Run b", "TEST", List.of(), false, 1, "a"))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Fallback graph contains a cycle");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsExcessiveDependenciesAndReplanTaskCount() {
        List<String> dependencies = java.util.stream.IntStream.range(0, OrchestrationService.MAX_DEPENDENCIES_PER_TASK + 1)
                .mapToObj(index -> "dependency-" + index)
                .toList();
        assertThatThrownBy(() -> service.create("too-many-dependencies", List.of(task("a", dependencies))))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("more than 32 dependencies");
        verifyNoInteractions(repository);
    }

    private static OrchestrationTaskSpec task(String key, List<String> dependencies) {
        return new OrchestrationTaskSpec(key, "Run " + key, "TEST", dependencies, false, 2, null);
    }
}
