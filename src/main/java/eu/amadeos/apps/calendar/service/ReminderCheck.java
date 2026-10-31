package eu.amadeos.apps.calendar.service;

import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.domain.Reminder;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the reminders that became due since the last check. Each reminder of each occurrence fires
 * once; reminders that were due before the program started are not caught up on, as in dtcm. A reminder
 * can be snoozed: it comes again after the given time.
 */
public final class ReminderCheck {
    /** A reminder that is due now. */
    public record Due(Occurrence occurrence, Reminder reminder) {
    }

    private final CalendarService calendar;
    private LocalDateTime lastCheck;
    private final Set<String> fired = new HashSet<>();
    private final List<Snoozed> snoozed = new ArrayList<>();

    private record Snoozed(Due due, LocalDateTime at) {
    }

    public ReminderCheck(CalendarService calendar, LocalDateTime start) {
        this.calendar = calendar;
        this.lastCheck = start;
    }

    /** The reminders due between the previous call and {@code now}, oldest first. */
    public List<Due> check(LocalDateTime now) {
        List<Due> due = new ArrayList<>();
        if (!now.isAfter(lastCheck)) return due;
        LocalDateTime from = lastCheck;
        // An occurrence can be reminded at most one day ahead of its start (the editor's limit), so
        // yesterday to tomorrow covers every reminder that can be due now.
        for (Occurrence o : calendar.occurrencesBetween(from.toLocalDate().minusDays(1), now.toLocalDate().plusDays(2))) {
            for (Reminder r : o.appointment().reminders()) {
                LocalDateTime at = o.start().minus(r.before());
                if (at.isAfter(from) && !at.isAfter(now)) {
                    String key = o.appointment().uid() + "|" + o.date() + "|" + r.type() + "|" + r.before();
                    if (fired.add(key)) due.add(new Due(o, r));
                }
            }
        }
        lastCheck = now;
        for (var it = snoozed.iterator(); it.hasNext(); ) {
            Snoozed s = it.next();
            if (!s.at().isAfter(now)) {
                due.add(s.due());
                it.remove();
            }
        }
        due.sort((a, b) -> a.occurrence().start().compareTo(b.occurrence().start()));
        return due;
    }

    /** Shows the reminder again at {@code now + delay}, at the first check after that. */
    public void snooze(Due due, LocalDateTime now, Duration delay) {
        snoozed.add(new Snoozed(due, now.plus(delay)));
    }
}
