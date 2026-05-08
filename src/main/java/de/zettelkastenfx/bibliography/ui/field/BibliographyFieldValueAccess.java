package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.BibValue;

import java.util.Collection;

/**
 * Beschreibt den Werttransfer einer bibliographischen Feldkomponente.
 * Eine Komponente kann genau einen oder mehrere BibValue-Werte liefern.
 */
public interface BibliographyFieldValueAccess {

  /**
   * Liest alle aktuell fachlich gesetzten Werte aus der Komponente.
   *
   * @return gelesene BibValue-Werte
   */
  Collection<BibValue> readValues();

  /**
   * Schreibt vorhandene BibValue-Werte in die Komponente zurück.
   *
   * @param values zu übernehmende Werte
   */
  void writeValues(Collection<? extends BibValue> values);

  /**
   * Leert den fachlichen Zustand der Komponente.
   */
  void clear();

  /**
   * Prüft, ob die Komponente fachlich leer ist.
   *
   * @return {@code true}, wenn kein speicherbarer Wert vorhanden ist
   */
  boolean isEmpty();
}