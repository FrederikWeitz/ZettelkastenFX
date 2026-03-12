package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.JournalEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.util.function.Consumer;

/**
 * Binder für das Zeitschriftenformular.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link JournalEntry}.
 */
public class JournalBibliographyBinder extends AbstractBibliographyBinder<JournalEntry> {

  private final BibliographyFieldView titleField;
  private final BibliographyFieldView subtitleField;
  private final BibliographyFieldView issnField;
  private final BibliographyFieldView publisherField;
  private final BibliographyFieldView placeField;
  private final BibliographyFieldView startYearField;
  private final BibliographyFieldView endYearField;
  private final BibliographyFieldView noteField;

  /**
   * Erzeugt einen Binder für Zeitschriften.
   *
   * @param titleField Titelfeld
   * @param subtitleField Untertitelfeld
   * @param issnField ISSN-Feld
   * @param publisherField Verlagsfeld
   * @param placeField Ortsfeld
   * @param startYearField Startjahrfeld
   * @param endYearField Endjahrfeld
   * @param noteField Notizfeld
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public JournalBibliographyBinder(
      BibliographyFieldView titleField,
      BibliographyFieldView subtitleField,
      BibliographyFieldView issnField,
      BibliographyFieldView publisherField,
      BibliographyFieldView placeField,
      BibliographyFieldView startYearField,
      BibliographyFieldView endYearField,
      BibliographyFieldView noteField,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.titleField = titleField;
    this.subtitleField = subtitleField;
    this.issnField = issnField;
    this.publisherField = publisherField;
    this.placeField = placeField;
    this.startYearField = startYearField;
    this.endYearField = endYearField;
    this.noteField = noteField;
  }

  /**
   * Überträgt die aktuellen Formularwerte in einen Zeitschrifteneintrag.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(JournalEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setSubtitle(subtitleField.getText());
    entry.setIssn(issnField.getText());
    entry.setPublisher(publisherField.getText());
    entry.setPlace(placeField.getText());
    entry.setStartYear(startYearField.getText());
    entry.setEndYear(endYearField.getText());
    entry.setNote(noteField.getText());
  }

  /**
   * Liest einen vorhandenen Zeitschrifteneintrag in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(JournalEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    subtitleField.setText(entry.getSubtitle());
    issnField.setText(entry.getIssn());
    publisherField.setText(entry.getPublisher());
    placeField.setText(entry.getPlace());
    startYearField.setText(entry.getStartYear());
    endYearField.setText(entry.getEndYear());
    noteField.setText(entry.getNote());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Zeitschriftenformular und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    subtitleField.setText("");
    issnField.setText("");
    publisherField.setText("");
    placeField.setText("");
    startYearField.setText("");
    endYearField.setText("");
    noteField.setText("");

    clearEntryState();
  }
}