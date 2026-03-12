package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.CollectionEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.List;
import java.util.function.Consumer;

public class CollectionBibliographyBinder extends AbstractBibliographyBinder<CollectionEntry> {

  private final BibliographyFieldView titleField;
  private final BibliographyFieldView subtitleField;
  private final BibliographyFieldView publisherField;
  private final BibliographyFieldView placeField;
  private final BibliographyFieldView yearField;
  private final BibliographyFieldView seriesField;
  private final BibliographyAuthorsView authorsView;

  public CollectionBibliographyBinder(
      BibliographyFieldView titleField,
      BibliographyFieldView subtitleField,
      BibliographyFieldView publisherField,
      BibliographyFieldView placeField,
      BibliographyFieldView yearField,
      BibliographyFieldView seriesField,
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
    this.seriesField = seriesField;
    this.authorsView = authorsView;
  }

  /**
   * Schreibt die UI-Daten des Sammelbandformulars in einen Sammelbandeintrag.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  public void writeInto(CollectionEntry entry) {
    if (entry == null) {
      return;
    }
    entry.setTitle(titleField.getText());
    entry.setSubtitle(subtitleField.getText());
    entry.setPublisher(publisherField.getText());
    entry.setPlace(placeField.getText());
    entry.setYear(yearField.getText());
    entry.setSeries(seriesField.getText());

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest einen Sammelbandeintrag in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular.
   */
  public void readFrom(CollectionEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    subtitleField.setText(entry.getSubtitle());
    publisherField.setText(entry.getPublisher());
    placeField.setText(entry.getPlace());
    yearField.setText(entry.getYear());
    seriesField.setText(entry.getSeries());

    authorsView.fromAuthors(entry.getAuthors());
    applyEntryState(entry.getId());
  }

  /**
   * Leert das Sammelbandformular und setzt seinen Lookup-Zustand zurück.
   */
  public void clear() {
    titleField.setText("");
    subtitleField.setText("");
    publisherField.setText("");
    placeField.setText("");
    yearField.setText("");
    seriesField.setText("");
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }
}
