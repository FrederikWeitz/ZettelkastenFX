package de.zettelkastenfx.bibliography.persistence;

import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.IdentifySuggestion;
import de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isIntegerDatatype;
import static de.zettelkastenfx.bibliography.util.BibtexDatatypeUtil.isPersonDatatype;

/**
 * Lädt und speichert bibliografische Einträge über das neue dynamische Modell.
 * <p>
 * Dieses Repository bildet den alleinigen Laufzeitpfad der dynamischen
 * Bibliografie-Logik ab und verwendet die Tabellen:
 * <ul>
 *   <li>{@code bibliography_entries}</li>
 *   <li>{@code bibliography_entries_string}</li>
 *   <li>{@code bibliography_entries_integer}</li>
 * </ul>
 * <p>
 * Zusätzlich werden Metadaten über {@link BibliographyMetadataRepository}
 * aufgelöst.
 * <p>
 * Aktueller Laufzeitpfad:
 * <ul>
 *   <li>{@code bibliography_entries.title} wird als technischer Titelfallback
 *       weiter gepflegt.</li>
 *   <li>Personenfelder werden ausschließlich über
 *       {@code bibliography_entries_integer} auf Personenlisten in
 *       {@code author_mapping} abgebildet.</li>
 * </ul>
 */
public class DynamicBibliographyRepository {

  private static final DateTimeFormatter SQLITE_TS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final DataSource ds;
  private final BibliographyMetadataRepository metadataRepository;

  /**
   * Erzeugt das Repository auf Basis der gegebenen Datenquelle.
   *
   * @param ds Datenquelle der Anwendung
   */
  public DynamicBibliographyRepository(DataSource ds) {
    this.ds = Objects.requireNonNull(ds, "ds");
    this.metadataRepository = new BibliographyMetadataRepository(ds);
  }

