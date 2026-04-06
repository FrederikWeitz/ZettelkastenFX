package de.zettelkastenfx.bibliography.api;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.IdentifySuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;

import java.util.List;
import java.util.Optional;

/**
 * Schmale Fassade für die Bibliographie-Logik.
 * <p>
 * Diese Schnittstelle kapselt die interne Tabellen- und Repository-Struktur
 * gegenüber aufrufenden Schichten wie Zettel-UI, Controller oder späteren
 * Popup-Editoren.
 */
public interface BibliographyService {

  /**
   * Lädt einen bibliographischen Eintrag vollständig.
   *
   * @param entryId Datenbank-ID des Eintrags
   * @return optionaler dynamischer Eintrag
   */
  Optional<DynamicBibliographyEntry> loadEntry(int entryId);

  /**
   * Erzeugt ein leeres bibliographisches Laufzeitobjekt für einen vorhandenen
   * Medientyp.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return leerer Eintrag
   */
  DynamicBibliographyEntry createEmptyEntry(int mediaTypeId);

  /**
   * Speichert einen bibliographischen Eintrag.
   *
   * @param entry zu speichernder Eintrag
   * @return Datenbank-ID des gespeicherten Eintrags
   */
  int saveEntry(DynamicBibliographyEntry entry);

  /**
   * Liefert alle verfügbaren Medientypen.
   *
   * @return alle Medientypen
   */
  List<MediaTypeDefinition> listMediaTypes();

  /**
   * Liefert nur die Medientypen, die gemäß Metadaten direkt verknüpft werden
   * dürfen.
   *
   * @return direkt verknüpfbare Medientypen
   */
  List<MediaTypeDefinition> listDirectAssignableMediaTypes();

  /**
   * Liefert alle Attributdefinitionen eines Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Attributdefinitionen des Medientyps
   */
  List<MediaAttributeDefinition> listAttributesForMediaType(int mediaTypeId);

  /**
   * Liefert nur die Identifikationsattribute eines Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Identifikationsattribute des Medientyps
   */
  List<MediaAttributeDefinition> listIdentifyAttributesForMediaType(int mediaTypeId);

  /**
   * Liefert alle Lookup-Definitionen eines Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Lookup-Definitionen des Medientyps
   */
  List<MediaAttributeLookupDefinition> listLookupDefinitionsForMediaType(int mediaTypeId);

  /**
   * Liefert die Identify-Attribute eines Medientyps anhand seines technischen
   * Namens.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @return Identify-Attribute des Medientyps
   */
  List<MediaAttributeDefinition> listIdentifyAttributesForMediaType(String mediaTypeBibName);

  /**
   * Sucht generische Vorschläge für ein Identify-Feld eines Medientyps.
   * Andere bereits gefüllte Identify-Felder werden dabei als Filter verwendet.
   * Integer-Felder sind von diesem Suchpfad ausgeschlossen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param identifyBibtexName technischer Feldname des aktiven Identify-Feldes
   * @param query aktueller Feldinhalt
   * @param stringFilters weitere gefüllte String-Identify-Felder
   * @param personFilters weitere gefüllte Person-Identify-Felder
   * @param limit maximale Trefferzahl
   * @return passende Identify-Vorschläge
   */
  List<IdentifySuggestion> findIdentifySuggestions(String mediaTypeBibName,
                                                   String identifyBibtexName,
                                                   String query,
                                                   java.util.Map<String, String> stringFilters,
                                                   java.util.Map<String, List<Author>> personFilters,
                                                   int limit);

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
  IdentifySuggestion resolveExactIdentify(String mediaTypeBibName,
                                          String identifyBibtexName,
                                          String value,
                                          java.util.Map<String, String> stringFilters,
                                          java.util.Map<String, List<Author>> personFilters);

  /**
   * Sucht Vorschläge für ein wiederverwendbares Wertelistenfeld eines
   * Medientyps.
   * Die Suche bleibt strikt beim übergebenen technischen Feldnamen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param query aktueller Feldinhalt
   * @return passende Wertvorschläge desselben Feldes
   */
  List<String> findValueListSuggestions(String mediaTypeBibName,
                                        String bibtexName,
                                        String query);

  /**
   * Löst einen exakten Wert eines wiederverwendbaren Wertelistenfeldes auf.
   * Die Prüfung erfolgt strikt innerhalb desselben technischen Feldnamens.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer Feldname
   * @param value exakter Feldwert
   * @return aufgelöster Wert oder {@code null}
   */
  String resolveExactValueListValue(String mediaTypeBibName,
                                    String bibtexName,
                                    String value);

  /**
   * Lädt die Lookup-Definition eines konkreten Feldes innerhalb eines
   * Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param bibtexTypeId Datenbank-ID des Feldes
   * @return optionale Lookup-Definition
   */
  Optional<MediaAttributeLookupDefinition> loadLookupDefinition(int mediaTypeId, int bibtexTypeId);

  /**
   * Lädt die Lookup-Definition eines konkreten Feldes anhand technischer Namen.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param bibtexName technischer BibTeX-Feldname
   * @return optionale Lookup-Definition
   */
  Optional<MediaAttributeLookupDefinition> loadLookupDefinition(String mediaTypeBibName, String bibtexName);

