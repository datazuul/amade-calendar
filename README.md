# amade-calendar

**amade-calendar** is the Calendar of the [amadeOS](https://github.com/amadeos-eu/amadeos) desktop: a
Java Swing calendar modelled on the CDE Calendar (`dtcm`), with the four views, the Appointment
Editor and the To Do Editor of the original, stored as a plain iCalendar file.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Debian](https://img.shields.io/badge/Debian-package-red)

> Status: early development. Appointments, To Do items, reminders, repeats, lists, find and
> options work; see *Roadmap* for printing, mail and other calendars.

---

## Window layout

```
┌ Calendar - October 2026 ────────────────────────────────────────────────────┐
│ File  Edit  View                                                       Help │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Appointment] [To Do]  [◀] [Today] [▶]  [Day] [Week] [Month] [Year]  October 2026 │
├─────────────────────────────────────────────────────────────────────────────┤
│  Sunday   Monday    Tuesday   Wednesday  Thursday  Friday   Saturday        │
│                                              1         2        3           │
│   4        5         6         7             8         9        10          │
│  11       12        13        14            15        16       ▌17▐         │
│                     1:00 PM Lunch                               2:30 PM Staff│
│  18       19        20        21            22        23        24          │
│           10:00 AM  9:00 AM   10:00 AM                10:00 AM  6:00 PM     │
├─────────────────────────────────────────────────────────────────────────────┤
│ Calendar loaded: 6 appointments, 2 To Do items                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Day view**: the day's appointments hour by hour (the hours between the *Begin* and *End* of the
  Display Settings; earlier and later appointments in a row of their own, appointments without a time
  at the top) and the three-month mini calendar. A day in the mini calendar selects that day, a month
  name switches to Month view. Days with appointments are bold.
- **Week view**: a column per day with the day's appointments, the selected day highlighted, and the
  week grid below with the taken hours shaded. A day name switches to Day view.
- **Month view**: the month with the appointments in the day cells, the selected day highlighted. A
  click selects the day, a click on the day number opens Day view.
- **Year view**: the twelve months without appointments. A month name opens Month view, a day Day view.
- In every view a double-click on a day (or on an hour) opens the Appointment Editor for it, a
  double-click on an appointment opens it. *Previous*, *Today* and *Next* step by the view's period;
  *View → Go To Date…* jumps anywhere.
- **Dates** are typed in the user's date format, or picked: the small calendar button next to every
  date field opens the date chooser of amade-commons (a month grid, previous and next month and year,
  Today; arrow keys move, Enter picks).
- **Appointment Editor** (*Edit → Appointment…*, the tool bar or a double-click): the day's
  appointments in a list; Date, Start, End (empty: an appointment without a time, or without an end)
  and What (the first line is shown in the views). *More* reveals the options of dtcm: reminders
  *Beep*, *Flash*, *Popup* and *Mail* with their lead times, *Occurs* (One Time, Daily, Weekly, Every
  Two Weeks, Monthly By Date, Monthly By Weekday, Yearly, Monday Thru Friday, Mon/Wed/Fri,
  Tue/Thu, Repeat Every *n* days, weeks or months) with *For* (number of times, empty for ever) and
  *Privacy* (Others See Time and Text, Time Only, Nothing). *Insert*, *Change*, *Delete*, *Clear*.
  Changing or deleting an occurrence of a repeating appointment asks, as dtcm did, whether it applies
  to *This One* (the occurrence becomes an exception of the series), *Forward* (the series ends
  before it and a new one starts there) or *All*. The days a series leaves out are listed under
  *More*, where *Restore* puts one back. An appointment without a time can span several days: the
  *to* field next to the date names its last day.
- **To Do Editor** (*Edit → To Do…*): due date, description and *Completed*.
- **Lists** (*View → Appointment List…*, *View → To Do List…*): the current view's period as a
  table; the To Do list has a check box per item, a filter for all, pending or completed items, and
  applies the checks with *OK* or *Apply*.
- **Find** (*View → Find…*): a text, searched six months back and ahead by default, *Search all* for
  the whole calendar, *Show Appointment* opens a match in Day view with the editor.
- **Reminders** fire while the Calendar runs: *Beep* sounds, *Flash* draws attention to the window,
  *Popup* shows a desktop notification with *Snooze 5 min* (a click shows the day), *Mail* opens the mailer (amade-mail, or whatever handles `mailto:`) with a
  message to the *Mail To* address that holds the appointment, ready to send. The *Mail To* field
  completes addresses from the address books of [amade-contacts](https://github.com/amadeos-eu/amade-contacts).
- **Birthdays** from the address books of amade-contacts appear in every view as day notes,
  "Birthday: Anna Berger (40)". They are not part of the calendar file and cannot be edited here; a
  double-click offers to open Contacts. *Display Settings* can hide them.
- **Interchange**, as between dtcm, Mailer and File Manager:
  - `amade-calendar file…` schedules the appointments in the files, so an `.ics` attachment or file
    opened from amade-mail or amade-files lands in the Calendar (the desktop entry handles
    `text/calendar`). Files and text dropped anywhere on the window are scheduled too.
  - Two formats are understood: iCalendar, and the dtcm appointment text that starts with
    `** Calendar Appointment **` and lists Date, Start, End and What (in the user's date format, in
    CDE's month/day/year or as an ISO date). Entries whose UID already exists are added as copies.
  - The **Drag Appointment** icon of the Appointment Editor carries the appointment out: as an `.ics` file for
    the File Manager or a mail attachment list, as dtcm text for a text field. **Mail…** opens the
    mailer with the appointment as the message text.
- **Printing**, as in dtcm: *File → Print Current View* (Ctrl+P) prints the view on screen with the
  Printer Settings; *File → Print…* opens the Print dialog with Printer Name, Report Type (Day, Week,
  Month or Year view, Appointment List, To Do List), Copies, the range of dates, Print Command Options
  and *Print to file*. The pages are rendered by the JDK's print service as PostScript and handed to
  `lp` with the printer, the copies and the options (e.g. `-o nobanner`), or kept as the file.
  Day and Week pages show the hours and columns of the views, Month and Year the grids, the lists a
  table; the footer names the Calendar and the date of printing.
- **Options** (*File → Options…*) is one dialog with a *Category* menu, as in dtcm:
  - *Editor Defaults*: duration, start time, reminders and privacy of new appointments.
  - *Display Settings*: the hours shown in Day and Week view, 12- or 24-hour times, the default view,
    and whether the birthdays from Contacts are shown.
  - *Date Format*: order of day, month and year, and the separator.
  - *Printer Settings*: printer or file, printer name, print command options, file name, *Units*
    (how many days, weeks, months or years *Print Current View* prints), copies, and whether to print
    only what others may see (appointments marked *Others See Time Only* print as busy, *Others See
    Nothing* are left out).
  - *Language*: English, Deutsch, Français, Español and Italiano. The Calendar follows the amadeOS
    desktop language; this overrides it for the Calendar alone (takes effect at the next start).

## Files

The calendar is one iCalendar file (RFC 5545), readable only by the user:

| File | Content |
|---|---|
| `~/.local/share/amadeos/calendar/calendar.ics` (or `$XDG_DATA_HOME/amadeos/calendar/`) | appointments as `VEVENT`, To Do items as `VTODO`, reminders as `VALARM`, repeats as `RRULE` |
| `~/.config/amadeos/calendar/calendar.conf` (or `$XDG_CONFIG_HOME/amadeos/calendar/`) | the options |

Times are local wall-clock times without a time zone, as in dtcm; times with a `TZID` or in UTC
from other programs are converted to the local time when read. Repeating appointments carry their
exceptions: days left out (`EXDATE`) and occurrences that were moved or changed (`RECURRENCE-ID`),
as a Google Calendar export has them. Appointments without a time may span several days
(`DTEND;VALUE=DATE`). Properties and components the Calendar does not know (locations, attendees,
time zones, alarms at an absolute time, ...) are kept and written back unchanged, so the file can be
exchanged with other calendar programs.

## Architecture

The Java 25 Swing application uses Guice for dependency injection. Theme, Motif look, language and
start-up come from [amade-commons](https://github.com/amadeos-eu/amade-commons): the Calendar follows
the desktop theme chosen in the Style Manager immediately, including the dark *Dark Slate*.

- `domain`: `Appointment`, `ToDoItem`, `Reminder`, `Repeat` (the `RRULE` subset the *Occurs* menu
  expresses, with the occurrence expansion) and `RepeatPreset` (the menu entries), `Privacy`,
  `CalendarPreferences`, `CalendarView` (the periods of the views).
- `repository`: `IcsCodec` reads and writes iCalendar; `IcsCalendarRepository` the file, atomically;
  `PreferencesRepository` the options.
- `service`: `CalendarService` holds the calendar in memory and answers the queries of the views
  (occurrences per day and range, find, To Do items); `DateFormats` formats and parses dates and
  times in the user's format; `ReminderCheck` finds the reminders that became due; `DtcmText` reads
  and writes the dtcm appointment text; `CalendarImporter` schedules iCalendar or dtcm text from files
  and drops. `service.print`: `ReportType`, `PrintRequest`, `ReportPages` (the pages of a report, a
  `Pageable` painted in black on white) and `CalendarPrinter` (PostScript through the JDK, then `lp`
  or a file).
- `ui.main`: `MainWindow`, `MainMenuBar`, `CalendarToolBar` and `MainPresenter`, which navigates,
  edits through the service and saves on the background executor. `ui.views` holds the four views
  and `MiniMonth`; `ui.editor` the two editors; `ui.lists` the list and find dialogs; `ui.options`
  the Options dialog; `ui.print` the Print dialog.

Texts live in `src/main/resources/eu/amadeos/apps/calendar/messages*.properties` (UTF-8); a
test checks that every translation is complete and the mnemonics are unique.

## Build and install

```sh
./gradlew test               # unit tests (the Swing tests skip themselves without a display)
./gradlew run                # start the Calendar
./gradlew deb                # build/distributions/amade-calendar_0.0.0~dev.0_all.deb
./gradlew installToAmadeOs   # ... and drop it into ../amadeos/config/packages.chroot/
```

Gradle takes amade-commons from JitPack, or builds a checkout in `../amade-commons` instead. The
package installs `/usr/bin/amade-calendar`, the jar under `/opt/amade-calendar/` and a desktop entry
(`Office;Calendar`) the front panel drawers pick up; the panel's calendar control starts it.

## Roadmap

Done so far, in the order of the CDE User's Guide chapter *Using Calendar*: the four views, the
editors, lists and Find, Options; reminders and the interchange with the mailer and the file manager;
printing; exceptions of repeating appointments, time zones and multi-day appointments in the file (a
Google Calendar export imports without loss); the date chooser on every date field.

The goals ahead, in the order they build on each other. The first three serve one purpose: to
fetch a calendar from any provider, Google Calendar first, with its whole history, to keep an own
calendar synchronised with a server, and to share a calendar with another person.

1. **Accounts in one place (amade-auth)**. A library `commons.accounts` in amade-commons with the
   account model (provider, user or e-mail, password or OAuth2), `~/.config/amadeos/accounts.conf`
   without secrets, passwords and refresh tokens in the keyring under the service `amadeos`, and the
   OAuth2 flow that lives in amade-mail today, so that one Google sign-in covers mail and calendar.
   A program `amade-auth` in amadeos-system, in the Tools drawer, with one dialog to add, sign in,
   check and remove accounts. Applications keep an account picker, a *Manage accounts…* button
   that starts amade-auth, and their own settings keyed by the account id (IMAP and SMTP servers in
   amade-mail, the calendar URL here). amade-mail is moved onto it.
2. **Other calendars on CalDAV**. Options get a category *Calendars*: the calendars of an account
   (Google through CalDAV with OAuth2, Nextcloud, Radicale, iCloud and others with a password), each
   with a colour and a check box to show it. Shown calendars appear read-only in Day, Week and Month
   view next to your own, the busy grid of Week view overlays them. *Import* copies a whole server
   calendar into the local one, which is the way to fetch the history from Google. That is dtcm's
   *Show Other Calendar* and *Compare Calendars*, with the browse list in the options instead of a
   Menu Editor. Tests run against a local Radicale started by the build.
3. **Synchronisation both ways**. A local calendar can be bound to a server calendar: changes made
   here are uploaded, changes on the server are fetched (ETags and the sync-token report of CalDAV),
   conflicts are shown and decided by the user. The local file stays the cache and works offline.
   A calendar shared with someone else is then simply a second bound calendar, in Google or on an own
   Radicale or Nextcloud, shown in its colour and edited by both.
4. **Several local calendars**. More than one calendar file (private, work, family), each in its
   colour, the editor asks which one an appointment goes to, lists and printing filter by calendar.
   Needed for 3, useful on its own.
5. **The rest of dtcm**. The Group Appointment Editor: pick calendars, see their free time in the
   overlaid grid, insert the appointment into the ones you may write to and mail the others. *Time
   Zone* in the View menu to see the calendar in another zone. The command line tools `lookup`,
   `insert` and `delete` as `dtcm_lookup`, `dtcm_insert` and `dtcm_delete` had them, and a standalone
   editor that writes one appointment to a file.
6. **Smaller things**: sending Mail reminders without the compose window once amade-mail can send
   from the command line; the page size and position fields of dtcm's Printer Settings; the
   reminders of the editor in two columns to make the dialog lower; the To Do Editor with a due
   time and reminders as dtcm had them; an *Undo* of the last change; a search over To Do items.

## License

EUPL-1.2, see [LICENSE](LICENSE).
