package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.day.GetHorizon;
import alebuc.puzzleagenda.application.day.ViewDay;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Day and horizon endpoints (contracts/api.md "Horizon" and "Days" sections). */
@RestController
@RequestMapping("/api")
public class DayController {

    private final GetHorizon getHorizon;
    private final ViewDay viewDay;
    private final TimeBlockResponseAssembler responseAssembler;

    public DayController(GetHorizon getHorizon, ViewDay viewDay, TimeBlockResponseAssembler responseAssembler) {
        this.getHorizon = getHorizon;
        this.viewDay = viewDay;
        this.responseAssembler = responseAssembler;
    }

    @GetMapping("/horizon")
    public HorizonResponse getHorizon() {
        GetHorizon.HorizonView view = getHorizon.execute();
        return new HorizonResponse(view.day1(), view.forwardBound());
    }

    @GetMapping("/days/{date}")
    public DayResponse getDay(@PathVariable LocalDate date) {
        ViewDay.DayView view = viewDay.execute(date);
        List<TimeBlockResponse> blocks = view.blocks().stream().map(responseAssembler::toResponse).toList();
        return new DayResponse(view.date(), view.materialized(), blocks);
    }

    public record HorizonResponse(LocalDate day1, LocalDate forwardBound) {
    }

    public record DayResponse(LocalDate date, boolean materialized, List<TimeBlockResponse> blocks) {
    }
}
