package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.EditionEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.List;
import java.util.function.Consumer;

/**
 * Binder für das Formular einer Edition.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link EditionEntry}.
 */
public class EditionBibliographyBinder extends AbstractBibliographyBinder<EditionEntry> {

  private final BibliographyAuthorsView authorsView;
  private final BibliographyFieldView titleField;
  private final BibliographyFieldView publisherField;
  private final BibliographyFieldView placeField;
  private final BibliographyFieldView yearField;
  private final BibliographyFieldView isbnField;
  private final BibliographyFieldView runField;
  private final BibliographyFieldView seriesField;
  private final BibliographyFieldView volumeField;

  /**
   * Erzeugt einen Binder für Editionen.
   *
   * @param authorsView Autorenansicht
   * @param titleField Titelfeld
   * @param publisherField Verlagsfeld
   * @param placeField Ortsfeld
   * @param yearField Jahresfeld
   * @param isbnField ISBN-Feld
   * @param runField Feld für die Auflage
   * @param seriesField Reihenfeld
   * @param volumeField Bandfeld
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public EditionBibliographyBinder(
      BibliographyAuthorsView authorsView,
      BibliographyFieldView titleField,
      BibliographyFieldView publisherField,
      BibliographyFieldView placeField,
      BibliographyFieldView yearField,
      BibliographyFieldView isbnField,
      BibliographyFieldView runField,
      BibliographyFieldView seriesField,
      BibliographyFieldView volumeField,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.authorsView = authorsView;
    this.titleField = titleField;
    this.publisherField = publisherField;
    this.placeField = placeField;
    this.yearField = yearField;
    this.isbnField = isbnField;
    this.runField = runField;
    this.seriesField = seriesField;
    this.volumeField = volumeField;
  }

  /**
   * Überträgt die aktuellen Formularwerte in eine Edition.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(EditionEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setPublisher(publisherField.getText());
    entry.setPlace(placeField.getText());
    entry.setYear(yearField.getText());
    entry.setIsbn(isbnField.getText());
    entry.setRun(runField.getText());
    entry.setSeries(seriesField.getText());
    entry.setVolume(volumeField.getText());

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest eine vorhandene Edition in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(EditionEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    publisherField.setText(entry.getPublisher());
    placeField.setText(entry.getPlace());
    yearField.setText(entry.getYear());
    isbnField.setText(entry.getIsbn());
    runField.setText(entry.getRun());
    seriesField.setText(entry.getSeries());
    volumeField.setText(entry.getVolume());
    authorsView.fromAuthors(entry.getAuthors());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Editionsformular und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    publisherField.setText("");
    placeField.setText("");
    yearField.setText("");
    isbnField.setText("");
    runField.setText("");
    seriesField.setText("");
    volumeField.setText("");
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }
}
