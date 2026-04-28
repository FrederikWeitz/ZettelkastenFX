package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;

import java.util.*;

import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isRelatedDatatype;

/**
 * Zentrale, metadatengetriebene Lookup-Orchestrierung für dynamische
 * Bibliographie-Formulare.
 * <p>
 * In diesem Ausbauschritt übernimmt der Koordinator die Strategien
 * {@code related_entry}, {@code identify_entry} sowie einen ersten
 * metadatengetriebenen Pfad für {@code value_list}.
 * Die Strategie {@code person} folgt anschließend als eigener Schritt.
 */
public class DynamicLookupCoordinator {

  /**
   * Schmale Zugriffsfläche auf den Laufzeitzustand der Shell.
   * Die eigentliche Lookup-Logik bleibt dadurch außerhalb der Shell.
   */
  public interface ShellAccess {

    /**
     * Prüft, ob Lookup-Callbacks momentan unterdrückt werden.
     *
     * @return {@code true}, wenn programmgesteuerte UI-Änderungen gerade keine
     *         Lookup-Reaktionen auslösen sollen
     */
    boolean isLookupSuppressed();

    /**
     * Setzt die temporäre Unterdrückung von Lookup-Callbacks.
     *
     * @param suppressed neuer Unterdrückungszustand
     */
    void setLookupSuppressed(boolean suppressed);

    /**
     * Liest den aktuellen Feldtext eines technischen Feldes.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param bibtexName technischer Feldname
     * @return aktueller Feldtext oder leerer String
     */
    String getCurrentFieldValue(String mediaTypeBibName, String bibtexName);

    /**
     * Setzt die Related-ID samt sichtbarem Anzeige­text eines technischen Feldes.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param bibtexName technischer Feldname
     * @param entryId Ziel-ID oder {@code null}
     * @param displayText sichtbarer Anzeige­text
     */
    void setCurrentRelatedEntry(String mediaTypeBibName,
                                String bibtexName,
                                Integer entryId,
                                String displayText);

    /**
     * Liefert die aktuell selektierte Datenbank-ID des Formulars.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @return selektierte Eintrags-ID oder {@code null}
     */
    Integer getSelectedEntryId(String mediaTypeBibName);

    /**
     * Setzt die aktuell selektierte Datenbank-ID des Formulars.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param entryId neue Eintrags-ID oder {@code null}
     */
    void setSelectedEntryId(String mediaTypeBibName, Integer entryId);

    /**
     * Prüft, ob ein Formular zuletzt per Auto-Fill befüllt wurde.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @return {@code true}, wenn zuletzt Auto-Fill aktiv war
     */
    boolean wasAutoFilled(String mediaTypeBibName);

    /**
     * Setzt das Auto-Fill-Merkmal eines Formulars.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param autoFilled neuer Auto-Fill-Zustand
     */
    void setAutoFilled(String mediaTypeBibName, boolean autoFilled);

    /**
     * Sperrt oder entsperrt die Auto-Fill-Reaktion eines Formulars.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param locked neuer Sperrzustand
     */
    void setAutoFillLocked(String mediaTypeBibName, boolean locked);

    /**
     * Liefert die derzeit eingetragenen Personen eines konkreten Personenfeldes.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param bibtexName technischer Feldname
     * @return eingetragene Personen
     */
    List<Author> getCurrentPersons(String mediaTypeBibName, String bibtexName);

    /**
     * Übernimmt einen aufgelösten Eintrag vollständig in das Formular.
     *
     * @param mediaTypeBibName technischer Medientypname
     * @param entryId aufgelöste Eintrags-ID
     * @param markAsAutoFill Kennzeichen für automatisches Befüllen
     */
    void applyResolvedEntry(String mediaTypeBibName, Integer entryId, boolean markAsAutoFill);
  }

  private record RelatedLookupContext(
      String ownerMediaTypeBibName,
      String relatedBibtexName,
      int minQueryLength,
      boolean autoResolveUnique,
      String searchMediaTypeBibName,
      String createTargetMediaTypeBibName,
      String displayBibtexName
  ) {
  }

  private record RelatedSuggestion(
      int entryId,
      String displayText
  ) {
  }

  private final BibliographyService bibliographyService;

  /**
   * Erzeugt den Koordinator auf Basis der Bibliographie-Fassade.
   *
   * @param bibliographyService Bibliographie-Service
   */
  public DynamicLookupCoordinator(BibliographyService bibliographyService) {
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");
  }

  /**
   * Liefert Vorschläge für ein Personenfeld eines dynamischen Formulars.
   * In diesem Ausbauschritt werden Personenvorschläge service-seitig noch
   * global gesucht; die Feldverdrahtung liegt aber bereits vollständig im
   * {@code DynamicLookupCoordinator}.
   *
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param personBibtexName technischer Feldname des Personenfeldes
   * @param lastNamePrefix aktuelles Präfix des Nachnamens
   * @param selectedEntryId aktuell selektierter Eintrag oder {@code null}
   * @return passende Personenvorschläge
   */
  public List<AuthorSuggestion> findPersonSuggestions(String ownerMediaTypeBibName,
                                                      String personBibtexName,
                                                      String lastNamePrefix,
                                                      Integer selectedEntryId) {
    return bibliographyService.findAuthorSuggestions(
        ownerMediaTypeBibName,
        lastNamePrefix,
        selectedEntryId
    );
  }

