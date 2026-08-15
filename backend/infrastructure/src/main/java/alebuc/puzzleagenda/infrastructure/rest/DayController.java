package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.day.GetHorizon;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Day and horizon endpoints (contracts/api.md "Horizon" and "Days" sections).
 *
 * <p>Only {@code GET /api/horizon} is implemented here in the Foundational
 * phase. {@code GET /api/days/{date}} (with its materialization side effect)
 * is added in tasks.md T031/US1.
 */
@RestController
@RequestMapping("/api")
public class DayController {

    private final GetHorizon getHorizon;

    public DayController(GetHorizon getHorizon) {
        this.getHorizon = getHorizon;
    }

    @GetMapping("/horizon")
    public HorizonResponse getHorizon() {
        GetHorizon.HorizonView view = getHorizon.execute();
        return new HorizonResponse(view.day1(), view.forwardBound());
    }

    public record HorizonResponse(LocalDate day1, LocalDate forwardBound) {
    }
}
