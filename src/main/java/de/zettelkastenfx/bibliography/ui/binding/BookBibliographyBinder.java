package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.BookEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.List;
import java.util.function.Consumer;

public class BookBibliographyBinder extends AbstractBibliographyBinder<BookEntry> {

  private final BibliographyFieldView titleField;
  private final BibliographyFieldView subtitleField;
  private final BibliographyFieldView publisherField;
  private final BibliographyFieldView placeField;
  private final BibliographyFieldView yearField;
  private final BibliographyFieldView isbnField;
  private final BibliographyFieldView runField;
  private final BibliographyFieldView seriesField;
  private final BibliographyFieldView seriesNumberField;
  private final BibliographyAuthorsView authorsView;

  public BookBibliographyBinder(
      BibliographyFieldView titleField,
      BibliographyFieldView subtitleField,
      BibliographyFieldView publisherField,
      BibliographyFieldView placeField,
      BibliographyFieldView yearField,
      BibliographyFieldView isbnField,
      BibliographyFieldView runField,
      BibliographyFieldView seriesField,
      BibliographyFieldView seriesNumberField,
      BibliographyAuthorsView authorsView,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.titleField = titleField;
    this.subtitleField = subtitleField;
    this.publisherField = publisherField;
    this.placeField = placeField;
    this.yearField = yearField;
    this.isbnField = isbnField;
    this.runField = runField;
    this.seriesField = seriesField;
    this.seriesNumberField = seriesNumberField;
    this.authorsView = authorsView;
  }

  /**
   * Überträgt die aktuellen Formularwerte in einen BookEntry.
   *
   * @param entry Zielobjekt, das mit den Formularwerten befüllt wird
   */
  public void writeInto(BookEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setSubtitle(subtitleField.getText());
    entry.setPublisher(publisherField.getText());
    entry.setPlace(placeField.getText());
    entry.setYear(yearField.getText());
    entry.setIsbn(isbnField.getText());
    entry.setRun(runField.getText());
    entry.setSeries(seriesField.getText());
    entry.setSeriesNumber(seriesNumberField.getText());

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Befüllt das Formular aus einem vorhandenen BookEntry.
   *
   * @param entry Quelldatensatz; bei {@code null} wird das Formular geleert
   */
  public void readFrom(BookEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    subtitleField.setText(entry.getSubtitle());
    publisherField.setText(entry.getPublisher());
    placeField.setText(entry.getPlace());
    yearField.setText(entry.getYear());
    isbnField.setText(entry.getIsbn());
    runField.setText(entry.getRun());
    seriesField.setText(entry.getSeries());
    seriesNumberField.setText(entry.getSeriesNumber());
    authorsView.fromAuthors(entry.getAuthors());

    applyEntryState(entry.getId());
  }

  /**
   * Leert alle Formularfelder und setzt den Binder-Zustand zurück.
   */
  public void clear() {
    titleField.setText("");
    subtitleField.setText("");
    publisherField.setText("");
    placeField.setText("");
    yearField.setText("");
    isbnField.setText("");
    runField.setText("");
    seriesField.setText("");
    seriesNumberField.setText("");
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }
}