package de.zettelkastenfx;

import de.zettelkastenfx.fx.ZettelkastenFxLauncher;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZettelkastenFxApplication {

  public static void main(String[] args) {
    Application.launch(ZettelkastenFxLauncher.class, args);
  }
}
