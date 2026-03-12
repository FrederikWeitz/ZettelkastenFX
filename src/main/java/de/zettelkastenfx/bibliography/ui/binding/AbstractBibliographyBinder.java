package de.zettelkastenfx.bibliography.ui.binding;

import java.util.function.Consumer;

/**
 * Abstrakte Basisklasse für Bibliographie-Binder mit gemeinsamem UI-Zustands-Reset.
 *
 * @param <T> Typ des gebundenen Domänenobjekts.
 */
public abstract class AbstractBibliographyBinder<T> implements BibliographyBinder<T> {

  private final Consumer<Integer> selectedEntryIdSetter;
  private final Consumer<Integer> sourceEntryIdSetter;
  private final Consumer<Boolean> autoFilledSetter;
  private final Consumer<Boolean> autoFillLockedSetter;

  /**
   * Erzeugt einen Binder mit gemeinsamen Status-Callbacks.
   *
   * @param selectedEntryIdSetter setzt die aktuell selektierte Eintrags-ID
   * @param sourceEntryIdSetter setzt die Ursprungs-ID des geladenen Eintrags
   * @param autoFilledSetter setzt den Marker für automatisches Befüllen
   * @param autoFillLockedSetter setzt die Sperre gegen weiteres Autofill
   */
  protected AbstractBibliographyBinder(
      Consumer<Integer> selectedEntryIdSetter,
      Consumer<Integer> sourceEntryIdSetter,
      Consumer<Boolean> autoFilledSetter,
      Consumer<Boolean> autoFillLockedSetter
  ) {
    this.selectedEntryIdSetter = selectedEntryIdSetter;
    this.sourceEntryIdSetter = sourceEntryIdSetter;
    this.autoFilledSetter = autoFilledSetter;
    this.autoFillLockedSetter = autoFillLockedSetter;
  }

  /**
   * Setzt den Lookup- und Auswahlzustand auf einen geladenen Eintrag.
   *
   * @param entryId ID des geladenen Eintrags; {@code null} entfernt den Bezug
   */
  protected void applyEntryState(Integer entryId) {
    selectedEntryIdSetter.accept(entryId);
    sourceEntryIdSetter.accept(entryId);
    autoFilledSetter.accept(false);
    autoFillLockedSetter.accept(false);
  }

  /**
   * Setzt den Lookup- und Auswahlzustand vollständig zurück.
   */
  protected void clearEntryState() {
    applyEntryState(null);
  }
}
