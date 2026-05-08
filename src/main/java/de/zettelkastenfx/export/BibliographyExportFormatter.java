package de.zettelkastenfx.export;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportBibliographyField;

import java.util.*;

/**
 * Bereitet bibliographische Eintraege nach Exportumfang und Feldrangfolge auf.
 */
public class BibliographyExportFormatter {

  private static final List<String> MODE_ORDER =
      List.of("identify", "necessary", "desired", "mandatory");

  private final BibliographyService bibliographyService;

  /**
   * Erzeugt den Formatter mit der vorhandenen Bibliographie-Fassade.
   *
   * @param bibliographyService Bibliographie-Fassade
   */
  public BibliographyExportFormatter(BibliographyService bibliographyService) {
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");
  }

  /**
   * Laedt und formatiert einen bibliographischen Eintrag.
   *
   * @param entryId Eintrags-ID
   * @param level Exportumfang
   * @return optionaler Exporteintrag
   */
  public Optional<ExportBibliographyEntry> format(Integer entryId, BibliographyExportLevel level) {
    if (entryId == null || entryId <= 0 || level == null || level == BibliographyExportLevel.NONE) {
      return Optional.empty();
    }

    return bibliographyService.loadEntry(entryId)
               .map(entry -> new ExportBibliographyEntry(entryId, formatFields(entry, level)))
               .filter(ExportBibliographyEntry::hasFields);
  }

  /**
   * Formatiert alle passenden Felder eines dynamischen Bibliographieeintrags.
   *
   * @param entry dynamischer Bibliographieeintrag
   * @param level Exportumfang
   * @return exportierbare Feldzeilen
   */
  private List<ExportBibliographyField> formatFields(DynamicBibliographyEntry entry,
                                                     BibliographyExportLevel level) {
    Map<String, MediaAttributeDefinition> definitions = new LinkedHashMap<>();
    for (MediaAttributeDefinition definition :
        bibliographyService.listAttributesForMediaType(entry.getMediaTypeBibName())) {
      if (definition != null && definition.fieldDefinition() != null) {
        definitions.put(normalize(definition.fieldDefinition().bibtexName()), definition);
      }
    }

    List<BibValue> values = new ArrayList<>(entry.getOrderedValues());
    values.sort(Comparator
                    .comparingInt((BibValue value) -> modeRank(definitions.get(normalize(value.bibtexName()))))
                    .thenComparing(value -> displayName(value, definitions), String.CASE_INSENSITIVE_ORDER));

    List<ExportBibliographyField> fields = new ArrayList<>();
    for (BibValue value : values) {
      MediaAttributeDefinition definition = definitions.get(normalize(value.bibtexName()));
      if (!isIncluded(definition, level)) {
        continue;
      }

      String formattedValue = formatValue(value);
      if (!formattedValue.isBlank()) {
        fields.add(new ExportBibliographyField(displayName(value, definitions), formattedValue));
      }
    }
    return fields;
  }

  /**
   * Prueft, ob ein Attribut fuer den gewaehlten Exportumfang ausgegeben wird.
   *
   * @param definition Attributdefinition
   * @param level Exportumfang
   * @return {@code true}, wenn das Feld enthalten ist
   */
  private boolean isIncluded(MediaAttributeDefinition definition, BibliographyExportLevel level) {
    if (level == BibliographyExportLevel.ALL) {
      return true;
    }
    if (definition == null) {
      return false;
    }
    if (level == BibliographyExportLevel.IDENTIFY) {
      return definition.isIdentify();
    }
    return definition.isIdentify() || definition.isNecessary();
  }

  /**
   * Liefert die Sortierposition eines Attributmodus.
   *
   * @param definition Attributdefinition
   * @return Sortierposition
   */
  private int modeRank(MediaAttributeDefinition definition) {
    if (definition == null || definition.mode().isBlank()) {
      return MODE_ORDER.size();
    }
    int index = MODE_ORDER.indexOf(definition.mode().toLowerCase(Locale.ROOT));
    return index < 0 ? MODE_ORDER.size() : index;
  }

  /**
   * Ermittelt den Anzeigenamen eines Feldwerts.
   *
   * @param value Feldwert
   * @param definitions Attributdefinitionen nach technischem Namen
   * @return Anzeigename
   */
  private String displayName(BibValue value, Map<String, MediaAttributeDefinition> definitions) {
    MediaAttributeDefinition definition = definitions.get(normalize(value.bibtexName()));
    if (definition != null && definition.fieldDefinition() != null
        && !definition.fieldDefinition().displayName().isBlank()) {
      return definition.fieldDefinition().displayName();
    }
    return value.bibtexName();
  }

  /**
   * Formatiert einen bibliographischen Feldwert als Text.
   *
   * @param value Feldwert
   * @return Textdarstellung
   */
  private String formatValue(BibValue value) {
    if (value == null || value.value() == null) {
      return "";
    }
    if (value instanceof PersonBibValue personValue) {
      return personValue.value().stream()
                 .filter(Objects::nonNull)
                 .map(PersonRef::displayName)
                 .filter(text -> !text.isBlank())
                 .reduce((left, right) -> left + "; " + right)
                 .orElse("");
    }
    if (value instanceof RelatedBibValue relatedValue) {
      return relatedValue.displayValue();
    }
    return String.valueOf(value.value()).trim();
  }

  /**
   * Normalisiert technische Namen fuer Map-Zugriffe.
   *
   * @param value Rohwert
   * @return normalisierter Wert
   */
  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
