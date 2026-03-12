package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.ThesisEntry;
import de.zettelkastenfx.bibliography.model.ThesisType;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Binder für das Formular einer Hochschulschrift.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link ThesisEntry}.
 */
public class ThesisBibliographyBinder extends AbstractBibliographyBinder<ThesisEntry> {

  private final BibliographyAuthorsView authorsView;
  private final BibliographyFieldView titleField;
  private final BibliographyFieldView subtitleField;
  private final Supplier<ThesisType> thesisTypeGetter;
  private final Consumer<ThesisType> thesisTypeSetter;
  private final BibliographyFieldView universityField;
  private final BibliographyFieldView placeField;
  private final BibliographyFieldView yearField;
  private final BibliographyFieldView advisorField;
  private final BibliographyFieldView seriesField;
  private final BibliographyFieldView noteField;

  /**
   * Erzeugt einen Binder für Hochschulschriften.
   *
   * @param authorsView Autorenansicht
   * @param titleField Titelfeld
   * @param subtitleField Untertitelfeld
   * @param thesisTypeGetter liefert den gewählten Thesis-Typ
   * @param thesisTypeSetter setzt den Thesis-Typ in der UI
   * @param universityField Hochschulfeld
   * @param placeField Ortsfeld
   * @param yearField Jahresfeld
   * @param advisorField Betreuerfeld
   * @param seriesField Reihenfeld
   * @param noteField Notizfeld
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public ThesisBibliographyBinder(
      BibliographyAuthorsView authorsView,
      BibliographyFieldView titleField,
      BibliographyFieldView subtitleField,
      Supplier<ThesisType> thesisTypeGetter,
      Consumer<ThesisType> thesisTypeSetter,
      BibliographyFieldView universityField,
      BibliographyFieldView placeField,
      BibliographyFieldView yearField,
      BibliographyFieldView advisorField,
      BibliographyFieldView seriesField,
      BibliographyFieldView noteField,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.authorsView = authorsView;
    this.titleField = titleField;
    this.subtitleField = subtitleField;
    this.thesisTypeGetter = thesisTypeGetter;
    this.thesisTypeSetter = thesisTypeSetter;
    this.universityField = universityField;
    this.placeField = placeField;
    this.yearField = yearField;
    this.advisorField = advisorField;
    this.seriesField = seriesField;
    this.noteField = noteField;
  }

  /**
   * Überträgt die aktuellen Formularwerte in einen Thesis-Eintrag.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(ThesisEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setSubtitle(subtitleField.getText());
    entry.setThesisType(thesisTypeGetter.get());
    entry.setUniversity(universityField.getText());
    entry.setPlace(placeField.getText());
    entry.setYear(yearField.getText());
    entry.setAdvisor(advisorField.getText());
    entry.setSeries(seriesField.getText());
    entry.setNote(noteField.getText());

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest einen vorhandenen Thesis-Eintrag in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(ThesisEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    subtitleField.setText(entry.getSubtitle());
    thesisTypeSetter.accept(entry.getThesisType());
    universityField.setText(entry.getUniversity());
    placeField.setText(entry.getPlace());
    yearField.setText(entry.getYear());
    advisorField.setText(entry.getAdvisor());
    seriesField.setText(entry.getSeries());
    noteField.setText(entry.getNote());
    authorsView.fromAuthors(entry.getAuthors());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Thesis-Formular und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    subtitleField.setText("");
    thesisTypeSetter.accept(ThesisType.DISSERTATION);
    universityField.setText("");
    placeField.setText("");
    yearField.setText("");
    advisorField.setText("");
    seriesField.setText("");
    noteField.setText("");
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }
}
