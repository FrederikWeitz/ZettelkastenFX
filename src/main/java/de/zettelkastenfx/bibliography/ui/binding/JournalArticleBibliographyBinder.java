package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.JournalArticleEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Binder für das Formular eines Zeitschriftenartikels.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link JournalArticleEntry}.
 */
public class JournalArticleBibliographyBinder extends AbstractBibliographyBinder<JournalArticleEntry> {

  private final BibliographyAuthorsView authorsView;
  private final BibliographyFieldView titleField;
  private final BibliographyFieldView subtitleField;
  private final BibliographyFieldView publisherField;
  private final BibliographyFieldView journalTitleField;
  private final BibliographyFieldView volumeField;
  private final BibliographyFieldView issueField;
  private final BibliographyFieldView yearField;
  private final BibliographyFieldView pagesField;
  private final BibliographyFieldView doiField;

  /**
   * Optionaler Verweis auf einen vorhandenen Zeitschrifteneintrag.
   */
  @Getter
  @Setter
  private Integer journalEntryId;

  /**
   * Erzeugt einen Binder für Zeitschriftenartikel.
   *
   * @param authorsView Autorenansicht
   * @param titleField Titelfeld
   * @param subtitleField Untertitelfeld
   * @param publisherField Verlagsfeld
   * @param journalTitleField Zeitschriftentitelfeld
   * @param volumeField Bandfeld
   * @param issueField Heftfeld
   * @param yearField Jahresfeld
   * @param pagesField Seitenfeld
   * @param doiField DOI-Feld
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public JournalArticleBibliographyBinder(
      BibliographyAuthorsView authorsView,
      BibliographyFieldView titleField,
      BibliographyFieldView subtitleField,
      BibliographyFieldView publisherField,
      BibliographyFieldView journalTitleField,
      BibliographyFieldView volumeField,
      BibliographyFieldView issueField,
      BibliographyFieldView yearField,
      BibliographyFieldView pagesField,
      BibliographyFieldView doiField,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.authorsView = authorsView;
    this.titleField = titleField;
    this.subtitleField = subtitleField;
    this.publisherField = publisherField;
    this.journalTitleField = journalTitleField;
    this.volumeField = volumeField;
    this.issueField = issueField;
    this.yearField = yearField;
    this.pagesField = pagesField;
    this.doiField = doiField;
  }

  /**
   * Überträgt die aktuellen Formularwerte in einen Zeitschriftenartikel.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(JournalArticleEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setSubtitle(subtitleField.getText());
    entry.setPublisher(publisherField.getText());
    entry.setVolume(volumeField.getText());
    entry.setIssue(issueField.getText());
    entry.setYear(yearField.getText());
    entry.setPages(pagesField.getText());
    entry.setDoi(doiField.getText());

    String journalTitle = journalTitleField.getText();
    if (journalTitle == null || journalTitle.isBlank()) {
      entry.clearJournalReference();
      journalEntryId = null;
    } else {
      entry.setJournalTitle(journalTitle);
      entry.setJournalEntryId(journalEntryId);
    }

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest einen vorhandenen Zeitschriftenartikel in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(JournalArticleEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    subtitleField.setText(entry.getSubtitle());
    publisherField.setText(entry.getPublisher());
    journalTitleField.setText(entry.getJournalTitle());
    volumeField.setText(entry.getVolume());
    issueField.setText(entry.getIssue());
    yearField.setText(entry.getYear());
    pagesField.setText(entry.getPages());
    doiField.setText(entry.getDoi());
    authorsView.fromAuthors(entry.getAuthors());

    journalEntryId = entry.getJournalEntryId();
    applyEntryState(entry.getId());
  }

  /**
   * Leert das Formular des Zeitschriftenartikels und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    subtitleField.setText("");
    publisherField.setText("");
    journalTitleField.setText("");
    volumeField.setText("");
    issueField.setText("");
    yearField.setText("");
    pagesField.setText("");
    doiField.setText("");
    authorsView.fromAuthors(List.of());

    journalEntryId = null;
    clearEntryState();
  }
}
