package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ Internetquelle.
 */
public class InternetEntry extends BibliographyEntry {

  /** Vollständige URL der Quelle. */
  @Getter private String url = "";

  /** Hostname oder übergeordnetes Projekt der Quelle. */
  @Getter private String hostName = "";

  /** Veröffentlichungsangabe in freier Form. */
  @Getter private String published = "";

  /** Zeitpunkt des letzten Zugriffs auf die Quelle. */
  @Getter @Setter private LocalDateTime accessedAt;

  /**
   * Erzeugt einen leeren Internet-Eintrag.
   */
  public InternetEntry() {
    super();
  }

  /**
   * Setzt die URL.
   *
   * @param url neue URL; {@code null} wird als leerer String behandelt
   */
  public void setUrl(String url) {
    this.url = url == null ? "" : url.trim();
  }

  /**
   * Setzt den Hostnamen oder Projektnamen.
   *
   * @param hostName neuer Hostname; {@code null} wird als leerer String behandelt
   */
  public void setHostName(String hostName) {
    this.hostName = hostName == null ? "" : hostName.trim();
  }

  /**
   * Setzt die Veröffentlichungsangabe.
   *
   * @param published neue Veröffentlichungsangabe; {@code null} wird als leerer String behandelt
   */
  public void setPublished(String published) {
    this.published = published == null ? "" : published.trim();
  }
}