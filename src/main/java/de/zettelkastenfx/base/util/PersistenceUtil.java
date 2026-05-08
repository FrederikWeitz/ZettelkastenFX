package de.zettelkastenfx.base.util;

import de.zettelkastenfx.persistence.DbBootstrap;
import de.zettelkastenfx.persistence.NoteRepository;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Zentraler Zugriffspunkt auf die persistenzbezogenen Laufzeitinstanzen.
 */
public final class PersistenceUtil {

  private static NoteRepository noteRepository;
  private static DataSource dataSource;

  private PersistenceUtil() {
    // Utility-Klasse: keine Instanzierung.
  }

  /**
   * Setzt die zentrale Datenquelle und erzeugt das dazugehörige Repository neu.
   *
   * @param ds Datenquelle der Anwendung
   */
  public static synchronized void setDataSource(DataSource ds) {
    dataSource = Objects.requireNonNull(ds, "ds");
    noteRepository = new NoteRepository(dataSource);
  }

  /**
   * Liefert die zentrale Datenquelle und initialisiert sie bei Bedarf.
   *
   * @return Datenquelle der Anwendung
   */
  public static synchronized DataSource getDataSource() {
    if (dataSource == null) {
      dataSource = DbBootstrap.initAndMigrate();
    }
    return dataSource;
  }

  /**
   * Liefert das zentrale Zettel-Repository und initialisiert es bei Bedarf.
   *
   * @return Zettel-Repository der Anwendung
   */
  public static synchronized NoteRepository getNoteRepository() {
    if (noteRepository == null) {
      noteRepository = new NoteRepository(getDataSource());
    }
    return noteRepository;
  }
}
