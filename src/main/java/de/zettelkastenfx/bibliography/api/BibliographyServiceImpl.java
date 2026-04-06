package de.zettelkastenfx.bibliography.api;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.persistence.BibliographyMetadataRepository;
import de.zettelkastenfx.bibliography.persistence.DynamicBibliographyRepository;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.IdentifySuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
   * Erzeugt den Service auf Basis bereits vorhandener Repository-Instanzen.
   *
   * @param metadataRepository Repository für Bibliographie-Metadaten
   * @param dynamicRepository Repository für dynamische Bibliographie-Einträge
   */
  public BibliographyServiceImpl(
      BibliographyMetadataRepository metadataRepository,
      DynamicBibliographyRepository dynamicRepository
  ) {
    this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
    this.dynamicRepository = Objects.requireNonNull(dynamicRepository, "dynamicRepository");
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

  @Override
  public List<MediaAttributeDefinition> listAttributesForMediaType(int mediaTypeId) {
    return metadataRepository.loadAttributesForMediaType(mediaTypeId);
  }

  @Override
  public List<MediaAttributeDefinition> listIdentifyAttributesForMediaType(int mediaTypeId) {
    return metadataRepository.loadAttributesForMediaType(mediaTypeId).stream()
               .filter(MediaAttributeDefinition::isIdentify)
               .toList();
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
                    .sorted((a, b) -> Integer.compare(a.position(), b.position()))
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