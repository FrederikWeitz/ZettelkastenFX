package de.zettelkastenfx.fx;

import de.zettelkastenfx.base.util.BrowserConnection;
import de.zettelkastenfx.base.util.PersistenceUtil;
import de.zettelkastenfx.fx.config.AppDirectories;
import de.zettelkastenfx.fx.start.StartWindow;
import de.zettelkastenfx.persistence.DbBootstrap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.sql.DataSource;

/**
 * JavaFX-Launcher der Anwendung.
 * Initialisiert Verzeichnisse, Datenbank und Startfenster ohne SpringBoot.
 */
public class ZettelkastenFxLauncher extends Application {

  @Override
  public void init() {
    AppDirectories.ensureAppDataDirectoryExists();
  }

  @Override
  public void start(Stage primaryStage) {
    DataSource ds = DbBootstrap.initAndMigrate();
    PersistenceUtil.setDataSource(ds);

    BrowserConnection.setAppInstance(this);

    StartWindow startWindow = new StartWindow();
    startWindow.show(primaryStage);
  }

  @Override
  public void stop() {
    Platform.exit();
  }
}
