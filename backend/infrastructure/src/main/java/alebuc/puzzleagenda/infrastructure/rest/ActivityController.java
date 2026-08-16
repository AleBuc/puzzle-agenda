package alebuc.puzzleagenda.infrastructure.rest;

import alebuc.puzzleagenda.application.activity.CreateActivity;
import alebuc.puzzleagenda.application.activity.DeleteActivity;
import alebuc.puzzleagenda.application.activity.EditActivity;
import alebuc.puzzleagenda.application.activity.ListActivities;
import alebuc.puzzleagenda.domain.activity.Activity;
import alebuc.puzzleagenda.domain.activity.ActivityStatus;
import alebuc.puzzleagenda.domain.activity.Priority;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Activity backlog endpoints (contracts/api.md "Activities" section). */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ListActivities listActivities;
    private final CreateActivity createActivity;
    private final EditActivity editActivity;
    private final DeleteActivity deleteActivity;

    public ActivityController(
            ListActivities listActivities, CreateActivity createActivity, EditActivity editActivity, DeleteActivity deleteActivity) {
        this.listActivities = listActivities;
        this.createActivity = createActivity;
        this.editActivity = editActivity;
        this.deleteActivity = deleteActivity;
    }

    @GetMapping
    public List<ActivityResponse> getActivities(@RequestParam(required = false) String status) {
        ActivityStatus filter = status == null ? null : ActivityStatus.valueOf(status.toUpperCase());
        return listActivities.execute(filter).stream().map(ActivityResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse createActivity(@RequestBody ActivityRequest request) {
        Activity activity = createActivity.execute(
                new CreateActivity.Command(request.name(), request.estimatedDurationMinutes(), request.priority(), request.category()));
        return ActivityResponse.from(activity);
    }

    @PutMapping("/{id}")
    public ActivityResponse editActivity(@PathVariable UUID id, @RequestBody ActivityRequest request) {
        Activity activity = editActivity.execute(
                id, new EditActivity.Command(request.name(), request.estimatedDurationMinutes(), request.priority(), request.category()));
        return ActivityResponse.from(activity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean confirm) {
        deleteActivity.execute(id, confirm);
    }

    public record ActivityRequest(String name, int estimatedDurationMinutes, Priority priority, String category) {
    }

    public record ActivityResponse(UUID id, String name, int estimatedDurationMinutes, String priority, String category, String status) {
        static ActivityResponse from(Activity activity) {
            return new ActivityResponse(
                    activity.id(),
                    activity.name(),
                    activity.estimatedDurationMinutes(),
                    activity.priority().name(),
                    activity.category(),
                    activity.status().name());
        }
    }
}