  /**
   * Verdrahtet ein dynamisches Feld anhand seiner Lookup-Metadaten.
   * <p>
   * Der Coordinator übernimmt in diesem Ausbauschritt die Strategien
   * {@code identify_entry}, {@code value_list} und {@code related_entry}.
   * Fehlen Lookup-Metadaten vollständig, wird nur dann noch ein Fallback auf
   * {@code related_entry} verwendet, wenn das Feld bereits über seinen
   * Datentyp als echtes Related-Feld erkannt werden kann.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param attribute Attributdefinition des Feldes
   * @param shellAccess Zugriff auf den Shell-Zustand
   */
  public void bindField(BibliographyFieldView fieldView,
                        String ownerMediaTypeBibName,
                        MediaAttributeDefinition attribute,
                        ShellAccess shellAccess) {
    if (fieldView == null || attribute == null || attribute.fieldDefinition() == null || shellAccess == null) {
      return;
    }

    String bibtexName = normalize(attribute.fieldDefinition().bibtexName());
    if (bibtexName.isBlank()) {
      return;
    }

    String datatype = attribute.fieldDefinition().datatype();
    boolean relatedDatatype = isRelatedDatatype(datatype);;

    Optional<MediaAttributeLookupDefinition> lookupDefinition =
        bibliographyService.loadLookupDefinition(ownerMediaTypeBibName, bibtexName);

    if (lookupDefinition.isPresent()) {
      MediaAttributeLookupDefinition definition = lookupDefinition.get();

      if (definition.isIdentifyEntryLookup()) {
        bindIdentifyField(
            fieldView,
            ownerMediaTypeBibName,
            attribute,
            definition,
            shellAccess
        );
        return;
      }

      if (definition.isValueListLookup()) {
        bindValueListField(
            fieldView,
            ownerMediaTypeBibName,
            attribute,
            definition,
            shellAccess
        );
        return;
      }

      if (definition.isRelatedEntryLookup()) {
        bindRelatedField(
            fieldView,
            ownerMediaTypeBibName,
            bibtexName,
            shellAccess
        );
      }

      return;
    }

    if (isWebsiteIdentifyFallback(ownerMediaTypeBibName, bibtexName)) {
      bindWebsiteIdentifyFallback(
          fieldView,
          ownerMediaTypeBibName,
          bibtexName,
          shellAccess
      );
      return;
    }

    if (relatedDatatype) {
      bindRelatedField(
          fieldView,
          ownerMediaTypeBibName,
          bibtexName,
          shellAccess
      );
    }
  }

  /**
   * Prüft, ob für Website-Felder ein metadatenfreier Identify-Fallback
   * aktiviert werden soll.
   * Dies betrifft unter dem aktuellen Ausbau die beiden gekoppelten Felder
   * {@code title} und {@code url}.
   *
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return {@code true}, wenn ein Website-Identify-Fallback verwendet werden soll
   */
  private boolean isWebsiteIdentifyFallback(String ownerMediaTypeBibName, String bibtexName) {
    String normalizedMediaType = normalize(ownerMediaTypeBibName);
    String normalizedBibtexName = normalize(bibtexName);

    if (!"website".equals(normalizedMediaType)) {
      return false;
    }

    return "title".equals(normalizedBibtexName) || "url".equals(normalizedBibtexName);
  }