  /**
   * Lädt die Related-Metadaten für ein bibliographisches Related-Feld.
   * Die Definition wird aus {@code related_media_type} aufgelöst und enthält
   * insbesondere den erlaubten Ziel-Medientyp sowie das Anzeigeattribut.
   *
   * @param bibtexTypeId Datenbank-ID des Related-Feldes
   * @return optionale Related-Definition
   */
  Optional<RelatedMediaDefinition> loadRelatedDefinition(int bibtexTypeId);

  /**
   * Lädt eine schlanke bibliographische Referenz für Anzeige- oder
   * Verknüpfungszwecke.
   *
   * @param entryId Datenbank-ID des bibliographischen Eintrags
   * @return optionale bibliographische Referenz
   */
  Optional<BibliographyReference> loadReference(int entryId);

  /**
   * Sucht Autorenvorschläge für einen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param prefix aktuelles Präfix des Nachnamens
   * @param selectedEntryId aktuell selektierter Eintrag oder {@code null}
   * @return passende Autorenvorschläge
   */
  List<AuthorSuggestion> findAuthorSuggestions(String mediaTypeBibName,
                                               String prefix,
                                               Integer selectedEntryId);

  /**
   * Sucht Titelvorschläge für einen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param query Suchtext
   * @param requiredAuthors optionale Autorenfilter
   * @return passende Titelvorschläge
   */
  List<TitleSuggestion> findTitleSuggestions(String mediaTypeBibName,
                                             String query,
                                             List<Author> requiredAuthors);

  /**
   * Löst einen exakten Titel für einen Medientyp auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param title exakter Titel
   * @param requiredAuthors optionale Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  TitleSuggestion resolveExactTitle(String mediaTypeBibName,
                                    String title,
                                    List<Author> requiredAuthors);

  /**
   * Löst anhand einer Autorenkombination einen eindeutigen Titel auf.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param requiredAuthors Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  TitleSuggestion resolveUniqueTitleForAuthors(String mediaTypeBibName,
                                               List<Author> requiredAuthors);

  /**
   * Sucht Reihenvorschläge für einen Medientyp.
   *
   * @param mediaTypeBibName technischer Medientypname
   * @param query Suchtext
   * @return passende Reihenwerte
   */
  List<String> findSeriesSuggestions(String mediaTypeBibName, String query);

  /**
   * Sucht KI-Anbieter mit optionalem Modellfilter.
   *
   * @param prefix Präfix für den Anbieter
   * @param modelFilter optionaler Modellfilter
   * @param limit maximale Trefferzahl
   * @return passende Anbieter
   */
  List<String> findAiProviders(String prefix, String modelFilter, int limit);

  /**
   * Sucht KI-Modelle mit optionalem Anbieterfilter.
   *
   * @param prefix Präfix für das Modell
   * @param providerFilter optionaler Anbieterfilter
   * @param limit maximale Trefferzahl
   * @return passende Modelle
   */
  List<String> findAiModels(String prefix, String providerFilter, int limit);

  /**
   * Lädt zuletzt verwendete KI-Anbieter.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Anbieter
   */
  List<String> loadRecentAiProviders(int limit);

  /**
   * Lädt zuletzt verwendete KI-Modelle.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Modelle
   */
  List<String> loadRecentAiModels(int limit);

  /**
   * Sucht potenzielle Dubletten eines bibliographischen Eintrags anhand der
   * Metadatenmodi {@code identify} und bei Bedarf {@code necessary}.
   *
   * @param probe zu prüfender Eintrag
   * @return passende bestehende Einträge
   */
  List<DynamicBibliographyEntry> findPotentialDuplicates(DynamicBibliographyEntry probe);

  /**
   * Sucht Bibliographie-Einträge direkt für das Medien-Popup.
   * <p>
   * Ist {@code mediaTypeBibName} leer, wird über alle Medientypen gesucht.
   * Ist {@code query} leer, werden die Einträge ungefiltert in stabiler
   * Reihenfolge geliefert.
   *
   * @param mediaTypeBibName technischer Medientypname oder leer für alle
   * @param query Suchtext oder leer für alle
   * @param limit maximale Trefferzahl
   * @return gefundene Referenzen
   */
  List<BibliographyReference> searchMediaReferences(String mediaTypeBibName, String query, int limit);

  /**
   * Sucht Bibliographie-Einträge direkt für metadatengetriebene Related-Lookups
   * über ein konkretes Anzeige- bzw. Suchfeld.
   * <p>
   * Ist {@code mediaTypeBibName} leer, wird über alle Medientypen gesucht.
   * Ist {@code displayBibtexName} leer, wird defensiv auf {@code title}
   * zurückgefallen.
   *
   * @param mediaTypeBibName technischer Medientypname oder leer für alle
   * @param displayBibtexName technischer Feldname, über den gesucht werden soll
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende bibliographische Referenzen
   */
  List<BibliographyReference> searchMediaReferences(String mediaTypeBibName,
                                                    String displayBibtexName,
                                                    String query,
                                                    int limit);

  /**
   * Löscht einen bibliographischen Eintrag vollständig aus dem Speicher.
   *
   * @param entryId Datenbank-ID des Eintrags
   */
  void deleteEntry(int entryId);
}
