package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;
import javafx.scene.Node;

/**
 * Wiederverwendbare UI-Komponente für ein einzelnes bibliographisches Feld.
 * Die Komponente kapselt Darstellung, Editierbarkeit und Werttransfer,
 * enthält aber keine Speicherlogik.
 */
public interface BibliographyFieldComponent extends BibliographyFieldValueAccess {

  /**
   * Liefert den technischen Medientypnamen des Besitzformulars.
   *
   * @return technischer Medientypname
   */
  String mediaTypeBibName();

  /**
   * Liefert den technischen BibTeX-Feldnamen.
   *
   * @return technischer Feldname
   */
  String bibtexName();

  /**
   * Liefert die zugrundeliegende Attributdefinition.
   *
   * @return Attributdefinition
   */
  MediaAttributeDefinition attribute();

  /**
   * Liefert den in JavaFX einbaubaren Wurzelknoten der Feldkomponente.
   *
   * @return UI-Knoten
   */
  Node getNode();

  /**
   * Schaltet die Komponente zwischen Bearbeitungs- und Lesemodus um.
   *
   * @param editable {@code true} für Bearbeitung
   */
  void setEditable(boolean editable);

  /**
   * Wendet eine neue Präsentationsform auf die Komponente an.
   * Die Komponente entscheidet selbst, wie Label, Kompaktmodus und Zusatzaktionen
   * sichtbar werden.
   *
   * @param presentation neue Präsentationsform
   */
  default void applyPresentation(BibliographyFieldPresentation presentation) {
  }
}