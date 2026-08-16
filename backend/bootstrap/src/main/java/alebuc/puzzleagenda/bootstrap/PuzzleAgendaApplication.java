package alebuc.puzzleagenda.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. {@code scanBasePackages} is required because this
 * class lives under {@code alebuc.puzzleagenda.bootstrap}, a sibling of (not a
 * parent of) {@code alebuc.puzzleagenda.infrastructure} — Spring Boot's default
 * component scan only covers the annotated class's own package and below.
 */
@SpringBootApplication(scanBasePackages = "alebuc.puzzleagenda")
public class PuzzleAgendaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PuzzleAgendaApplication.class, args);
    }
}
