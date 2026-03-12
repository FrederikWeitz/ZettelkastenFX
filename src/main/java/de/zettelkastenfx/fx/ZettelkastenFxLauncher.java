package de.zettelkastenfx.fx;

import de.zettelkastenfx.ZettelkastenFxApplication;
import de.zettelkastenfx.base.util.BrowserConnection;
import de.zettelkastenfx.fx.config.AppDirectories;
import de.zettelkastenfx.fx.start.StartWindow;
import de.zettelkastenfx.persistence.DbBootstrap;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;

public class ZettelkastenFxLauncher extends Application {

  private ConfigurableApplicationContext springContext;

  @Override
  public void init() {
    AppDirectories.ensureAppDataDirectoryExists();

    springContext = new SpringApplicationBuilder(ZettelkastenFxApplication.class)
                        .run(getParameters().getRaw().toArray(new String[0]));
  }

  @Override
  public void start(Stage primaryStage) {
    DataSource ds = DbBootstrap.initAndMigrate();
    BrowserConnection.setAppInstance(this);

    StartWindow startWindow = new StartWindow();
    startWindow.show(primaryStage);
  }

  @Override
  public void stop() {
    if (springContext != null) {
      springContext.close();
    }
    Platform.exit();
  }
}
