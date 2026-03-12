package de.zettelkastenfx.bibliography.ui.binding;

/**
 * Gemeinsamer Vertrag für Binder zwischen Bibliographieformularen und Domänenobjekten.
 *
 * @param <T> Typ des gebundenen Domänenobjekts.
 */
public interface BibliographyBinder<T> {

  /**
   * Überträgt die aktuellen UI-Werte in ein Domänenobjekt.
   *
   * @param entry Zielobjekt für die Übernahme.
   */
  void writeInto(T entry);

  /**
   * Befüllt die UI aus einem vorhandenen Domänenobjekt.
   *
   * @param entry Quellobjekt; {@code null} leert die UI.
   */
  void readFrom(T entry);

  /**
   * Leert die UI und setzt binderinternen Zustand zurück.
   */
  void clear();
}
