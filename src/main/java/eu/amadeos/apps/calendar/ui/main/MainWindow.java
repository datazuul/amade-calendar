package eu.amadeos.apps.calendar.ui.main;

import com.google.inject.Inject;
import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.domain.ToDoItem;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.apps.calendar.service.DateFormats;
import eu.amadeos.apps.calendar.ui.editor.AppointmentEditor;
import eu.amadeos.apps.calendar.ui.editor.ToDoEditor;
import eu.amadeos.apps.calendar.ui.lists.AppointmentListDialog;
import eu.amadeos.apps.calendar.ui.lists.FindDialog;
import eu.amadeos.apps.calendar.ui.lists.ToDoListDialog;
import eu.amadeos.apps.calendar.ui.options.OptionsDialog;
import eu.amadeos.apps.calendar.ui.print.PrintDialog;
import eu.amadeos.apps.calendar.ui.views.CalendarPanel;
import eu.amadeos.apps.calendar.ui.views.DayView;
import eu.amadeos.apps.calendar.ui.views.MonthView;
import eu.amadeos.apps.calendar.ui.views.ViewContext;
import eu.amadeos.apps.calendar.ui.views.WeekView;
import eu.amadeos.apps.calendar.ui.views.YearView;
import eu.amadeos.commons.contacts.ContactDirectory;
import eu.amadeos.commons.desktop.Notification;
import eu.amadeos.commons.desktop.Notifications;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static eu.amadeos.commons.i18n.Texts.tr;

/**
 * The Calendar window: menu bar and tool bar on top, the chosen view below, the message line at the
 * bottom, as in dtcm.
 */
public final class MainWindow extends JFrame implements MainView {
    private final LocaleSettings locales;
    private final ViewContext context;
    private final CardLayout cards = new CardLayout();
    private final JPanel viewArea = new JPanel(cards);
    private final Map<CalendarView, CalendarPanel> panels = new EnumMap<>(CalendarView.class);
    private final JLabel status = new JLabel(" ");
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
    private MainPresenter presenter;
    private MainMenuBar menuBar;
    private CalendarToolBar toolBar;
    private CalendarView currentView = CalendarView.MONTH;
    private AppointmentEditor appointmentEditor;
    private ToDoEditor toDoEditor;
    private AppointmentListDialog appointmentList;
    private ToDoListDialog toDoList;
    private FindDialog findDialog;
    private PrintDialog printDialog;
    private Timer reminderTimer;

    @Inject
    public MainWindow(LocaleSettings locales, CalendarService calendar, Locale locale, ContactDirectory contacts) {
        super(tr("app.title"));
        this.locales = locales;
        EdtExecutor.requireEdt();
        CalendarPreferences defaults = CalendarPreferences.defaultsFor(locale);
        this.context = new ViewContext(calendar, new DateFormats(defaults, locale), defaults, contacts);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(new ImageIcon(getClass().getResource("/icons/amade-calendar.png")).getImage());

        viewArea.setOpaque(false);
        viewArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        statusBar.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        statusBar.add(status);

        JPanel content = new JPanel(new BorderLayout());
        content.add(viewArea, BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);
        setContentPane(content);
        setPreferredSize(new Dimension(980, 720));
        setLocationRelativeTo(null);
    }

