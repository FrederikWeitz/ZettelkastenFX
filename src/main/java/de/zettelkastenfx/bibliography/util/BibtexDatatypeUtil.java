package de.zettelkastenfx.bibliography.util;

import java.util.Locale;

/**
 * Zentrale Hilfsmethoden für technische BibTeX-Datentypen.
 */
public final class BibtexDatatypeUtil {

  private BibtexDatatypeUtil() {
  }

  /**
   * Normalisiert einen Metadaten-Datentyp für technische Vergleiche.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return normalisierte Datentypbezeichnung
   */
  public static String normalizeDatatype(String datatype) {
    return datatype == null ? "" : datatype.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Prüft, ob ein Datentyp als Personenfeld behandelt wird.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return {@code true}, wenn Personensemantik vorliegt
   */
  public static boolean isPersonDatatype(String datatype) {
    return "person".equals(normalizeDatatype(datatype));
  }

  /**
   * Prüft, ob ein Datentyp als Related-Feld behandelt wird.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return {@code true}, wenn Related-Semantik vorliegt
   */
  public static boolean isRelatedDatatype(String datatype) {
    return "anderer eintrag/integer".equals(normalizeDatatype(datatype));
  }

  /**
   * Prüft, ob ein Datentyp als Integer-Feld behandelt wird.
   *
   * @param datatype Datentyp aus den Metadaten
   * @return {@code true}, wenn Integer-Semantik vorliegt
   */
  public static boolean isIntegerDatatype(String datatype) {
    String normalized = normalizeDatatype(datatype);
    return "integer".equals(normalized)
               || "year".equals(normalized)
               || "month".equals(normalized);
  }
}