  /**
   * Lädt einen bibliografischen Eintrag vollständig in das dynamische Modell.
   *
   * @param entryId Datenbank-ID des Eintrags
   * @return optionaler Eintrag
   */
  public Optional<DynamicBibliographyEntry> load(int entryId) {
    try (Connection c = ds.getConnection()) {
      Optional<DynamicBibliographyEntry> loaded = loadBaseEntry(c, entryId);
      if (loaded.isEmpty()) {
        return Optional.empty();
      }

      DynamicBibliographyEntry entry = loaded.get();
      loadStringValues(c, entry);
      loadIntegerAndRelatedValues(c, entry);
      loadPersonValues(c, entry);

      return Optional.of(entry);
    } catch (SQLException ex) {
      throw new IllegalStateException("Dynamischer Bibliographie-Eintrag konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Laedt mehrere bibliographische Eintraege ueber eine gemeinsame Verbindung.
   * Die Reihenfolge der Rueckgabe folgt der Reihenfolge der uebergebenen IDs;
   * doppelte, leere und nicht vorhandene IDs werden defensiv uebersprungen.
   *
   * @param entryIds Datenbank-IDs der Eintraege
   * @return vollstaendig geladene Eintraege
   */
  public List<DynamicBibliographyEntry> loadAll(Collection<Integer> entryIds) {
    if (entryIds == null || entryIds.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<Integer> normalizedEntryIds = new LinkedHashSet<>();
    for (Integer entryId : entryIds) {
      if (entryId != null && entryId > 0) {
        normalizedEntryIds.add(entryId);
      }
    }
    if (normalizedEntryIds.isEmpty()) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      List<DynamicBibliographyEntry> entries = new ArrayList<>();
      for (Integer entryId : normalizedEntryIds) {
        Optional<DynamicBibliographyEntry> loaded = loadBaseEntry(c, entryId);
        if (loaded.isEmpty()) {
          continue;
        }

        DynamicBibliographyEntry entry = loaded.get();
        loadStringValues(c, entry);
        loadIntegerAndRelatedValues(c, entry);
        loadPersonValues(c, entry);
        entries.add(entry);
      }
      return entries;
    } catch (SQLException ex) {
      throw new IllegalStateException("Dynamische Bibliographie-Eintraege konnten nicht gebuendelt geladen werden.", ex);
    }
  }

  /**
   * Erzeugt ein leeres Laufzeitobjekt für einen vorhandenen Medientyp.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return leeres bibliografisches Laufzeitobjekt
   */
  public DynamicBibliographyEntry createEmptyEntry(int mediaTypeId) {
    MediaTypeDefinition mediaType = metadataRepository.loadMediaTypeById(mediaTypeId)
                                        .orElseThrow(() -> new IllegalArgumentException("Unbekannter mediaTypeId: " + mediaTypeId));

    DynamicBibliographyEntry entry = new DynamicBibliographyEntry();
    entry.setMediaTypeId(mediaType.id());
    entry.setMediaTypeBibName(mediaType.bibName());
    entry.setMediaTypeName(mediaType.name());
    return entry;
  }

  /**
   * Speichert einen dynamischen Bibliografie-Eintrag.
   * <p>
   * Bei fehlender ID wird ein neuer Eintrag angelegt, andernfalls aktualisiert.
   *
   * @param entry zu speichernder Eintrag
   * @return Datenbank-ID des gespeicherten Eintrags
   */
  public int save(DynamicBibliographyEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry darf nicht null sein.");
    }
    if (entry.getMediaTypeId() == null || entry.getMediaTypeId() <= 0) {
      throw new IllegalArgumentException("entry.mediaTypeId muss gesetzt sein.");
    }

    try (Connection c = ds.getConnection()) {
      boolean oldAutoCommit = c.getAutoCommit();
      c.setAutoCommit(false);

      try {
        int entryId = saveBaseEntry(c, entry);
        entry.setId(entryId);

        deleteStringValues(c, entryId);
        deleteNonPersonIntegerValues(c, entryId);
        persistStringValues(c, entry);
        persistIntegerAndRelatedValues(c, entry);
        persistPersonValues(c, entry);

        c.commit();
        return entryId;
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(oldAutoCommit);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Dynamischer Bibliographie-Eintrag konnte nicht gespeichert werden.", ex);
    }
  }

  /**
   * Sucht bestehende bibliografische Einträge desselben Medientyps, die anhand
   * der fachlichen Identifikationsfelder zum gegebenen Eintrag passen.
   * <p>
   * Zuerst werden nur befüllte {@code identify}-Felder berücksichtigt.
   * Liefert diese erste Stufe mehrere Treffer, werden zusätzlich die befüllten
   * {@code necessary}-Felder einbezogen, um die Treffermenge zu verfeinern.
   *
   * @param probe zu prüfender Eintrag
   * @return passende bestehende Einträge
   */
  public List<DynamicBibliographyEntry> findPotentialDuplicates(DynamicBibliographyEntry probe) {
    if (probe == null || probe.getMediaTypeId() == null || probe.getMediaTypeId() <= 0) {
      return List.of();
    }

    List<MediaAttributeDefinition> attributes =
        metadataRepository.loadAttributesForMediaType(probe.getMediaTypeId());

    List<MediaAttributeDefinition> identifyAttributes = attributes.stream()
                                                            .filter(MediaAttributeDefinition::isIdentify)
                                                            .toList();

    List<DynamicBibliographyEntry> matches = findMatchesByAttributes(probe, identifyAttributes);

    if (matches.size() <= 1) {
      return matches;
    }

    List<MediaAttributeDefinition> identifyAndNecessaryAttributes = attributes.stream()
                                                                        .filter(attribute -> attribute.isIdentify() || attribute.isNecessary())
                                                                        .toList();

    return findMatchesByAttributes(probe, identifyAndNecessaryAttributes);
  }

  /**
   * Sucht Eintrags-IDs optional eingeschränkt auf einen Medientyp anhand des in
   * {@code bibliography_entries} gepflegten Titels.
   * <p>
   * Ist {@code mediaTypeId} {@code null}, werden alle Medientypen durchsucht.
   * Ist {@code query} leer, werden die Einträge ungefiltert geliefert.
   *
   * @param mediaTypeId optionale Datenbank-ID des Medientyps
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Eintrags-IDs
   */
  public List<Integer> searchEntryIdsByTitle(Integer mediaTypeId, String query, int limit) {
    String normalizedQuery = normalizeNullable(query);
    if (normalizedQuery == null) {
      normalizedQuery = "";
    }

    StringBuilder sql = new StringBuilder("""
        SELECT id
        FROM bibliography_entries
        WHERE 1 = 1
        """);

    List<Object> parameters = new ArrayList<>();

    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND media_types_id = ?");
      parameters.add(mediaTypeId);
    }

    if (!normalizedQuery.isBlank()) {
      sql.append(" AND LOWER(TRIM(COALESCE(title, ''))) LIKE LOWER(?)");
      parameters.add("%" + normalizedQuery + "%");
    }

    sql.append(" ORDER BY LOWER(TRIM(COALESCE(title, ''))), id LIMIT ?");
    parameters.add(Math.max(1, limit));

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {

      for (int i = 0; i < parameters.size(); i++) {
        Object parameter = parameters.get(i);
        if (parameter instanceof Integer integer) {
          ps.setInt(i + 1, integer);
        } else {
          ps.setString(i + 1, String.valueOf(parameter));
        }
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<Integer> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getInt("id"));
        }
        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Bibliographie-Suche konnte nicht ausgeführt werden.", ex);
    }
  }

  /**
   * Sucht Eintrags-IDs optional eingeschränkt auf einen Medientyp über ein
   * konkretes Feld des dynamischen Datenmodells.
   * <p>
   * Unterstützt derzeit:
   * <ul>
   *   <li>String-artige Felder über {@code bibliography_entries_string}</li>
   *   <li>Person-Felder über {@code bibliography_entries_integer -> author_mapping -> authors}</li>
   * </ul>
   * Integer-artige Felder sind ausdrücklich von der Suche ausgeschlossen und
   * liefern daher keine Treffer.
   * <p>
   * Ist {@code displayBibtexName} leer oder unbekannt, wird defensiv auf
   * {@link #searchEntryIdsByTitle(Integer, String, int)} zurückgefallen.
   *
   * @param mediaTypeId optionale Medientyp-ID; {@code null} bedeutet alle Typen
   * @param displayBibtexName technischer Feldname, über den gesucht werden soll
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Eintrags-IDs
   */
  public List<Integer> searchEntryIdsByFieldValue(Integer mediaTypeId,
                                                  String displayBibtexName,
                                                  String query,
                                                  int limit) {
    String normalizedQuery = normalizeNullable(query);
    if (normalizedQuery == null || normalizedQuery.isBlank()) {
      return searchEntryIdsByTitle(mediaTypeId, "", limit);
    }

    String normalizedDisplayBibtexName = sanitize(displayBibtexName);
    if (normalizedDisplayBibtexName.isBlank()) {
      return searchEntryIdsByTitle(mediaTypeId, normalizedQuery, limit);
    }

    Optional<BibtexFieldDefinition> fieldDefinition =
        metadataRepository.loadBibtexTypeByBibName(normalizedDisplayBibtexName);

    if (fieldDefinition.isEmpty()) {
      return searchEntryIdsByTitle(mediaTypeId, normalizedQuery, limit);
    }

    String datatype = sanitize(fieldDefinition.get().datatype());

    if (isPersonDatatype(datatype)) {
      return searchEntryIdsByPersonField(mediaTypeId, fieldDefinition.get().id(), normalizedQuery, limit);
    }

    if (isIntegerDatatype(datatype)) {
      return List.of();
    }

    return searchEntryIdsByStringField(mediaTypeId, fieldDefinition.get().id(), normalizedQuery, limit);
  }

  /**
   * Sucht Eintrags-IDs ueber alle Personenfelder.
   * Mehrere Autoren werden als UND-Filter interpretiert, ohne vollstaendige
   * Bibliographie-Eintraege zu laden.
   *
   * @param mediaTypeId optionale Medientyp-ID
   * @param requiredAuthors Autorenfilter
   * @param limit maximale Trefferzahl
   * @return passende Eintrags-IDs
   */
  public List<Integer> searchEntryIdsByAuthors(Integer mediaTypeId,
                                               List<Author> requiredAuthors,
                                               int limit) {
    List<Author> authors = normalizeAuthors(requiredAuthors);
    if (authors.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<Integer> intersection = null;
    int queryLimit = Math.max(Math.max(1, limit) * 20, 1_000);
    for (Author author : authors) {
      LinkedHashSet<Integer> hits = new LinkedHashSet<>(
          searchEntryIdsByAuthor(mediaTypeId, author, queryLimit)
      );
      if (intersection == null) {
        intersection = hits;
      } else {
        intersection.retainAll(hits);
      }
      if (intersection.isEmpty()) {
        return List.of();
      }
    }

    return intersection == null
               ? List.of()
               : intersection.stream().limit(Math.max(1, limit)).toList();
  }

  /**
   * Ermittelt direkt verknuepfbare Medien-IDs zu einer Treffermenge.
   * Direkte Medientypen werden ueber {@code media_types.direct_link}
   * beibehalten; nicht direkte Container werden ueber Related-Felder auf ihre
   * enthaltenen direkten Medien abgebildet.
   *
   * @param entryIds Ausgangs-IDs
   * @return direkt verknuepfbare Medien-IDs
   */
  public List<Integer> resolveDirectEntryIds(Collection<Integer> entryIds) {
    List<Integer> normalized = normalizeIds(entryIds);
    if (normalized.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<Integer> direct = new LinkedHashSet<>();
    int chunkSize = 800;
    for (int start = 0; start < normalized.size(); start += chunkSize) {
      int end = Math.min(start + chunkSize, normalized.size());
      List<Integer> chunk = normalized.subList(start, end);
      direct.addAll(loadDirectEntryIds(chunk));
      direct.addAll(loadDirectEntriesContainedIn(chunk));
    }
    return new ArrayList<>(direct);
  }

  /**
   * Sucht Eintrags-IDs über ein String-artiges Feld.
   *
   * @param mediaTypeId optionale Medientyp-ID
   * @param bibtexTypeId Feld-ID
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Eintrags-IDs
   */
  private List<Integer> searchEntryIdsByStringField(Integer mediaTypeId,
                                                    int bibtexTypeId,
                                                    String query,
                                                    int limit) {
    String normalizedQuery = normalizeNullable(query);
    if (normalizedQuery == null) {
      normalizedQuery = "";
    }

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT bes.entries_id
        FROM bibliography_entries_string bes
        JOIN bibliography_entries be
          ON be.id = bes.entries_id
        WHERE bes.bibtex_type_id = ?
        """);

    List<Object> parameters = new ArrayList<>();
    parameters.add(bibtexTypeId);

    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND be.media_types_id = ?");
      parameters.add(mediaTypeId);
    }

    if (!normalizedQuery.isBlank()) {
      sql.append(" AND LOWER(TRIM(COALESCE(bes.entry, ''))) LIKE LOWER(?)");
      parameters.add("%" + normalizedQuery + "%");
    }

    sql.append("""
         ORDER BY LOWER(TRIM(COALESCE(bes.entry, ''))), bes.entries_id
         LIMIT ?
        """);
    parameters.add(Math.max(1, limit));

    return executeEntryIdQuery(sql.toString(), parameters);
  }

  /**
   * Sucht Eintrags-IDs über ein Personenfeld.
   *
   * @param mediaTypeId optionale Medientyp-ID
   * @param bibtexTypeId Feld-ID
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Eintrags-IDs
   */
  private List<Integer> searchEntryIdsByPersonField(Integer mediaTypeId,
                                                    int bibtexTypeId,
                                                    String query,
                                                    int limit) {
    String normalizedQuery = normalizeNullable(query);
    if (normalizedQuery == null) {
      normalizedQuery = "";
    }

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT bei.entries_id
        FROM bibliography_entries_integer bei
        JOIN bibliography_entries be
          ON be.id = bei.entries_id
        JOIN author_mapping am
          ON am.person_group_id = bei.entry
        JOIN authors a
          ON a.id = am.author_id
        WHERE bei.bibtex_type_id = ?
        """);

    List<Object> parameters = new ArrayList<>();
    parameters.add(bibtexTypeId);

    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND be.media_types_id = ?");
      parameters.add(mediaTypeId);
    }

    if (!normalizedQuery.isBlank()) {
      sql.append("""
           AND (
               LOWER(TRIM(COALESCE(a.last_name, ''))) LIKE LOWER(?)
            OR LOWER(TRIM(COALESCE(a.first_name, ''))) LIKE LOWER(?)
            OR LOWER(TRIM(
                   COALESCE(a.last_name, '') || ', ' || COALESCE(a.first_name, '')
               )) LIKE LOWER(?)
           )
          """);
      String pattern = "%" + normalizedQuery + "%";
      parameters.add(pattern);
      parameters.add(pattern);
      parameters.add(pattern);
    }

    sql.append("""
         ORDER BY LOWER(TRIM(COALESCE(a.last_name, ''))),
                  LOWER(TRIM(COALESCE(a.first_name, ''))),
                  bei.entries_id
         LIMIT ?
        """);
    parameters.add(Math.max(1, limit));

    return executeEntryIdQuery(sql.toString(), parameters);
  }

  private List<Integer> searchEntryIdsByAuthor(Integer mediaTypeId, Author author, int limit) {
    String lastName = sanitize(author.getLastName());
    String firstName = sanitize(author.getFirstName());
    if (lastName.isBlank() && firstName.isBlank()) {
      return List.of();
    }

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT bei.entries_id
        FROM bibliography_entries_integer bei
        JOIN bibliography_entries be
          ON be.id = bei.entries_id
        JOIN bibtex_type bt
          ON bt.id = bei.bibtex_type_id
        JOIN author_mapping am
          ON am.person_group_id = bei.entry
        JOIN authors a
          ON a.id = am.author_id
        WHERE LOWER(TRIM(bt.datatype)) = 'person'
        """);

    List<Object> parameters = new ArrayList<>();
    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND be.media_types_id = ?");
      parameters.add(mediaTypeId);
    }
    if (!lastName.isBlank()) {
      sql.append(" AND LOWER(TRIM(COALESCE(a.last_name, ''))) LIKE LOWER(?)");
      parameters.add("%" + lastName + "%");
    }
    if (!firstName.isBlank()) {
      sql.append(" AND LOWER(TRIM(COALESCE(a.first_name, ''))) LIKE LOWER(?)");
      parameters.add("%" + firstName + "%");
    }

    sql.append("""
         ORDER BY bei.entries_id
         LIMIT ?
        """);
    parameters.add(Math.max(1, limit));

    return executeEntryIdQuery(sql.toString(), parameters);
  }

  private List<Integer> loadDirectEntryIds(List<Integer> entryIds) {
    String placeholders = String.join(", ", Collections.nCopies(entryIds.size(), "?"));
    String sql = """
        SELECT be.id
        FROM bibliography_entries be
        JOIN media_types mt
          ON mt.id = be.media_types_id
        WHERE be.id IN (""" + placeholders + """
        )
          AND mt.direct_link = 1
        ORDER BY be.id
        """;
    return executeEntryIdQuery(sql, new ArrayList<>(entryIds));
  }

  private List<Integer> loadDirectEntriesContainedIn(List<Integer> containerEntryIds) {
    String placeholders = String.join(", ", Collections.nCopies(containerEntryIds.size(), "?"));
    String sql = """
        SELECT DISTINCT bei.entries_id
        FROM bibliography_entries_integer bei
        JOIN bibtex_type bt
          ON bt.id = bei.bibtex_type_id
        JOIN bibliography_entries child
          ON child.id = bei.entries_id
        JOIN media_types child_type
          ON child_type.id = child.media_types_id
        WHERE bei.entry IN (""" + placeholders + """
        )
          AND LOWER(TRIM(bt.datatype)) = 'related'
          AND child_type.direct_link = 1
        ORDER BY bei.entries_id
        """;
    return executeEntryIdQuery(sql, new ArrayList<>(containerEntryIds));
  }

  /**
   * Sucht Autorenvorschläge direkt aus der Autorentabelle.
   * Die Vorschläge hängen damit nicht mehr von author_mapping oder von
   * vorhandenen Bibliografie-Verknüpfungen ab.
   *
   * @param mediaTypeId optionaler Medientyp; wird hier bewusst nicht verwendet
   * @param lastNamePrefix Präfix des Nachnamens
   * @param selectedEntryId optional aktuell selektierter Eintrag; wird hier bewusst nicht verwendet
   * @param limit maximale Trefferzahl
   * @return passende Autorenvorschläge
   */
  public List<AuthorSuggestion> findAuthorSuggestions(
      Integer mediaTypeId,
      String lastNamePrefix,
      Integer selectedEntryId,
      int limit
  ) {
    String normalizedPrefix = normalizeNullable(lastNamePrefix);
    if (normalizedPrefix == null || normalizedPrefix.isBlank()) {
      return List.of();
    }

    String sql = """
        SELECT DISTINCT
               a.id,
               a.first_name,
               a.last_name
        FROM authors a
        WHERE LOWER(TRIM(COALESCE(a.last_name, ''))) LIKE LOWER(?)
        ORDER BY LOWER(TRIM(COALESCE(a.last_name, ''))),
                 LOWER(TRIM(COALESCE(a.first_name, ''))),
                 a.id
        LIMIT ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, "%" + normalizedPrefix + "%");
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        List<AuthorSuggestion> result = new ArrayList<>();

        while (rs.next()) {
          result.add(new AuthorSuggestion(
              (Integer) rs.getObject("id"),
              sanitize(rs.getString("first_name")),
              sanitize(rs.getString("last_name"))
          ));
        }

        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Autorenvorschläge konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Sucht Titelvorschläge auf Basis des neuen dynamischen Datenmodells.
   * Die Suche arbeitet über den in {@code bibliography_entries.title} gepflegten
   * Titelfallback und kann optional über Autoren gefiltert werden.
   *
   * @param mediaTypeId optionale Medientyp-ID; bei {@code null} werden alle Typen berücksichtigt
   * @param query Suchtext des Titels
   * @param requiredAuthors optionale Autorenfilter
   * @param limit maximale Trefferzahl
   * @return passende Titelvorschläge
   */
  public List<de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion> findTitleSuggestions(
      Integer mediaTypeId,
      String query,
      List<Author> requiredAuthors,
      int limit
  ) {
    String normalizedQuery = normalizeNullable(query);
    if (normalizedQuery == null || normalizedQuery.isBlank()) {
      return List.of();
    }

    List<Integer> candidateIds = searchEntryIdsByTitle(
        mediaTypeId,
        normalizedQuery,
        Math.max(limit * 5, limit)
    );

    if (candidateIds.isEmpty()) {
      return List.of();
    }

    List<de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion> result = new ArrayList<>();

    for (Integer entryId : candidateIds) {
      Optional<DynamicBibliographyEntry> loaded = load(entryId);
      if (loaded.isEmpty()) {
        continue;
      }

      DynamicBibliographyEntry entry = loaded.get();
      if (!matchesRequiredAuthors(entry, requiredAuthors)) {
        continue;
      }

      String title = extractTitle(entry);
      if (title.isBlank()) {
        continue;
      }

      result.add(toTitleSuggestion(entry, title));
      if (result.size() >= limit) {
        break;
      }
    }

    return result;
  }

  /**
   * Löst einen exakten Titel optional unter Berücksichtigung von Autoren auf.
   *
   * @param mediaTypeId optionale Medientyp-ID; bei {@code null} werden alle Typen berücksichtigt
   * @param title exakter Titel
   * @param requiredAuthors optionale Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  public de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion resolveExactTitle(
      Integer mediaTypeId,
      String title,
      List<Author> requiredAuthors
  ) {
    String normalizedTitle = normalizeNullable(title);
    if (normalizedTitle == null || normalizedTitle.isBlank()) {
      return null;
    }

    List<de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion> hits =
        findTitleSuggestions(mediaTypeId, normalizedTitle, requiredAuthors, 50).stream()
            .filter(hit -> normalize(hit.title()).equals(normalize(normalizedTitle)))
            .toList();

    return hits.size() == 1 ? hits.getFirst() : null;
  }

  /**
   * Löst über eine Autorenliste einen eindeutig passenden Titel auf.
   *
   * @param mediaTypeId optionale Medientyp-ID; bei {@code null} werden alle Typen berücksichtigt
   * @param requiredAuthors Autorenfilter
   * @return eindeutiger Treffer oder {@code null}
   */
  public de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion resolveUniqueTitleForAuthors(
      Integer mediaTypeId,
      List<Author> requiredAuthors
  ) {
    if (requiredAuthors == null || requiredAuthors.isEmpty()) {
      return null;
    }

    StringBuilder sql = new StringBuilder("""
        SELECT id
        FROM bibliography_entries
        WHERE 1 = 1
        """);

    List<Object> parameters = new ArrayList<>();
    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND media_types_id = ?");
      parameters.add(mediaTypeId);
    }

    sql.append(" ORDER BY id");

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {

      for (int i = 0; i < parameters.size(); i++) {
        Object parameter = parameters.get(i);
        if (parameter instanceof Integer integer) {
          ps.setInt(i + 1, integer);
        } else {
          ps.setString(i + 1, String.valueOf(parameter));
        }
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<de.zettelkastenfx.bibliography.ui.lookup.TitleSuggestion> hits = new ArrayList<>();

        while (rs.next()) {
          int entryId = rs.getInt("id");
          Optional<DynamicBibliographyEntry> loaded = load(entryId);
          if (loaded.isEmpty()) {
            continue;
          }

          DynamicBibliographyEntry entry = loaded.get();
          if (!matchesRequiredAuthors(entry, requiredAuthors)) {
            continue;
          }

          String title = extractTitle(entry);
          if (title.isBlank()) {
            continue;
          }

          hits.add(toTitleSuggestion(entry, title));
          if (hits.size() > 1) {
            return null;
          }
        }

        return hits.size() == 1 ? hits.getFirst() : null;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Eindeutiger Titel konnte nicht aufgelöst werden.", ex);
    }
  }

  /**
   * Sucht Vorschläge für ein generisches Identify-Feld.
   * Die Suche arbeitet über das aktive Identify-Feld und filtert anschließend
   * geladene Kandidaten über weitere bereits gefüllte Identify-Felder.
   *
   * @param mediaTypeId optionale Medientyp-ID
   * @param identifyBibtexName technischer Feldname des aktiven Identify-Feldes
   * @param query aktueller Feldinhalt
   * @param stringFilters weitere gefüllte String-Identify-Felder
   * @param personFilters weitere gefüllte Person-Identify-Felder
   * @param limit maximale Trefferzahl
   * @return passende Vorschläge
   */
  public List<IdentifySuggestion> findIdentifySuggestions(Integer mediaTypeId,
                                                          String identifyBibtexName,
                                                          String query,
                                                          Map<String, String> stringFilters,
                                                          Map<String, List<Author>> personFilters,
                                                          int limit) {
    String normalizedIdentifyBibtexName = sanitize(identifyBibtexName);
    if (normalizedIdentifyBibtexName.isBlank()) {
      return List.of();
    }

    List<Integer> candidateIds = searchEntryIdsByFieldValue(
        mediaTypeId,
        normalizedIdentifyBibtexName,
        query,
        Math.max(limit * 5, limit)
    );

    if (candidateIds.isEmpty()) {
      return List.of();
    }

    List<IdentifySuggestion> result = new ArrayList<>();

    for (Integer entryId : candidateIds) {
      Optional<DynamicBibliographyEntry> loaded = load(entryId);
      if (loaded.isEmpty()) {
        continue;
      }

      DynamicBibliographyEntry entry = loaded.get();
      if (!matchesIdentifyStringFilters(entry, normalizedIdentifyBibtexName, stringFilters)) {
        continue;
      }
      if (!matchesIdentifyPersonFilters(entry, personFilters)) {
        continue;
      }

      String displayValue = extractIdentifyDisplayValue(entry, normalizedIdentifyBibtexName);
      if (displayValue.isBlank()) {
        continue;
      }

      result.add(new IdentifySuggestion(
          entry.getId(),
          displayValue,
          extractAuthorsForSuggestion(entry)
      ));

      if (result.size() >= limit) {
        break;
      }
    }

    return result;
  }

  /**
   * Löst einen exakten Identify-Wert optional unter Berücksichtigung weiterer
   * Identify-Filter auf.
   *
   * @param mediaTypeId optionale Medientyp-ID
   * @param identifyBibtexName technischer Feldname des aktiven Identify-Feldes
   * @param value exakter Feldwert
   * @param stringFilters weitere gefüllte String-Identify-Felder
   * @param personFilters weitere gefüllte Person-Identify-Felder
   * @return eindeutiger Treffer oder {@code null}
   */
  public IdentifySuggestion resolveExactIdentify(Integer mediaTypeId,
                                                 String identifyBibtexName,
                                                 String value,
                                                 Map<String, String> stringFilters,
                                                 Map<String, List<Author>> personFilters) {
    String normalizedValue = normalizeNullable(value);
    if (normalizedValue == null || normalizedValue.isBlank()) {
      return null;
    }

    List<IdentifySuggestion> hits = findIdentifySuggestions(
        mediaTypeId,
        identifyBibtexName,
        normalizedValue,
        stringFilters,
        personFilters,
        50
    ).stream()
                                        .filter(hit -> normalize(hit.displayValue()).equals(normalize(normalizedValue)))
                                        .toList();

    return hits.size() == 1 ? hits.getFirst() : null;
  }

  /**
   * Sucht wiederverwendbare String-Werte eines konkreten Feldes.
   * Die Suche bleibt strikt beim übergebenen {@code bibtexName}.
   * Integer-Felder sind hiervon ausgeschlossen.
   *
   * @param mediaTypeId optionale Medientyp-ID; bei {@code null} werden alle Typen berücksichtigt
   * @param bibtexName technischer Feldname
   * @param query Suchtext
   * @param limit maximale Trefferzahl
   * @return passende Feldwerte
   */
  public List<String> findValueListSuggestions(Integer mediaTypeId,
                                               String bibtexName,
                                               String query,
                                               int limit) {
    String normalizedBibtexName = sanitize(bibtexName);
    String normalizedQuery = normalizeNullable(query);

    if (normalizedBibtexName.isBlank() || normalizedQuery == null || normalizedQuery.isBlank()) {
      return List.of();
    }

    Optional<BibtexFieldDefinition> fieldDefinition =
        metadataRepository.loadBibtexTypeByBibName(normalizedBibtexName);

    if (fieldDefinition.isEmpty()) {
      return List.of();
    }

    String datatype = sanitize(fieldDefinition.get().datatype());
    if (isPersonDatatype(datatype) || isIntegerDatatype(datatype)) {
      return List.of();
    }

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT bes.entry
        FROM bibliography_entries_string bes
        JOIN bibliography_entries be
          ON be.id = bes.entries_id
        WHERE bes.bibtex_type_id = ?
          AND LOWER(TRIM(COALESCE(bes.entry, ''))) LIKE LOWER(?)
        """);

    List<Object> parameters = new ArrayList<>();
    parameters.add(fieldDefinition.get().id());
    parameters.add("%" + normalizedQuery + "%");

    if (mediaTypeId != null && mediaTypeId > 0) {
      sql.append(" AND be.media_types_id = ?");
      parameters.add(mediaTypeId);
    }

    sql.append("""
         ORDER BY LOWER(TRIM(COALESCE(bes.entry, '')))
         LIMIT ?
        """);
    parameters.add(Math.max(1, limit));

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {

      for (int i = 0; i < parameters.size(); i++) {
        Object parameter = parameters.get(i);
        if (parameter instanceof Integer integer) {
          ps.setInt(i + 1, integer);
        } else {
          ps.setString(i + 1, String.valueOf(parameter));
        }
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          String value = sanitize(rs.getString("entry"));
          if (!value.isBlank()) {
            result.add(value);
          }
        }
        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Wertelisten-Vorschläge konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Sucht KI-Anbieter im neuen dynamischen Datenmodell.
   *
   * @param prefix optionales Präfix des Anbieternamens
   * @param modelFilter optionaler Modellfilter
   * @param limit maximale Trefferzahl
   * @return passende Anbieterwerte
   */
  public List<String> findAiProviders(String prefix, String modelFilter, int limit) {
    return findAiStringValues("ai_provider", prefix, "ai_model", modelFilter, limit);
  }

  /**
   * Sucht KI-Modelle im neuen dynamischen Datenmodell.
   *
   * @param prefix optionales Präfix des Modellnamens
   * @param providerFilter optionaler Anbieterfilter
   * @param limit maximale Trefferzahl
   * @return passende Modellwerte
   */
  public List<String> findAiModels(String prefix, String providerFilter, int limit) {
    return findAiStringValues("ai_model", prefix, "ai_provider", providerFilter, limit);
  }

  /**
   * Lädt zuletzt verwendete KI-Anbieter.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Anbieterwerte
   */
  public List<String> loadRecentAiProviders(int limit) {
    return loadRecentAiStringValues("ai_provider", limit);
  }

  /**
   * Lädt zuletzt verwendete KI-Modelle.
   *
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Modellwerte
   */
  public List<String> loadRecentAiModels(int limit) {
    return loadRecentAiStringValues("ai_model", limit);
  }

  /**
   * Sucht String-Werte eines KI-Feldes optional unter zusätzlichem Filter
   * auf ein zweites KI-Feld desselben Eintrags.
   *
   * @param targetBibtexName Ziel-Feldname
   * @param prefix optionales Suchpräfix für das Ziel-Feld
   * @param filterBibtexName optionaler Filter-Feldname
   * @param filterValue optionaler Filterwert
   * @param limit maximale Trefferzahl
   * @return passende Feldwerte
   */
  private List<String> findAiStringValues(String targetBibtexName,
                                          String prefix,
                                          String filterBibtexName,
                                          String filterValue,
                                          int limit) {
    String normalizedPrefix = normalizeNullable(prefix);
    String normalizedFilterValue = normalizeNullable(filterValue);

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT target.entry
        FROM bibliography_entries_string target
        JOIN bibtex_type target_bt
          ON target_bt.id = target.bibtex_type_id
        """);

    List<Object> parameters = new ArrayList<>();

    boolean hasFilter = normalizedFilterValue != null && !normalizedFilterValue.isBlank();
    if (hasFilter) {
      sql.append("""
          JOIN bibliography_entries_string filter_value
            ON filter_value.entries_id = target.entries_id
          JOIN bibtex_type filter_bt
            ON filter_bt.id = filter_value.bibtex_type_id
          """);
    }

    sql.append("""
        WHERE LOWER(TRIM(target_bt.bibtex_name)) = LOWER(TRIM(?))
        """);
    parameters.add(targetBibtexName);

    if (normalizedPrefix != null && !normalizedPrefix.isBlank()) {
      sql.append("""
           AND LOWER(TRIM(COALESCE(target.entry, ''))) LIKE LOWER(?)
          """);
      parameters.add("%" + normalizedPrefix + "%");
    }

    if (hasFilter) {
      sql.append("""
           AND LOWER(TRIM(filter_bt.bibtex_name)) = LOWER(TRIM(?))
           AND LOWER(TRIM(COALESCE(filter_value.entry, ''))) LIKE LOWER(?)
          """);
      parameters.add(filterBibtexName);
      parameters.add("%" + normalizedFilterValue + "%");
    }

    sql.append("""
         ORDER BY LOWER(TRIM(COALESCE(target.entry, '')))
         LIMIT ?
        """);
    parameters.add(Math.max(1, limit));

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {

      for (int i = 0; i < parameters.size(); i++) {
        Object parameter = parameters.get(i);
        if (parameter instanceof Integer integer) {
          ps.setInt(i + 1, integer);
        } else {
          ps.setString(i + 1, String.valueOf(parameter));
        }
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          String value = sanitize(rs.getString("entry"));
          if (!value.isBlank()) {
            result.add(value);
          }
        }
        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("KI-Werte konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt zuletzt verwendete String-Werte eines KI-Feldes anhand der
   * Aktualisierungs- bzw. Erstellungszeit des Basiseintrags.
   *
   * @param bibtexName technischer Feldname
   * @param limit maximale Trefferzahl
   * @return zuletzt verwendete Feldwerte
   */
  private List<String> loadRecentAiStringValues(String bibtexName, int limit) {
    String sql = """
        SELECT value
        FROM (
            SELECT target.entry AS value,
                   MAX(COALESCE(be.updated_at, be.created_at)) AS last_used_at
            FROM bibliography_entries_string target
            JOIN bibtex_type bt
              ON bt.id = target.bibtex_type_id
            JOIN bibliography_entries be
              ON be.id = target.entries_id
            WHERE LOWER(TRIM(bt.bibtex_name)) = LOWER(TRIM(?))
              AND TRIM(COALESCE(target.entry, '')) <> ''
            GROUP BY LOWER(TRIM(target.entry)), target.entry
        )
        ORDER BY last_used_at DESC, LOWER(TRIM(value))
        LIMIT ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, bibtexName);
      ps.setInt(2, Math.max(1, limit));

      try (ResultSet rs = ps.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rs.next()) {
          String value = sanitize(rs.getString("value"));
          if (!value.isBlank()) {
            result.add(value);
          }
        }
        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Letzte KI-Werte konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Prüft, ob ein Eintrag alle geforderten Autoren bzw. Personen enthält.
   * Es werden nur fachlich befüllte Personen berücksichtigt.
   *
   * @param entry zu prüfender Eintrag
   * @param requiredAuthors geforderte Personen
   * @return {@code true}, wenn alle geforderten Personen enthalten sind
   */
  private boolean matchesRequiredAuthors(DynamicBibliographyEntry entry,
                                         List<Author> requiredAuthors) {
    if (entry == null || requiredAuthors == null || requiredAuthors.isEmpty()) {
      return true;
    }

    return matchesRequiredPersons(extractAllPersons(entry), requiredAuthors);
  }

  /**
   * Prüft weitere String-Identify-Filter eines Kandidaten.
   *
   * @param entry Kandidat
   * @param activeIdentifyBibtexName aktives Suchfeld
   * @param stringFilters weitere gefüllte String-Filter
   * @return {@code true}, wenn alle String-Filter passen
   */
  private boolean matchesIdentifyStringFilters(DynamicBibliographyEntry entry,
                                               String activeIdentifyBibtexName,
                                               Map<String, String> stringFilters) {
    if (entry == null || stringFilters == null || stringFilters.isEmpty()) {
      return true;
    }

    for (Map.Entry<String, String> filter : stringFilters.entrySet()) {
      String bibtexName = sanitize(filter.getKey());
      String expected = normalizeNullable(filter.getValue());

      if (bibtexName.isBlank()
              || expected == null
              || expected.isBlank()
              || bibtexName.equalsIgnoreCase(activeIdentifyBibtexName)) {
        continue;
      }

      String actual = extractIdentifyDisplayValue(entry, bibtexName);
      if (!normalize(actual).equals(normalize(expected))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Prüft weitere Person-Identify-Filter eines Kandidaten.
   * Es werden nur fachlich befüllte Personen berücksichtigt.
   *
   * @param entry Kandidat
   * @param personFilters weitere gefüllte Personenfilter
   * @return {@code true}, wenn alle Personenfilter passen
   */
  private boolean matchesIdentifyPersonFilters(DynamicBibliographyEntry entry,
                                               Map<String, List<Author>> personFilters) {
    if (entry == null || personFilters == null || personFilters.isEmpty()) {
      return true;
    }

    for (Map.Entry<String, List<Author>> filter : personFilters.entrySet()) {
      String bibtexName = sanitize(filter.getKey());
      List<Author> requiredAuthors = filter.getValue();

      if (bibtexName.isBlank() || requiredAuthors == null || requiredAuthors.isEmpty()) {
        continue;
      }

      Optional<BibValue> value = entry.findValue(bibtexName);
      if (value.isEmpty() || !(value.get() instanceof PersonBibValue personBibValue)) {
        return false;
      }

      if (!matchesRequiredPersons(personBibValue.value(), requiredAuthors)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Prüft, ob eine Personenliste alle geforderten Personen enthält.
   * Leere Personenobjekte werden dabei ignoriert.
   *
   * @param persons Kandidatenpersonen
   * @param requiredAuthors geforderte Personen
   * @return {@code true}, wenn alle geforderten Personen vorhanden sind
   */
  private boolean matchesRequiredPersons(List<PersonRef> persons, List<Author> requiredAuthors) {
    if (requiredAuthors == null || requiredAuthors.isEmpty()) {
      return true;
    }
    if (!hasMeaningfulPersons(persons)) {
      return false;
    }

    List<PersonRef> meaningfulPersons = persons.stream()
                                            .filter(Objects::nonNull)
                                            .filter(person -> !sanitize(person.firstName()).isBlank()
                                                                  || !sanitize(person.lastName()).isBlank())
                                            .toList();

    for (Author requiredAuthor : requiredAuthors) {
      if (requiredAuthor == null) {
        continue;
      }

      String requiredFirstName = normalize(requiredAuthor.getFirstName());
      String requiredLastName = normalize(requiredAuthor.getLastName());

      if (requiredFirstName.isBlank() && requiredLastName.isBlank()) {
        continue;
      }

      boolean matched = meaningfulPersons.stream().anyMatch(candidate ->
                                                                normalize(candidate.firstName()).equals(requiredFirstName)
                                                                    && normalize(candidate.lastName()).equals(requiredLastName)
      );

      if (!matched) {
        return false;
      }
    }

    return true;
  }

  /**
   * Extrahiert den sichtbaren Wert eines Identify-Feldes aus einem Eintrag.
   *
   * @param entry Eintrag
   * @param identifyBibtexName technischer Feldname
   * @return sichtbarer Feldwert oder leerer String
   */
  private String extractIdentifyDisplayValue(DynamicBibliographyEntry entry, String identifyBibtexName) {
    if (entry == null || identifyBibtexName == null || identifyBibtexName.isBlank()) {
      return "";
    }

    return entry.findValue(identifyBibtexName)
               .map(this::renderIdentifyValue)
               .orElse("");
  }

  /**
   * Rendert einen Identify-Wert in eine sichtbare Form.
   *
   * @param value Feldwert
   * @return sichtbarer Wert
   */
  private String renderIdentifyValue(BibValue value) {
    if (value == null) {
      return "";
    }

    return switch (value) {
      case StringBibValue stringValue ->
          stringValue.value() == null ? "" : stringValue.value().trim();

      case PersonBibValue personValue -> {
        List<PersonRef> persons = normalizePersonList(personValue.value());
        if (persons.isEmpty()) {
          yield "";
        }

        PersonRef first = persons.getFirst();
        String firstName = sanitize(first.firstName());
        String lastName = sanitize(first.lastName());

        if (lastName.isBlank()) {
          yield firstName;
        }
        if (firstName.isBlank()) {
          yield lastName;
        }
        yield lastName + ", " + firstName;
      }

      default -> "";
    };
  }

  /**
   * Extrahiert alle fachlich befüllten Personenfelder eines Eintrags zu einer
   * flachen Liste.
   * Leere Personenobjekte werden ignoriert.
   *
   * @param entry Eintrag
   * @return enthaltene Personen
   */
  private List<PersonRef> extractAllPersons(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return List.of();
    }

    List<PersonRef> result = new ArrayList<>();
    for (BibValue value : entry.getOrderedValues()) {
      if (!(value instanceof PersonBibValue personBibValue)) {
        continue;
      }

      result.addAll(normalizePersonList(personBibValue.value()));
    }

    return List.copyOf(result);
  }

  /**
   * Ermittelt den fachlichen Titel eines Eintrags.
   *
   * @param entry Eintrag
   * @return Titel oder leerer String
   */
  private String extractTitle(DynamicBibliographyEntry entry) {
    if (entry == null) {
      return "";
    }

    return entry.findValue("title")
               .filter(StringBibValue.class::isInstance)
               .map(StringBibValue.class::cast)
               .map(StringBibValue::value)
               .map(this::sanitize)
               .orElse("");
  }

  /**
   * Wandelt die fachlich befüllten Personen eines Eintrags in Autorenmodelle
   * für einen Titel- oder Identify-Vorschlag um.
   *
   * @param entry Eintrag
   * @return Autorenliste des Vorschlags
   */
  private List<Author> extractAuthorsForSuggestion(DynamicBibliographyEntry entry) {
    List<PersonRef> persons = extractAllPersons(entry);
    if (persons.isEmpty()) {
      return List.of();
    }

    List<Author> result = new ArrayList<>();
    int fallbackPosition = 1;

    for (PersonRef person : persons) {
      String firstName = sanitize(person.firstName());
      String lastName = sanitize(person.lastName());

      if (firstName.isBlank() && lastName.isBlank()) {
        continue;
      }

      Author author = new Author();
      author.setId(person.id());
      author.setFirstName(firstName);
      author.setLastName(lastName);
      author.setPosition(person.position() != null && person.position() > 0
                             ? person.position()
                             : fallbackPosition);

      result.add(author);
      fallbackPosition++;
    }

    return List.copyOf(result);
  }

  /**
   * Erzeugt einen Titelvorschlag aus einem geladenen Eintrag.
   *
   * @param entry Eintrag
   * @param title fachlicher Titel
   * @return Titelvorschlag
   */
  private TitleSuggestion toTitleSuggestion(
      DynamicBibliographyEntry entry,
      String title
  ) {
    return new TitleSuggestion(
        entry == null ? null : entry.getId(),
        sanitize(title),
        extractAuthorsForSuggestion(entry)
    );
  }

  /**
   * Löscht einen bibliografischen Eintrag vollständig, einschließlich seiner
   * String-, Integer-, Related- und Personenwerte.
   *
   * @param entryId Datenbank-ID des Eintrags
   */
  public void delete(int entryId) {
    if (entryId <= 0) {
      return;
    }

    try (Connection c = ds.getConnection()) {
      boolean oldAutoCommit = c.getAutoCommit();
      c.setAutoCommit(false);

      try {
        deleteStringValues(c, entryId);
        deleteNonPersonIntegerValues(c, entryId);
        deleteExistingPersonValues(c, entryId);

        try (PreparedStatement delBase = c.prepareStatement(
            "DELETE FROM bibliography_entries WHERE id = ?")) {
          delBase.setInt(1, entryId);
          delBase.executeUpdate();
        }

        c.commit();
      } catch (Exception ex) {
        c.rollback();
        throw ex;
      } finally {
        c.setAutoCommit(oldAutoCommit);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Bibliographischer Eintrag konnte nicht gelöscht werden.", ex);
    }
  }

  /**
   * Sucht Einträge desselben Medientyps, die in allen befüllten Attributen der
   * übergebenen Attributliste übereinstimmen.
   *
   * @param probe zu prüfender Eintrag
   * @param attributes zu berücksichtigende Attribute
   * @return passende bestehende Einträge
   */
  private List<DynamicBibliographyEntry> findMatchesByAttributes(DynamicBibliographyEntry probe,
                                                                 List<MediaAttributeDefinition> attributes) {
    List<BibValue> relevantValues = attributes.stream()
                                        .map(attribute -> probe.findValue(attribute.fieldDefinition().bibtexName()).orElse(null))
                                        .filter(Objects::nonNull)
                                        .filter(this::isMeaningfullyFilled)
                                        .toList();

    if (relevantValues.isEmpty()) {
      return List.of();
    }

    try (Connection c = ds.getConnection()) {
      List<Integer> candidateIds = loadCandidateEntryIds(c, probe.getMediaTypeId(), probe.getId());

      List<DynamicBibliographyEntry> matches = new ArrayList<>();
      for (Integer candidateId : candidateIds) {
        Optional<DynamicBibliographyEntry> loaded = load(candidateId);
        if (loaded.isEmpty()) {
          continue;
        }

        DynamicBibliographyEntry candidate = loaded.get();
        if (matchesAllRelevantValues(candidate, relevantValues)) {
          matches.add(candidate);
        }
      }

      return matches;
    } catch (SQLException ex) {
      throw new IllegalStateException("Potenzielle Dubletten konnten nicht ermittelt werden.", ex);
    }
  }

  /**
   * Lädt alle Kandidaten-IDs desselben Medientyps.
   *
   * @param c aktive Datenbankverbindung
   * @param mediaTypeId Medientyp-ID
   * @param excludeEntryId optional auszuschließende Eintrags-ID
   * @return Kandidaten-IDs
   * @throws SQLException bei Datenbankfehlern
   */
  private List<Integer> loadCandidateEntryIds(Connection c,
                                              int mediaTypeId,
                                              Integer excludeEntryId) throws SQLException {
    String sql = """
        SELECT id
        FROM bibliography_entries
        WHERE media_types_id = ?
        """ + (excludeEntryId != null && excludeEntryId > 0 ? " AND id <> ?" : "");

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, mediaTypeId);

      if (excludeEntryId != null && excludeEntryId > 0) {
        ps.setInt(2, excludeEntryId);
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<Integer> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getInt("id"));
        }
        return result;
      }
    }
  }

  /**
   * Prüft, ob alle relevanten Werte eines Probe-Eintrags auch im Kandidaten
   * gleich befüllt sind.
   *
   * @param candidate vorhandener Eintrag
   * @param relevantValues relevante Probe-Werte
   * @return {@code true}, wenn alle Werte übereinstimmen
   */
  private boolean matchesAllRelevantValues(DynamicBibliographyEntry candidate,
                                           List<BibValue> relevantValues) {
    for (BibValue probeValue : relevantValues) {
      Optional<BibValue> candidateValue = candidate.findValue(probeValue.bibtexName());
      if (candidateValue.isEmpty()) {
        return false;
      }
      if (!sameBibValue(probeValue, candidateValue.get())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Prüft, ob ein bibliografischer Wert fachlich befüllt ist.
   *
   * @param value zu prüfender Wert
   * @return {@code true}, wenn der Wert inhaltlich gesetzt ist
   */
  private boolean isMeaningfullyFilled(BibValue value) {
    if (value == null) {
      return false;
    }

    return switch (value.valueType()) {
      case STRING -> value instanceof StringBibValue stringValue
                         && stringValue.value() != null
                         && !stringValue.value().trim().isBlank();

      case INTEGER -> value instanceof IntegerBibValue integerValue
                          && integerValue.value() != null;

      case PERSON -> value instanceof PersonBibValue personValue
                         && hasMeaningfulPersons(personValue.value());

      case RELATED -> value instanceof RelatedBibValue relatedValue
                          && relatedValue.relatedEntryId() != null;
    };
  }

  /**
   * Vergleicht zwei bibliografische Werte feldspezifisch.
   *
   * @param left linker Wert
   * @param right rechter Wert
   * @return {@code true}, wenn beide fachlich gleich sind
   */
  private boolean sameBibValue(BibValue left, BibValue right) {
    if (left == null || right == null) {
      return false;
    }
    if (!Objects.equals(normalize(left.bibtexName()), normalize(right.bibtexName()))) {
      return false;
    }
    if (left.valueType() != right.valueType()) {
      return false;
    }

    return switch (left.valueType()) {
      case STRING -> sameStringValue(left, right);
      case INTEGER -> sameIntegerValue(left, right);
      case PERSON -> samePersonValue(left, right);
      case RELATED -> sameRelatedValue(left, right);
    };
  }

  private boolean sameStringValue(BibValue left, BibValue right) {
    String lv = left instanceof StringBibValue l ? normalizeNullable(l.value()) : normalizeNullable(String.valueOf(left.value()));
    String rv = right instanceof StringBibValue r ? normalizeNullable(r.value()) : normalizeNullable(String.valueOf(right.value()));
    return Objects.equals(normalize(lv), normalize(rv));
  }

  private boolean sameIntegerValue(BibValue left, BibValue right) {
    Integer lv = left instanceof IntegerBibValue l ? l.value() : (Integer) left.value();
    Integer rv = right instanceof IntegerBibValue r ? r.value() : (Integer) right.value();
    return Objects.equals(lv, rv);
  }

  /**
   * Vergleicht zwei Personenwerte nach bereinigten, fachlich befüllten
   * Personenlisten.
   *
   * @param left linker Wert
   * @param right rechter Wert
   * @return {@code true}, wenn beide Personenlisten fachlich gleich sind
   */
  private boolean samePersonValue(BibValue left, BibValue right) {
    List<PersonRef> lp = normalizePersonList(left instanceof PersonBibValue l ? l.value() : List.of());
    List<PersonRef> rp = normalizePersonList(right instanceof PersonBibValue r ? r.value() : List.of());

    if (lp.size() != rp.size()) {
      return false;
    }

    for (int i = 0; i < lp.size(); i++) {
      PersonRef lpr = lp.get(i);
      PersonRef rpr = rp.get(i);

      if (!Objects.equals(normalize(lpr.lastName()), normalize(rpr.lastName()))
              || !Objects.equals(normalize(lpr.firstName()), normalize(rpr.firstName()))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Entfernt leere Personen aus einer Personenliste und gibt eine stabile
   * Vergleichsliste zurück.
   *
   * @param persons ursprüngliche Personenliste
   * @return bereinigte Personenliste
   */
  private List<PersonRef> normalizePersonList(List<PersonRef> persons) {
    if (persons == null || persons.isEmpty()) {
      return List.of();
    }

    return persons.stream()
               .filter(Objects::nonNull)
               .filter(person -> !sanitize(person.firstName()).isBlank()
                                     || !sanitize(person.lastName()).isBlank())
               .toList();
  }

  private boolean sameRelatedValue(BibValue left, BibValue right) {
    Integer lv = left instanceof RelatedBibValue l ? l.relatedEntryId() : null;
    Integer rv = right instanceof RelatedBibValue r ? r.relatedEntryId() : null;
    return Objects.equals(lv, rv);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Lädt den Basiseintrag aus {@code bibliography_entries} einschließlich der
   * Medientyp-Information.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Datenbank-ID des Eintrags
   * @return optionaler Basiseintrag
   * @throws SQLException bei Datenbankfehlern
   */
  private Optional<DynamicBibliographyEntry> loadBaseEntry(Connection c, int entryId) throws SQLException {
    String sql = """
        SELECT be.id,
               be.citekey,
               be.created_at,
               be.updated_at,
               be.media_types_id,
               mt.name AS media_type_name,
               mt.bib_name AS media_type_bib_name
        FROM bibliography_entries be
        LEFT JOIN media_types mt
          ON mt.id = be.media_types_id
        WHERE be.id = ?
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entryId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }

        DynamicBibliographyEntry entry = new DynamicBibliographyEntry();
        entry.setId(rs.getInt("id"));

        Integer mediaTypeId = (Integer) rs.getObject("media_types_id");
        entry.setMediaTypeId(mediaTypeId);
        entry.setMediaTypeName(rs.getString("media_type_name"));
        entry.setMediaTypeBibName(rs.getString("media_type_bib_name"));
        entry.setCitekey(rs.getString("citekey"));
        entry.setCreatedAt(parseDateTime(rs.getString("created_at")));
        entry.setUpdatedAt(parseDateTime(rs.getString("updated_at")));

        return Optional.of(entry);
      }
    }
  }

