package de.zettelkastenfx.bibliography.model;

/**
 * Vereinheitlicht alle bibliographischen Feldwerte für das dynamische
 * Laufzeitmodell.
 */
public interface BibValue {

  /**
   * Liefert den technischen BibTeX-Namen des Feldes, z. B. {@code title},
   * {@code year}, {@code author} oder {@code related_journal}.
   *
   * @return der technische Feldname
   */
  String bibtexName();

  /**
   * Liefert die fachliche Wertart des Feldes.
   *
   * @return der Werttyp
   */
  BibValueType valueType();

  /**
   * Liefert den eigentlichen Nutzwert des Feldes.
   * <p>
   * Je nach Implementierung ist dies z. B. ein {@link String}, ein
   * {@link Integer}, eine Liste von Personen oder eine Ziel-ID.
   *
   * @return der gespeicherte Wert
   */
  Object value();
}
