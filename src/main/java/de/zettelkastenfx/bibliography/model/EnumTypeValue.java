package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt einen einzelnen Eintrag aus der Tabelle {@code enum_types}.
 *
 * @param type Oberbegriff des Enum-Bereichs, z. B. {@code Thesis}
 * @param typeindex konkreter Enum-Wert, z. B. {@code Bachelor}
 */
public record EnumTypeValue(
    String type,
    String typeindex
) {

  /**
   * Erzeugt einen Enum-Wert mit bereinigten Textfeldern.
   *
   * @param type Oberbegriff des Enum-Bereichs
   * @param typeindex konkreter Enum-Wert
   */
  public EnumTypeValue {
    type = sanitize(type);
    typeindex = sanitize(typeindex);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}