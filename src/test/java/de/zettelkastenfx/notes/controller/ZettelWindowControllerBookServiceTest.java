package de.zettelkastenfx.notes.controller;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.BibtexFieldDefinition;
import de.zettelkastenfx.bibliography.model.BibliographyReference;
import de.zettelkastenfx.bibliography.model.DynamicBibliographyEntry;
import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;
import de.zettelkastenfx.bibliography.model.PersonBibValue;
import de.zettelkastenfx.bibliography.model.PersonRef;
import de.zettelkastenfx.bibliography.model.StringBibValue;
import de.zettelkastenfx.persistence.NoteRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Grenzt Verzögerungen beim Wechsel in den BOOK-Service ein.
 */
class ZettelWindowControllerBookServiceTest {

  private static final Unsafe UNSAFE = loadUnsafe();

  /**
   * Startet die JavaFX-Plattform fuer View-nahe Controller-Tests.
   *
   * @throws InterruptedException wenn der Testthread unterbrochen wird
   */
  @BeforeAll
  static void startJavaFxToolkit() throws InterruptedException {
    CountDownLatch startupLatch = new CountDownLatch(1);
    try {
      Platform.startup(() -> {
        Platform.setImplicitExit(false);
        startupLatch.countDown();
      });
      if (!startupLatch.await(5, TimeUnit.SECONDS)) {
        fail("JavaFX-Plattform wurde nicht rechtzeitig gestartet.");
      }
    } catch (IllegalStateException alreadyStarted) {
      Platform.setImplicitExit(false);
    }
  }