  /**
   * Lädt alle String-Werte eines Eintrags.
   *
   * @param c aktive Datenbankverbindung
   * @param entry Zielobjekt
   * @throws SQLException bei Datenbankfehlern
   */
  private void loadStringValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    String sql = """
        SELECT bes.bibtex_type_id,
               bes.entry,
               bt.bibtex_name
        FROM bibliography_entries_string bes
        JOIN bibtex_type bt
          ON bt.id = bes.bibtex_type_id
        WHERE bes.entries_id = ?
        ORDER BY bt.id
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entry.getId());

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          entry.putValue(new StringBibValue(
              rs.getString("bibtex_name"),
              rs.getString("entry")
          ));
        }
      }
    }
  }

  /**
   * Lädt alle Integer- und Related-Werte eines Eintrags.
   * Personenfelder werden hier bewusst ausgeschlossen und ausschließlich über
   * {@link #loadPersonValues(Connection, DynamicBibliographyEntry)} geladen.
   *
   * @param c aktive Datenbankverbindung
   * @param entry Zielobjekt
   * @throws SQLException bei Datenbankfehlern
   */
  private void loadIntegerAndRelatedValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    String sql = """
    SELECT bei.bibtex_type_id,
           bei.entry,
           bt.bibtex_name
    FROM bibliography_entries_integer bei
    JOIN bibtex_type bt
      ON bt.id = bei.bibtex_type_id
    WHERE bei.entries_id = ?
      AND LOWER(TRIM(bt.datatype)) <> 'person'
    ORDER BY bt.id
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entry.getId());

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          int bibtexTypeId = rs.getInt("bibtex_type_id");
          Integer value = (Integer) rs.getObject("entry");
          String bibtexName = rs.getString("bibtex_name");

          Optional<RelatedMediaDefinition> relatedDefinition =
              metadataRepository.loadRelatedDefinition(bibtexTypeId);

          if (relatedDefinition.isPresent()) {
            RelatedMediaDefinition definition = relatedDefinition.get();
            Integer relatedMediaTypeId = definition.targetMediaTypeDefinition().id();
            Integer displayBibtexTypeId = definition.displayFieldDefinition().id();
            String displayValue = loadRelatedDisplayValue(c, value, displayBibtexTypeId);

            entry.putValue(new RelatedBibValue(
                bibtexName,
                value,
                relatedMediaTypeId,
                displayValue
            ));
          } else {
            entry.putValue(new IntegerBibValue(bibtexName, value));
          }
        }
      }
    }
  }

  /**
   * Lädt alle Personenfelder eines Eintrags ausschließlich über die neue
   * feldspezifische Indirektion
   * {@code bibliography_entries_integer -> author_mapping}.
   *
   * @param c aktive Datenbankverbindung
   * @param entry Zielobjekt
   * @throws SQLException bei Datenbankfehlern
   */
  private void loadPersonValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    Map<String, List<PersonRef>> personsByBibtexName = new LinkedHashMap<>();

    String sql = """
        SELECT bt.bibtex_name,
               bei.entry AS person_group_id,
               a.id,
               a.first_name,
               a.last_name,
               am.author_pos
        FROM bibliography_entries_integer bei
        JOIN bibtex_type bt
          ON bt.id = bei.bibtex_type_id
        JOIN author_mapping am
          ON am.person_group_id = bei.entry
        JOIN authors a
          ON a.id = am.author_id
        WHERE bei.entries_id = ?
          AND LOWER(TRIM(bt.datatype)) = 'person'
        ORDER BY bt.id, am.author_pos ASC
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entry.getId());

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String bibtexName = sanitize(rs.getString("bibtex_name"));
          if (bibtexName.isBlank()) {
            continue;
          }

          personsByBibtexName
              .computeIfAbsent(bibtexName, ignored -> new ArrayList<>())
              .add(new PersonRef(
                  rs.getInt("id"),
                  rs.getString("first_name"),
                  rs.getString("last_name"),
                  rs.getInt("author_pos")
              ));
        }
      }
    }

    /*
     * Personen werden ausschließlich über den feldspezifischen
     * person_group-Pfad des neuen dynamischen Modells geladen.
     */

    for (Map.Entry<String, List<PersonRef>> group : personsByBibtexName.entrySet()) {
      if (!group.getValue().isEmpty()) {
        entry.putValue(new PersonBibValue(group.getKey(), group.getValue()));
      }
    }
  }

  /**
   * Speichert den Basiseintrag in {@code bibliography_entries}.
   *
   * @param c aktive Datenbankverbindung
   * @param entry zu speichernder Eintrag
   * @return Datenbank-ID des Eintrags
   * @throws SQLException bei Datenbankfehlern
   */
  private int saveBaseEntry(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    Integer existingId = entry.getId();
    String legacyTitle = extractLegacyTitle(entry);
    String now = Instant.now().toString();

    if (existingId == null || existingId <= 0) {
      String insertSql = """
        INSERT INTO bibliography_entries(title, citekey, media_types_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?)
        """;

      try (PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, legacyTitle);
        ps.setString(2, normalizeNullable(entry.getCitekey()));

        if (entry.getMediaTypeId() == null) {
          ps.setNull(3, Types.INTEGER);
        } else {
          ps.setInt(3, entry.getMediaTypeId());
        }

        ps.setString(4, now);
        ps.setNull(5, Types.VARCHAR);
        ps.executeUpdate();

        try (ResultSet keys = ps.getGeneratedKeys()) {
          if (!keys.next()) {
            throw new IllegalStateException("Keine Bibliographie-ID von SQLite erhalten.");
          }
          int newId = keys.getInt(1);
          entry.setCreatedAt(parseDateTime(now));
          entry.setUpdatedAt(null);
          return newId;
        }
      }
    }

    String updateSql = """
      UPDATE bibliography_entries
      SET title = ?,
          citekey = ?,
          media_types_id = ?,
          updated_at = ?
      WHERE id = ?
      """;

    try (PreparedStatement ps = c.prepareStatement(updateSql)) {
      ps.setString(1, legacyTitle);
      ps.setString(2, normalizeNullable(entry.getCitekey()));

      if (entry.getMediaTypeId() == null) {
        ps.setNull(3, Types.INTEGER);
      } else {
        ps.setInt(3, entry.getMediaTypeId());
      }

      ps.setString(4, now);
      ps.setInt(5, existingId);
      ps.executeUpdate();
    }

    entry.setUpdatedAt(parseDateTime(now));
    return existingId;
  }

  /**
   * Entfernt alle String-Werte eines Eintrags vor dem erneuten Schreiben.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Datenbank-ID des Eintrags
   * @throws SQLException bei Datenbankfehlern
   */
  private void deleteStringValues(Connection c, int entryId) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(
        "DELETE FROM bibliography_entries_string WHERE entries_id = ?")) {
      ps.setInt(1, entryId);
      ps.executeUpdate();
    }
  }

  /**
   * Entfernt alle nicht-personbezogenen Integer-Werte eines Eintrags vor dem
   * erneuten Schreiben.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Datenbank-ID des Eintrags
   * @throws SQLException bei Datenbankfehlern
   */
  private void deleteNonPersonIntegerValues(Connection c, int entryId) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("""
        DELETE FROM bibliography_entries_integer
        WHERE entries_id = ?
          AND bibtex_type_id NOT IN (
              SELECT id
              FROM bibtex_type
              WHERE LOWER(TRIM(datatype)) = 'person'
          )
        """)) {
      ps.setInt(1, entryId);
      ps.executeUpdate();
    }
  }

  /**
   * Schreibt alle String-Werte eines Eintrags in
   * {@code bibliography_entries_string}.
   *
   * @param c aktive Datenbankverbindung
   * @param entry zu speichernder Eintrag
   * @throws SQLException bei Datenbankfehlern
   */
  private void persistStringValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    String sql = """
        INSERT INTO bibliography_entries_string(entries_id, bibtex_type_id, entry)
        VALUES (?, ?, ?)
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (BibValue value : entry.getOrderedValues()) {
        if (!(value instanceof StringBibValue(String bibtexName, String value1))) {
          continue;
        }

        Integer bibtexTypeId = loadBibtexTypeIdByName(c, bibtexName);
        if (bibtexTypeId == null) {
          continue;
        }

        String normalizedValue = normalizeNullable(value1);
        if (normalizedValue == null || normalizedValue.isBlank()) {
          continue;
        }

        ps.setInt(1, entry.getId());
        ps.setInt(2, bibtexTypeId);
        ps.setString(3, normalizedValue);
        ps.addBatch();
      }

      ps.executeBatch();
    }
  }

  /**
   * Schreibt alle Integer- und Related-Werte eines Eintrags in
   * {@code bibliography_entries_integer}.
   *
   * @param c aktive Datenbankverbindung
   * @param entry zu speichernder Eintrag
   * @throws SQLException bei Datenbankfehlern
   */
  private void persistIntegerAndRelatedValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    String sql = """
        INSERT INTO bibliography_entries_integer(entries_id, bibtex_type_id, entry)
        VALUES (?, ?, ?)
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (BibValue value : entry.getOrderedValues()) {
        Integer bibtexTypeId = null;
        Integer integerValue = null;

        if (value instanceof IntegerBibValue integerBibValue) {
          bibtexTypeId = loadBibtexTypeIdByName(c, integerBibValue.bibtexName());
          integerValue = integerBibValue.value();
        } else if (value instanceof RelatedBibValue relatedBibValue) {
          bibtexTypeId = loadBibtexTypeIdByName(c, relatedBibValue.bibtexName());
          integerValue = relatedBibValue.relatedEntryId();
        }

        if (bibtexTypeId == null) {
          continue;
        }
        if (integerValue == null) {
          continue;
        }

        ps.setInt(1, entry.getId());
        ps.setInt(2, bibtexTypeId);
        ps.setInt(3, integerValue);

        ps.addBatch();
      }

      ps.executeBatch();
    }
  }

  /**
   * Schreibt alle Personenfelder eines Eintrags, wobei bestehende
   * Personenlisten-IDs stabil erhalten bleiben.
   *
   * Leere oder vollständig ungültige Personenlisten erzeugen dabei keine
   * verwaisten person_group-Referenzen mehr.
   *
   * @param c aktive Datenbankverbindung
   * @param entry zu speichernder Eintrag
   * @throws SQLException bei Datenbankfehlern
   */
  private void persistPersonValues(Connection c, DynamicBibliographyEntry entry) throws SQLException {
    List<PersonBibValue> personValues = entry.getOrderedValues().stream()
                                            .filter(PersonBibValue.class::isInstance)
                                            .map(PersonBibValue.class::cast)
                                            .toList();

    Map<Integer, Integer> existingGroupsByBibtexTypeId = loadExistingPersonGroupIds(c, entry.getId());

    if (personValues.isEmpty()) {
      deleteExistingPersonValues(c, entry.getId());
      return;
    }

    Set<Integer> handledBibtexTypeIds = new LinkedHashSet<>();

    for (PersonBibValue personValue : personValues) {
      Integer bibtexTypeId = loadBibtexTypeIdByName(c, personValue.bibtexName());
      if (bibtexTypeId == null) {
        continue;
      }

      handledBibtexTypeIds.add(bibtexTypeId);

      if (!hasMeaningfulPersons(personValue.value())) {
        deletePersonGroupReference(c, entry.getId(), bibtexTypeId);
        continue;
      }

      Integer existingGroupId = existingGroupsByBibtexTypeId.get(bibtexTypeId);
      if (existingGroupId != null && existingGroupId > 0) {
        int insertedCount = replacePersonGroupContents(c, existingGroupId, personValue.value());

        if (insertedCount <= 0) {
          deletePersonGroupReference(c, entry.getId(), bibtexTypeId);
        } else {
          persistPersonGroupReference(c, entry.getId(), bibtexTypeId, existingGroupId);
        }
      } else {
        int personGroupId = allocateNextPersonGroupId(c);
        int insertedCount = persistPersonGroup(c, personGroupId, personValue.value());

        if (insertedCount > 0) {
          persistPersonGroupReference(c, entry.getId(), bibtexTypeId, personGroupId);
        }
      }
    }

    removeObsoletePersonValues(c, entry.getId(), existingGroupsByBibtexTypeId, handledBibtexTypeIds);

    /*
     * Legacy-Direktbestände werden nicht mehr im Repository bereinigt.
     * Eine eventuelle Altbereinigung erfolgt ausschließlich über
     * kontrollierte Datenmigrationen.
     */
  }

  /**
   * Prüft, ob eine Personenliste mindestens eine fachlich befüllte Person enthält.
   *
   * @param persons Personenliste
   * @return {@code true}, wenn mindestens Vor- oder Nachname gesetzt ist
   */
  private boolean hasMeaningfulPersons(List<PersonRef> persons) {
    if (persons == null || persons.isEmpty()) {
      return false;
    }

    for (PersonRef person : persons) {
      if (person == null) {
        continue;
      }

      if (!sanitize(person.firstName()).isBlank()
              || !sanitize(person.lastName()).isBlank()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Lädt die bereits vorhandenen Personenlisten-IDs eines Eintrags je
   * Personenfeld.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Bibliographie-ID
   * @return Zuordnung von bibtex_type_id zu person_group_id
   * @throws SQLException bei Datenbankfehlern
   */
  private Map<Integer, Integer> loadExistingPersonGroupIds(Connection c, int entryId) throws SQLException {
    Map<Integer, Integer> result = new LinkedHashMap<>();

    String sql = """
        SELECT bei.bibtex_type_id, bei.entry
        FROM bibliography_entries_integer bei
        JOIN bibtex_type bt
          ON bt.id = bei.bibtex_type_id
        WHERE bei.entries_id = ?
          AND LOWER(TRIM(bt.datatype)) = 'person'
          AND bei.entry IS NOT NULL
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, entryId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Integer bibtexTypeId = (Integer) rs.getObject("bibtex_type_id");
          Integer personGroupId = (Integer) rs.getObject("entry");

          if (bibtexTypeId != null && personGroupId != null && personGroupId > 0) {
            result.put(bibtexTypeId, personGroupId);
          }
        }
      }
    }

    return result;
  }

  /**
   * Ersetzt die Inhalte einer bestehenden Personenliste, ohne deren ID zu ändern.
   *
   * @param c aktive Datenbankverbindung
   * @param personGroupId bestehende Personenlisten-ID
   * @param persons neue Personenliste
   * @return Anzahl tatsächlich neu geschriebener author_mapping-Zeilen
   * @throws SQLException bei Datenbankfehlern
   */
  private int replacePersonGroupContents(Connection c,
                                         int personGroupId,
                                         List<PersonRef> persons) throws SQLException {
    try (PreparedStatement del = c.prepareStatement(
        "DELETE FROM author_mapping WHERE person_group_id = ?")) {
      del.setInt(1, personGroupId);
      del.executeUpdate();
    }

    return persistPersonGroup(c, personGroupId, persons);
  }

  /**
   * Entfernt Personenfelder, die früher vorhanden waren, im aktuellen Eintrag
   * aber nicht mehr vorkommen.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Bibliographie-ID
   * @param existingGroupsByBibtexTypeId bisherige Personenfelder
   * @param handledBibtexTypeIds aktuell weiterverwendete Personenfelder
   * @throws SQLException bei Datenbankfehlern
   */
  private void removeObsoletePersonValues(Connection c,
                                          int entryId,
                                          Map<Integer, Integer> existingGroupsByBibtexTypeId,
                                          Set<Integer> handledBibtexTypeIds) throws SQLException {
    for (Map.Entry<Integer, Integer> existing : existingGroupsByBibtexTypeId.entrySet()) {
      Integer bibtexTypeId = existing.getKey();
      Integer personGroupId = existing.getValue();

      if (handledBibtexTypeIds.contains(bibtexTypeId)) {
        continue;
      }

      try (PreparedStatement delRef = c.prepareStatement("""
          DELETE FROM bibliography_entries_integer
          WHERE entries_id = ?
            AND bibtex_type_id = ?
          """)) {
        delRef.setInt(1, entryId);
        delRef.setInt(2, bibtexTypeId);
        delRef.executeUpdate();
      }

      if (personGroupId != null && personGroupId > 0) {
        try (PreparedStatement delGroup = c.prepareStatement(
            "DELETE FROM author_mapping WHERE person_group_id = ?")) {
          delGroup.setInt(1, personGroupId);
          delGroup.executeUpdate();
        }
      }
    }
  }

  /**
   * Löscht alle bereits gespeicherten Personenfeld-Zuordnungen eines Eintrags
   * einschließlich der zugehörigen Personenlisten.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Bibliographie-ID
   * @throws SQLException bei Datenbankfehlern
   */
  private void deleteExistingPersonValues(Connection c, int entryId) throws SQLException {
    List<Integer> personGroupIds = new ArrayList<>();

    String selectSql = """
        SELECT bei.entry
        FROM bibliography_entries_integer bei
        JOIN bibtex_type bt
          ON bt.id = bei.bibtex_type_id
        WHERE bei.entries_id = ?
          AND LOWER(TRIM(bt.datatype)) = 'person'
          AND bei.entry IS NOT NULL
        """;

    try (PreparedStatement ps = c.prepareStatement(selectSql)) {
      ps.setInt(1, entryId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          Integer personGroupId = (Integer) rs.getObject("entry");
          if (personGroupId != null && personGroupId > 0) {
            personGroupIds.add(personGroupId);
          }
        }
      }
    }

    try (PreparedStatement delRefs = c.prepareStatement("""
        DELETE FROM bibliography_entries_integer
        WHERE entries_id = ?
          AND bibtex_type_id IN (
              SELECT id
              FROM bibtex_type
              WHERE LOWER(TRIM(datatype)) = 'person'
          )
        """)) {
      delRefs.setInt(1, entryId);
      delRefs.executeUpdate();
    }

    if (!personGroupIds.isEmpty()) {
      try (PreparedStatement delGroups = c.prepareStatement(
          "DELETE FROM author_mapping WHERE person_group_id = ?")) {
        for (Integer personGroupId : personGroupIds) {
          delGroups.setInt(1, personGroupId);
          delGroups.addBatch();
        }
        delGroups.executeBatch();
      }
    }

    /*
     * Legacy-Direktbestände werden nicht im Repository gelöscht.
     * Eine eventuelle Altbereinigung erfolgt ausschließlich über
     * kontrollierte Datenmigrationen.
     */
  }

  /**
   * Ermittelt die nächste freie Personenlisten-ID.
   * Berücksichtigt sowohl vorhandene Gruppen in author_mapping als auch
   * bereits referenzierte Personengruppen in bibliography_entries_integer.
   *
   * @param c aktive Datenbankverbindung
   * @return nächste freie Personenlisten-ID
   * @throws SQLException bei Datenbankfehlern
   */
  private int allocateNextPersonGroupId(Connection c) throws SQLException {
    String sql = """
        SELECT COALESCE(MAX(id_value), 0) + 1 AS next_id
        FROM (
            SELECT person_group_id AS id_value
            FROM author_mapping

            UNION

            SELECT bei.entry AS id_value
            FROM bibliography_entries_integer bei
            JOIN bibtex_type bt
              ON bt.id = bei.bibtex_type_id
            WHERE LOWER(TRIM(bt.datatype)) = 'person'
              AND bei.entry IS NOT NULL
        )
        """;

    try (PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      if (!rs.next()) {
        return 1;
      }
      return Math.max(1, rs.getInt("next_id"));
    }
  }

  /**
   * Schreibt eine Personenliste in {@code author_mapping}.
   *
   * @param c aktive Datenbankverbindung
   * @param personGroupId ID der Personenliste
   * @param persons Personenliste
   * @return Anzahl tatsächlich geschriebener author_mapping-Zeilen
   * @throws SQLException bei Datenbankfehlern
   */
  private int persistPersonGroup(Connection c,
                                 int personGroupId,
                                 List<PersonRef> persons) throws SQLException {
    if (persons == null || persons.isEmpty()) {
      return 0;
    }

    int insertedCount = 0;

    try (PreparedStatement ins = c.prepareStatement(
        "INSERT INTO author_mapping(person_group_id, author_id, author_pos) VALUES (?, ?, ?)")) {
      int position = 1;
      for (PersonRef personRef : persons) {
        Integer authorId = ensureAuthorId(c, personRef);
        if (authorId == null) {
          continue;
        }

        ins.setInt(1, personGroupId);
        ins.setInt(2, authorId);
        ins.setInt(3, position++);
        ins.addBatch();
        insertedCount++;
      }

      if (insertedCount > 0) {
        ins.executeBatch();
      }
    }

    return insertedCount;
  }

  /**
   * Verknüpft ein Personenfeld eines Bibliografie-Eintrags mit einer
   * Personenlisten-ID in {@code bibliography_entries_integer}.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Bibliographie-ID
   * @param bibtexTypeId Feld-ID des Personenfeldes
   * @param personGroupId Personenlisten-ID
   * @throws SQLException bei Datenbankfehlern
   */
  private void persistPersonGroupReference(Connection c,
                                           int entryId,
                                           int bibtexTypeId,
                                           int personGroupId) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("""
        INSERT INTO bibliography_entries_integer(entries_id, bibtex_type_id, entry)
        VALUES (?, ?, ?)
        ON CONFLICT(entries_id, bibtex_type_id) DO UPDATE SET
            entry = excluded.entry
        """)) {
      ps.setInt(1, entryId);
      ps.setInt(2, bibtexTypeId);
      ps.setInt(3, personGroupId);
      ps.executeUpdate();
    }
  }

  /**
   * Entfernt die Referenz eines Personenfeldes auf eine Personenlisten-ID
   * aus {@code bibliography_entries_integer}.
   *
   * @param c aktive Datenbankverbindung
   * @param entryId Bibliographie-ID
   * @param bibtexTypeId Feld-ID des Personenfeldes
   * @throws SQLException bei Datenbankfehlern
   */
  private void deletePersonGroupReference(Connection c,
                                          int entryId,
                                          int bibtexTypeId) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("""
        DELETE FROM bibliography_entries_integer
        WHERE entries_id = ?
          AND bibtex_type_id = ?
        """)) {
      ps.setInt(1, entryId);
      ps.setInt(2, bibtexTypeId);
      ps.executeUpdate();
    }
  }

  /**
   * Sorgt dafür, dass ein Autor in der Tabelle {@code authors} vorhanden ist.
   *
   * @param c aktive Datenbankverbindung
   * @param personRef Personendaten
   * @return ID des Autors oder {@code null}, wenn Vor- und Nachname leer sind
   * @throws SQLException bei Datenbankfehlern
   */
  private Integer ensureAuthorId(Connection c, PersonRef personRef) throws SQLException {
    String firstName = sanitize(personRef.firstName());
    String lastName = sanitize(personRef.lastName());

    if (firstName.isBlank() && lastName.isBlank()) {
      return null;
    }

    try (PreparedStatement sel = c.prepareStatement(
        "SELECT id FROM authors WHERE first_name = ? AND last_name = ?")) {
      sel.setString(1, firstName);
      sel.setString(2, lastName);

      try (ResultSet rs = sel.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1);
        }
      }
    }

    try (PreparedStatement ins = c.prepareStatement(
        "INSERT INTO authors(first_name, last_name) VALUES(?, ?)",
        Statement.RETURN_GENERATED_KEYS)) {
      ins.setString(1, firstName);
      ins.setString(2, lastName);
      ins.executeUpdate();

      try (ResultSet keys = ins.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new IllegalStateException("Keine Author-ID erhalten.");
        }
        return keys.getInt(1);
      }
    }
  }

  /**
   * Lädt die ID eines BibTeX-Feldes über seinen technischen Namen.
   *
   * @param c aktive Datenbankverbindung
   * @param bibtexName technischer Feldname
   * @return Feld-ID oder {@code null}
   * @throws SQLException bei Datenbankfehlern
   */
  private Integer loadBibtexTypeIdByName(Connection c, String bibtexName) throws SQLException {
    String sql = """
        SELECT id
        FROM bibtex_type
        WHERE LOWER(TRIM(bibtex_name)) = LOWER(TRIM(?))
        """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, sanitize(bibtexName));

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt("id") : null;
      }
    }
  }

  /**
   * Lädt den Anzeigetext eines verknüpften Related-Zielmediums über die in
   * {@code related_media_type} konfigurierte Anzeige-Spalte.
   * Fällt bei leerem dynamischem Wert auf {@code bibliography_entries.title}
   * zurück.
   *
   * @param c aktive Datenbankverbindung
   * @param relatedEntryId ID des Zielmediums
   * @param displayBibtexTypeId BibTeX-Feld-ID des Anzeigeattributs
   * @return Anzeigetext oder leerer String
   * @throws SQLException bei Datenbankfehlern
   */
  private String loadRelatedDisplayValue(Connection c,
                                         Integer relatedEntryId,
                                         Integer displayBibtexTypeId) throws SQLException {
    if (relatedEntryId == null || relatedEntryId <= 0) {
      return "";
    }

    if (displayBibtexTypeId != null && displayBibtexTypeId > 0) {
      String sql = """
          SELECT entry
          FROM bibliography_entries_string
          WHERE entries_id = ?
            AND bibtex_type_id = ?
          """;

      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, relatedEntryId);
        ps.setInt(2, displayBibtexTypeId);

        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            String value = sanitize(rs.getString("entry"));
            if (!value.isBlank()) {
              return value;
            }
          }
        }
      }
    }

    String fallbackSql = """
        SELECT title
        FROM bibliography_entries
        WHERE id = ?
        """;

    try (PreparedStatement ps = c.prepareStatement(fallbackSql)) {
      ps.setInt(1, relatedEntryId);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return sanitize(rs.getString("title"));
        }
      }
    }

    return "";
  }

  /**
   * Ermittelt für die Altstruktur einen kompatiblen Titelwert.
   * <p>
   * Vorrang hat das neue Feld {@code title}; fehlt dieses, wird der leere String
   * verwendet.
   *
   * @param entry bibliografischer Eintrag
   * @return Alt-kompatibler Titelwert
   */
  private String extractLegacyTitle(DynamicBibliographyEntry entry) {
    return entry.findValue("title")
               .filter(StringBibValue.class::isInstance)
               .map(StringBibValue.class::cast)
               .map(StringBibValue::value)
               .map(this::sanitize)
               .orElse("");
  }

  /**
   * Parst einen Datenbank-Zeitstempel tolerant gegen mehrere Formate.
   *
   * @param raw roher Datenbankwert
   * @return geparstes {@link LocalDateTime} oder {@code null}
   */
  private LocalDateTime parseDateTime(String raw) {
    String value = sanitize(raw);
    if (value.isBlank()) {
      return null;
    }

    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
      // nächster Versuch
    }

    try {
      return LocalDateTime.parse(value, SQLITE_TS);
    } catch (DateTimeParseException ignored) {
      // nächster Versuch
    }

    try {
      return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  /**
   * Führt eine ID-Suche mit gemischten Parametertypen aus.
   *
   * @param sql SQL-Abfrage
   * @param parameters geordnete Parameterliste
   * @return gefundene Eintrags-IDs
   */
  private List<Integer> executeEntryIdQuery(String sql, List<Object> parameters) {
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      for (int i = 0; i < parameters.size(); i++) {
        Object parameter = parameters.get(i);
        if (parameter instanceof Integer integer) {
          ps.setInt(i + 1, integer);
        } else {
          ps.setString(i + 1, String.valueOf(parameter));
        }
      }

      try (ResultSet rs = ps.executeQuery()) {
        List<Integer> result = new ArrayList<>();
        while (rs.next()) {
          result.add(rs.getInt(1));
        }
        return result;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Feldbasierte Bibliographie-Suche konnte nicht ausgeführt werden.", ex);
    }
  }

  /**
   * Wandelt einen String in einen für JDBC geeigneten nullable-Wert um.
   *
   * @param value Eingabewert
   * @return bereinigter Wert oder {@code null}
   */
  private String normalizeNullable(String value) {
    String sanitized = sanitize(value);
    return sanitized.isBlank() ? null : sanitized;
  }

  private List<Author> normalizeAuthors(List<Author> authors) {
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }

    List<Author> normalized = new ArrayList<>();
    for (Author author : authors) {
      if (author == null) {
        continue;
      }
      String firstName = sanitize(author.getFirstName());
      String lastName = sanitize(author.getLastName());
      if (firstName.isBlank() && lastName.isBlank()) {
        continue;
      }
      Author copy = new Author();
      copy.setFirstName(firstName);
      copy.setLastName(lastName);
      normalized.add(copy);
    }
    return normalized;
  }

  private List<Integer> normalizeIds(Collection<Integer> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return ids.stream()
               .filter(Objects::nonNull)
               .filter(id -> id > 0)
               .distinct()
               .toList();
  }

  /**
   * Bereinigt einen String defensiv.
   *
   * @param value Eingabewert
   * @return bereinigter String oder leerer String
   */
  private String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}
