package alebuc.puzzleagenda.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot app for infrastructure's own {@code @SpringBootTest}s.
 * The real entry point ({@code PuzzleAgendaApplication}) lives in the
 * bootstrap module, which depends on infrastructure — the reverse
 * dependency direction means infrastructure's tests can't use it directly.
 */
@SpringBootApplication(scanBasePackages = "alebuc.puzzleagenda")
public class TestApplication {
}
