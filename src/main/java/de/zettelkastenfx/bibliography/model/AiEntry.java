package de.zettelkastenfx.bibliography.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Domänenobjekt für bibliographische Angaben vom Typ KI-Quelle.
 */
public class AiEntry extends BibliographyEntry {

  /** Anbieter des KI-Systems. */
  @Getter private String provider = "";

  /** Modellbezeichnung. */
  @Getter private String model = "";

  /** Kontext oder Umgebung der Nutzung. */
  @Getter private String context = "";

  /** Kurzer Titel oder Betreff des Prompts. */
  @Getter private String promptTitle = "";

  /** Zeitpunkt der Nutzung. */
  @Getter @Setter private LocalDateTime usedAt;

  /**
   * Erzeugt einen leeren KI-Eintrag.
   */
  public AiEntry() {
    super();
  }

  /**
   * Setzt den Anbieter.
   *
   * @param provider neuer Anbieter; {@code null} wird als leerer String behandelt
   */
  public void setProvider(String provider) {
    this.provider = provider == null ? "" : provider.trim();
  }

  /**
   * Setzt die Modellbezeichnung.
   *
   * @param model neue Modellbezeichnung; {@code null} wird als leerer String behandelt
   */
  public void setModel(String model) {
    this.model = model == null ? "" : model.trim();
  }

  /**
   * Setzt den Nutzungskontext.
   *
   * @param context neuer Kontext; {@code null} wird als leerer String behandelt
   */
  public void setContext(String context) {
    this.context = context == null ? "" : context.trim();
  }

  /**
   * Setzt den Prompttitel.
   *
   * @param promptTitle neuer Prompttitel; {@code null} wird als leerer String behandelt
   */
  public void setPromptTitle(String promptTitle) {
    this.promptTitle = promptTitle == null ? "" : promptTitle.trim();
  }
}