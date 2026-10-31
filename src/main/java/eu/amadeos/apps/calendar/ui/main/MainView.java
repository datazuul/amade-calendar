package eu.amadeos.apps.calendar.ui.main;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.domain.ToDoItem;
import eu.amadeos.apps.calendar.service.DateFormats;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/** What the presenter needs from the main window. All methods run on the EDT. */
public interface MainView {
    /** Shows the view with the period that contains the date; the date is the selected day. */
    void showView(CalendarView view, LocalDate date);

    /** Redraws the current view and every open editor after the calendar changed. */
    void refresh();

    /** New formats or display settings: every view and editor takes them over. */
    void setFormats(DateFormats formats, CalendarPreferences preferences);

    void setStatus(String text);

    void showError(String message);

    boolean confirm(String message);

    /** Says that a birthday comes from the address books; true if the user wants to open Contacts. */
    boolean offerContacts(String message);

    void openAppointmentEditor(LocalDate date, LocalTime time, Appointment select);

    void openToDoEditor(LocalDate date, ToDoItem select);

    void openAppointmentList(String period, List<Occurrence> occurrences);

    void openToDoList(String period, List<ToDoItem> items);

    void openFind();

    /** The Go To Date dialog; empty if cancelled or not a date. */
    Optional<LocalDate> askDate();

    void openOptions(CalendarPreferences preferences);

    /** The Print dialog with the settings and the current view's report and period filled in. */
    void openPrintDialog(eu.amadeos.apps.calendar.domain.PrintSettings settings,
                         eu.amadeos.apps.calendar.service.print.ReportType type, LocalDate from, LocalDate to);

    /** The Beep reminder. */
    void beep();

    /** The Flash reminder: draws attention to the window. */
    void flash();

    /** The Popup reminder: a non-modal message. */
    void popup(String title, String text);

    /**
     * The Popup reminder as a desktop notification with <i>Snooze 5 min</i> and a click that shows the day;
     * {@code snooze} and {@code open} are called on the EDT.
     */
    void remind(String summary, String body, Runnable snooze, Runnable open);

    void dispose();
}
