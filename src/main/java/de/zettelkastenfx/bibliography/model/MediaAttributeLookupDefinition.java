package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt die Lookup-Metadaten eines Bibliographie-Feldes aus der Tabelle
 * {@code media_attribute_lookup}.
 * <p>
 * Diese Definition sagt nicht, ob ein Feld fachlich {@code identify},
 * {@code necessary}, {@code desired}, {@code mandatory} oder
 * {@code forbidden} ist, sondern wie das Feld im UI-Lookup behandelt werden
 * soll.
 *
 * @param mediaTypeId Datenbank-ID des Medientyps
 * @param bibtexTypeId Datenbank-ID des Feldes
 * @param lookupStrategy technische Lookup-Strategie
 * @param minQueryLength minimale Suchlänge
 * @param autoResolveUnique {@code true}, wenn eindeutige Treffer automatisch
 *                          übernommen werden dürfen
 * @param allowFreeText {@code true}, wenn freie Eingaben ohne Treffer erlaubt
 *                      bleiben sollen
 * @param mediaTypeDefinition aufgelöste Medientypdefinition
 * @param fieldDefinition aufgelöste Felddefinition
 */
public record MediaAttributeLookupDefinition(
    int mediaTypeId,
    int bibtexTypeId,
    String lookupStrategy,
    int minQueryLength,
    boolean autoResolveUnique,
    boolean allowFreeText,
    MediaTypeDefinition mediaTypeDefinition,
    BibtexFieldDefinition fieldDefinition
) {

  /**
   * Erzeugt eine Lookup-Definition mit bereinigter Strategie und nichtnegativer
   * Mindestlänge.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param bibtexTypeId Datenbank-ID des Feldes
   * @param lookupStrategy technische Lookup-Strategie
   * @param minQueryLength minimale Suchlänge
   * @param autoResolveUnique automatische Übernahme eindeutiger Treffer
   * @param allowFreeText freie Eingabe ohne Treffer erlaubt
   * @param mediaTypeDefinition aufgelöste Medientypdefinition
   * @param fieldDefinition aufgelöste Felddefinition
   */
  public MediaAttributeLookupDefinition {
    lookupStrategy = sanitize(lookupStrategy);
    minQueryLength = Math.max(0, minQueryLength);
  }

  /**
   * Prüft auf die Strategie {@code identify_entry}.
   *
   * @return {@code true}, wenn das Feld Einträge desselben Medientyps sucht
   */
  public boolean isIdentifyEntryLookup() {
    return "identify_entry".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code person}.
   *
   * @return {@code true}, wenn das Feld Personenvorschläge nutzt
   */
  public boolean isPersonLookup() {
    return "person".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code related_entry}.
   *
   * @return {@code true}, wenn das Feld Related-Zielmedien sucht
   */
  public boolean isRelatedEntryLookup() {
    return "related_entry".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code value_list}.
   *
   * @return {@code true}, wenn das Feld freie Wiederverwendungswerte sucht
   */
  public boolean isValueListLookup() {
    return "value_list".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code ai_provider}.
   *
   * @return {@code true}, wenn das Feld KI-Anbieter vorschlägt
   */
  public boolean isAiProviderLookup() {
    return "ai_provider".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code ai_model}.
   *
   * @return {@code true}, wenn das Feld KI-Modelle vorschlägt
   */
  public boolean isAiModelLookup() {
    return "ai_model".equalsIgnoreCase(lookupStrategy);
  }

  /**
   * Prüft auf die Strategie {@code none}.
   *
   * @return {@code true}, wenn für das Feld kein aktives Lookup vorgesehen ist
   */
  public boolean isNoneLookup() {
    return "none".equalsIgnoreCase(lookupStrategy);
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}