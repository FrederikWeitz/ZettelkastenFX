package de.zettelkastenfx.persistence;

import de.zettelkastenfx.fx.config.AppDirectories;
import de.zettelkastenfx.fx.config.ExternalStylesheetService;
import lombok.Getter;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

public final class DbBootstrap {

  @Getter
  private static SQLiteDataSource ds;

  private DbBootstrap() {}

  public static Path databaseFile() {
    return AppDirectories.ensureAppDataDirectoryExists().resolve("zettelkastenfx.db");
  }

  public static DataSource initAndMigrate() {
    Path dbFile = databaseFile();
    ensureImageDirectoryExists(dbFile);
    ExternalStylesheetService.ensureExternalStylesheet(dbFile.toAbsolutePath().normalize().getParent());

    ds = new SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());

    // FK in SQLite aktivieren (pro Connection)
    try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
      st.execute("PRAGMA foreign_keys = ON");
    } catch (Exception ex) {
      throw new IllegalStateException("SQLite PRAGMA foreign_keys konnte nicht gesetzt werden.", ex);
    }

    Flyway flyway = Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration")
                        // hilfreich, falls schon eine DB existiert, aber noch ohne Flyway-History:
                        .baselineOnMigrate(true)
                        .load();

    flyway.migrate();
    return ds;
  }

  /**
   * Legt beim Datenbankstart den Bildordner neben der Datenbank an.
   *
   * @param dbFile Datenbankdatei
   */
  private static void ensureImageDirectoryExists(Path dbFile) {
    Path parent = dbFile.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      throw new IllegalStateException("Datenbankpfad hat keinen Elternordner: " + dbFile);
    }
    Path imageDirectory = parent.resolve("image");
    try {
      Files.createDirectories(imageDirectory);
    } catch (IOException exception) {
      throw new IllegalStateException("Konnte Bildordner nicht anlegen: " + imageDirectory, exception);
    }
  }
}
