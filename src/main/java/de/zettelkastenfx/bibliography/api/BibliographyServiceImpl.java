package de.zettelkastenfx.bibliography.api;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.persistence.BibliographyMetadataRepository;
import de.zettelkastenfx.bibliography.persistence.DynamicBibliographyRepository;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.IdentifySuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isPersonDatatype;
import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isRelatedDatatype;

/**
 * Standardimplementierung der Bibliographie-Fassade.
 * <p>
 * Diese Klasse kapselt das neue dynamische Repository und das
 * Metadaten-Repository hinter einer kleinen, nach außen stabilen API.
 */
public class BibliographyServiceImpl implements BibliographyService {

  private final BibliographyMetadataRepository metadataRepository;
  private final DynamicBibliographyRepository dynamicRepository;

  /**
   * Erzeugt den Service auf Basis einer Datenquelle.
   *
   * @param ds Datenquelle der Anwendung
   */
  public BibliographyServiceImpl(DataSource ds) {
    Objects.requireNonNull(ds, "ds");
    this.metadataRepository = new BibliographyMetadataRepository(ds);
    this.dynamicRepository = new DynamicBibliographyRepository(ds);
  }

  /**
   * Lädt einen bibliographischen Eintrag ausschließlich aus dem neuen
   * dynamischen Datenmodell.
   *
   * @param entryId Datenbank-ID des Eintrags
   * @return optionaler bibliographischer Eintrag
   */
  @Override
  public Optional<DynamicBibliographyEntry> loadEntry(int entryId) {
    return dynamicRepository.load(entryId);
  }

  @Override
  public DynamicBibliographyEntry createEmptyEntry(int mediaTypeId) {
    return dynamicRepository.createEmptyEntry(mediaTypeId);
  }

  @Override
  public int saveEntry(DynamicBibliographyEntry entry) {
    return dynamicRepository.save(entry);
  }

  @Override
  public List<MediaTypeDefinition> listMediaTypes() {
    return metadataRepository.loadMediaTypes();
  }

  @Override
  public List<MediaTypeDefinition> listDirectAssignableMediaTypes() {
    return metadataRepository.loadMediaTypes().stream()
               .filter(MediaTypeDefinition::directLink)
               .toList();
  }

  /**
   * Lädt einen Medientyp anhand seines technischen Namens.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return optionale Medientypdefinition
   */
  @Override
  public Optional<MediaTypeDefinition> loadMediaTypeByBibName(String mediaTypeBibName) {
    return metadataRepository.loadMediaTypeByBibName(mediaTypeBibName);
  }

  @Override
  public List<MediaAttributeDefinition> listAttributesForMediaType(int mediaTypeId) {
    return metadataRepository.loadAttributesForMediaType(mediaTypeId);
  }

