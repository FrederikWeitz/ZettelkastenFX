package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;

import java.util.Objects;

/**
 * Bündelt alle Informationen, die zum Erzeugen einer bibliographischen
 * Feldkomponente benötigt werden.
 *
 * @param mediaTypeBibName technischer Medientypname
 * @param attribute Attributdefinition
 * @param presentation Darstellungsoptionen
 * @param additionalField ob es sich um ein dynamisch ergänztes Zusatzfeld handelt
 * @param onRemove optionale Löschaktion
 */
public record BibliographyFieldRequest(
    String mediaTypeBibName,
    MediaAttributeDefinition attribute,
    BibliographyFieldPresentation presentation,
    boolean additionalField,
    Runnable onRemove
) {

  /**
   * Erzeugt eine sichere Anfrage.
   */
  public BibliographyFieldRequest {
    mediaTypeBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();
    Objects.requireNonNull(attribute, "attribute");
    presentation = presentation == null ? BibliographyFieldPresentation.popup() : presentation;
    onRemove = onRemove == null ? () -> {} : onRemove;
  }

  /**
   * Liefert den technischen BibTeX-Feldnamen.
   *
   * @return technischer Feldname
   */
  public String bibtexName() {
    if (attribute.fieldDefinition() == null) {
      return "";
    }
    return attribute.fieldDefinition().bibtexName() == null
               ? ""
               : attribute.fieldDefinition().bibtexName().trim();
  }
}