  /**
   * Verdrahtet die beiden Website-Identify-Felder {@code title} und {@code url}
   * auch dann als reaktive Identify-Felder, wenn für eines der beiden Felder
   * noch keine explizite Lookup-Definition in den Metadaten hinterlegt ist.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param identifyBibtexName technischer Feldname ({@code title} oder {@code url})
   * @param shellAccess Shell-Zugriff
   */
  private void bindWebsiteIdentifyFallback(BibliographyFieldView fieldView,
                                           String ownerMediaTypeBibName,
                                           String identifyBibtexName,
                                           ShellAccess shellAccess) {
    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (shellAccess.isLookupSuppressed()) {
        return;
      }

      if (shellAccess.wasAutoFilled(ownerMediaTypeBibName)) {
        shellAccess.setAutoFillLocked(ownerMediaTypeBibName, true);
        shellAccess.setAutoFilled(ownerMediaTypeBibName, false);
      }

      shellAccess.setSelectedEntryId(ownerMediaTypeBibName, null);
      showWebsiteIdentifySuggestions(
          fieldView,
          ownerMediaTypeBibName,
          identifyBibtexName,
          shellAccess
      );
    });

    fieldView.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        handleWebsiteIdentifyCommit(
            fieldView,
            ownerMediaTypeBibName,
            identifyBibtexName,
            shellAccess
        );
      }
    });

    fieldView.getEditor().setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case ENTER, TAB -> handleWebsiteIdentifyCommit(
            fieldView,
            ownerMediaTypeBibName,
            identifyBibtexName,
            shellAccess
        );
        case ESCAPE -> fieldView.hideSuggestions();
        default -> {
        }
      }
    });
  }

  /**
   * Zeigt Vorschläge für das Website-Feld {@code title} oder {@code url}.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param identifyBibtexName aktives Identify-Feld
   * @param shellAccess Shell-Zugriff
   */
  private void showWebsiteIdentifySuggestions(BibliographyFieldView fieldView,
                                              String ownerMediaTypeBibName,
                                              String identifyBibtexName,
                                              ShellAccess shellAccess) {
    if (fieldView == null || fieldView.getEditor() == null || !fieldView.getEditor().isFocused()) {
      if (fieldView != null) {
        fieldView.hideSuggestions();
      }
      return;
    }

    String query = fieldView.getText();
    if (query == null || query.trim().length() < 1) {
      fieldView.hideSuggestions();
      return;
    }

    Map<String, String> stringFilters = collectIdentifyStringFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );
    Map<String, List<Author>> personFilters = collectIdentifyPersonFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );

    List<IdentifySuggestion> hits = bibliographyService.findIdentifySuggestions(
        ownerMediaTypeBibName,
        identifyBibtexName,
        query,
        stringFilters,
        personFilters,
        20
    );

    if (hits.isEmpty() || containsOnlyCurrentIdentifyValue(query, hits)) {
      fieldView.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (IdentifySuggestion hit : hits) {
      Label option = new Label(hit.displayValue());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyIdentifySuggestion(
          fieldView,
          ownerMediaTypeBibName,
          hit,
          shellAccess
      ));
      items.add(item);
    }

    fieldView.showSuggestions(items);
  }

  /**
   * Commit-Logik für die Website-Identify-Felder {@code title} und {@code url}.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param identifyBibtexName aktives Identify-Feld
   * @param shellAccess Shell-Zugriff
   */
  private void handleWebsiteIdentifyCommit(BibliographyFieldView fieldView,
                                           String ownerMediaTypeBibName,
                                           String identifyBibtexName,
                                           ShellAccess shellAccess) {
    if (fieldView == null) {
      return;
    }

    fieldView.hideSuggestions();

    if (shellAccess.isLookupSuppressed()) {
      return;
    }

    String text = fieldView.getText();
    if (text == null || text.isBlank()) {
      shellAccess.setSelectedEntryId(ownerMediaTypeBibName, null);
      return;
    }

    if (matchesCurrentSelectionExactly(
        ownerMediaTypeBibName,
        identifyBibtexName,
        text,
        shellAccess
    )) {
      return;
    }

    Map<String, String> stringFilters = collectIdentifyStringFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );
    Map<String, List<Author>> personFilters = collectIdentifyPersonFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );

    IdentifySuggestion exact = bibliographyService.resolveExactIdentify(
        ownerMediaTypeBibName,
        identifyBibtexName,
        text,
        stringFilters,
        personFilters
    );

    if (exact != null && exact.entryId() != null && exact.entryId() > 0) {
      applyIdentifySuggestion(fieldView, ownerMediaTypeBibName, exact, shellAccess);
      return;
    }

    List<IdentifySuggestion> hits = bibliographyService.findIdentifySuggestions(
        ownerMediaTypeBibName,
        identifyBibtexName,
        text,
        stringFilters,
        personFilters,
        2
    );

    if (hits.size() == 1) {
      applyIdentifySuggestion(fieldView, ownerMediaTypeBibName, hits.getFirst(), shellAccess);
      return;
    }

    tryAutoResolveIdentifyCombinationForField(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );
  }

  /**
   * Verdrahtet ein Related-Feld mit Vorschlags- und Commit-Logik.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param relatedBibtexName technischer Feldname
   * @param shellAccess Shell-Zugriff
   */
  private void bindRelatedField(BibliographyFieldView fieldView,
                                String ownerMediaTypeBibName,
                                String relatedBibtexName,
                                ShellAccess shellAccess) {
    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (shellAccess.isLookupSuppressed()) {
        return;
      }

      shellAccess.setCurrentRelatedEntry(
          ownerMediaTypeBibName,
          relatedBibtexName,
          null,
          newValue == null ? "" : newValue
      );

      showRelatedSuggestions(fieldView, ownerMediaTypeBibName, relatedBibtexName, shellAccess);
    });

    fieldView.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        handleRelatedCommit(fieldView, ownerMediaTypeBibName, relatedBibtexName, shellAccess);
      }
    });

    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (shellAccess.isLookupSuppressed()) {
        return;
      }

      String normalizedText = newValue == null ? "" : newValue;

      /*
       * Die bestehende Related-ID bleibt erhalten, solange der Nutzer den
       * Feldinhalt nicht wirklich leert. Dadurch gehen unveränderte oder nur
       * programmgesteuert gesetzte Related-Felder nicht verloren.
       */
      if (normalizedText.isBlank()) {
        shellAccess.setCurrentRelatedEntry(
            ownerMediaTypeBibName,
            relatedBibtexName,
            null,
            ""
        );
      }

      showRelatedSuggestions(fieldView, ownerMediaTypeBibName, relatedBibtexName, shellAccess);
    });
  }

  /**
   * Verdrahtet ein erstes {@code identify_entry}-Feld.
   * In diesem Zwischenstand wird bewusst nur der bereits vorhandene
   * titelbasierte Identifikationspfad übernommen.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param attribute Felddefinition
   * @param lookupDefinition Lookup-Metadaten
   * @param shellAccess Shell-Zugriff
   */
  private void bindIdentifyField(BibliographyFieldView fieldView,
                                 String ownerMediaTypeBibName,
                                 MediaAttributeDefinition attribute,
                                 MediaAttributeLookupDefinition lookupDefinition,
                                 ShellAccess shellAccess) {
    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (shellAccess.isLookupSuppressed()) {
        return;
      }

      if (shellAccess.wasAutoFilled(ownerMediaTypeBibName)) {
        shellAccess.setAutoFillLocked(ownerMediaTypeBibName, true);
        shellAccess.setAutoFilled(ownerMediaTypeBibName, false);
      }

      shellAccess.setSelectedEntryId(ownerMediaTypeBibName, null);
      showIdentifySuggestions(
          fieldView,
          ownerMediaTypeBibName,
          lookupDefinition,
          shellAccess
      );
    });

    fieldView.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        handleIdentifyCommit(
            fieldView,
            ownerMediaTypeBibName,
            lookupDefinition,
            shellAccess
        );
      }
    });

    fieldView.getEditor().setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case ENTER, TAB -> handleIdentifyCommit(
            fieldView,
            ownerMediaTypeBibName,
            lookupDefinition,
            shellAccess
        );
        case ESCAPE -> fieldView.hideSuggestions();
        default -> {
        }
      }
    });
  }

  /**
   * Verdrahtet ein Feld der Strategie {@code value_list}.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param attribute Felddefinition
   * @param lookupDefinition Lookup-Metadaten
   * @param shellAccess Shell-Zugriff
   */
  private void bindValueListField(BibliographyFieldView fieldView,
                                  String ownerMediaTypeBibName,
                                  MediaAttributeDefinition attribute,
                                  MediaAttributeLookupDefinition lookupDefinition,
                                  ShellAccess shellAccess) {
    String bibtexName = normalize(attribute.fieldDefinition().bibtexName());

    fieldView.getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
      if (shellAccess.isLookupSuppressed()) {
        return;
      }

      showValueListSuggestions(
          fieldView,
          ownerMediaTypeBibName,
          bibtexName,
          lookupDefinition
      );
    });

    fieldView.getEditor().focusedProperty().addListener((obs, oldValue, focused) -> {
      if (!focused) {
        handleValueListCommit(
            fieldView,
            ownerMediaTypeBibName,
            bibtexName,
            lookupDefinition
        );
      }
    });

    fieldView.getEditor().setOnKeyPressed(event -> {
      switch (event.getCode()) {
        case ENTER, TAB -> handleValueListCommit(
            fieldView,
            ownerMediaTypeBibName,
            bibtexName,
            lookupDefinition
        );
        case ESCAPE -> fieldView.hideSuggestions();
        default -> {
        }
      }
    });
  }

  /**
   * Zeigt Vorschläge für ein titelbasiertes Identify-Feld.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param lookupDefinition Lookup-Metadaten
   * @param shellAccess Shell-Zugriff
   */
  private void showIdentifySuggestions(BibliographyFieldView fieldView,
                                       String ownerMediaTypeBibName,
                                       MediaAttributeLookupDefinition lookupDefinition,
                                       ShellAccess shellAccess) {
    if (fieldView == null || fieldView.getEditor() == null || !fieldView.getEditor().isFocused()) {
      if (fieldView != null) {
        fieldView.hideSuggestions();
      }
      return;
    }

    String query = fieldView.getText();
    if (query.length() < lookupDefinition.minQueryLength()) {
      fieldView.hideSuggestions();
      return;
    }

    String identifyBibtexName = normalize(lookupDefinition.fieldDefinition().bibtexName());
    Map<String, String> stringFilters = collectIdentifyStringFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );
    Map<String, List<Author>> personFilters = collectIdentifyPersonFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );

    List<IdentifySuggestion> hits = bibliographyService.findIdentifySuggestions(
        ownerMediaTypeBibName,
        identifyBibtexName,
        query,
        stringFilters,
        personFilters,
        20
    );

    if (hits.isEmpty() || containsOnlyCurrentIdentifyValue(query, hits)) {
      fieldView.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (IdentifySuggestion hit : hits) {
      Label option = new Label(hit.displayValue());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyIdentifySuggestion(
          fieldView,
          ownerMediaTypeBibName,
          hit,
          shellAccess
      ));
      items.add(item);
    }

    fieldView.showSuggestions(items);
  }

  /**
   * Zeigt Vorschläge für ein Feld der Strategie {@code value_list}.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param lookupDefinition Lookup-Metadaten
   */
  private void showValueListSuggestions(BibliographyFieldView fieldView,
                                        String ownerMediaTypeBibName,
                                        String bibtexName,
                                        MediaAttributeLookupDefinition lookupDefinition) {
    if (fieldView == null || fieldView.getEditor() == null || !fieldView.getEditor().isFocused()) {
      if (fieldView != null) {
        fieldView.hideSuggestions();
      }
      return;
    }

    String query = fieldView.getText();
    if (query.length() < lookupDefinition.minQueryLength()) {
      fieldView.hideSuggestions();
      return;
    }

    List<String> hits = bibliographyService.findValueListSuggestions(
        ownerMediaTypeBibName,
        bibtexName,
        query
    );

    if (hits.isEmpty() || containsOnlyCurrentValueListValue(query, hits)) {
      fieldView.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (String hit : hits) {
      Label option = new Label(hit);
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyValueListSuggestion(fieldView, hit));
      items.add(item);
    }

    fieldView.showSuggestions(items);
  }

  /**
   * Commit-Logik für das titelbasierte Identify-Feld.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param lookupDefinition Lookup-Metadaten
   * @param shellAccess Shell-Zugriff
   */
  private void handleIdentifyCommit(BibliographyFieldView fieldView,
                                    String ownerMediaTypeBibName,
                                    MediaAttributeLookupDefinition lookupDefinition,
                                    ShellAccess shellAccess) {
    if (fieldView == null) {
      return;
    }

    fieldView.hideSuggestions();

    if (shellAccess.isLookupSuppressed()) {
      return;
    }

    String text = fieldView.getText();
    String identifyBibtexName = normalize(lookupDefinition.fieldDefinition().bibtexName());

    if (text.isBlank()) {
      shellAccess.setSelectedEntryId(ownerMediaTypeBibName, null);
      return;
    }

    if (matchesCurrentSelectionExactly(
        ownerMediaTypeBibName,
        identifyBibtexName,
        text,
        shellAccess
    )) {
      return;
    }

    Map<String, String> stringFilters = collectIdentifyStringFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );
    Map<String, List<Author>> personFilters = collectIdentifyPersonFilters(
        ownerMediaTypeBibName,
        identifyBibtexName,
        shellAccess
    );

    IdentifySuggestion exact = bibliographyService.resolveExactIdentify(
        ownerMediaTypeBibName,
        identifyBibtexName,
        text,
        stringFilters,
        personFilters
    );

    if (exact != null && exact.entryId() != null && exact.entryId() > 0) {
      applyIdentifySuggestion(fieldView, ownerMediaTypeBibName, exact, shellAccess);
      return;
    }

    if (lookupDefinition.autoResolveUnique()) {
      List<IdentifySuggestion> hits = bibliographyService.findIdentifySuggestions(
          ownerMediaTypeBibName,
          identifyBibtexName,
          text,
          stringFilters,
          personFilters,
          2
      );

      if (hits.size() == 1) {
        applyIdentifySuggestion(fieldView, ownerMediaTypeBibName, hits.getFirst(), shellAccess);
        return;
      }
    }

    tryAutoResolveIdentifyCombination(
        ownerMediaTypeBibName,
        lookupDefinition,
        shellAccess
    );
  }

  /**
   * Prüft, ob die aktuell bereits gefüllten Identify-Felder eines Medientyps
   * zusammen genau einen bestehenden Eintrag bestimmen. Ist dies der Fall,
   * wird der Eintrag vollständig in das Formular übernommen.
   *
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param lookupDefinition Lookup-Metadaten des aktuell bearbeiteten Feldes
   * @param shellAccess Shell-Zugriff
   */
  private void tryAutoResolveIdentifyCombination(String ownerMediaTypeBibName,
                                                 MediaAttributeLookupDefinition lookupDefinition,
                                                 ShellAccess shellAccess) {
    if (lookupDefinition == null || lookupDefinition.fieldDefinition() == null) {
      return;
    }

    tryAutoResolveIdentifyCombinationForField(
        ownerMediaTypeBibName,
        lookupDefinition.fieldDefinition().bibtexName(),
        shellAccess
    );
  }

  /**
   * Prüft feldunabhängig, ob die aktuell gefüllten Identify-Felder zusammen
   * genau einen bestehenden Eintrag bestimmen.
   *
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param activeIdentifyBibtexName aktives Identify-Feld
   * @param shellAccess Shell-Zugriff
   */
  private void tryAutoResolveIdentifyCombinationForField(String ownerMediaTypeBibName,
                                                         String activeIdentifyBibtexName,
                                                         ShellAccess shellAccess) {
    if (shellAccess == null) {
      return;
    }

    String normalizedBibtexName = normalize(activeIdentifyBibtexName);
    if (normalizedBibtexName.isBlank()) {
      return;
    }

    String activeValue = shellAccess.getCurrentFieldValue(ownerMediaTypeBibName, normalizedBibtexName);
    if (normalize(activeValue).isBlank()) {
      return;
    }

    Map<String, String> stringFilters = collectIdentifyStringFilters(
        ownerMediaTypeBibName,
        normalizedBibtexName,
        shellAccess
    );
    Map<String, List<Author>> personFilters = collectIdentifyPersonFilters(
        ownerMediaTypeBibName,
        normalizedBibtexName,
        shellAccess
    );

    List<IdentifySuggestion> hits = bibliographyService.findIdentifySuggestions(
        ownerMediaTypeBibName,
        normalizedBibtexName,
        activeValue,
        stringFilters,
        personFilters,
        2
    );

    if (hits.size() == 1) {
      IdentifySuggestion hit = hits.getFirst();
      if (hit != null && hit.entryId() != null && hit.entryId() > 0) {
        Integer currentSelectedEntryId = shellAccess.getSelectedEntryId(ownerMediaTypeBibName);
        if (Objects.equals(currentSelectedEntryId, hit.entryId())) {
          return;
        }
        shellAccess.applyResolvedEntry(ownerMediaTypeBibName, hit.entryId(), true);
      }
    }
  }

  /**
   * Prüft, ob der aktuell selektierte Eintrag bereits exakt zur aktuellen
   * Identify-Eingabe des aktiven Feldes passt.
   *
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param identifyBibtexName aktives Identify-Feld
   * @param currentText aktueller Feldtext
   * @param shellAccess Shell-Zugriff
   * @return {@code true}, wenn die aktuelle Selektion bereits exakt passt
   */
  private boolean matchesCurrentSelectionExactly(String ownerMediaTypeBibName,
                                                 String identifyBibtexName,
                                                 String currentText,
                                                 ShellAccess shellAccess) {
    if (shellAccess == null) {
      return false;
    }

    Integer selectedEntryId = shellAccess.getSelectedEntryId(ownerMediaTypeBibName);
    if (selectedEntryId == null || selectedEntryId <= 0) {
      return false;
    }

    DynamicBibliographyEntry entry = bibliographyService.loadEntry(selectedEntryId).orElse(null);
    if (entry == null) {
      return false;
    }

    BibValue value = entry.findValue(identifyBibtexName).orElse(null);
    if (value == null) {
      return false;
    }

    String displayValue = extractDisplayValue(value);
    return normalize(displayValue).equalsIgnoreCase(normalize(currentText));
  }

  /**
   * Commit-Logik für ein Feld der Strategie {@code value_list}.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param lookupDefinition Lookup-Metadaten
   */
  private void handleValueListCommit(BibliographyFieldView fieldView,
                                     String ownerMediaTypeBibName,
                                     String bibtexName,
                                     MediaAttributeLookupDefinition lookupDefinition) {
    if (fieldView == null) {
      return;
    }

    fieldView.hideSuggestions();

    String text = fieldView.getText();
    if (text.isBlank()) {
      return;
    }

    String exact = bibliographyService.resolveExactValueListValue(
        ownerMediaTypeBibName,
        bibtexName,
        text
    );

    if (exact != null) {
      applyValueListSuggestion(fieldView, exact);
      return;
    }

    if (lookupDefinition.autoResolveUnique()) {
      List<String> hits = bibliographyService.findValueListSuggestions(
          ownerMediaTypeBibName,
          bibtexName,
          text
      );

      if (hits.size() == 1) {
        applyValueListSuggestion(fieldView, hits.getFirst());
      }
    }
  }

  /**
   * Übernimmt einen aufgelösten Identify-Treffer in das Formular.
   *
   * @param fieldView Feldansicht
   * @param ownerMediaTypeBibName technischer Medientypname
   * @param suggestion ausgewählter Treffer
   * @param shellAccess Shell-Zugriff
   */
  private void applyIdentifySuggestion(BibliographyFieldView fieldView,
                                       String ownerMediaTypeBibName,
                                       IdentifySuggestion suggestion,
                                       ShellAccess shellAccess) {
    if (suggestion == null || suggestion.entryId() == null || suggestion.entryId() <= 0) {
      return;
    }

    shellAccess.setLookupSuppressed(true);
    try {
      shellAccess.applyResolvedEntry(ownerMediaTypeBibName, suggestion.entryId(), true);
      fieldView.setText(suggestion.displayValue() == null ? "" : suggestion.displayValue());
    } finally {
      shellAccess.setLookupSuppressed(false);
    }

    fieldView.hideSuggestions();
  }

  /**
   * Übernimmt einen aufgelösten Wertelisten-Vorschlag in das Feld.
   *
   * @param fieldView Feldansicht
   * @param value ausgewählter Wert
   */
  private void applyValueListSuggestion(BibliographyFieldView fieldView, String value) {
    if (fieldView == null) {
      return;
    }

    fieldView.setText(value == null ? "" : value);
    fieldView.hideSuggestions();
  }

  /**
   * Löst den Ziel-Medientyp eines Related-Feldes metadatengetrieben auf.
   * Gibt es keinen Eintrag in {@code related_media_type}, bleibt der Rückgabewert
   * leer. In diesem Fall ist Suchen weiter erlaubt, das Anlegen eines neuen
   * Zielmediums aber nicht eindeutig genug.
   *
   * @param ownerMediaTypeBibName technischer Medientypname des Formulars
   * @param relatedBibtexName technischer Feldname
   * @return technischer Ziel-Medientypname oder leerer String
   */
  public String resolveRelatedTargetMediaTypeBibName(String ownerMediaTypeBibName,
                                                     String relatedBibtexName) {
    return resolveRelatedDefinition(ownerMediaTypeBibName, relatedBibtexName)
               .map(RelatedMediaDefinition::targetMediaTypeDefinition)
               .map(MediaTypeDefinition::bibName)
               .map(this::normalize)
               .orElse("");
  }

  private void showRelatedSuggestions(BibliographyFieldView fieldView,
                                      String ownerMediaTypeBibName,
                                      String relatedBibtexName,
                                      ShellAccess shellAccess) {
    if (fieldView == null || fieldView.getEditor() == null || !fieldView.getEditor().isFocused()) {
      if (fieldView != null) {
        fieldView.hideSuggestions();
      }
      return;
    }

    RelatedLookupContext context = resolveRelatedLookupContext(ownerMediaTypeBibName, relatedBibtexName);
    String query = fieldView.getText();

    if (query.length() < context.minQueryLength()) {
      fieldView.hideSuggestions();
      return;
    }

    List<RelatedSuggestion> hits = searchRelatedSuggestions(context, query);
    if (hits.isEmpty() || containsOnlyCurrentDisplay(query, hits)) {
      fieldView.hideSuggestions();
      return;
    }

    List<CustomMenuItem> items = new ArrayList<>();
    for (RelatedSuggestion hit : hits) {
      Label option = new Label(hit.displayText());
      CustomMenuItem item = new CustomMenuItem(option, true);
      item.setOnAction(e -> applyRelatedSuggestion(
          fieldView,
          context,
          hit,
          shellAccess
      ));
      items.add(item);
    }

    fieldView.showSuggestions(items);
  }

  private void handleRelatedCommit(BibliographyFieldView fieldView,
                                   String ownerMediaTypeBibName,
                                   String relatedBibtexName,
                                   ShellAccess shellAccess) {
    if (fieldView == null) {
      return;
    }

    fieldView.hideSuggestions();

    if (shellAccess.isLookupSuppressed()) {
      return;
    }

    RelatedLookupContext context = resolveRelatedLookupContext(ownerMediaTypeBibName, relatedBibtexName);
    String text = fieldView.getText();

    if (text.isBlank()) {
      shellAccess.setCurrentRelatedEntry(
          ownerMediaTypeBibName,
          relatedBibtexName,
          null,
          ""
      );
      return;
    }

    List<RelatedSuggestion> hits = searchRelatedSuggestions(context, text);
    RelatedSuggestion exact = hits.stream()
                                  .filter(hit -> hit.displayText().equalsIgnoreCase(text))
                                  .findFirst()
                                  .orElse(null);

    if (exact != null) {
      applyRelatedSuggestion(fieldView, context, exact, shellAccess);
      return;
    }

    if (context.autoResolveUnique() && hits.size() == 1) {
      applyRelatedSuggestion(fieldView, context, hits.getFirst(), shellAccess);
    }

    /*
     * Kein exakter Treffer:
     * Die bestehende Related-ID bleibt bewusst erhalten, solange der Nutzer
     * den Feldtext nicht leert. So führt ein bloßer Fokusverlust nicht mehr
     * zum stillen Verlust der Verknüpfung.
     */
  }

  private void applyRelatedSuggestion(BibliographyFieldView fieldView,
                                      RelatedLookupContext context,
                                      RelatedSuggestion suggestion,
                                      ShellAccess shellAccess) {
    shellAccess.setLookupSuppressed(true);
    try {
      shellAccess.setCurrentRelatedEntry(
          context.ownerMediaTypeBibName(),
          context.relatedBibtexName(),
          suggestion.entryId(),
          suggestion.displayText()
      );
      fieldView.setText(suggestion.displayText());
    } finally {
      shellAccess.setLookupSuppressed(false);
    }

    fieldView.hideSuggestions();
  }

  /**
   * Löst die Felddefinition eines technischen BibTeX-Feldes direkt über die
   * Metadaten auf.
   *
   * @param relatedBibtexName technischer Feldname
   * @return optionale Felddefinition
   */
  private Optional<BibtexFieldDefinition> resolveBibtexFieldDefinition(String relatedBibtexName) {
    String normalizedRelated = normalize(relatedBibtexName);
    if (normalizedRelated.isBlank()) {
      return Optional.empty();
    }

    return bibliographyService.loadBibtexTypeByBibName(normalizedRelated);
  }

  private RelatedLookupContext resolveRelatedLookupContext(String ownerMediaTypeBibName,
                                                           String relatedBibtexName) {
    String normalizedOwner = normalize(ownerMediaTypeBibName);
    String normalizedRelated = normalize(relatedBibtexName);

    MediaAttributeLookupDefinition lookupDefinition =
        bibliographyService.loadLookupDefinition(normalizedOwner, normalizedRelated).orElse(null);

    RelatedMediaDefinition relatedDefinition =
        resolveRelatedDefinition(normalizedOwner, normalizedRelated).orElse(null);

    int minQueryLength = lookupDefinition == null ? 1 : lookupDefinition.minQueryLength();
    boolean autoResolveUnique = lookupDefinition != null && lookupDefinition.autoResolveUnique();

    String searchMediaTypeBibName = relatedDefinition == null
                                        ? ""
                                        : normalize(relatedDefinition.targetMediaTypeDefinition().bibName());

    String createTargetMediaTypeBibName = searchMediaTypeBibName;

    String displayBibtexName = relatedDefinition == null
                                   ? "title"
                                   : normalize(relatedDefinition.displayFieldDefinition().bibtexName());

    if (displayBibtexName.isBlank()) {
      displayBibtexName = "title";
    }

    return new RelatedLookupContext(
        normalizedOwner,
        normalizedRelated,
        Math.max(0, minQueryLength),
        autoResolveUnique,
        searchMediaTypeBibName,
        createTargetMediaTypeBibName,
        displayBibtexName
    );
  }

  private Optional<RelatedMediaDefinition> resolveRelatedDefinition(String ownerMediaTypeBibName,
                                                                    String relatedBibtexName) {
    Optional<BibtexFieldDefinition> fieldDefinition =
        resolveBibtexFieldDefinition(relatedBibtexName);

    if (fieldDefinition.isEmpty()) {
      return Optional.empty();
    }

    return bibliographyService.loadRelatedDefinition(fieldDefinition.get().id());
  }

  private List<RelatedSuggestion> searchRelatedSuggestions(RelatedLookupContext context, String query) {
    return bibliographyService.searchMediaReferences(
            context.searchMediaTypeBibName(),
            context.displayBibtexName(),
            query,
            20
        ).stream()
               .map(reference -> new RelatedSuggestion(
                   reference.entryId(),
                   resolveSuggestionDisplayText(reference.entryId(), context.displayBibtexName(), reference.displayText())
               ))
               .filter(suggestion -> suggestion.displayText() != null && !suggestion.displayText().isBlank())
               .toList();
  }

  private String resolveSuggestionDisplayText(int entryId,
                                              String displayBibtexName,
                                              String fallbackDisplayText) {
    Optional<DynamicBibliographyEntry> loaded = bibliographyService.loadEntry(entryId);
    if (loaded.isEmpty()) {
      return fallbackDisplayText == null ? "" : fallbackDisplayText;
    }

    return loaded.get().findValue(displayBibtexName)
               .map(this::extractDisplayValue)
               .filter(value -> value != null && !value.isBlank())
               .orElseGet(() -> fallbackDisplayText == null ? "" : fallbackDisplayText);
  }

  private String extractDisplayValue(BibValue value) {
    return switch (value) {
      case StringBibValue stringValue ->
          stringValue.value() == null ? "" : stringValue.value().trim();

      case IntegerBibValue integerValue ->
          integerValue.value() == null ? "" : String.valueOf(integerValue.value());

      case RelatedBibValue relatedValue ->
          relatedValue.displayValue() == null ? "" : relatedValue.displayValue().trim();

      case PersonBibValue personValue -> {
        if (personValue.value() == null || personValue.value().isEmpty()) {
          yield "";
        }
        PersonRef person = personValue.value().getFirst();
        String firstName = person.firstName() == null ? "" : person.firstName().trim();
        String lastName = person.lastName() == null ? "" : person.lastName().trim();

        if (lastName.isBlank() && firstName.isBlank()) {
          yield "";
        }
        if (lastName.isBlank()) {
          yield firstName;
        }
        if (firstName.isBlank()) {
          yield lastName;
        }
        yield lastName + ", " + firstName;
      }
      default -> throw new IllegalStateException("Unexpected value: " + value);
    };
  }

  private boolean containsOnlyCurrentDisplay(String currentValue, List<RelatedSuggestion> hits) {
    String normalizedCurrentValue = normalize(currentValue).toLowerCase();
    if (normalizedCurrentValue.isBlank() || hits == null || hits.isEmpty()) {
      return false;
    }

    for (RelatedSuggestion hit : hits) {
      String display = normalize(hit.displayText()).toLowerCase();
      if (!normalizedCurrentValue.equals(display)) {
        return false;
      }
    }

    return true;
  }

  private boolean containsOnlyCurrentIdentifyValue(String currentValue, List<IdentifySuggestion> hits) {
    String normalizedCurrentValue = normalize(currentValue).toLowerCase();
    if (normalizedCurrentValue.isBlank() || hits == null || hits.isEmpty()) {
      return false;
    }

    for (IdentifySuggestion hit : hits) {
      String value = normalize(hit.displayValue()).toLowerCase();
      if (!normalizedCurrentValue.equals(value)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Prüft, ob alle Wertelisten-Vorschläge bereits exakt dem aktuellen Feldwert
   * entsprechen.
   *
   * @param currentValue aktueller Feldwert
   * @param hits Wertvorschläge
   * @return {@code true}, wenn keine echte Auswahlalternative sichtbar wäre
   */
  private boolean containsOnlyCurrentValueListValue(String currentValue, List<String> hits) {
    String normalizedCurrentValue = normalize(currentValue).toLowerCase();
    if (normalizedCurrentValue.isBlank() || hits == null || hits.isEmpty()) {
      return false;
    }

    for (String hit : hits) {
      String value = normalize(hit).toLowerCase();
      if (!normalizedCurrentValue.equals(value)) {
        return false;
      }
    }

    return true;
  }

  private Map<String, String> collectIdentifyStringFilters(String ownerMediaTypeBibName,
                                                           String activeIdentifyBibtexName,
                                                           ShellAccess shellAccess) {
    Map<String, String> result = new LinkedHashMap<>();

    for (MediaAttributeDefinition attribute : bibliographyService.listIdentifyAttributesForMediaType(ownerMediaTypeBibName)) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      String bibtexName = normalize(attribute.fieldDefinition().bibtexName());
      String datatype = normalize(attribute.fieldDefinition().datatype());

      if (bibtexName.isBlank()
              || bibtexName.equalsIgnoreCase(activeIdentifyBibtexName)
              || "person".equalsIgnoreCase(datatype)
              || isIntegerLikeDatatype(datatype)) {
        continue;
      }

      String value = shellAccess.getCurrentFieldValue(ownerMediaTypeBibName, bibtexName);
      if (value != null && !value.trim().isBlank()) {
        result.put(bibtexName, value.trim());
      }
    }

    return result;
  }

  private Map<String, List<Author>> collectIdentifyPersonFilters(String ownerMediaTypeBibName,
                                                                 String activeIdentifyBibtexName,
                                                                 ShellAccess shellAccess) {
    Map<String, List<Author>> result = new LinkedHashMap<>();

    for (MediaAttributeDefinition attribute : bibliographyService.listIdentifyAttributesForMediaType(ownerMediaTypeBibName)) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      String bibtexName = normalize(attribute.fieldDefinition().bibtexName());
      String datatype = normalize(attribute.fieldDefinition().datatype());

      if (bibtexName.isBlank()
              || bibtexName.equalsIgnoreCase(activeIdentifyBibtexName)
              || !"person".equalsIgnoreCase(datatype)) {
        continue;
      }

      List<Author> persons = shellAccess.getCurrentPersons(ownerMediaTypeBibName, bibtexName);
      if (persons != null && !persons.isEmpty()) {
        result.put(bibtexName, persons);
      }
    }

    return result;
  }

  private boolean isIntegerLikeDatatype(String datatype) {
    String normalized = normalize(datatype).toLowerCase();
    return normalized.equals("integer")
               || normalized.equals("year")
               || normalized.equals("month")
               || normalized.equals("anderer eintrag/integer");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}