  /**
   * Liefert alle Attributdefinitionen eines Medientyps anhand seines
   * technischen Namens.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Attributdefinitionen des Medientyps
   */
  @Override
  public List<MediaAttributeDefinition> listAttributesForMediaType(String mediaTypeBibName) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    if (mediaTypeId == null) {
      return List.of();
    }
    return listAttributesForMediaType(mediaTypeId);
  }

  @Override
  public List<MediaAttributeDefinition> listIdentifyAttributesForMediaType(int mediaTypeId) {
    return metadataRepository.loadAttributesForMediaType(mediaTypeId).stream()
               .filter(MediaAttributeDefinition::isIdentify)
               .toList();
  }

  /**
   * Lädt eine Attributdefinition eines Medientyps anhand technischer Namen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @return optionale Attributdefinition
   */
  @Override
  public Optional<MediaAttributeDefinition> loadAttributeDefinition(String mediaTypeBibName,
                                                                    String bibtexName) {
    String normalizedBibtexName = normalizeTechnicalName(bibtexName);
    if (normalizedBibtexName.isBlank()) {
      return Optional.empty();
    }

    return listAttributesForMediaType(mediaTypeBibName).stream()
               .filter(Objects::nonNull)
               .filter(attribute -> attribute.fieldDefinition() != null)
               .filter(attribute -> normalizeTechnicalName(attribute.fieldDefinition().bibtexName())
                                        .equals(normalizedBibtexName))
               .findFirst();
  }

  /**
   * Erzeugt eine synthetische Attributdefinition für ein zusätzlich in der
   * Shell ergänztes BibTeX-Feld.
   * Diese Definition dient ausschließlich dazu, Lookup- und Related-Logik
   * an dieselben Metadatenpfade zu binden wie reguläre Formularfelder.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param fieldDefinition Felddefinition des Zusatzfeldes
   * @return optionale synthetische Attributdefinition
   */
  @Override
  public Optional<MediaAttributeDefinition> createSyntheticAdditionalAttribute(String mediaTypeBibName,
                                                                               BibtexFieldDefinition fieldDefinition) {
    if (fieldDefinition == null) {
      return Optional.empty();
    }

    return loadMediaTypeByBibName(mediaTypeBibName)
               .map(mediaType -> new MediaAttributeDefinition(
                   mediaType.id(),
                   fieldDefinition.id(),
                   "desired",
                   fieldDefinition
               ));
  }

  /**
   * Liefert alle definierten BibTeX-Felder in stabiler Reihenfolge.
   *
   * @return alle BibTeX-Felddefinitionen
   */
  @Override
  public List<BibtexFieldDefinition> listBibtexTypes() {
    return metadataRepository.loadBibtexTypes();
  }

  /**
   * Lädt eine BibTeX-Felddefinition anhand ihres technischen Namens.
   *
   * @param bibtexName technischer Feldname
   * @return optionale Felddefinition
   */
  @Override
  public Optional<BibtexFieldDefinition> loadBibtexTypeByBibName(String bibtexName) {
    return metadataRepository.loadBibtexTypeByBibName(bibtexName);
  }

  /**
   * Lädt das von einem BibTeX-Feld abhängige Pflichtfeld, falls eines über
   * {@code requires} definiert ist.
   *
   * @param fieldDefinition Ausgangsfeld
   * @return abhängige Felddefinition oder leer
   */
  @Override
  public Optional<BibtexFieldDefinition> loadRequiredBibtexType(BibtexFieldDefinition fieldDefinition) {
    if (fieldDefinition == null) {
      return Optional.empty();
    }

    String requiredBibtexName = normalizeTechnicalName(fieldDefinition.requires());
    if (requiredBibtexName.isBlank()) {
      return Optional.empty();
    }

    return loadBibtexTypeByBibName(requiredBibtexName);
  }

  /**
   * Liefert alle zusätzlich auswählbaren BibTeX-Felder eines Medientyps.
   * Bereits vorhandene sowie als {@code forbidden} markierte Felder werden
   * ausgeschlossen. Die Filterung des Suchtexts erfolgt über Anzeigename und
   * technischen BibTeX-Namen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param existingBibtexNames bereits im Formular vorhandene Feldnamen
   * @param query aktueller Suchtext
   * @return auswählbare zusätzliche BibTeX-Felder
   */
  @Override
  public List<BibtexFieldDefinition> findAvailableAdditionalBibtexTypes(String mediaTypeBibName,
                                                                        java.util.Set<String> existingBibtexNames,
                                                                        String query) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    if (mediaTypeId == null) {
      return List.of();
    }

    String normalizedQuery = normalizeSearchText(query);

    java.util.Set<String> existing = new java.util.LinkedHashSet<>();
    if (existingBibtexNames != null) {
      for (String value : existingBibtexNames) {
        String normalized = normalizeTechnicalName(value);
        if (!normalized.isBlank()) {
          existing.add(normalized);
        }
      }
    }

    java.util.Set<String> forbidden = metadataRepository.loadAttributesForMediaType(mediaTypeId).stream()
                                          .filter(java.util.Objects::nonNull)
                                          .filter(attribute -> attribute.fieldDefinition() != null)
                                          .filter(attribute -> "forbidden".equalsIgnoreCase(
                                              attribute.mode() == null ? "" : attribute.mode().trim()
                                          ))
                                          .map(attribute -> normalizeTechnicalName(attribute.fieldDefinition().bibtexName()))
                                          .filter(value -> !value.isBlank())
                                          .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

    return metadataRepository.loadBibtexTypes().stream()
               .filter(java.util.Objects::nonNull)
               .filter(field -> !normalizeTechnicalName(field.bibtexName()).isBlank())
               .filter(field -> !existing.contains(normalizeTechnicalName(field.bibtexName())))
               .filter(field -> !forbidden.contains(normalizeTechnicalName(field.bibtexName())))
               .filter(field -> {
                 if (normalizedQuery.isBlank()) {
                   return true;
                 }

                 String displayName = normalizeSearchText(field.displayName());
                 String bibtexName = normalizeSearchText(field.bibtexName());
                 return displayName.contains(normalizedQuery) || bibtexName.contains(normalizedQuery);
               })
               .toList();
  }

  /**
   * Löst eine manuell eingegebene Zusatzfeld-Auswahl gegen dieselben Regeln auf
   * wie das Vorschlagsdropdown.
   * Es werden nur solche Felder zurückgegeben, die für den Medientyp aktuell
   * tatsächlich zusätzlich auswählbar sind.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param existingBibtexNames bereits im Formular vorhandene Feldnamen
   * @param rawText manueller Eingabetext
   * @return passende und erlaubte Felddefinition oder leer
   */
  @Override
  public Optional<BibtexFieldDefinition> resolveAvailableAdditionalBibtexType(String mediaTypeBibName,
                                                                              java.util.Set<String> existingBibtexNames,
                                                                              String rawText) {
    String normalizedText = normalizeSearchText(rawText);
    if (normalizedText.isBlank()) {
      return Optional.empty();
    }

    return findAvailableAdditionalBibtexTypes(mediaTypeBibName, existingBibtexNames, normalizedText).stream()
               .filter(Objects::nonNull)
               .filter(field -> normalizeSearchText(field.displayName()).equals(normalizedText)
                                    || normalizeTechnicalName(field.bibtexName()).equals(normalizedText))
               .findFirst();
  }

  /**
   * Liefert alle Lookup-Definitionen eines Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Lookup-Definitionen des Medientyps
   */
  @Override
  public List<MediaAttributeLookupDefinition> listLookupDefinitionsForMediaType(int mediaTypeId) {
    return metadataRepository.loadLookupDefinitionsForMediaType(mediaTypeId);
  }

  /**
   * Liefert die Identify-Attribute eines Medientyps anhand seines technischen
   * Namens.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Identify-Attribute des Medientyps
   */
  @Override
  public List<MediaAttributeDefinition> listIdentifyAttributesForMediaType(String mediaTypeBibName) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    if (mediaTypeId == null) {
      return List.of();
    }
    return listIdentifyAttributesForMediaType(mediaTypeId);
  }

  /**
   * Liefert alle Attribute eines Medientyps, die lokal für die Validierung
   * eines Speichervorgangs relevant sind.
   * Berücksichtigt werden alle Felder mit {@code identify} oder
   * {@code necessary}.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return für die lokale Validierung relevante Attribute
   */
  @Override
  public List<MediaAttributeDefinition> listRequiredAttributesForMediaType(String mediaTypeBibName) {
    return listAttributesForMediaType(mediaTypeBibName).stream()
               .filter(Objects::nonNull)
               .filter(attribute -> attribute.isIdentify() || attribute.isNecessary())
               .toList();
  }

  /**
   * Prüft einen lokalen Editorzustand mit konkreten Personenwerten.
   * Personenfelder gelten nur dann als erfüllt, wenn mindestens eine Person
   * mit Vor- oder Nachnamen vorhanden ist.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param fieldValues aktuelle String-/Integerwerte nach BibTeX-Namen
   * @param personValues aktuelle Personenwerte nach BibTeX-Namen
   * @param relatedFieldNames technische Feldnamen belegter Related-Felder
   * @return {@code true}, wenn alle Pflichtattribute lokal erfüllt sind
   */
  @Override
  public boolean isLocallyValidForSave(String mediaTypeBibName,
                                       java.util.Map<String, String> fieldValues,
                                       java.util.Map<String, java.util.List<Author>> personValues,
                                       java.util.Set<String> relatedFieldNames) {
    if (resolveMediaTypeId(mediaTypeBibName) == null) {
      return false;
    }

    java.util.Map<String, String> safeFieldValues = fieldValues == null
                                                        ? java.util.Map.of()
                                                        : fieldValues;

    java.util.Map<String, java.util.List<Author>> safePersonValues = personValues == null
                                                                         ? java.util.Map.of()
                                                                         : personValues;

    java.util.Set<String> safeRelatedFieldNames = relatedFieldNames == null
                                                      ? java.util.Set.of()
                                                      : relatedFieldNames.stream()
                                                        .filter(java.util.Objects::nonNull)
                                                        .map(this::normalizeTechnicalName)
                                                        .filter(value -> !value.isBlank())
                                                        .collect(java.util.stream.Collectors.toSet());

    for (MediaAttributeDefinition attribute : listRequiredAttributesForMediaType(mediaTypeBibName)) {
      if (attribute == null || attribute.fieldDefinition() == null) {
        continue;
      }

      String bibtexName = normalizeTechnicalName(attribute.fieldDefinition().bibtexName());
      String datatype = normalizeTechnicalName(attribute.fieldDefinition().datatype());

      if (bibtexName.isBlank()) {
        continue;
      }

      if (isPersonDatatype(datatype)) {
        if (!hasMeaningfulAuthors(safePersonValues.get(bibtexName))) {
          return false;
        }
        continue;
      }

      if (isRelatedDatatype(datatype)) {
        if (!safeRelatedFieldNames.contains(bibtexName)) {
          return false;
        }
        continue;
      }

      String value = safeFieldValues.getOrDefault(bibtexName, "");
      if (value == null || value.trim().isBlank()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Prüft, ob eine Autoren-/Personenliste mindestens eine fachlich befüllte
   * Person enthält.
   *
   * @param authors Personenliste
   * @return {@code true}, wenn mindestens Vor- oder Nachname gesetzt ist
   */
  private boolean hasMeaningfulAuthors(java.util.List<Author> authors) {
    if (authors == null || authors.isEmpty()) {
      return false;
    }

    for (Author author : authors) {
      if (author == null) {
        continue;
      }

      if (!sanitize(author.getFirstName()).isBlank()
              || !sanitize(author.getLastName()).isBlank()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Liefert alle im Editor sichtbar aufzubauenden Attribute eines Medientyps
   * in der gewünschten Anzeige-Reihenfolge.
   * Berücksichtigt werden die Modi {@code identify}, {@code necessary},
   * {@code desired} und {@code mandatory}.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return sortierte Editor-Attribute des Medientyps
   */
  @Override
  public List<MediaAttributeDefinition> listEditorAttributesForMediaType(String mediaTypeBibName) {
    return listAttributesForMediaType(mediaTypeBibName).stream()
               .filter(Objects::nonNull)
               .filter(attribute -> {
                 if (attribute.isIdentify() || attribute.isNecessary() || attribute.isDesired()) {
                   return true;
                 }

                 String mode = attribute.mode() == null ? "" : attribute.mode().trim().toLowerCase(java.util.Locale.ROOT);
                 return "mandatory".equals(mode);
               })
               .sorted(java.util.Comparator
                           .comparingInt(this::editorDisplayRank)
                           .thenComparing(attribute -> {
                             if (attribute == null || attribute.fieldDefinition() == null) {
                               return Integer.MAX_VALUE;
                             }
                             return attribute.fieldDefinition().id();
                           }))
               .toList();
  }

  /**
   * Liefert den Anzeigerang eines Attributs innerhalb des Editors.
   *
   * @param attribute Attributdefinition
   * @return Sortierrang
   */
  private int editorDisplayRank(MediaAttributeDefinition attribute) {
    if (attribute == null) {
      return Integer.MAX_VALUE;
    }

    if (attribute.isIdentify()) {
      return 0;
    }
    if (attribute.isNecessary()) {
      return 1;
    }
    if (attribute.isDesired()) {
      return 2;
    }

    String mode = attribute.mode() == null ? "" : attribute.mode().trim().toLowerCase(java.util.Locale.ROOT);
    if ("mandatory".equals(mode)) {
      return 3;
    }

    return 4;
  }

  /**
   * Lädt die Lookup-Definition eines konkreten Feldes innerhalb eines
   * Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param bibtexTypeId Datenbank-ID des Feldes
   * @return optionale Lookup-Definition
   */
  @Override
  public Optional<MediaAttributeLookupDefinition> loadLookupDefinition(int mediaTypeId, int bibtexTypeId) {
    return metadataRepository.loadLookupDefinition(mediaTypeId, bibtexTypeId);
  }

  /**
   * Lädt die Lookup-Definition eines konkreten Feldes anhand technischer Namen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer BibTeX-Feldname
   * @return optionale Lookup-Definition
   */
  @Override
  public Optional<MediaAttributeLookupDefinition> loadLookupDefinition(String mediaTypeBibName,
                                                                       String bibtexName) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    if (mediaTypeId == null) {
      return Optional.empty();
    }

    Optional<BibtexFieldDefinition> bibtexField =
        metadataRepository.loadBibtexTypeByBibName(bibtexName);

    if (bibtexField.isEmpty()) {
      return Optional.empty();
    }

    return metadataRepository.loadLookupDefinition(mediaTypeId, bibtexField.get().id());
  }

  /**
   * Lädt die Related-Metadaten für ein bibliographisches Related-Feld.
   *
   * @param bibtexTypeId Datenbank-ID des Related-Feldes
   * @return optionale Related-Definition
   */
  @Override
  public Optional<RelatedMediaDefinition> loadRelatedDefinition(int bibtexTypeId) {
    return metadataRepository.loadRelatedDefinition(bibtexTypeId);
  }

  @Override
  public Optional<BibliographyReference> loadReference(int entryId) {
    Optional<DynamicBibliographyEntry> loaded = dynamicRepository.load(entryId);
    if (loaded.isEmpty()) {
      return Optional.empty();
    }

    DynamicBibliographyEntry entry = loaded.get();
    String displayText = buildDisplayText(entry);

    return Optional.of(new BibliographyReference(
        Objects.requireNonNullElse(entry.getId(), entryId),
        entry.getMediaTypeId(),
        entry.getMediaTypeBibName(),
        entry.getMediaTypeName(),
        displayText
    ));
  }

  /**
   * Sucht Autorenvorschläge global über alle Medientypen hinweg.
   *
   * @param mediaTypeBibName technischer Medientypname; wird für die
   *                         Autorenvorschläge bewusst nicht mehr als Filter verwendet
   * @param prefix aktuelles Präfix des Nachnamens
   * @param selectedEntryId optional aktuell selektierter Eintrag
   * @return passende Autorenvorschläge
   */
  @Override
  public List<AuthorSuggestion> findAuthorSuggestions(String mediaTypeBibName,
                                                      String prefix,
                                                      Integer selectedEntryId) {
    return dynamicRepository.findAuthorSuggestions(
        null,
        prefix,
        selectedEntryId,
        20
    );
  }

  /**
   * Sucht Titelvorschläge auf Basis des neuen dynamischen Datenmodells.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param query Suchtext
   * @param requiredAuthors optionale Autorenfilter
   * @return passende Titelvorschläge
   */
  @Override
  public List<TitleSuggestion> findTitleSuggestions(String mediaTypeBibName,
                                                    String query,
                                                    List<Author> requiredAuthors) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.findTitleSuggestions(
        mediaTypeId,
        query,
        requiredAuthors,
        20
    );
  }

  /**
   * Löst einen exakten Titel optional unter Berücksichtigung von Autoren auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param title exakter Titel
   * @param requiredAuthors optionale Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  @Override
  public TitleSuggestion resolveExactTitle(String mediaTypeBibName,
                                           String title,
                                           List<Author> requiredAuthors) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.resolveExactTitle(
        mediaTypeId,
        title,
        requiredAuthors
    );
  }

  /**
   * Löst über eine Autorenliste einen eindeutig passenden Titel auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param requiredAuthors Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  @Override
  public TitleSuggestion resolveUniqueTitleForAuthors(String mediaTypeBibName,
                                                      List<Author> requiredAuthors) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.resolveUniqueTitleForAuthors(
        mediaTypeId,
        requiredAuthors
    );
  }

  /**
   * Sucht Reihenvorschläge im neuen dynamischen Datenmodell.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param query Suchtext
   * @return passende Reihenwerte
   */
  @Override
  public List<String> findSeriesSuggestions(String mediaTypeBibName, String query) {
    return findValueListSuggestions(mediaTypeBibName, query, "series");
  }

  /**
   * Sucht generische Vorschläge für ein Identify-Feld eines Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param identifyBibtexName technischer Feldname des aktiven Identify-Feldes
   * @param query aktueller Feldinhalt
   * @param stringFilters weitere gefüllte String-Identify-Felder
   * @param personFilters weitere gefüllte Person-Identify-Felder
   * @param limit maximale Trefferzahl
   * @return passende Identify-Vorschläge
   */
  @Override
  public List<IdentifySuggestion> findIdentifySuggestions(String mediaTypeBibName,
                                                          String identifyBibtexName,
                                                          String query,
                                                          java.util.Map<String, String> stringFilters,
                                                          java.util.Map<String, List<Author>> personFilters,
                                                          int limit) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.findIdentifySuggestions(
        mediaTypeId,
        identifyBibtexName,
        query,
        stringFilters,
        personFilters,
        limit
    );
  }

  /**
   * Löst einen exakten Identify-Wert optional unter Berücksichtigung weiterer
   * Identify-Filter auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param identifyBibtexName technischer Feldname des aktiven Identify-Feldes
   * @param value exakter Feldwert
   * @param stringFilters weitere gefüllte String-Identify-Felder
   * @param personFilters weitere gefüllte Person-Identify-Felder
   * @return eindeutiger Treffer oder {@code null}
   */
  @Override
  public IdentifySuggestion resolveExactIdentify(String mediaTypeBibName,
                                                 String identifyBibtexName,
                                                 String value,
                                                 java.util.Map<String, String> stringFilters,
                                                 java.util.Map<String, List<Author>> personFilters) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.resolveExactIdentify(
        mediaTypeId,
        identifyBibtexName,
        value,
        stringFilters,
        personFilters
    );
  }

  /**
   * Sucht Vorschläge für ein wiederverwendbares Wertelistenfeld eines
   * Medientyps.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param query aktueller Feldinhalt
   * @return passende Wertvorschläge
   */
  @Override
  public List<String> findValueListSuggestions(String mediaTypeBibName,
                                               String bibtexName,
                                               String query) {
    Integer mediaTypeId = resolveMediaTypeId(mediaTypeBibName);
    return dynamicRepository.findValueListSuggestions(
        mediaTypeId,
        bibtexName,
        query,
        20
    );
  }

  /**
   * Löst einen exakten Wert eines wiederverwendbaren Wertelistenfeldes auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param value exakter Feldwert
   * @return aufgelöster Wert oder {@code null}
   */
  @Override
  public String resolveExactValueListValue(String mediaTypeBibName,
                                           String bibtexName,
                                           String value) {
    String normalizedValue = value == null ? "" : value.trim();
    if (normalizedValue.isBlank()) {
      return null;
    }

    return findValueListSuggestions(mediaTypeBibName, bibtexName, normalizedValue).stream()
               .filter(candidate -> candidate != null && candidate.equalsIgnoreCase(normalizedValue))
               .findFirst()
               .orElse(null);
  }

  /**
   * Sucht KI-Anbieter im neuen dynamischen Datenmodell.
   *
   * @param prefix optionales Präfix des Anbieternamens
   * @param modelFilter optionaler Modellfilter
   * @param limit maximale Trefferzahl
   * @return passende Anbieterwerte
   */
  @Override
  public List<String> findAiProviders(String prefix, String modelFilter, int limit) {
    return dynamicRepository.findAiProviders(prefix, modelFilter, limit);
  }

  /**
   * Sucht KI-Modelle im neuen dynamischen Datenmodell.
   *
   * @param prefix optionales Präfix des Modellnamens
   * @param providerFilter optionaler Anbieterfilter
   * @param limit maximale Trefferzahl
   * @return passende Modellwerte
   */
  @Override
  public List<String> findAiModels(String prefix, String providerFilter, int limit) {
    return dynamicRepository.findAiModels(prefix, providerFilter, limit);
  }

  /**
   * Lädt zuletzt verwendete KI-Anbieter.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Anbieterwerte
   */
  @Override
  public List<String> loadRecentAiProviders(int limit) {
    return dynamicRepository.loadRecentAiProviders(limit);
  }

  /**
   * Lädt zuletzt verwendete KI-Modelle.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Modellwerte
   */
  @Override
  public List<String> loadRecentAiModels(int limit) {
    return dynamicRepository.loadRecentAiModels(limit);
  }

  @Override
  public List<DynamicBibliographyEntry> findPotentialDuplicates(DynamicBibliographyEntry probe) {
    return dynamicRepository.findPotentialDuplicates(probe);
  }

  @Override
  public List<BibliographyReference> searchMediaReferences(String mediaTypeBibName,
                                                           String query,
                                                           int limit) {
    return searchMediaReferences(mediaTypeBibName, "title", query, limit);
  }

  /**
   * Sucht Bibliographie-Einträge über ein konkretes Anzeige- bzw. Suchfeld.
   *
   * @param mediaTypeBibName technischer Medientypname oder leer für alle
   * @param displayBibtexName technischer Feldname, über den gesucht werden soll
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende bibliographische Referenzen
   */
  @Override
  public List<BibliographyReference> searchMediaReferences(String mediaTypeBibName,
                                                           String displayBibtexName,
                                                           String query,
                                                           int limit) {
    String normalizedBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();

    Integer mediaTypeId = null;
    if (!normalizedBibName.isBlank()) {
      Optional<MediaTypeDefinition> mediaType = metadataRepository.loadMediaTypeByBibName(normalizedBibName);
      if (mediaType.isEmpty()) {
        return List.of();
      }
      mediaTypeId = mediaType.get().id();
    }

    return dynamicRepository.searchEntryIdsByFieldValue(
            mediaTypeId,
            displayBibtexName,
            query,
            limit
        ).stream()
               .map(this::loadReference)
               .flatMap(Optional::stream)
               .toList();
  }

  @Override
  public List<BibliographyReference> searchMediaReferencesByAuthors(String mediaTypeBibName,
                                                                    List<Author> authors,
                                                                    int limit) {
    return searchMediaEntryIdsByAuthors(mediaTypeBibName, authors, limit).stream()
               .map(this::loadReference)
               .flatMap(Optional::stream)
               .toList();
  }

  @Override
  public List<Integer> searchMediaEntryIdsByAuthors(String mediaTypeBibName,
                                                    List<Author> authors,
                                                    int limit) {
    String normalizedBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();

    Integer mediaTypeId = null;
    if (!normalizedBibName.isBlank()) {
      Optional<MediaTypeDefinition> mediaType = metadataRepository.loadMediaTypeByBibName(normalizedBibName);
      if (mediaType.isEmpty()) {
        return List.of();
      }
      mediaTypeId = mediaType.get().id();
    }

    return dynamicRepository.searchEntryIdsByAuthors(mediaTypeId, authors, limit);
  }

  @Override
  public List<Integer> resolveDirectEntryIds(List<Integer> entryIds) {
    return dynamicRepository.resolveDirectEntryIds(entryIds);
  }

  @Override
  public void deleteEntry(int entryId) {
    dynamicRepository.delete(entryId);
  }

  /**
   * Löst optional einen technischen Medientypnamen in eine Datenbank-ID auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Medientyp-ID oder {@code null}
   */
  private Integer resolveMediaTypeId(String mediaTypeBibName) {
    String normalizedBibName = mediaTypeBibName == null ? "" : mediaTypeBibName.trim();
    if (normalizedBibName.isBlank()) {
      return null;
    }

    return metadataRepository.loadMediaTypeByBibName(normalizedBibName)
               .map(MediaTypeDefinition::id)
               .orElse(null);
  }

  /**
   * Baut einen kompakten Anzeigetext aus den {@code identify}-Feldern des
   * Medientyps auf.
   * <p>
   * Wenn keine Identifikationsfelder befüllt sind, wird ersatzweise das Feld
   * {@code title} verwendet. Bleibt auch dieses leer, wird ein technischer
   * Fallback erzeugt.
   *
   * @param entry bibliographischer Eintrag
   * @return kompakter Anzeigetext
   */
  private String buildDisplayText(DynamicBibliographyEntry entry) {
    if (entry.getMediaTypeId() != null) {
      List<MediaAttributeDefinition> identifyAttributes =
          metadataRepository.loadAttributesForMediaType(entry.getMediaTypeId()).stream()
              .filter(MediaAttributeDefinition::isIdentify)
              .toList();

      List<String> identifyParts = new ArrayList<>();
      for (MediaAttributeDefinition attribute : identifyAttributes) {
        String bibtexName = attribute.fieldDefinition().bibtexName();
        entry.findValue(bibtexName)
            .map(this::renderValue)
            .filter(text -> !text.isBlank())
            .ifPresent(identifyParts::add);
      }

      if (!identifyParts.isEmpty()) {
        return identifyParts.stream()
                   .filter(text -> !text.isBlank())
                   .collect(Collectors.joining(" | "));
      }
    }

    Optional<String> titleFallback = entry.findValue("title")
                                         .map(this::renderValue)
                                         .filter(text -> !text.isBlank());

    if (titleFallback.isPresent()) {
      return titleFallback.get();
    }

    String mediaTypeName = sanitize(entry.getMediaTypeName());
    if (!mediaTypeName.isBlank() && entry.getId() != null) {
      return mediaTypeName + " #" + entry.getId();
    }
    if (!mediaTypeName.isBlank()) {
      return mediaTypeName;
    }
    if (entry.getId() != null) {
      return "Eintrag #" + entry.getId();
    }
    return "Unbenannter Eintrag";
  }

  /**
   * Rendert einen bibliographischen Feldwert in einen kompakten Anzeigetext.
   *
   * @param value bibliographischer Feldwert
   * @return Anzeigetext
   */
  private String renderValue(BibValue value) {
    if (value == null) {
      return "";
    }

    return switch (value.valueType()) {
      case STRING -> {
        if (value instanceof StringBibValue stringBibValue) {
          yield sanitize(stringBibValue.value());
        }
        yield sanitize(stringValue(value.value()));
      }
      case INTEGER -> {
        if (value instanceof IntegerBibValue integerBibValue) {
          yield integerBibValue.value() == null ? "" : String.valueOf(integerBibValue.value());
        }
        yield value.value() == null ? "" : String.valueOf(value.value());
      }
      case PERSON -> {
        if (value instanceof PersonBibValue personBibValue) {
          yield personBibValue.value().stream()
                    .filter(Objects::nonNull)
                    .filter(person -> !sanitize(person.firstName()).isBlank()
                                          || !sanitize(person.lastName()).isBlank())
                    .sorted(Comparator.comparingInt(person ->
                                                        person.position() == null || person.position() <= 0
                                                            ? Integer.MAX_VALUE
                                                            : person.position()
                    ))
                    .map(PersonRef::displayName)
                    .map(this::sanitize)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining(", "));
        }
        yield "";
      }
      case RELATED -> {
        if (value instanceof RelatedBibValue relatedBibValue) {
          String displayValue = sanitize(relatedBibValue.displayValue());
          if (!displayValue.isBlank()) {
            yield displayValue;
          }
          yield relatedBibValue.relatedEntryId() == null
                    ? ""
                    : "Eintrag #" + relatedBibValue.relatedEntryId();
        }
        yield "";
      }
    };
  }

  /**
   * Normalisiert einen Suchtext für Vergleich und Filterung.
   *
   * @param text Eingabetext
   * @return normalisierter Suchtext
   */
  private String normalizeSearchText(String text) {
    return text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Normalisiert einen technischen Namen defensiv.
   *
   * @param value technischer Name
   * @return normalisierte Form
   */
  private String normalizeTechnicalName(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * Wandelt einen generischen Wert defensiv in einen String um.
   *
   * @param value Eingabewert
   * @return Stringdarstellung
   */
  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Bereinigt Textwerte defensiv.
   *
   * @param value Eingabewert
   * @return bereinigter Text
   */
  private String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}
