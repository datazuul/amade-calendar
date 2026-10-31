package eu.amadeos.apps.calendar.ui.main;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.domain.Privacy;
import eu.amadeos.apps.calendar.domain.Reminder;
import eu.amadeos.apps.calendar.domain.ReminderType;
import eu.amadeos.apps.calendar.domain.Repeat;
import eu.amadeos.apps.calendar.domain.ToDoItem;
import eu.amadeos.apps.calendar.repository.CalendarRepository;
import eu.amadeos.apps.calendar.repository.PreferencesRepository;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.apps.calendar.service.print.CalendarPrinter;
import eu.amadeos.apps.calendar.service.print.PrintRequest;
import eu.amadeos.apps.calendar.service.print.ReportType;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.desktop.DesktopLauncher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MainPresenterTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 8, 54);
    private final MainView view = mock(MainView.class);
    private final CalendarService calendar = new CalendarService();
    private final CalendarRepository repository = mock(CalendarRepository.class);
    private final PreferencesRepository preferences = mock(PreferencesRepository.class);
    private final DesktopLauncher desktop = mock(DesktopLauncher.class);
    private final List<List<String>> commands = new java.util.ArrayList<>();
    private final CalendarPrinter printer = new CalendarPrinter(command -> {
        commands.add(command);
        return "request id is HP-Printer-42 (1 file(s))";
    });
    private final java.nio.file.Path contactsRoot = addressBook();
    private final ContactDirectory contacts = new ContactDirectory(contactsRoot, java.time.Clock.systemUTC());
    private final MainPresenter presenter = new MainPresenter(view, calendar, repository, preferences,
            new DirectExecutorService(), Locale.US, desktop, contacts, printer, () -> NOW);

    /** An address book with Anna, whose birthday is the day after {@link #NOW}. */
    private static java.nio.file.Path addressBook() {
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory("contacts");
            java.nio.file.Path book = java.nio.file.Files.createDirectories(root.resolve("personal"));
            java.nio.file.Files.writeString(book.resolve("anna.vcf"),
                    "BEGIN:VCARD\r\nVERSION:4.0\r\nUID:anna\r\nFN:Anna Berger\r\nBDAY:19861014\r\nEND:VCARD\r\n");
            return root;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Appointment meeting() {
        return new Appointment("m", NOW.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(10, 0), "Project meeting",
                List.of(new Reminder(ReminderType.POPUP, Duration.ofMinutes(5))), Repeat.ONCE, Privacy.TIME_AND_TEXT);
    }

    private void start() throws IOException {
        when(preferences.load()).thenReturn(CalendarPreferences.defaultsFor(Locale.US).withDefaultView(CalendarView.WEEK));
        when(repository.load()).thenReturn(new CalendarData(List.of(meeting()), List.of(), List.of()));
        presenter.start();
    }

    @Test
    void startLoadsAndShowsTheDefaultViewOnToday() throws IOException {
        start();
        verify(view).showView(CalendarView.WEEK, NOW.toLocalDate());
        verify(view).setStatus("Calendar loaded: 1 appointment, no To Do items");
        assertEquals(1, calendar.appointmentCount());
        assertEquals(CalendarView.WEEK, presenter.currentView());
    }

    @Test
    void navigationStepsByTheCurrentView() throws IOException {
        start();
        presenter.showView(CalendarView.MONTH);
        presenter.next();
        verify(view).showView(CalendarView.MONTH, NOW.toLocalDate().plusMonths(1));
        presenter.previous();
        presenter.previous();
        verify(view).showView(CalendarView.MONTH, NOW.toLocalDate().minusMonths(1));
        presenter.today();
        verify(view, times(3)).showView(CalendarView.MONTH, NOW.toLocalDate());
        assertEquals("October 2026", presenter.periodTitle());
        when(view.askDate()).thenReturn(Optional.of(LocalDate.of(2027, 1, 1)));
        presenter.goToDate();
        verify(view).showView(CalendarView.MONTH, LocalDate.of(2027, 1, 1));
        assertEquals("January 2027", presenter.periodTitle());
    }

    @Test
    void editingSavesInTheBackgroundAndRefreshes() throws IOException {
        start();
        Appointment lunch = new Appointment("l", NOW.toLocalDate(), LocalTime.of(12, 30), LocalTime.of(13, 0), "eat lunch",
                List.of(), Repeat.ONCE, Privacy.TIME_ONLY);
        presenter.insertAppointment(lunch);
        verify(repository).save(argThat(d -> d.appointments().size() == 2));
        verify(view).setStatus("Inserted: eat lunch");
        presenter.deleteAppointment(lunch, lunch.date(), eu.amadeos.apps.calendar.domain.EditScope.ALL);
        verify(repository).save(argThat(d -> d.appointments().size() == 1));
        ToDoItem todo = new ToDoItem("t", NOW.toLocalDate(), "Report", false);
        presenter.insertToDo(todo);
        presenter.setToDosCompleted(Map.of("t", true));
        assertTrue(calendar.toDo("t").orElseThrow().completed());
        verify(view, times(4)).refresh();
        verify(view).setStatus("1 To Do item updated");
    }

    @Test
    void saveErrorsAreShown() throws IOException {
        start();
        doThrow(new IOException("disk full")).when(repository).save(any());
        when(repository.location()).thenReturn("/x/calendar.ics");
        presenter.insertToDo(new ToDoItem("t", NOW.toLocalDate(), "Report", false));
        // The error is reported on the EDT; wait for it.
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception e) {
            // headless: the EDT still exists
        }
        verify(view, timeout(2000)).showError("Cannot save the calendar /x/calendar.ics: disk full");
    }

    // --8<-- [start:save-order]
    @Test
    void savesRunOneAfterTheOtherInTheOrderOfTheChanges() throws IOException {
        // An executor that only queues: the test decides when each task runs.
        java.util.ArrayDeque<Runnable> queued = new java.util.ArrayDeque<>();
        java.util.concurrent.Executor queue = queued::add;
        MainPresenter slow = new MainPresenter(view, calendar, repository, preferences, new QueueExecutorService(queue),
                Locale.US, desktop, contacts, printer, () -> NOW);
        when(preferences.load()).thenReturn(CalendarPreferences.defaultsFor(Locale.US));
        when(repository.load()).thenReturn(CalendarData.EMPTY);
        slow.start();

        slow.insertToDo(new ToDoItem("a", NOW.toLocalDate(), "First", false));
        slow.insertToDo(new ToDoItem("b", NOW.toLocalDate(), "Second", false));

        // The second save waits for the first instead of racing it.
        assertEquals(1, queued.size());
        queued.poll().run();
        assertEquals(1, queued.size());
        queued.poll().run();
        var order = inOrder(repository);
        order.verify(repository).save(argThat(d -> d.toDos().size() == 1));
        order.verify(repository).save(argThat(d -> d.toDos().size() == 2));
    }
    // --8<-- [end:save-order]

    /** Hands every task to an executor the test controls. */
    static final class QueueExecutorService extends AbstractExecutorService {
        private final java.util.concurrent.Executor queue;

        QueueExecutorService(java.util.concurrent.Executor queue) {
            this.queue = queue;
        }

        @Override
        public void execute(Runnable command) {
            queue.execute(command);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    @Test
    void listsCoverTheCurrentPeriod() throws IOException {
        start();
        presenter.showView(CalendarView.DAY);
        presenter.openAppointmentList();
        verify(view).openAppointmentList(eq("Tuesday, 13 October 2026"), argThat(l -> l.size() == 1));
        presenter.showView(CalendarView.YEAR, LocalDate.of(2025, 5, 5));
        presenter.openAppointmentList();
        verify(view).openAppointmentList(eq("2025"), argThat(l -> l.size() == 1
                && CalendarService.isBirthday(l.getFirst().appointment()))); // only Anna's birthday
        presenter.openToDoList();
        verify(view).openToDoList(eq("2025"), argThat(List::isEmpty));
        List<Occurrence> found = presenter.find("project", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(1, found.size());
        presenter.showAppointment(found.getFirst());
        verify(view, times(2)).showView(CalendarView.DAY, NOW.toLocalDate());
        verify(view).openAppointmentEditor(NOW.toLocalDate(), null, meeting());
    }

    @Test
    void optionsAreSavedAndAppliedToTheViews() throws IOException {
        start();
        CalendarPreferences changed = CalendarPreferences.defaultsFor(Locale.GERMANY);
        presenter.applyOptions(changed);
        verify(preferences).save(changed);
        verify(view).setFormats(argThat(f -> f.twentyFourHour()), eq(changed));
        assertEquals("13.10.2026", presenter.formats().date(NOW.toLocalDate()));
    }

    @Test
    void remindersReachTheWindow() throws IOException {
        start();
        presenter.checkReminders();
        verify(view, never()).remind(anyString(), anyString(), any(), any());
        MainPresenter later = new MainPresenter(view, calendar, repository, preferences, new DirectExecutorService(),
                Locale.US, desktop, contacts, printer, new java.util.function.Supplier<>() {
                    int calls;

                    @Override
                    public LocalDateTime get() {
                        return calls++ < 2 ? NOW : NOW.plusMinutes(1);
                    }
                });
        later.start();
        later.checkReminders();
        ArgumentCaptor<Runnable> snooze = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> open = ArgumentCaptor.forClass(Runnable.class);
        verify(view).remind(eq("Project meeting"), eq("10/13/2026 9:00 AM - 10:00 AM"), snooze.capture(), open.capture());
        open.getValue().run();
        verify(view).showView(CalendarView.DAY, NOW.toLocalDate());
    }

    @Test
    void aSnoozedReminderComesAgainFiveMinutesLater() throws IOException {
        start();
        LocalDateTime[] now = {NOW};
        MainPresenter later = new MainPresenter(view, calendar, repository, preferences, new DirectExecutorService(),
                Locale.US, desktop, contacts, printer, () -> now[0]);
        later.start();
        now[0] = NOW.plusMinutes(1);
        later.checkReminders();
        ArgumentCaptor<Runnable> snooze = ArgumentCaptor.forClass(Runnable.class);
        verify(view).remind(anyString(), anyString(), snooze.capture(), any());
        snooze.getValue().run();
        now[0] = NOW.plusMinutes(5);
        later.checkReminders();
        verify(view, times(1)).remind(anyString(), anyString(), any(), any());
        now[0] = NOW.plusMinutes(6);
        later.checkReminders();
        verify(view, times(2)).remind(anyString(), anyString(), any(), any());
    }

    @Test
    void filesFromTheCommandLineAreScheduled(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
        when(preferences.load()).thenReturn(CalendarPreferences.defaultsFor(Locale.US));
        when(repository.load()).thenReturn(CalendarData.EMPTY);
        java.nio.file.Path file = dir.resolve("a.ics");
        java.nio.file.Files.writeString(file, "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:i\nDTSTART:20261105T090000\nSUMMARY:Imported\nEND:VEVENT\nEND:VCALENDAR\n");
        presenter.start(List.of(file.toString(), dir.resolve("missing.ics").toString()));
        assertEquals(1, calendar.appointmentCount());
        verify(view).showView(CalendarView.DAY, LocalDate.of(2026, 11, 5));
        verify(view).setStatus("Scheduled: 1 appointment, no To Do items");
        verify(view).showError(argThat(m -> m.contains("missing.ics")));
        verify(repository).save(any());
    }

    @Test
    void droppedTextIsScheduledOrRejected() throws IOException {
        start();
        presenter.importText("Hello");
        assertEquals(1, calendar.appointmentCount());
        presenter.importText("** Calendar Appointment **\nDate: 11/06/2026\nWhat: Dropped\n");
        assertEquals(2, calendar.appointmentCount());
        verify(view).showView(CalendarView.DAY, LocalDate.of(2026, 11, 6));
    }

    @Test
    void mailGoesThroughTheDesktopMailer() throws IOException {
        start();
        presenter.mailAppointment(meeting());
        verify(desktop).browse(argThat(uri -> uri.startsWith("mailto:?subject=Appointment%3A%20Project%20meeting&body=")));
        verify(view).setStatus("Mailer opened with: Project meeting");
        doThrow(new IOException("no mailer")).when(desktop).browse(anyString());
        presenter.mailAppointment(meeting());
        verify(view).showError("Cannot open the mailer: no mailer");
    }

    @Test
    void mailRemindersOpenTheMailerWithTheRecipient() throws IOException {
        Appointment withMail = new Appointment("m", NOW.toLocalDate(), LocalTime.of(9, 0), LocalTime.of(10, 0), "Project meeting",
                List.of(new Reminder(ReminderType.MAIL, Duration.ofMinutes(5), "kent@dailyplanet")), Repeat.ONCE, Privacy.TIME_AND_TEXT);
        when(preferences.load()).thenReturn(CalendarPreferences.defaultsFor(Locale.US));
        when(repository.load()).thenReturn(new CalendarData(List.of(withMail), List.of(), List.of()));
        int[] calls = {0};
        MainPresenter later = new MainPresenter(view, calendar, repository, preferences, new DirectExecutorService(),
                Locale.US, desktop, contacts, printer, () -> calls[0]++ < 2 ? NOW : NOW.plusMinutes(1));
        later.start();
        later.checkReminders();
        verify(desktop).browse(argThat(uri -> uri.startsWith("mailto:kent%40dailyplanet?subject=Reminder%3A%20Project%20meeting")));
    }

    @Test
    void printCurrentViewHandsPostScriptToLp() throws Exception {
        start();
        presenter.showView(CalendarView.WEEK);
        presenter.applyOptions(presenter.preferences().withPrint(new eu.amadeos.apps.calendar.domain.PrintSettings(
                eu.amadeos.apps.calendar.domain.PrintSettings.Destination.PRINTER, "HP-Printer", "-o nobanner",
                "calendar.ps", 2, 3, false)));
        presenter.printCurrentView();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // headless
        }
        assertEquals(1, commands.size());
        List<String> lp = commands.getFirst();
        assertEquals("lp", lp.getFirst());
        assertTrue(lp.containsAll(List.of("-d", "HP-Printer", "-n", "3", "-o", "nobanner")), lp.toString());
        assertTrue(lp.getLast().endsWith(".ps"));
        verify(view, timeout(2000)).setStatus("Printed 2 pages: request id is HP-Printer-42 (1 file(s))");
    }

    @Test
    void printDialogOpensOnTheCurrentPeriod() throws IOException {
        start();
        presenter.showView(CalendarView.MONTH);
        presenter.openPrintDialog();
        verify(view).openPrintDialog(eq(presenter.preferences().print()), eq(ReportType.MONTH),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)));
    }


    /** Runs tasks on the calling thread. */
    // --8<-- [start:birthday-test]
    @Test
    void birthdaysFromTheAddressBooksAreDayNotesThatCannotBeEdited() throws IOException {
        start();
        LocalDate day = NOW.toLocalDate().plusDays(1);
        List<Occurrence> onDay = calendar.occurrencesOn(day);
        assertEquals(1, onDay.size());
        Appointment birthday = onDay.getFirst().appointment();
        assertEquals("Birthday: Anna Berger (40)", birthday.summary());
        assertFalse(birthday.hasTime());
        assertTrue(CalendarService.isBirthday(birthday));
        assertEquals(1, calendar.appointmentCount(), "only the meeting: birthdays are not part of the calendar");

        presenter.editAppointment(onDay.getFirst());
        verify(view).offerContacts(contains("Anna Berger"));
        verify(view, never()).openAppointmentEditor(any(), any(), any());

        presenter.deleteAppointment(birthday, day, eu.amadeos.apps.calendar.domain.EditScope.ALL);
        assertEquals(1, calendar.occurrencesOn(day).size());
        verify(repository, never()).save(any());
    }
    // --8<-- [end:birthday-test]

    @Test
    void theDisplaySettingsCanHideTheBirthdays() throws IOException {
        start();
        LocalDate day = NOW.toLocalDate().plusDays(1);
        presenter.applyOptions(presenter.preferences().withBirthdays(false));
        assertEquals(List.of(), calendar.occurrencesOn(day));
        presenter.applyOptions(presenter.preferences().withBirthdays(true));
        assertEquals(1, calendar.occurrencesOn(day).size());
    }

    static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
