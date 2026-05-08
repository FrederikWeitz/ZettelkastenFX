package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;

import java.util.Objects;

import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isIntegerDatatype;
import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isPersonDatatype;
import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isRelatedDatatype;

/**
 * Metadatengesteuerte Factory für bibliographische Feldkomponenten.
 * In diesem Ausbauschritt werden String- und Integer-Felder erzeugt.
 * Personen- und Related-Felder folgen als eigene Komponenten.
 */
public class BibliographyFieldFactory {

  /**
   * Erzeugt eine bibliographische Feldkomponente anhand der Attributmetadaten.
   *
   * @param request Erzeugungsanfrage
   * @return passende Feldkomponente
   */
  public BibliographyFieldComponent create(BibliographyFieldRequest request) {
    Objects.requireNonNull(request, "request");

    MediaAttributeDefinition attribute = request.attribute();
    if (attribute.fieldDefinition() == null) {
      throw new IllegalArgumentException("Attribut ohne Felddefinition kann nicht gerendert werden.");
    }

    String datatype = attribute.fieldDefinition().datatype();

    if (isPersonDatatype(datatype)) {
      throw new UnsupportedOperationException(
          "Personenfelder benötigen Shell-Callbacks und werden über createPersonField(...) erzeugt."
      );
    }

    if (isRelatedDatatype(datatype)) {
      throw new UnsupportedOperationException(
          "Related-Felder benötigen Shell-Zugriff auf Related-Ziel-IDs und werden über createRelatedField(...) erzeugt."
      );
    }

    if (isIntegerDatatype(datatype)) {
      return new IntegerBibliographyFieldComponent(request);
    }

    return new StringBibliographyFieldComponent(request);
  }

  /**
   * Erzeugt eine Personenfeld-Komponente mit den notwendigen Callback-Brücken
   * zur umgebenden Shell.
   *
   * @param request Erzeugungsanfrage
   * @param suggestionProvider Provider für Autorenvorschläge
   * @param authorsEditedHandler Callback nach Bearbeitung einer Personenzeile
   * @param refreshSelectionHandler Callback zur Aktualisierung abhängiger Auswahlzustände
   * @param suppressLookupCallbackSupplier liefert, ob Lookup-Callbacks gerade unterdrückt werden
   * @param selectedEntryUpdater Callback zum Aktualisieren der selektierten Eintrags-ID
   * @param visibleRowCount sichtbare Zeilenzahl der Personenliste
   * @return Personenfeld-Komponente
   */
  public PersonBibliographyFieldComponent createPersonField(
      BibliographyFieldRequest request,
      java.util.function.Function<de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView.AuthorRow,
                                     java.util.List<de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion>> suggestionProvider,
      java.util.function.Consumer<String> authorsEditedHandler,
      Runnable refreshSelectionHandler,
      java.util.function.Supplier<Boolean> suppressLookupCallbackSupplier,
      java.util.function.BiConsumer<String, Integer> selectedEntryUpdater,
      int visibleRowCount
  ) {
    Objects.requireNonNull(request, "request");
    return new PersonBibliographyFieldComponent(
        request,
        suggestionProvider,
        authorsEditedHandler,
        refreshSelectionHandler,
        suppressLookupCallbackSupplier,
        selectedEntryUpdater,
        visibleRowCount
    );
  }

  /**
   * Erzeugt eine Related-Feldkomponente mit Zugriff auf den Related-Zustand
   * der umgebenden Shell.
   *
   * @param request Erzeugungsanfrage
   * @param relatedEntryIdReader liest die Related-Ziel-ID für Medientyp und Feld
   * @param relatedEntryWriter schreibt Related-Ziel-ID und Anzeigetext zurück
   * @return Related-Feldkomponente
   */
  public RelatedBibliographyFieldComponent createRelatedField(
      BibliographyFieldRequest request,
      java.util.function.BiFunction<String, String, Integer> relatedEntryIdReader,
      RelatedBibliographyFieldComponent.RelatedEntryWriter relatedEntryWriter
  ) {
    Objects.requireNonNull(request, "request");
    return new RelatedBibliographyFieldComponent(
        request,
        relatedEntryIdReader,
        relatedEntryWriter
    );
  }

  /**
   * Erzeugt eine Datumsfeld-Komponente.
   *
   * @param request Erzeugungsanfrage
   * @return Datumsfeld-Komponente
   */
  public DateBibliographyFieldComponent createDateField(BibliographyFieldRequest request) {
    Objects.requireNonNull(request, "request");
    return new DateBibliographyFieldComponent(request);
  }
}