  /**
   * Der BOOK-Umschaltvorgang darf die Datenbankarbeit nicht synchron im Klickpfad starten.
   *
   * @throws Exception wenn die JavaFX- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void switchingToBookServiceShowsPaneBeforeRefreshWorkStarts() throws Exception {
    runOnFxThread(() -> {
      AtomicInteger searches = new AtomicInteger();
      ZettelWindowView view = new ZettelWindowView();
      CountingNoteRepository noteRepository = new CountingNoteRepository(Map.of(), List.of());
      BibliographyService bibliographyService = bibliographyService(List.of(), searches);
      ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

      view.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      invokePrivate(controller, "refreshServiceAreaForCurrentState");

      assertAll(
          () -> assertNotNull(view.getBookPane().getParent(), "BOOK-Pane wurde nicht in die Service-Area eingesetzt."),
          () -> assertEquals(0, searches.get(), "Der BOOK-Wechsel startet die Mediensuche noch synchron.")
      );
    });
  }

  /**
   * Der geplante BOOK-Refresh darf den JavaFX-Thread nicht blockieren, waehrend Daten geladen werden.
   *
   * @throws Exception wenn die JavaFX- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void scheduledBookRefreshKeepsJavaFxThreadResponsiveWhileDataLoads() throws Exception {
    AtomicInteger searches = new AtomicInteger();
    CountDownLatch searchStarted = new CountDownLatch(1);
    CountDownLatch releaseSearch = new CountDownLatch(1);
    CountDownLatch markerReached = new CountDownLatch(1);

    ZettelWindowView view = callOnFxThread(() -> {
      ZettelWindowView createdView = new ZettelWindowView();
      createdView.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      return createdView;
    });
    CountingNoteRepository noteRepository = new CountingNoteRepository(Map.of(), List.of());
    BibliographyService bibliographyService = blockingBibliographyService(searches, searchStarted, releaseSearch);
    ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

    try {
      runOnFxThread(() -> {
        invokePrivate(controller, "scheduleBookRefreshAfterServiceSwitch");
        Platform.runLater(markerReached::countDown);
      });

      assertTrue(searchStarted.await(2, TimeUnit.SECONDS), "Der Hintergrund-Refresh wurde nicht gestartet.");
      assertTrue(
          markerReached.await(150, TimeUnit.MILLISECONDS),
          "Der JavaFX-Thread bleibt waehrend des BOOK-Datenladens blockiert."
      );
    } finally {
      releaseSearch.countDown();
      shutdownBookRefreshExecutor(controller);
    }
  }

  /**
   * Ein langsamer aelterer BOOK-Refresh darf ein neueres Ergebnis nicht ueberschreiben.
   *
   * @throws Exception wenn die JavaFX-, Hintergrund- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void newerAsyncBookRefreshIgnoresOlderResult() throws Exception {
    AtomicInteger searches = new AtomicInteger();
    CountDownLatch firstSearchStarted = new CountDownLatch(1);
    CountDownLatch releaseFirstSearch = new CountDownLatch(1);
    CountDownLatch secondSearchFinished = new CountDownLatch(1);

    ZettelWindowView view = callOnFxThread(() -> {
      ZettelWindowView createdView = new ZettelWindowView();
      createdView.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      return createdView;
    });
    CountingNoteRepository noteRepository = new CountingNoteRepository(Map.of(), List.of());
    BibliographyService bibliographyService = sequentialBlockingBibliographyService(
        searches,
        firstSearchStarted,
        releaseFirstSearch,
        secondSearchFinished
    );
    ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

    try {
      runOnFxThread(() -> invokePrivate(controller, "requestAsyncBookRefresh"));
      assertTrue(firstSearchStarted.await(2, TimeUnit.SECONDS), "Der erste Refresh wurde nicht gestartet.");

      runOnFxThread(() -> invokePrivate(controller, "requestAsyncBookRefresh"));
      releaseFirstSearch.countDown();

      assertTrue(secondSearchFinished.await(2, TimeUnit.SECONDS), "Der zweite Refresh wurde nicht beendet.");
      waitForFxCondition(
          () -> view.getBookMediaTable().getItems().stream().anyMatch(row -> row.getEntryId() == 2),
          "Das neuere BOOK-Ergebnis wurde nicht auf die Tabelle angewendet."
      );

      List<Integer> visibleEntryIds = callOnFxThread(() -> view.getBookMediaTable().getItems().stream()
          .map(ZettelWindowView.BookMediaRow::getEntryId)
          .toList());
      assertEquals(
          List.of(2),
          visibleEntryIds,
          "Ein veraltetes BOOK-Ergebnis hat die aktuelle Tabelle ueberschrieben."
      );
    } finally {
      releaseFirstSearch.countDown();
      shutdownBookRefreshExecutor(controller);
    }
  }

  /**
   * Die BOOK-Medientabelle trennt Autor und Titel auch dann, wenn der
   * Referenztext fuer Popup-Listen bereits kombiniert formatiert ist.
   *
   * @throws Exception wenn die JavaFX- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void bookMediaTableUsesSeparateAuthorAndTitleColumnsFromEntryFields() throws Exception {
    runOnFxThread(() -> {
      AtomicInteger searches = new AtomicInteger();
      ZettelWindowView view = new ZettelWindowView();
      CountingNoteRepository noteRepository = new CountingNoteRepository(Map.of(1, 1), List.of());

      DynamicBibliographyEntry entry = bookEntry(1, "Analytical Engine", "Ada", "Lovelace");
      BibliographyService bibliographyService = bibliographyService(
          List.of(reference(1, "Ada Lovelace: Analytical Engine")),
          Map.of(1, entry),
          searches
      );
      ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

      view.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      invokePrivate(controller, "refreshBookWindow");

      ZettelWindowView.BookMediaRow row = view.getBookMediaTable().getItems().stream()
                                               .filter(item -> item.getEntryId() == 1)
                                               .findFirst()
                                               .orElseThrow();

      assertAll(
          () -> assertEquals("Lovelace, Ada", row.getAuthorDisplay(), "Autor muss in der Autor-Spalte stehen."),
          () -> assertEquals("Analytical Engine", row.getTitle(), "Titel darf nicht den kombinierten Popup-Text enthalten.")
      );
    });
  }

  /**
   * Eine ausgeblendete Zetteltabelle darf beim BOOK-Refresh nicht aufgebaut oder sortiert werden.
   *
   * @throws Exception wenn die JavaFX- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void hiddenBookNotesTableUsesCountsWithoutLoadingOrSortingNoteRows() throws Exception {
    runOnFxThread(() -> {
      AtomicInteger searches = new AtomicInteger();
      AtomicInteger noteSorts = new AtomicInteger();
      ZettelWindowView view = new ZettelWindowView();
      CountingNoteRepository noteRepository = new CountingNoteRepository(
          Map.of(1, 2, 2, 1),
          List.of(
              new NoteRepository.BibliographyUsageRow(12, "Zettel B", 1),
              new NoteRepository.BibliographyUsageRow(10, "Zettel A", 1),
              new NoteRepository.BibliographyUsageRow(20, "Zettel C", 2)
          )
      );
      BibliographyService bibliographyService = bibliographyService(
          List.of(
              reference(1, "Autor, Ada | Alpha"),
              reference(2, "Beta")
          ),
          searches
      );
      ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

      view.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      view.getBookShowNotesToggleButton().setSelected(false);
      view.getBookNotesTable().setSortPolicy(table -> {
        noteSorts.incrementAndGet();
        return true;
      });
      noteSorts.set(0);

      invokePrivate(controller, "refreshBookWindow");

      assertAll(
          () -> assertEquals(1, searches.get(), "Die Mediensuche soll genau einmal laufen."),
          () -> assertEquals(1, noteRepository.usageCountLoadCount, "Notizzahlen sollen als Aggregat geladen werden."),
          () -> assertEquals(0, noteRepository.usageRowsLoadCount, "Ausgeblendete Notizen duerfen keine Detailzeilen laden."),
          () -> assertEquals(0, noteSorts.get(), "Ausgeblendete Notizen duerfen nicht sortiert werden."),
          () -> assertTrue(view.getBookNotesTable().getItems().isEmpty(), "Die ausgeblendete Notiztabelle bleibt leer."),
          () -> assertEquals(2, view.getBookMediaTable().getItems().size(), "Beide Medien werden angezeigt."),
          () -> assertEquals(
              2,
              view.getBookMediaTable().getItems().stream()
                  .filter(row -> row.getEntryId() == 1)
                  .findFirst()
                  .orElseThrow()
                  .getNoteCount(),
              "Notizzahl aus Aggregat fehlt."
          )
      );
    });
  }

  /**
   * Eine sichtbare Zetteltabelle laedt Detailzeilen nur dann, wenn sie wirklich angezeigt wird.
   *
   * @throws Exception wenn die JavaFX- oder Reflexionsausfuehrung fehlschlaegt
   */
  @Test
  void visibleBookNotesTableLoadsRowsOnlyWhenRequested() throws Exception {
    runOnFxThread(() -> {
      AtomicInteger searches = new AtomicInteger();
      ZettelWindowView view = new ZettelWindowView();
      CountingNoteRepository noteRepository = new CountingNoteRepository(
          Map.of(1, 2, 2, 1),
          List.of(
              new NoteRepository.BibliographyUsageRow(12, "Zettel B", 1),
              new NoteRepository.BibliographyUsageRow(10, "Zettel A", 1),
              new NoteRepository.BibliographyUsageRow(20, "Zettel C", 2),
              new NoteRepository.BibliographyUsageRow(99, "Nicht sichtbar", 99)
          )
      );
      BibliographyService bibliographyService = bibliographyService(
          List.of(
              reference(1, "Autor, Ada | Alpha"),
              reference(2, "Beta")
          ),
          searches
      );
      ZettelWindowController controller = controllerWith(view, noteRepository, bibliographyService);

      view.setActiveServiceAreaMode(ZettelWindowView.ServiceAreaMode.BOOK);
      view.getBookShowNotesToggleButton().setSelected(true);
      view.setBookNotesVisible(true);

      invokePrivate(controller, "refreshBookWindow");

      assertAll(
          () -> assertEquals(1, noteRepository.usageCountLoadCount, "Notizzahlen werden weiterhin als Aggregat geladen."),
          () -> assertEquals(1, noteRepository.usageRowsLoadCount, "Detailzeilen werden nur bei sichtbarer Tabelle geladen."),
          () -> assertEquals(List.of(10, 12, 20), view.getBookNotesTable().getItems().stream()
                                                     .map(ZettelWindowView.BookNoteRow::getNoteId)
                                                     .toList()),
          () -> assertFalse(view.getBookNotesTable().getItems().stream()
                              .anyMatch(row -> row.getNoteId() == 99), "Unsichtbare Medien duerfen keine Notizen zeigen.")
      );
    });
  }