    public void bind(MainPresenter presenter) {
        EdtExecutor.requireEdt();
        this.presenter = presenter;
        menuBar = new MainMenuBar(presenter, this);
        setJMenuBar(menuBar);
        toolBar = new CalendarToolBar(presenter);
        getContentPane().add(toolBar, BorderLayout.NORTH);
        panels.put(CalendarView.DAY, new DayView(context, presenter));
        panels.put(CalendarView.WEEK, new WeekView(context, presenter));
        panels.put(CalendarView.MONTH, new MonthView(context, presenter));
        panels.put(CalendarView.YEAR, new YearView(context, presenter));
        panels.forEach((v, p) -> viewArea.add(p, v.name()));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                presenter.exit();
            }
        });
        reminderTimer = new Timer(30_000, e -> presenter.checkReminders());
        reminderTimer.start();
        // Files and text dropped anywhere on the window are scheduled, as dtcm took drops from Mailer
        // and File Manager.
        ((JComponent) getContentPane()).setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    if (support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        List<java.io.File> files = (List<java.io.File>) support.getTransferable()
                                .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                        presenter.importFiles(files.stream().map(java.io.File::toPath).toList());
                        return true;
                    }
                    if (support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                        presenter.importText((String) support.getTransferable()
                                .getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
                        return true;
                    }
                } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException e) {
                    showError(tr("error.import", e.getMessage()));
                }
                return false;
            }
        });
        pack();
        setLocationRelativeTo(null);
    }

    // ---- MainView -------------------------------------------------------------------------------

    @Override
    public void showView(CalendarView view, LocalDate date) {
        currentView = view;
        panels.get(view).show(date);
        cards.show(viewArea, view.name());
        menuBar.setView(view);
        toolBar.setView(view);
        toolBar.setPeriod(presenter.periodTitle());
        setTitle(tr("app.title.with", presenter.periodTitle()));
    }

    @Override
    public void refresh() {
        panels.get(currentView).refresh();
        if (appointmentEditor != null) appointmentEditor.refresh();
        if (toDoEditor != null) toDoEditor.refresh();
    }

    @Override
    public void setFormats(DateFormats formats, CalendarPreferences preferences) {
        context.update(formats, preferences);
        if (appointmentEditor != null) appointmentEditor.refresh();
        if (toDoEditor != null) toDoEditor.refresh();
        if (presenter != null && toolBar != null) {
            toolBar.setPeriod(presenter.periodTitle());
            setTitle(tr("app.title.with", presenter.periodTitle()));
        }
    }

    @Override
    public void setStatus(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, tr("app.title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean offerContacts(String message) {
        String open = tr("birthday.open-contacts");
        String ok = UIManager.getString("OptionPane.okButtonText", getLocale());
        return JOptionPane.showOptionDialog(this, message, tr("app.title"), JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, new Object[]{open, ok}, ok) == 0;
    }

    @Override
    public boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, tr("app.title"), JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    @Override
    public void openAppointmentEditor(LocalDate date, LocalTime time, Appointment select) {
        if (appointmentEditor == null) {
            appointmentEditor = new AppointmentEditor(this, context, new AppointmentEditor.Handler() {
                @Override
                public void insert(Appointment appointment) {
                    presenter.insertAppointment(appointment);
                }

                @Override
                public void change(Appointment original, LocalDate day, Appointment edited,
                                   eu.amadeos.apps.calendar.domain.EditScope scope) {
                    presenter.changeAppointment(original, day, edited, scope);
                }

                @Override
                public void delete(Appointment original, LocalDate day, eu.amadeos.apps.calendar.domain.EditScope scope) {
                    presenter.deleteAppointment(original, day, scope);
                }

                @Override
                public void mail(Appointment appointment) {
                    presenter.mailAppointment(appointment);
                }
            });
            appointmentEditor.setIconImage(getIconImage());
        }
        appointmentEditor.showFor(date, time, select);
    }

    @Override
    public void openToDoEditor(LocalDate date, ToDoItem select) {
        if (toDoEditor == null) {
            toDoEditor = new ToDoEditor(this, context, new ToDoEditor.Handler() {
                @Override
                public void insert(ToDoItem item) {
                    presenter.insertToDo(item);
                }

                @Override
                public void change(ToDoItem item) {
                    presenter.changeToDo(item);
                }

                @Override
                public void delete(ToDoItem item) {
                    presenter.deleteToDo(item);
                }
            });
            toDoEditor.setIconImage(getIconImage());
        }
        toDoEditor.showFor(date, select);
    }

    @Override
    public void openAppointmentList(String period, List<Occurrence> occurrences) {
        if (appointmentList == null || !appointmentList.isDisplayable()) {
            appointmentList = new AppointmentListDialog(this, context, presenter::editAppointment);
            appointmentList.setIconImage(getIconImage());
        }
        appointmentList.show(period, occurrences);
    }

    @Override
    public void openToDoList(String period, List<ToDoItem> items) {
        if (toDoList == null || !toDoList.isDisplayable()) {
            toDoList = new ToDoListDialog(this, context, presenter::setToDosCompleted);
            toDoList.setIconImage(getIconImage());
        }
        toDoList.show(period, items);
    }

    @Override
    public void openFind() {
        if (findDialog == null || !findDialog.isDisplayable()) {
            findDialog = new FindDialog(this, context, presenter::find, presenter::showAppointment);
            findDialog.setIconImage(getIconImage());
        }
        findDialog.open();
    }

    @Override
    public Optional<LocalDate> askDate() {
        String answer = (String) JOptionPane.showInputDialog(this, tr("goto.prompt"), tr("goto.title"),
                JOptionPane.QUESTION_MESSAGE, null, null, context.formats().date(presenter.selected()));
        if (answer == null) return Optional.empty();
        Optional<LocalDate> date = context.formats().parseDateLenient(answer);
        if (date.isEmpty()) showError(tr("editor.error.date", context.formats().date(java.time.LocalDate.now())));
        return date;
    }

    @Override
    public void openOptions(CalendarPreferences preferences) {
        OptionsDialog dialog = new OptionsDialog(this, context.formats(), presenter::applyOptions, locales);
        dialog.open(preferences, OptionsDialog.EDITOR_DEFAULTS);
    }

    @Override
    public void openPrintDialog(eu.amadeos.apps.calendar.domain.PrintSettings settings,
                                eu.amadeos.apps.calendar.service.print.ReportType type, LocalDate from, LocalDate to) {
        if (printDialog == null || !printDialog.isDisplayable()) {
            printDialog = new PrintDialog(this, context, presenter::print);
            printDialog.setIconImage(getIconImage());
        }
        printDialog.open(settings, type, from, to);
    }

    @Override
    public void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    @Override
    public void flash() {
        // Invert the message line a few times and bring the window up, as dtcm flashed its icon.
        Color normal = statusBar.getBackground();
        int[] count = {0};
        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            boolean on = count[0] % 2 == 0;
            statusBar.setBackground(on ? AmadeUI.theme().selectionBackground() : normal);
            status.setForeground(on ? AmadeUI.theme().selectionText() : AmadeUI.theme().textActive());
            if (++count[0] >= 8) {
                timer.stop();
                statusBar.setBackground(normal);
                status.setForeground(AmadeUI.theme().textActive());
            }
        });
        timer.start();
        if (getState() == Frame.ICONIFIED) setState(Frame.NORMAL);
        toFront();
    }

    @Override
    public void popup(String title, String text) {
        JOptionPane pane = new JOptionPane(text, JOptionPane.INFORMATION_MESSAGE);
        JDialog dialog = pane.createDialog(this, title);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        beep();
    }

    // --8<-- [start:remind]
    @Override
    public void remind(String summary, String body, Runnable snooze, Runnable open) {
        long id = Notifications.send(Notification.of(summary)
                .body(Notification.escape(body))
                .icon(getIconImage())
                .category("x-amade.reminder")
                .action("snooze", tr("reminder.snooze"), () -> SwingUtilities.invokeLater(snooze))
                .defaultAction(() -> SwingUtilities.invokeLater(() -> {
                    open.run();
                    if (getState() == Frame.ICONIFIED) setState(Frame.NORMAL);
                    toFront();
                })));
        // Without a notification service and without a system tray, the reminder still has to show.
        if (id == 0 && !SystemTray.isSupported()) popup(tr("reminder.popup.title"), summary + "\n" + body);
    }
    // --8<-- [end:remind]

    @Override
    public void dispose() {
        if (reminderTimer != null) reminderTimer.stop();
        for (Window w : Window.getWindows()) if (w != this && w.isDisplayable()) w.dispose();
        super.dispose();
    }

    void showAbout() {
        JOptionPane.showMessageDialog(this, tr("about.text") + "\nhttps://github.com/amadeos-eu/amade-calendar",
                tr("menu.help.about"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(getIconImage()));
    }
}
