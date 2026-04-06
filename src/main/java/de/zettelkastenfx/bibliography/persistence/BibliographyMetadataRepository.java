package de.zettelkastenfx.bibliography.persistence;

import de.zettelkastenfx.bibliography.model.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Lädt die bibliographischen Metadaten aus den neuen Steuerungstabellen.
 * <p>
 * Dieses Repository bildet das datenbankseitige Metamodell ab und liefert:
 * <ul>
 *   <li>Medientypen</li>
 *   <li>BibTeX-Felddefinitionen</li>
 *   <li>Attributzuordnungen je Medientyp</li>
 *   <li>Related-Definitionen</li>
 *   <li>Enum-Werte</li>
 * </ul>
 */
public class BibliographyMetadataRepository {

  private final DataSource ds;

  /**
   * Erzeugt das Repository mit der verwendeten Datenquelle.
   *
   * @param ds Datenquelle der Anwendung
   */
  public BibliographyMetadataRepository(DataSource ds) {
    this.ds = Objects.requireNonNull(ds, "ds");
  }

  /**
   * Lädt alle Medientypen in stabiler Reihenfolge nach ID.
   *
   * @return alle Medientypen
   */
  public List<MediaTypeDefinition> loadMediaTypes() {
    String sql = """
        SELECT id, name, bib_name, direct_link
        FROM media_types
        ORDER BY id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<MediaTypeDefinition> result = new ArrayList<>();
      while (rs.next()) {
        result.add(mapMediaType(rs));
      }
      return List.copyOf(result);
    } catch (SQLException ex) {
      throw new IllegalStateException("Medientypen konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt einen Medientyp anhand seiner ID.
   *
   * @param id Datenbank-ID des Medientyps
   * @return optionaler Medientyp
   */
  public Optional<MediaTypeDefinition> loadMediaTypeById(int id) {
    String sql = """
        SELECT id, name, bib_name, direct_link
        FROM media_types
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapMediaType(rs)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Medientyp mit ID " + id + " konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt einen Medientyp anhand seines technischen BibTeX-Namens.
   *
   * @param bibName technischer Medientypname
   * @return optionaler Medientyp
   */
  public Optional<MediaTypeDefinition> loadMediaTypeByBibName(String bibName) {
    String sql = """
        SELECT id, name, bib_name, direct_link
        FROM media_types
        WHERE LOWER(TRIM(bib_name)) = LOWER(TRIM(?))
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, sanitize(bibName));

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapMediaType(rs)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Medientyp mit bib_name '" + bibName + "' konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt ein BibTeX-Feld anhand seines technischen Namens.
   *
   * @param bibtexName technischer BibTeX-Name des Feldes
   * @return optionale Felddefinition
   */
  public Optional<BibtexFieldDefinition> loadBibtexTypeByBibName(String bibtexName) {
    String sql = """
        SELECT id, bibtex_name, name, datatype, requires
        FROM bibtex_type
        WHERE LOWER(TRIM(bibtex_name)) = LOWER(TRIM(?))
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, sanitize(bibtexName));

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapBibtexField(rs)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "BibTeX-Feld mit bibtex_name '" + bibtexName + "' konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt alle definierten BibTeX-Felder in stabiler Reihenfolge nach ID.
   *
   * @return alle Felddefinitionen
   */
  public List<BibtexFieldDefinition> loadBibtexTypes() {
    String sql = """
        SELECT id, bibtex_name, name, datatype, requires
        FROM bibtex_type
        ORDER BY id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

      List<BibtexFieldDefinition> result = new ArrayList<>();
      while (rs.next()) {
        result.add(mapBibtexField(rs));
      }
      return List.copyOf(result);
    } catch (SQLException ex) {
      throw new IllegalStateException("BibTeX-Felddefinitionen konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt ein BibTeX-Feld anhand seiner ID.
   *
   * @param id Datenbank-ID des Feldes
   * @return optionale Felddefinition
   */
  public Optional<BibtexFieldDefinition> loadBibtexTypeById(int id) {
    String sql = """
        SELECT id, bibtex_name, name, datatype, requires
        FROM bibtex_type
        WHERE id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapBibtexField(rs)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("BibTeX-Feld mit ID " + id + " konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt alle Attributdefinitionen für einen Medientyp in der Reihenfolge der
   * BibTeX-Feld-IDs.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Attributdefinitionen des Medientyps
   */
  public List<MediaAttributeDefinition> loadAttributesForMediaType(int mediaTypeId) {
    String sql = """
        SELECT ma.media_types_id,
               ma.bibtex_type_id,
               ma.mode,
               bt.id AS bt_id,
               bt.bibtex_name,
               bt.name,
               bt.datatype,
               bt.requires
        FROM media_attributes ma
        JOIN bibtex_type bt
          ON bt.id = ma.bibtex_type_id
        WHERE ma.media_types_id = ?
        ORDER BY bt.id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, mediaTypeId);

      try (ResultSet rs = ps.executeQuery()) {
        List<MediaAttributeDefinition> result = new ArrayList<>();
        while (rs.next()) {
          BibtexFieldDefinition fieldDefinition = new BibtexFieldDefinition(
              rs.getInt("bt_id"),
              rs.getString("bibtex_name"),
              rs.getString("name"),
              rs.getString("datatype"),
              rs.getString("requires")
          );

          result.add(new MediaAttributeDefinition(
              rs.getInt("media_types_id"),
              rs.getInt("bibtex_type_id"),
              rs.getString("mode"),
              fieldDefinition
          ));
        }
        return List.copyOf(result);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "Attribute für Medientyp " + mediaTypeId + " konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt alle Lookup-Definitionen eines Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @return Lookup-Definitionen des Medientyps
   */
  public List<MediaAttributeLookupDefinition> loadLookupDefinitionsForMediaType(int mediaTypeId) {
    String sql = """
        SELECT mal.media_types_id,
               mal.bibtex_type_id,
               mal.lookup_strategy,
               mal.min_query_length,
               mal.auto_resolve_unique,
               mal.allow_free_text,

               mt.id AS mt_id,
               mt.name AS mt_name,
               mt.bib_name AS mt_bib_name,
               mt.direct_link AS mt_direct_link,

               bt.id AS bt_id,
               bt.bibtex_name,
               bt.name,
               bt.datatype,
               bt.requires
        FROM media_attribute_lookup mal
        JOIN media_types mt
          ON mt.id = mal.media_types_id
        JOIN bibtex_type bt
          ON bt.id = mal.bibtex_type_id
        WHERE mal.media_types_id = ?
        ORDER BY bt.id
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, mediaTypeId);

      try (ResultSet rs = ps.executeQuery()) {
        List<MediaAttributeLookupDefinition> result = new ArrayList<>();
        while (rs.next()) {
          result.add(mapMediaAttributeLookup(rs));
        }
        return List.copyOf(result);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "Lookup-Definitionen für Medientyp " + mediaTypeId + " konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt die Lookup-Definition eines konkreten Feldes innerhalb eines
   * Medientyps.
   *
   * @param mediaTypeId Datenbank-ID des Medientyps
   * @param bibtexTypeId Datenbank-ID des Feldes
   * @return optionale Lookup-Definition
   */
  public Optional<MediaAttributeLookupDefinition> loadLookupDefinition(int mediaTypeId, int bibtexTypeId) {
    String sql = """
        SELECT mal.media_types_id,
               mal.bibtex_type_id,
               mal.lookup_strategy,
               mal.min_query_length,
               mal.auto_resolve_unique,
               mal.allow_free_text,

               mt.id AS mt_id,
               mt.name AS mt_name,
               mt.bib_name AS mt_bib_name,
               mt.direct_link AS mt_direct_link,

               bt.id AS bt_id,
               bt.bibtex_name,
               bt.name,
               bt.datatype,
               bt.requires
        FROM media_attribute_lookup mal
        JOIN media_types mt
          ON mt.id = mal.media_types_id
        JOIN bibtex_type bt
          ON bt.id = mal.bibtex_type_id
        WHERE mal.media_types_id = ?
          AND mal.bibtex_type_id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, mediaTypeId);
      ps.setInt(2, bibtexTypeId);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(mapMediaAttributeLookup(rs)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "Lookup-Definition für Medientyp " + mediaTypeId
              + " und BibTeX-Feld " + bibtexTypeId + " konnte nicht geladen werden.",
          ex
      );
    }
  }

  /**
   * Lädt die Related-Definition für ein Related-Feld.
   *
   * @param bibtexTypeId Datenbank-ID des Related-Feldes
   * @return optionale Related-Definition
   */
  public Optional<RelatedMediaDefinition> loadRelatedDefinition(int bibtexTypeId) {
    String sql = """
        SELECT rmt.bibtex_type_id,
               rmt.media_types_id,
               rmt.media_attribute_bibtex_type_id,

               rel_bt.id   AS rel_id,
               rel_bt.bibtex_name AS rel_bibtex_name,
               rel_bt.name AS rel_name,
               rel_bt.datatype AS rel_datatype,
               rel_bt.requires AS rel_requires,

               mt.id       AS mt_id,
               mt.name     AS mt_name,
               mt.bib_name AS mt_bib_name,
               mt.direct_link AS mt_direct_link,

               display_bt.id   AS display_id,
               display_bt.bibtex_name AS display_bibtex_name,
               display_bt.name AS display_name,
               display_bt.datatype AS display_datatype,
               display_bt.requires AS display_requires
        FROM related_media_type rmt
        JOIN bibtex_type rel_bt
          ON rel_bt.id = rmt.bibtex_type_id
        JOIN media_types mt
          ON mt.id = rmt.media_types_id
        JOIN bibtex_type display_bt
          ON display_bt.id = rmt.media_attribute_bibtex_type_id
        WHERE rmt.bibtex_type_id = ?
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setInt(1, bibtexTypeId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }

        BibtexFieldDefinition relatedField = new BibtexFieldDefinition(
            rs.getInt("rel_id"),
            rs.getString("rel_bibtex_name"),
            rs.getString("rel_name"),
            rs.getString("rel_datatype"),
            rs.getString("rel_requires")
        );

        MediaTypeDefinition mediaTypeDefinition = new MediaTypeDefinition(
            rs.getInt("mt_id"),
            rs.getString("mt_name"),
            rs.getString("mt_bib_name"),
            rs.getInt("mt_direct_link") == 1
        );

        BibtexFieldDefinition displayFieldDefinition = new BibtexFieldDefinition(
            rs.getInt("display_id"),
            rs.getString("display_bibtex_name"),
            rs.getString("display_name"),
            rs.getString("display_datatype"),
            rs.getString("display_requires")
        );

        return Optional.of(new RelatedMediaDefinition(
            rs.getInt("bibtex_type_id"),
            rs.getInt("media_types_id"),
            rs.getInt("media_attribute_bibtex_type_id"),
            relatedField,
            mediaTypeDefinition,
            displayFieldDefinition
        ));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException(
          "Related-Definition für BibTeX-Feld " + bibtexTypeId + " konnte nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt alle Enum-Werte eines fachlichen Enum-Bereichs.
   *
   * @param type Name des Enum-Bereichs, z. B. {@code Thesis}
   * @return Enum-Werte in alphabetischer Reihenfolge des {@code typeindex}
   */
  public List<EnumTypeValue> loadEnumValues(String type) {
    String sql = """
        SELECT type, typeindex
        FROM enum_types
        WHERE LOWER(TRIM(type)) = LOWER(TRIM(?))
        ORDER BY LOWER(TRIM(typeindex))
        """;

    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

      ps.setString(1, sanitize(type));

      try (ResultSet rs = ps.executeQuery()) {
        List<EnumTypeValue> result = new ArrayList<>();
        while (rs.next()) {
          result.add(new EnumTypeValue(
              rs.getString("type"),
              rs.getString("typeindex")
          ));
        }
        return List.copyOf(result);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Enum-Werte für '" + type + "' konnten nicht geladen werden.", ex);
    }
  }

  /**
   * Lädt alle Attributdefinitionen aller Medientypen und gruppiert sie nach
   * Medientyp-ID.
   *
   * @return Map von Medientyp-ID auf Attributdefinitionen
   */
  public Map<Integer, List<MediaAttributeDefinition>> loadAllAttributesGroupedByMediaType() {
    List<MediaTypeDefinition> mediaTypes = loadMediaTypes();
    Map<Integer, List<MediaAttributeDefinition>> result = new LinkedHashMap<>();

    for (MediaTypeDefinition mediaType : mediaTypes) {
      result.put(mediaType.id(), loadAttributesForMediaType(mediaType.id()));
    }

    return Collections.unmodifiableMap(result);
  }

  private static MediaAttributeLookupDefinition mapMediaAttributeLookup(ResultSet rs) throws SQLException {
    MediaTypeDefinition mediaTypeDefinition = new MediaTypeDefinition(
        rs.getInt("mt_id"),
        rs.getString("mt_name"),
        rs.getString("mt_bib_name"),
        rs.getInt("mt_direct_link") == 1
    );

    BibtexFieldDefinition fieldDefinition = new BibtexFieldDefinition(
        rs.getInt("bt_id"),
        rs.getString("bibtex_name"),
        rs.getString("name"),
        rs.getString("datatype"),
        rs.getString("requires")
    );

    return new MediaAttributeLookupDefinition(
        rs.getInt("media_types_id"),
        rs.getInt("bibtex_type_id"),
        rs.getString("lookup_strategy"),
        rs.getInt("min_query_length"),
        rs.getInt("auto_resolve_unique") == 1,
        rs.getInt("allow_free_text") == 1,
        mediaTypeDefinition,
        fieldDefinition
    );
  }

  private static MediaTypeDefinition mapMediaType(ResultSet rs) throws SQLException {
    return new MediaTypeDefinition(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("bib_name"),
        rs.getInt("direct_link") == 1
    );
  }

  private static BibtexFieldDefinition mapBibtexField(ResultSet rs) throws SQLException {
    return new BibtexFieldDefinition(
        rs.getInt("id"),
        rs.getString("bibtex_name"),
        rs.getString("name"),
        rs.getString("datatype"),
        rs.getString("requires")
    );
  }

  private static String sanitize(String value) {
    return value == null ? "" : value.trim();
  }
}