  /**
   * Erzeugt eine Testreferenz.
   *
   * @param entryId Eintrags-ID
   * @param displayText Anzeigetext
   * @return Bibliographie-Referenz
   */
  private static BibliographyReference reference(int entryId, String displayText) {
    return new BibliographyReference(entryId, 1, "book", "Buch", displayText);
  }

  /**
   * Baut einen Controller ohne dessen produktiven Konstruktor, damit keine echte Datenbank geoeffnet wird.
   *
   * @param view Test-View
   * @param noteRepository Test-Repository
   * @param bibliographyService Test-Bibliographie-Service
   * @return vorbereiteter Controller
   * @throws Exception wenn das Setzen der Felder fehlschlaegt
   */
  private static ZettelWindowController controllerWith(
      ZettelWindowView view,
      NoteRepository noteRepository,
      BibliographyService bibliographyService
  ) throws Exception {
    ZettelWindowController controller = (ZettelWindowController) UNSAFE.allocateInstance(ZettelWindowController.class);
    setField(controller, "view", view);
    setField(controller, "noteRepository", noteRepository);
    setField(controller, "bibliographyService", bibliographyService);
    setField(controller, "bookRefreshExecutor", Executors.newSingleThreadExecutor());
    return controller;
  }

  /**
   * Ruft eine private parameterlose Controller-Methode auf.
   *
   * @param target Zielobjekt
   * @param methodName Methodenname
   * @throws Exception wenn die Methode fehlschlaegt
   */
  private static void invokePrivate(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    try {
      method.invoke(target);
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof Exception nestedException) {
        throw nestedException;
      }
      if (cause instanceof Error nestedError) {
        throw nestedError;
      }
      throw new AssertionError(cause);
    }
  }

  /**
   * Setzt ein privates Feld fuer einen reflexionsbasierten Testcontroller.
   *
   * @param target Zielobjekt
   * @param fieldName Feldname
   * @param value Wert
   * @throws Exception wenn das Feld nicht gesetzt werden kann
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Stoppt den BOOK-Hintergrundexecutor eines Testcontrollers.
   *
   * @param controller Testcontroller
   * @throws Exception wenn das Feld nicht gelesen werden kann
   */
  private static void shutdownBookRefreshExecutor(ZettelWindowController controller) throws Exception {
    Field field = controller.getClass().getDeclaredField("bookRefreshExecutor");
    field.setAccessible(true);
    Object value = field.get(controller);
    if (value instanceof ExecutorService executorService) {
      executorService.shutdownNow();
    }
  }

  /**
   * Laedt Unsafe fuer das Erzeugen eines Controllers ohne produktiven Konstruktor.
   *
   * @return Unsafe-Instanz
   */
  private static Unsafe loadUnsafe() {
    try {
      Field field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return (Unsafe) field.get(null);
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  /**
   * Erzeugt eine minimal reagierende Bibliographie-Fassade fuer BOOK-Refresh-Tests.
   *
   * @param references Suchtreffer
   * @param searches Zaehler der Suchaufrufe
   * @return Test-Service
   */
  private static BibliographyService bibliographyService(
      List<BibliographyReference> references,
      AtomicInteger searches
  ) {
    return bibliographyService(references, Map.of(), searches);
  }

  /**
   * Erzeugt eine minimal reagierende Bibliographie-Fassade fuer BOOK-Refresh-Tests.
   *
   * @param references Suchtreffer
   * @param entriesById geladene Eintraege nach ID
   * @param searches Zaehler der Suchaufrufe
   * @return Test-Service
   */
  private static BibliographyService bibliographyService(
      List<BibliographyReference> references,
      Map<Integer, DynamicBibliographyEntry> entriesById,
      AtomicInteger searches
  ) {
    return (BibliographyService) Proxy.newProxyInstance(
        BibliographyService.class.getClassLoader(),
        new Class<?>[]{BibliographyService.class},
        (proxy, method, args) -> {
          if ("searchMediaReferences".equals(method.getName()) && args != null && args.length == 3) {
            searches.incrementAndGet();
            return references;
          }
          if ("loadEntry".equals(method.getName())) {
            Integer entryId = args == null || args.length == 0 ? null : (Integer) args[0];
            return Optional.ofNullable(entryId == null ? null : entriesById.get(entryId));
          }
          if ("listAttributesForMediaType".equals(method.getName())) {
            return List.of(authorAttribute());
          }
          if ("toString".equals(method.getName())) {
            return "TestBibliographyService";
          }
          return defaultValue(method.getReturnType());
        }
    );
  }

  /**
   * Erzeugt einen minimalen Bucheintrag fuer BOOK-Tabellentests.
   *
   * @param entryId Eintrags-ID
   * @param title Titel
   * @param firstName Vorname
   * @param lastName Nachname
   * @return dynamischer Bibliographie-Eintrag
   */
  private static DynamicBibliographyEntry bookEntry(int entryId, String title, String firstName, String lastName) {
    DynamicBibliographyEntry entry = new DynamicBibliographyEntry();
    entry.setId(entryId);
    entry.setMediaTypeId(1);
    entry.setMediaTypeBibName("book");
    entry.setMediaTypeName("Buch");
    entry.putValue(new StringBibValue("title", title));
    entry.putValue(new PersonBibValue("author", List.of(new PersonRef(1, firstName, lastName, 1))));
    return entry;
  }

  /**
   * Liefert die Personenfeld-Definition fuer Testeintraege.
   *
   * @return Autor-Attribut
   */
  private static MediaAttributeDefinition authorAttribute() {
    return new MediaAttributeDefinition(
        1,
        1,
        "identify",
        new BibtexFieldDefinition(1, "author", "Autor", "person", "")
    );
  }

  /**
   * Erzeugt eine Bibliographie-Fassade, deren Suche bis zur Freigabe blockiert.
   *
   * @param searches Zaehler der Suchaufrufe
   * @param searchStarted Signal fuer gestartete Suche
   * @param releaseSearch Freigabe der blockierten Suche
   * @return blockierende Test-Fassade
   */
  private static BibliographyService blockingBibliographyService(
      AtomicInteger searches,
      CountDownLatch searchStarted,
      CountDownLatch releaseSearch
  ) {
    return (BibliographyService) Proxy.newProxyInstance(
        BibliographyService.class.getClassLoader(),
        new Class<?>[]{BibliographyService.class},
        (proxy, method, args) -> {
          if ("searchMediaReferences".equals(method.getName()) && args != null && args.length == 3) {
            searches.incrementAndGet();
            searchStarted.countDown();
            releaseSearch.await(2, TimeUnit.SECONDS);
            return List.of();
          }
          if ("loadEntry".equals(method.getName())) {
            return Optional.empty();
          }
          if ("toString".equals(method.getName())) {
            return "BlockingTestBibliographyService";
          }
          return defaultValue(method.getReturnType());
        }
    );
  }

  /**
   * Erzeugt eine Bibliographie-Fassade, bei der der erste Suchlauf blockiert und der zweite sofort liefert.
   *
   * @param searches Zaehler der Suchaufrufe
   * @param firstSearchStarted Signal fuer gestartete erste Suche
   * @param releaseFirstSearch Freigabe der ersten Suche
   * @param secondSearchFinished Signal fuer beendete zweite Suche
   * @return sequenziell blockierende Test-Fassade
   */
  private static BibliographyService sequentialBlockingBibliographyService(
      AtomicInteger searches,
      CountDownLatch firstSearchStarted,
      CountDownLatch releaseFirstSearch,
      CountDownLatch secondSearchFinished
  ) {
    return (BibliographyService) Proxy.newProxyInstance(
        BibliographyService.class.getClassLoader(),
        new Class<?>[]{BibliographyService.class},
        (proxy, method, args) -> {
          if ("searchMediaReferences".equals(method.getName()) && args != null && args.length == 3) {
            int call = searches.incrementAndGet();
            if (call == 1) {
              firstSearchStarted.countDown();
              releaseFirstSearch.await(2, TimeUnit.SECONDS);
              return List.of(reference(1, "Alt"));
            }
            if (call == 2) {
              secondSearchFinished.countDown();
              return List.of(reference(2, "Neu"));
            }
            return List.of();
          }
          if ("loadEntry".equals(method.getName())) {
            return Optional.empty();
          }
          if ("toString".equals(method.getName())) {
            return "SequentialBlockingTestBibliographyService";
          }
          return defaultValue(method.getReturnType());
        }
    );
  }

  /**
   * Liefert einen defensiven Standardwert fuer nicht genutzte Proxy-Methoden.
   *
   * @param returnType Rueckgabetyp
   * @return Standardwert
   */
  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) {
      return null;
    }
    if (returnType == Boolean.TYPE) {
      return false;
    }
    if (returnType == Integer.TYPE) {
      return 0;
    }
    if (returnType == Optional.class) {
      return Optional.empty();
    }
    if (returnType == List.class) {
      return List.of();
    }
    return null;
  }

  /**
   * Fuehrt Testcode synchron auf dem JavaFX-Thread aus.
   *
   * @param action auszufuehrender Testcode
   * @throws Exception wenn der Testcode fehlschlaegt oder der JavaFX-Thread nicht antwortet
   */
  private static void runOnFxThread(FxAction action) throws Exception {
    callOnFxThread(() -> {
      action.run();
      return null;
    });
  }

  /**
   * Fuehrt Testcode synchron auf dem JavaFX-Thread aus und liefert ein Ergebnis.
   *
   * @param action auszufuehrender Testcode
   * @param <T> Ergebnistyp
   * @return Ergebnis des Testcodes
   * @throws Exception wenn der Testcode fehlschlaegt oder der JavaFX-Thread nicht antwortet
   */
  private static <T> T callOnFxThread(FxSupplier<T> action) throws Exception {
    if (Platform.isFxApplicationThread()) {
      return action.get();
    }

    CountDownLatch actionLatch = new CountDownLatch(1);
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Platform.runLater(() -> {
      try {
        result.set(action.get());
      } catch (Throwable throwable) {
        failure.set(throwable);
      } finally {
        actionLatch.countDown();
      }
    });

    if (!actionLatch.await(5, TimeUnit.SECONDS)) {
      fail("JavaFX-Testcode wurde nicht rechtzeitig ausgefuehrt.");
    }
    if (failure.get() instanceof Exception exception) {
      throw exception;
    }
    if (failure.get() instanceof Error error) {
      throw error;
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    return result.get();
  }

  /**
   * Wartet, bis eine JavaFX-Bedingung wahr wird.
   *
   * @param condition gepruefte Bedingung auf dem JavaFX-Thread
   * @param failureMessage Fehlermeldung bei Zeitueberschreitung
   * @throws Exception wenn die JavaFX-Ausfuehrung fehlschlaegt oder der Testthread unterbrochen wird
   */
  private static void waitForFxCondition(FxSupplier<Boolean> condition, String failureMessage) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      if (Boolean.TRUE.equals(callOnFxThread(condition))) {
        return;
      }
      Thread.sleep(20);
    }
    fail(failureMessage);
  }

  /**
   * Beschreibt eine Aktion, die auf dem JavaFX-Thread ausgefuehrt wird.
   */
  @FunctionalInterface
  private interface FxAction {

    /**
     * Fuehrt die Aktion aus.
     *
     * @throws Exception wenn die Aktion fehlschlaegt
     */
    void run() throws Exception;
  }

  /**
   * Beschreibt eine Aktion mit Rueckgabewert, die auf dem JavaFX-Thread ausgefuehrt wird.
   *
   * @param <T> Ergebnistyp
   */
  @FunctionalInterface
  private interface FxSupplier<T> {

    /**
     * Fuehrt die Aktion aus.
     *
     * @return Ergebnis der Aktion
     * @throws Exception wenn die Aktion fehlschlaegt
     */
    T get() throws Exception;
  }

  /**
   * Test-Repository mit Zaehlern fuer BOOK-Nutzungsabfragen.
   */
  private static final class CountingNoteRepository extends NoteRepository {
    private final Map<Integer, Integer> usageCountsByEntryId;
    private final List<BibliographyUsageRow> usageRows;
    private int usageCountLoadCount;
    private int usageRowsLoadCount;

    /**
     * Erzeugt ein Repository mit statischen Testdaten.
     *
     * @param usageCountsByEntryId aggregierte Zettelzahlen
     * @param usageRows Zetteldetailzeilen
     */
    private CountingNoteRepository(
        Map<Integer, Integer> usageCountsByEntryId,
        List<BibliographyUsageRow> usageRows
    ) {
      super(new UnsupportedDataSource(), bibliographyService(List.of(), new AtomicInteger()));
      this.usageCountsByEntryId = new LinkedHashMap<>(usageCountsByEntryId);
      this.usageRows = List.copyOf(usageRows);
    }

    /**
     * Zaehlt den Aggregatabruf und liefert die vorbereiteten Notizzahlen.
     *
     * @return aggregierte Zettelzahlen
     */
    @Override
    public Map<Integer, Integer> loadBibliographyUsageCountsByEntryId() {
      usageCountLoadCount++;
      return usageCountsByEntryId;
    }

    /**
     * Zaehlt den Detailabruf und liefert die vorbereiteten Zettelzeilen.
     *
     * @return Zetteldetailzeilen
     */
    @Override
    public List<BibliographyUsageRow> loadBibliographyUsageRows() {
      usageRowsLoadCount++;
      return usageRows;
    }
  }

  /**
   * Datenquelle, die im Test nie echte Verbindungen bereitstellen darf.
   */
  private static final class UnsupportedDataSource implements DataSource {

    /**
     * Verhindert unerwartete Datenbankzugriffe.
     *
     * @return keine Verbindung
     * @throws SQLException immer
     */
    @Override
    public Connection getConnection() throws SQLException {
      throw new SQLFeatureNotSupportedException("Testdatenquelle darf nicht geoeffnet werden.");
    }

    /**
     * Verhindert unerwartete Datenbankzugriffe.
     *
     * @param username Benutzername
     * @param password Passwort
     * @return keine Verbindung
     * @throws SQLException immer
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      throw new SQLFeatureNotSupportedException("Testdatenquelle darf nicht geoeffnet werden.");
    }

    /**
     * Liefert keinen LogWriter.
     *
     * @return {@code null}
     */
    @Override
    public PrintWriter getLogWriter() {
      return null;
    }

    /**
     * Ignoriert den LogWriter.
     *
     * @param out LogWriter
     */
    @Override
    public void setLogWriter(PrintWriter out) {
    }

    /**
     * Ignoriert den Login-Timeout.
     *
     * @param seconds Sekunden
     */
    @Override
    public void setLoginTimeout(int seconds) {
    }

    /**
     * Liefert keinen Login-Timeout.
     *
     * @return Timeout in Sekunden
     */
    @Override
    public int getLoginTimeout() {
      return 0;
    }

    /**
     * Liefert den globalen Logger.
     *
     * @return Logger
     */
    @Override
    public Logger getParentLogger() {
      return Logger.getGlobal();
    }

    /**
     * Entpacken wird nicht unterstuetzt.
     *
     * @param iface Zieltyp
     * @param <T> Zieltyp
     * @return kein Objekt
     * @throws SQLException immer
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
      throw new SQLFeatureNotSupportedException("unwrap nicht unterstuetzt.");
    }

    /**
     * Meldet keine Wrapper-Unterstuetzung.
     *
     * @param iface Zieltyp
     * @return {@code false}
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) {
      return false;
    }
  }
}
