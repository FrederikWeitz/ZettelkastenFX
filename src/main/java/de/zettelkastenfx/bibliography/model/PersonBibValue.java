package de.zettelkastenfx.bibliography.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repräsentiert ein bibliographisches Personenfeld wie
 * {@code author}, {@code editor} oder {@code translator}.
 *
 * @param bibtexName technischer Feldname
 * @param value geordnete Personenliste
 */
public record PersonBibValue(String bibtexName, List<PersonRef> value) implements BibValue {

  /**
   * Erzeugt ein Personenfeld mit bereinigtem Feldnamen und defensiver Kopie
   * der Personenliste.
   *
   * @param bibtexName technischer Feldname
   * @param value geordnete Personenliste
   */
  public PersonBibValue {
    bibtexName = sanitizeName(bibtexName);
    value = value == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(value));
  }

  @Override
  public BibValueType valueType() {
    return BibValueType.PERSON;
  }

  private static String sanitizeName(String name) {
    return name == null ? "" : name.trim();
  }
}