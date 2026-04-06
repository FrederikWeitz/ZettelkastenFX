package de.zettelkastenfx.bibliography.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Flexibles Laufzeitmodell für einen bibliographischen Eintrag.
 * <p>
 * Der Eintrag besteht aus:
 * <ul>
 *   <li>den Basisdaten des Mediums</li>
 *   <li>dem Medientyp</li>
 *   <li>einer geordneten Feldmenge auf Basis von {@link BibValue}</li>
 * </ul>
 * <p>
 * Die Feldwerte werden in einer {@link LinkedHashMap} gehalten, damit die
 * Einfügereihenfolge, und später die vom Medientyp vorgegebene Feldreihenfolge,
 * stabil bleibt.
 */
public class DynamicBibliographyEntry {

  private Integer id;
  private Integer mediaTypeId;
  private String mediaTypeBibName = "";
  private String mediaTypeName = "";
  private String citekey = "";
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  private final LinkedHashMap<String, BibValue> values = new LinkedHashMap<>();

  /**
   * Liefert die Datenbank-ID des bibliographischen Eintrags.
   *
   * @return Eintrags-ID oder {@code null}
   */
  public Integer getId() {
    return id;
  }

  /**
   * Setzt die Datenbank-ID des bibliographischen Eintrags.
   *
   * @param id Eintrags-ID
   */
  public void setId(Integer id) {
    this.id = id;
  }

  /**
   * Liefert die Datenbank-ID des Medientyps.
   *
   * @return Medientyp-ID oder {@code null}
   */
  public Integer getMediaTypeId() {
    return mediaTypeId;
  }

  /**
   * Setzt die Datenbank-ID des Medientyps.
   *
   * @param mediaTypeId Medientyp-ID
   */
  public void setMediaTypeId(Integer mediaTypeId) {
    this.mediaTypeId = mediaTypeId;
  }

  /**
   * Liefert den technischen Namen des Medientyps, z. B. {@code book} oder
   * {@code article}.
   *
   * @return technischer Medientypname
   */
  public String getMediaTypeBibName() {
    return mediaTypeBibName;
  }

  /**
   * Setzt den technischen Namen des Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   */
  public void setMediaTypeBibName(String mediaTypeBibName) {
    this.mediaTypeBibName = sanitize(mediaTypeBibName);
  }

  /**
   * Liefert den Anzeigenamen des Medientyps.
   *
   * @return Anzeigename des Medientyps
   */
  public String getMediaTypeName() {
    return mediaTypeName;
  }

  /**
   * Setzt den Anzeigenamen des Medientyps.
   *
   * @param mediaTypeName Anzeigename des Medientyps
   */
  public void setMediaTypeName(String mediaTypeName) {
    this.mediaTypeName = sanitize(mediaTypeName);
  }

  /**
   * Liefert den Citekey des Eintrags.
   *
   * @return Citekey
   */
  public String getCitekey() {
    return citekey;
  }

  /**
   * Setzt den Citekey des Eintrags.
   *
   * @param citekey Citekey
   */
  public void setCitekey(String citekey) {
    this.citekey = sanitize(citekey);
  }

  /**
   * Liefert den Erstellungszeitpunkt.
   *
   * @return Erstellungszeitpunkt
   */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Setzt den Erstellungszeitpunkt.
   *
   * @param createdAt Erstellungszeitpunkt
   */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Liefert den Änderungszeitpunkt.
   *
   * @return Änderungszeitpunkt
   */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * Setzt den Änderungszeitpunkt.
   *
   * @param updatedAt Änderungszeitpunkt
   */
  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  /**
   * Fügt einen bibliographischen Feldwert ein oder ersetzt einen vorhandenen
   * Wert mit demselben {@code bibtexName}.
   *
   * @param value einzufügender Feldwert
   */
  public void putValue(BibValue value) {
    if (value == null || value.bibtexName().isBlank()) {
      return;
    }
    values.put(value.bibtexName(), value);
  }

  /**
   * Fügt mehrere Feldwerte ein oder ersetzt vorhandene Einträge mit gleichem
   * {@code bibtexName}.
   *
   * @param newValues einzufügende Feldwerte
   */
  public void putValues(Collection<? extends BibValue> newValues) {
    if (newValues == null || newValues.isEmpty()) {
      return;
    }

    for (BibValue value : newValues) {
      putValue(value);
    }
  }

  /**
   * Entfernt einen Feldwert anhand seines technischen Feldnamens.
   *
   * @param bibtexName technischer Feldname
   * @return der vorher gespeicherte Wert oder {@code null}
   */
  public BibValue removeValue(String bibtexName) {
    return values.remove(sanitize(bibtexName));
  }

  /**
   * Prüft, ob ein Feldwert mit dem angegebenen technischen Namen vorhanden ist.
   *
   * @param bibtexName technischer Feldname
   * @return {@code true}, wenn ein Wert vorhanden ist
   */
  public boolean hasValue(String bibtexName) {
    return values.containsKey(sanitize(bibtexName));
  }

  /**
   * Liefert einen Feldwert anhand seines technischen Feldnamens.
   *
   * @param bibtexName technischer Feldname
   * @return optionaler Feldwert
   */
  public Optional<BibValue> findValue(String bibtexName) {
    return Optional.ofNullable(values.get(sanitize(bibtexName)));
  }

  /**
   * Liefert alle Feldwerte als unveränderliche Sicht auf die interne Map.
   *
   * @return unveränderliche Sicht auf die Feldwerte
   */
  public Map<String, BibValue> getValuesView() {
    return Collections.unmodifiableMap(values);
  }

  /**
   * Liefert alle Feldwerte in ihrer gespeicherten Reihenfolge.
   *
   * @return unveränderliche Sicht auf die Feldwerte
   */
  public Collection<BibValue> getOrderedValues() {
    return Collections.unmodifiableCollection(values.values());
  }

  /**
   * Entfernt alle bisher gespeicherten Feldwerte.
   */
  public void clearValues() {
    values.clear();
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}