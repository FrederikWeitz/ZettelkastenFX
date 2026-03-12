package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

public class Author {

  @Setter @Getter private Integer id;       // DB-ID (kommt in Schritt 2)
  @Getter private String firstName; // Vorname
  @Getter private String lastName; // Nachname
  @Getter @Setter int position; // intern für die Reihenfolge, die der Nutzer dann sieht, damit diese gewählt werden kann, und gleich bleibt

  public void setFirstName(String firstName) { this.firstName = firstName == null ? "" : firstName.trim(); }
  public void setLastName(String lastName) { this.lastName = lastName == null ? "" : lastName.trim(); }

  @Override
  public String toString() {
    String fn = firstName == null ? "" : firstName.trim();
    String ln = lastName == null ? "" : lastName.trim();
    return (fn + " " + ln).trim();
  }
}