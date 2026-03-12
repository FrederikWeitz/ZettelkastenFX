package de.zettelkastenfx.bibliography.ui.binding;

import de.zettelkastenfx.bibliography.model.AiEntry;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Binder für das Formular einer KI-Quelle.
 * Die Klasse überträgt Werte zwischen UI-Feldern und {@link AiEntry}.
 */
public class AiBibliographyBinder extends AbstractBibliographyBinder<AiEntry> {

  private final BibliographyFieldView titleField;
  private final Supplier<String> providerGetter;
  private final Consumer<String> providerSetter;
  private final Supplier<String> modelGetter;
  private final Consumer<String> modelSetter;
  private final BibliographyFieldView contextField;
  private final BibliographyFieldView promptTitleField;
  private final Supplier<LocalDateTime> usedAtGetter;
  private final Consumer<LocalDateTime> usedAtSetter;

  /**
   * Erzeugt einen Binder für KI-Quellen.
   *
   * @param titleField Titelfeld
   * @param providerGetter liefert den Anbieter aus der UI
   * @param providerSetter setzt den Anbieter in der UI
   * @param modelGetter liefert das Modell aus der UI
   * @param modelSetter setzt das Modell in der UI
   * @param contextField Kontextfeld
   * @param promptTitleField Prompttitelfeld
   * @param usedAtGetter liefert den Nutzungszeitpunkt aus der UI
   * @param usedAtSetter setzt den Nutzungszeitpunkt in der UI
   * @param selectedEntryIdSetter Setter für die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter Setter für die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter Setter für den Marker automatischer Befüllung
   * @param autoFillLockedSetter Setter für den Autofill-Lock-Zustand
   */
  public AiBibliographyBinder(
      BibliographyFieldView titleField,
      Supplier<String> providerGetter,
      Consumer<String> providerSetter,
      Supplier<String> modelGetter,
      Consumer<String> modelSetter,
      BibliographyFieldView contextField,
      BibliographyFieldView promptTitleField,
      Supplier<LocalDateTime> usedAtGetter,
      Consumer<LocalDateTime> usedAtSetter,
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    super(selectedEntryIdSetter, sourceEntryIdSetter, autoFilledSetter, autoFillLockedSetter);
    this.titleField = titleField;
    this.providerGetter = providerGetter;
    this.providerSetter = providerSetter;
    this.modelGetter = modelGetter;
    this.modelSetter = modelSetter;
    this.contextField = contextField;
    this.promptTitleField = promptTitleField;
    this.usedAtGetter = usedAtGetter;
    this.usedAtSetter = usedAtSetter;
  }

  /**
   * Überträgt die aktuellen Formularwerte in eine KI-Quelle.
   *
   * @param entry Zielobjekt für die Übernahme
   */
  @Override
  public void writeInto(AiEntry entry) {
    if (entry == null) {
      return;
    }

    entry.setTitle(titleField.getText());
    entry.setProvider(providerGetter.get());
    entry.setModel(modelGetter.get());
    entry.setContext(contextField.getText());
    entry.setPromptTitle(promptTitleField.getText());
    entry.setUsedAt(usedAtGetter.get());
  }

  /**
   * Liest eine vorhandene KI-Quelle in das Formular ein.
   *
   * @param entry Quellobjekt; {@code null} leert das Formular
   */
  @Override
  public void readFrom(AiEntry entry) {
    if (entry == null) {
      clear();
      return;
    }

    titleField.setText(entry.getTitle());
    providerSetter.accept(entry.getProvider());
    modelSetter.accept(entry.getModel());
    contextField.setText(entry.getContext());
    promptTitleField.setText(entry.getPromptTitle());
    usedAtSetter.accept(entry.getUsedAt());

    applyEntryState(entry.getId());
  }

  /**
   * Leert das Formular der KI-Quelle und setzt den Binder-Zustand zurück.
   */
  @Override
  public void clear() {
    titleField.setText("");
    providerSetter.accept("");
    modelSetter.accept("");
    contextField.setText("");
    promptTitleField.setText("");
    usedAtSetter.accept(null);

    clearEntryState();
  }
}