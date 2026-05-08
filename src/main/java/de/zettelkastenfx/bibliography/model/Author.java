package de.zettelkastenfx.bibliography.model;

/**
 * Beschreibt eine Person innerhalb bibliographischer Angaben.
 */
public class Author {

  private Integer id;
  private String firstName;
  private String lastName;
  int position;

  /**
   * Liefert die Datenbank-ID des Autors.
   *
   * @return Datenbank-ID oder {@code null}
   */
  public Integer getId() {
    return id;
  }

  /**
   * Setzt die Datenbank-ID des Autors.
   *
   * @param id Datenbank-ID oder {@code null}
   */
  public void setId(Integer id) {
    this.id = id;
  }

  /**
   * Liefert den Vornamen.
   *
   * @return Vorname
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * Setzt den Vornamen.
   *
   * @param firstName Vorname
   */
  public void setFirstName(String firstName) {
    this.firstName = firstName == null ? "" : firstName.trim();
  }

  /**
   * Liefert den Nachnamen.
   *
   * @return Nachname
   */
  public String getLastName() {
    return lastName;
  }

  /**
   * Setzt den Nachnamen.
   *
   * @param lastName Nachname
   */
  public void setLastName(String lastName) {
    this.lastName = lastName == null ? "" : lastName.trim();
  }

  /**
   * Liefert die Position innerhalb einer Personenliste.
   *
   * @return Position
   */
  public int getPosition() {
    return position;
  }

  /**
   * Setzt die Position innerhalb einer Personenliste.
   *
   * @param position Position
   */
  public void setPosition(int position) {
    this.position = position;
  }

  @Override
  public String toString() {
    String fn = firstName == null ? "" : firstName.trim();
    String ln = lastName == null ? "" : lastName.trim();
    return (fn + " " + ln).trim();
  }
}
