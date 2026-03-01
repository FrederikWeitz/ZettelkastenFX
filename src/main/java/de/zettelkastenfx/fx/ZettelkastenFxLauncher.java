package de.zettelkastenfx.fx;

import de.zettelkastenfx.ZettelkastenFxApplication;
import de.zettelkastenfx.fx.start.StartWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class ZettelkastenFxLauncher extends Application {

  private ConfigurableApplicationContext springContext;

  @Override
  public void init() {
    de.zettelkastenfx.fx.config.AppDirectories.ensureAppDataDirectoryExists();

    springContext = new SpringApplicationBuilder(ZettelkastenFxApplication.class)
                        .run(getParameters().getRaw().toArray(new String[0]));
  }

  @Override
  public void start(Stage primaryStage) {
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
