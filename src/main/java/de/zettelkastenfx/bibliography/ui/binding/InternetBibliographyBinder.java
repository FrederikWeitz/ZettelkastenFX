package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.InternetEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Binder für das Formular einer Internetquelle.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link InternetEntry}.
 */
public class InternetBibliographyBinder extends AbstractBibliographyBinder<InternetEntry> {

  private final BibliographyAuthorsView authorsView;
  private final BibliographyFieldView titleField;
  private final BibliographyFieldView hostNameField;
  private final BibliographyFieldView urlField;
  private final Supplier<String> publishedGetter;
  private final Consumer<String> publishedSetter;
  private final Supplier<LocalDateTime> accessedAtGetter;
  private final Consumer<LocalDateTime> accessedAtSetter;

  /**
   * Erzeugt einen Binder für Internetquellen.
   *
   * @param authorsView Autorenansicht
   * @param titleField Titelfeld
   * @param hostNameField Feld für Hostnamen
   * @param urlField URL-Feld
   * @param publishedGetter liefert das Veröffentlichungsdatum
   * @param publishedSetter setzt das Veröffentlichungsdatum
   * @param accessedAtGetter liefert den Zugriffszeitpunkt aus der UI
   * @param accessedAtSetter setzt den Zugriffszeitpunkt in der UI
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public InternetBibliographyBinder(
      BibliographyAuthorsView authorsView,
      BibliographyFieldView titleField,
      BibliographyFieldView hostNameField,
      BibliographyFieldView urlField,
      Supplier<String> publishedGetter,
      Consumer<String> publishedSetter,
      Supplier<LocalDateTime> accessedAtGetter,
      Consumer<LocalDateTime> accessedAtSetter,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.authorsView = authorsView;
    this.titleField = titleField;
    this.hostNameField = hostNameField;
    this.urlField = urlField;
    this.publishedGetter = publishedGetter;
    this.publishedSetter = publishedSetter;
    this.accessedAtGetter = accessedAtGetter;
    this.accessedAtSetter = accessedAtSetter;
  }

  /**
   * Überträgt die aktuellen Formularwerte in eine Internetquelle.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(InternetEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setHostName(hostNameField.getText());
    entry.setUrl(urlField.getText());
    entry.setPublished(publishedGetter.get());
    entry.setAccessedAt(accessedAtGetter.get());

    entry.getAuthors().clear();
    entry.getAuthors().addAll(authorsView.toAuthors());
  }

  /**
   * Liest eine vorhandene Internetquelle in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(InternetEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    hostNameField.setText(entry.getHostName());
    urlField.setText(entry.getUrl());
    publishedSetter.accept(entry.getPublished());
    accessedAtSetter.accept(entry.getAccessedAt());
    authorsView.fromAuthors(entry.getAuthors());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Formular der Internetquelle und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    hostNameField.setText("");
    urlField.setText("");
    publishedSetter.accept("");
    accessedAtSetter.accept(null);
    authorsView.fromAuthors(List.of());

    clearEntryState();
  }
}