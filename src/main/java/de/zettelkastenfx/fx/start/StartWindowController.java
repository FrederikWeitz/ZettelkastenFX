package de.zettelkastenfx.fx.start;

import de.zettelkastenfx.notes.controller.ZettelWindow;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class StartWindowController {

  private static final Duration ANIMATION_DURATION = Duration.millis(150);

  private final Stage stage;
  private final StartWindowView view;

  private final double collapsedWidth;
  private final double collapsedHeight;

  private final double expandedWidth;
  private final double expandedHeight;

  private final DoubleProperty animatedWidth;
  private final DoubleProperty animatedHeight;

  private Timeline currentAnimation;
  private final Rectangle windowShape;

  private double dragOffsetX;
  private double dragOffsetY;

  /**
   * Kennzeichnet, ob das Hauptfenster vollständig aufgeklappt ist.
   * Erst dann dürfen Fenster-Menüs und Untermenüs reagieren.
   */
  private boolean expanded;

  /**
   * Sperrt die Collapse-Logik, solange ein Menü oder Untermenü aktiv offen ist.
   */
  private boolean overlayInteractionLocked;

  /**
   * Kennzeichnet, ob gerade eine Expand- oder Collapse-Animation läuft.
   * Verhindert Rückkopplungen und Flimmern durch mehrfaches Neu-Starten.
   */
  private boolean animationRunning;

  private Popup windowMenuPopup;
  private Popup zettelkastenPopup;
  private Popup createZettelkastenPopup;

  private VBox windowMenuPopupContent;
  private VBox createZettelkastenPopupContent;

  /**
   * Kleine Verzögerung für das Schließen der Menüs.
   * Dadurch kann die Maus stabil vom Hauptpopup ins Unterpopup wechseln.
   */
  private final PauseTransition popupCloseDelay = new PauseTransition(Duration.millis(160));

  private StartMenuItem popupZettelkastenItem;
  private StartMenuItem popupSchreibtischItem;
  private StartMenuItem popupProjektItem;

  private VBox zettelkastenPopupContent;

  private TextField createNameField;
  private ToggleGroup colorToggleGroup;
  private Button createOkButton;

  /**
   * Laufzeitdaten eines aktuell geöffneten Zettelkastens.
   */
  private static final class ZettelkastenEntry {
    private String name;
    private String color;
    private Stage stage;

    /**
     * Erzeugt einen Laufzeiteintrag für einen Zettelkasten.
     *
     * @param name Anzeigename des Fensters
     * @param color optionale Akzentfarbe
     */
    private ZettelkastenEntry(String name, String color) {
      this.name = (name == null || name.isBlank()) ? "Zettel" : name;
      this.color = (color == null || color.isBlank()) ? null : color;
    }

    /**
     * Aktualisiert Anzeigename und Akzentfarbe des Eintrags.
     *
     * @param name neuer Anzeigename
     * @param color neue Akzentfarbe
     */
    private void applyProfile(String name, String color) {
      this.name = (name == null || name.isBlank()) ? "Zettel" : name;
      this.color = (color == null || color.isBlank()) ? null : color;
    }
  }

  private final List<ZettelkastenEntry> zettelkaesten = new ArrayList<>();

  /**
   * Erzeugt den Controller des Startfensters.
   *
   * @param stage Start-Stage
   * @param view zugehörige View
   */
  public StartWindowController(Stage stage, StartWindowView view) {
    this.stage = stage;
    this.view = view;

    this.collapsedWidth = 72;
    this.collapsedHeight = 72;

    this.expandedWidth = 120;
    this.expandedHeight = 150;

    this.animatedWidth = new SimpleDoubleProperty(collapsedWidth);
    this.animatedHeight = new SimpleDoubleProperty(collapsedHeight);

    this.windowShape = new Rectangle(collapsedWidth, collapsedHeight);
    this.windowShape.setArcWidth(16);
    this.windowShape.setArcHeight(16);

    applyMainMenuItemStyle(view.getWindowItem());
    applyMainMenuItemStyle(view.getItemTwo());
    applyMainMenuItemStyle(view.getItemThree());

    wireSizing();
    buildWindowMenuPopup();
    buildZettelkastenPopup();
    buildCreateZettelkastenPopup();
    wireEvents();

    expanded = false;
    overlayInteractionLocked = false;
    animationRunning = false;
  }

  public double getCollapsedDiameter() {
    // bleibt kompatibel zu StartWindow (Scene init); wir nutzen jetzt width statt “diameter”
    return collapsedWidth;
  }

  public double getHeight() {
    return collapsedHeight;
  }

  /**
   * Setzt das Startfenster in den initialen eingeklappten Zustand.
   * Alle Popups werden geschlossen und Sperrzustände zurückgesetzt.
   */
  public void applyInitialState() {
    setMenuVisible(false);
    setExitVisible(false);
    setAvatarVisible(true);
    hideAllPopups();

    expanded = false;
    overlayInteractionLocked = false;
    animationRunning = false;

    animatedWidth.set(collapsedWidth);
    animatedHeight.set(collapsedHeight);
    applySize(collapsedWidth, collapsedHeight);
  }

  /**
   * Verdrahtet Maus- und Aktionsereignisse des Startfensters.
   * Solange Popups offen sind, bleibt die Collapse-Logik gesperrt.
   */
  private void wireEvents() {
    StackPane root = view.getRoot();

    root.setOnMouseEntered(event -> {
      cancelPopupCloseCheck();
      if (!overlayInteractionLocked) {
        expand();
      }
    });

    root.setOnMouseExited(event -> {
      if (isTransientPopupShowing()) {
        schedulePopupCloseCheck();
      } else if (!overlayInteractionLocked && expanded) {
        collapse();
      }
    });

    view.getExitButton().setOnAction(event -> Platform.exit());
    view.getItemTwo().setOnMouseClicked(event -> System.out.println("DB-Dialog kommt später"));
    view.getItemThree().setOnMouseClicked(event -> Platform.exit());

    view.getWindowItem().setOnMouseEntered(event -> {
      cancelPopupCloseCheck();
      if (!expanded) {
        return;
      }
      lockOverlayInteraction();
      showWindowMenuPopup();
    });

    view.getWindowItem().setOnMouseClicked(event -> {
      cancelPopupCloseCheck();
      if (!expanded) {
        return;
      }
      lockOverlayInteraction();
      showWindowMenuPopup();
    });

    view.getWindowItem().setOnMouseExited(event -> {
      if (isTransientPopupShowing()) {
        schedulePopupCloseCheck();
      }
    });

    createOkButton.setOnAction(event -> {
      String name = createNameField.getText();
      String color = getSelectedColor();

      ZettelkastenEntry entry = new ZettelkastenEntry(name, color);

      createNameField.clear();
      clearColorSelection();

      hideCreateZettelkastenPopup();
      unlockOverlayInteractionIfNoPopupShowing();
      openZettelkasten(entry);
    });

    enableWindowDragging();
  }

  private void wireSizing() {
    Region rootRegion = view.getRoot();
    rootRegion.setShape(windowShape);

    animatedWidth.addListener((observable, oldValue, newValue) ->
                                  applySize(newValue.doubleValue(), animatedHeight.get())
    );
    animatedHeight.addListener((observable, oldValue, newValue) ->
                                   applySize(animatedWidth.get(), newValue.doubleValue())
    );
  }

  /**
   * Aktiviert die Interaktionssperre für Expand/Collapse.
   */
  private void lockOverlayInteraction() {
    overlayInteractionLocked = true;
  }

  /**
   * Bricht eine geplante Popup-Schließprüfung ab.
   */
  private void cancelPopupCloseCheck() {
    popupCloseDelay.stop();
  }

  /**
   * Plant eine verzögerte Prüfung, ob Hauptpopup und Unterpopup geschlossen werden sollen.
   * Das verhindert, dass die Menüs beim Übergang zwischen den Popupflächen sofort verschwinden.
   */
  private void schedulePopupCloseCheck() {
    popupCloseDelay.stop();
    popupCloseDelay.setOnFinished(event -> {
      if (shouldHideTransientPopups()) {
        hideTransientPopups();

        if (expanded && !view.getRoot().isHover() && !isPopupShowing(createZettelkastenPopup)) {
          collapse();
        }
      }
    });
    popupCloseDelay.playFromStart();
  }

  /**
   * Prüft, ob die flüchtigen Menüs des Arbeitsfensters sichtbar sind.
   *
   * @return {@code true}, wenn Hauptpopup oder Unterpopup sichtbar sind
   */
  private boolean isTransientPopupShowing() {
    return isPopupShowing(windowMenuPopup) || isPopupShowing(zettelkastenPopup);
  }

  /**
   * Prüft, ob die flüchtigen Menüs geschlossen werden sollen.
   *
   * @return {@code true}, wenn weder Anker noch Popupflächen aktuell gehovert sind
   */
  private boolean shouldHideTransientPopups() {
    return !view.getWindowItem().isHover()
               && !isNodeHovering(windowMenuPopupContent)
               && !isNodeHovering(zettelkastenPopupContent);
  }

  /**
   * Prüft, ob ein Popup-Inhaltsknoten gerade gehovert wird.
   *
   * @param node zu prüfender Knoten
   * @return {@code true}, wenn sichtbar und gehovert
   */
  private boolean isNodeHovering(Node node) {
    return node != null && node.isVisible() && node.isHover();
  }

  /**
   * Blendet Hauptpopup und Unterpopup des Arbeitsfensters aus und hebt danach
   * gegebenenfalls die Interaktionssperre wieder auf.
   */
  private void hideTransientPopups() {
    hideZettelkastenPopup();
    hideWindowMenuPopup();
    unlockOverlayInteractionIfNoPopupShowing();
  }

  /**
   * Löscht die aktuelle Farbauswahl im Anlege-Popup.
   */
  private void clearColorSelection() {
    if (colorToggleGroup != null) {
      colorToggleGroup.selectToggle(null);
    }
  }

  /**
   * Gibt die aktuell gewählte Farbe zurück oder {@code null}, wenn keine gewählt ist.
   */
  private String getSelectedColor() {
    if (colorToggleGroup == null || colorToggleGroup.getSelectedToggle() == null) {
      return null;
    }
    return (String) colorToggleGroup.getSelectedToggle().getUserData();
  }

  /**
   * Hebt die Interaktionssperre auf, wenn kein Popup mehr sichtbar ist.
   */
  private void unlockOverlayInteractionIfNoPopupShowing() {
    if (!isAnyPopupShowing()) {
      overlayInteractionLocked = false;
    }
  }

  /**
   * Prüft, ob derzeit mindestens ein Popup sichtbar ist.
   *
   * @return {@code true}, wenn ein Popup geöffnet ist
   */
  private boolean isAnyPopupShowing() {
    return isPopupShowing(windowMenuPopup)
               || isPopupShowing(zettelkastenPopup)
               || isPopupShowing(createZettelkastenPopup);
  }

  /**
   * Prüft, ob ein Popup sichtbar ist.
   *
   * @param popup zu prüfendes Popup
   * @return {@code true}, wenn sichtbar
   */
  private boolean isPopupShowing(Popup popup) {
    return popup != null && popup.isShowing();
  }

  /**
   * Prüft, ob ein Stage-Eintrag noch auf ein sichtbares Fenster zeigt.
   *
   * @param entry zu prüfender Eintrag
   * @return {@code true}, wenn das Fenster noch sichtbar ist
   */
  private boolean isEntryOpen(ZettelkastenEntry entry) {
    return entry != null && entry.stage != null && entry.stage.isShowing();
  }

  /**
   * Aktiviert ein bereits geöffnetes Zettelfenster.
   * Ist das Fenster minimiert, wird es wiederhergestellt, auf dem Bildschirm zentriert
   * und in den Vordergrund geholt. Andernfalls wird es nur in den Vordergrund geholt.
   *
   * @param entry Eintrag des geöffneten Zettelkastens
   */
  private void activateOpenZettelkasten(ZettelkastenEntry entry) {
    if (!isEntryOpen(entry)) {
      return;
    }

    Stage targetStage = entry.stage;

    if (targetStage.isIconified()) {
      targetStage.setIconified(false);
      targetStage.centerOnScreen();
    }

    targetStage.toFront();
    targetStage.requestFocus();
  }

  /**
   * Gibt einem Popup-Root eine feste, undurchsichtige Darstellung.
   *
   * @param root zu stylender Popup-Container
   */
  private void applyPopupContainerStyle(Region root) {
    root.setStyle(
        "-fx-background-color: #f8f8ff;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 10;" +
            "-fx-border-color: f0f0f8;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 8;"
    );
  }

  /**
   * Gibt einem Menü-Popup eine kantige, menüartige Darstellung ohne Rundungen.
   *
   * @param root zu stylender Popup-Container
   */
  private void applyMenuPopupContainerStyle(Region root) {
    root.setStyle(
        "-fx-background-color: #f8f8ff;" +
            "-fx-background-insets: 0;" +
            "-fx-background-radius: 0;" +
            "-fx-border-color: transparent;" +
            "-fx-border-width: 0;" +
            "-fx-border-radius: 0;" +
            "-fx-padding: 0;"
    );
  }

  /**
   * Gestaltet einen Eintrag innerhalb eines Menü-Popups so, dass er dem normalen Menü
   * optisch ähnelt: keine Rundung, keine Zwischenlinie, etwas höher und mit kräftigem
   * Hover-Blau.
   *
   * @param item zu gestaltender Menüeintrag
   */
  private void applyPopupMenuItemStyle(StartMenuItem item) {
    item.setMaxWidth(Double.MAX_VALUE);
    item.setMinHeight(26);
    item.setPrefHeight(26);
    item.setStyle(
        "-fx-background-color: transparent;" +
            "-fx-background-radius: 0;" +
            "-fx-border-color: transparent;" +
            "-fx-border-width: 0;" +
            "-fx-padding: 0 10 0 10;" +
            "-fx-text-fill: black;" +
            "-fx-alignment: center-left;"
    );

    item.hoverProperty().addListener((obs, oldValue, hovering) -> {
      if (hovering) {
        item.setStyle(
            "-fx-background-color: #009ACD;" +
                "-fx-background-radius: 0;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-text-fill: white;" +
                "-fx-alignment: center-left;"
        );
      } else {
        item.setStyle(
            "-fx-background-color: transparent;" +
                "-fx-background-radius: 0;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-text-fill: black;" +
                "-fx-alignment: center-left;"
        );
      }
    });
  }

  /**
   * Gestaltet einen Eintrag des normalen Startfenster-Menüs in derselben
   * harten Menüoptik wie die Popup-Menüs.
   *
   * @param item zu gestaltender Menüeintrag
   */
  private void applyMainMenuItemStyle(StartMenuItem item) {
    item.setMaxWidth(Double.MAX_VALUE);
    item.setMinHeight(26);
    item.setPrefHeight(26);
    item.setStyle(
        "-fx-background-color: transparent;" +
            "-fx-background-radius: 0;" +
            "-fx-border-color: transparent;" +
            "-fx-border-width: 0;" +
            "-fx-padding: 0 10 0 10;" +
            "-fx-text-fill: black;" +
            "-fx-alignment: center-left;"
    );

    item.hoverProperty().addListener((obs, oldValue, hovering) -> {
      if (hovering) {
        item.setStyle(
            "-fx-background-color: #009ACD;" +
                "-fx-background-radius: 0;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-text-fill: white;" +
                "-fx-alignment: center-left;"
        );
      } else {
        item.setStyle(
            "-fx-background-color: transparent;" +
                "-fx-background-radius: 0;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0;" +
                "-fx-padding: 0 10 0 10;" +
                "-fx-text-fill: black;" +
                "-fx-alignment: center-left;"
        );
      }
    });
  }

  /**
   * Baut das Popup für das Arbeitsfenster-Menü auf.
   */
  private void buildWindowMenuPopup() {
    windowMenuPopup = new Popup();
    windowMenuPopup.setAutoHide(false);
    windowMenuPopup.setAutoFix(true);
    windowMenuPopup.setHideOnEscape(true);

    windowMenuPopupContent = new VBox(0);
    windowMenuPopupContent.getStyleClass().add("start-hover-menu");
    windowMenuPopupContent.setPadding(Insets.EMPTY);
    applyMenuPopupContainerStyle(windowMenuPopupContent);

    windowMenuPopupContent.setOnMouseEntered(event -> cancelPopupCloseCheck());
    windowMenuPopupContent.setOnMouseExited(event -> schedulePopupCloseCheck());

    popupZettelkastenItem = new StartMenuItem("Zettelkasten");
    popupSchreibtischItem = new StartMenuItem("Schreibtisch");
    popupProjektItem = new StartMenuItem("Projekt");

    applyPopupMenuItemStyle(popupZettelkastenItem);
    applyPopupMenuItemStyle(popupSchreibtischItem);
    applyPopupMenuItemStyle(popupProjektItem);

    popupZettelkastenItem.setOnMouseEntered(event -> {
      cancelPopupCloseCheck();
      lockOverlayInteraction();
      rebuildZettelkastenPopupContent();
      showZettelkastenPopup();
    });

    popupZettelkastenItem.setOnMouseClicked(event -> {
      cancelPopupCloseCheck();
      lockOverlayInteraction();
      rebuildZettelkastenPopupContent();
      showZettelkastenPopup();
    });

    popupSchreibtischItem.setOnMouseEntered(event -> {
      hideZettelkastenPopup();
      schedulePopupCloseCheck();
    });

    popupProjektItem.setOnMouseEntered(event -> {
      hideZettelkastenPopup();
      schedulePopupCloseCheck();
    });

    windowMenuPopupContent.getChildren().addAll(
        popupZettelkastenItem,
        popupSchreibtischItem,
        popupProjektItem
    );

    windowMenuPopup.getContent().setAll(windowMenuPopupContent);
  }

  /**
   * Baut das Popup für vorhandene Zettelkästen und den Anlegeeintrag auf.
   */
  private void buildZettelkastenPopup() {
    zettelkastenPopup = new Popup();
    zettelkastenPopup.setAutoHide(false);
    zettelkastenPopup.setAutoFix(true);
    zettelkastenPopup.setHideOnEscape(true);

    zettelkastenPopupContent = new VBox(0);
    zettelkastenPopupContent.getStyleClass().add("start-hover-submenu");
    zettelkastenPopupContent.setPadding(Insets.EMPTY);
    applyMenuPopupContainerStyle(zettelkastenPopupContent);

    zettelkastenPopupContent.setOnMouseEntered(event -> cancelPopupCloseCheck());
    zettelkastenPopupContent.setOnMouseExited(event -> schedulePopupCloseCheck());

    zettelkastenPopup.getContent().setAll(zettelkastenPopupContent);
    rebuildZettelkastenPopupContent();
  }

  /**
   * Baut das Popup zum Anlegen eines neuen Zettelkastens auf.
   */
  private void buildCreateZettelkastenPopup() {
    createZettelkastenPopup = new Popup();
    createZettelkastenPopup.setAutoHide(false);
    createZettelkastenPopup.setAutoFix(true);
    createZettelkastenPopup.setHideOnEscape(true);

    createZettelkastenPopupContent = new VBox(12);
    createZettelkastenPopupContent.getStyleClass().add("start-create-popup");
    createZettelkastenPopupContent.setPadding(new Insets(12));
    applyPopupContainerStyle(createZettelkastenPopupContent);

    Label title = new Label("Zettelkasten");

    HBox nameRow = new HBox(6);
    nameRow.setAlignment(Pos.CENTER_LEFT);

    Label nameLabel = new Label("Name:");
    createNameField = new TextField();
    HBox.setHgrow(createNameField, Priority.ALWAYS);
    nameRow.getChildren().addAll(nameLabel, createNameField);

    // 🎨 Farbfelder
    colorToggleGroup = new ToggleGroup();
    HBox colorRow = new HBox(8);

    String[] colors = {
        "#ffd6d6",
        "#d6f5f0",
        "#e0d6ff",
        "#fff3cd",
        "#d6ffe6",
        "#d6e4ff",
        "#f8d6ff",
        "#ffe4d6"
    };

    for (String color : colors) {
      ToggleButton btn = new ToggleButton();
      btn.setToggleGroup(colorToggleGroup);
      btn.setUserData(color);

      btn.setMinSize(22, 22);
      btn.setMaxSize(22, 22);

      btn.setStyle(
          "-fx-background-color: " + color + ";" +
              "-fx-background-radius: 6;" +
              "-fx-border-color: #888;" +
              "-fx-border-radius: 6;"
      );

      // visuelles Feedback
      btn.selectedProperty().addListener((obs, oldV, selected) -> {
        if (selected) {
          btn.setStyle(
              "-fx-background-color: " + color + ";" +
                  "-fx-background-radius: 6;" +
                  "-fx-border-color: #ffffff;" +
                  "-fx-border-width: 2;" +
                  "-fx-border-radius: 6;"
          );
        } else {
          btn.setStyle(
              "-fx-background-color: " + color + ";" +
                  "-fx-background-radius: 6;" +
                  "-fx-border-color: #888;" +
                  "-fx-border-width: 1;" +
                  "-fx-border-radius: 6;"
          );
        }
      });

      colorRow.getChildren().add(btn);
    }

    createOkButton = new Button("Anlegen");
    createOkButton.setMinWidth(88);
    createOkButton.setPrefWidth(88);

    Region okSpacer = new Region();
    HBox.setHgrow(okSpacer, Priority.ALWAYS);

    HBox okRow = new HBox(8, okSpacer, createOkButton);
    okRow.setAlignment(Pos.CENTER_RIGHT);

    createZettelkastenPopupContent.getChildren().addAll(
        title,
        nameRow,
        colorRow,
        okRow
    );

    createZettelkastenPopup.getContent().setAll(createZettelkastenPopupContent);
  }

  /**
   * Blendet alle Popups aus.
   */
  private void hideAllPopups() {
    hideCreateZettelkastenPopup();
    hideZettelkastenPopup();
    hideWindowMenuPopup();
  }

  /**
   * Zeigt das Arbeitsfenster-Menü als Popup direkt rechts neben dem Eintrag an,
   * sodass Button und Popup Kante an Kante erscheinen.
   */
  private void showWindowMenuPopup() {
    cancelPopupCloseCheck();

    Bounds bounds = view.getWindowItem().localToScreen(view.getWindowItem().getBoundsInLocal());
    if (bounds == null) {
      return;
    }

    double x = bounds.getMaxX();
    double y = bounds.getMinY();

    if (windowMenuPopup.isShowing()) {
      windowMenuPopup.setX(x);
      windowMenuPopup.setY(y);
      return;
    }

    windowMenuPopup.show(stage, x, y);
  }

  /**
   * Blendet das Arbeitsfenster-Menü aus.
   * Das Untermenü wird dabei ebenfalls geschlossen.
   */
  private void hideWindowMenuPopup() {
    if (zettelkastenPopup.isShowing()) {
      zettelkastenPopup.hide();
    }
    if (windowMenuPopup.isShowing()) {
      windowMenuPopup.hide();
    }
  }

  /**
   * Zeigt das Zettelkasten-Untermenü direkt rechts neben dem Haupteintrag an,
   * sodass beide Popupflächen Kante an Kante anschließen.
   */
  private void showZettelkastenPopup() {
    cancelPopupCloseCheck();

    if (!windowMenuPopup.isShowing()) {
      return;
    }

    Bounds bounds = popupZettelkastenItem.localToScreen(popupZettelkastenItem.getBoundsInLocal());
    if (bounds == null) {
      return;
    }

    double x = bounds.getMaxX();
    double y = bounds.getMinY();

    if (zettelkastenPopup.isShowing()) {
      zettelkastenPopup.setX(x);
      zettelkastenPopup.setY(y);
      return;
    }

    zettelkastenPopup.show(stage, x, y);
  }

  /**
   * Blendet das Zettelkasten-Untermenü aus.
   */
  private void hideZettelkastenPopup() {
    if (zettelkastenPopup.isShowing()) {
      zettelkastenPopup.hide();
    }
  }

  /**
   * Zeigt das Anlege-Popup mittig relativ zum Startfenster an.
   * Das Namensfeld wird dabei jedes Mal fokussiert.
   */
  private void showCreateZettelkastenPopup() {
    cancelPopupCloseCheck();

    createNameField.clear();
    clearColorSelection();

    createZettelkastenPopupContent.applyCss();
    createZettelkastenPopupContent.autosize();

    double popupWidth = createZettelkastenPopupContent.prefWidth(-1);
    double popupHeight = createZettelkastenPopupContent.prefHeight(-1);

    double x = stage.getX() + (stage.getWidth() - popupWidth) / 2.0;
    double y = stage.getY() + (stage.getHeight() - popupHeight) / 2.0;

    if (createZettelkastenPopup.isShowing()) {
      createZettelkastenPopup.hide();
    }

    createZettelkastenPopup.show(
        stage,
        Math.max(stage.getX() + 8, x),
        Math.max(stage.getY() + 8, y)
    );

    lockOverlayInteraction();

    focusCreateNameField();
  }

  /**
   * Erzwingt den Fokus auf das Namensfeld des Anlege-Popups.
   * Der Fokus wird doppelt verzögert gesetzt, damit er auch dann stabil ankommt,
   * wenn zuvor ein anderes Arbeitsfenster den Fokus gehalten hat.
   */
  private void focusCreateNameField() {
    Platform.runLater(() -> {
      stage.toFront();
      stage.requestFocus();

      Platform.runLater(() -> {
        createNameField.setDisable(false);
        createNameField.setEditable(true);
        createNameField.requestFocus();
        createNameField.positionCaret(createNameField.getText().length());
      });
    });
  }

  /**
   * Blendet das Anlege-Popup aus.
   */
  private void hideCreateZettelkastenPopup() {
    if (createZettelkastenPopup.isShowing()) {
      createZettelkastenPopup.hide();
    }
    unlockOverlayInteractionIfNoPopupShowing();
  }

  private void applySize(double width, double height) {
    view.getRoot().setPrefSize(width, height);
    view.getRoot().setMinSize(width, height);
    view.getRoot().setMaxSize(width, height);

    windowShape.setWidth(width);
    windowShape.setHeight(height);

    stage.setWidth(width);
    stage.setHeight(height);
  }

  /**
   * Klappt das Startfenster animiert auf.
   * Während einer laufenden Animation wird kein zweiter Expand-Vorgang gestartet.
   */
  private void expand() {
    if (expanded || animationRunning) {
      return;
    }

    animationRunning = true;
    stopCurrentAnimation();

    setMenuVisible(true);
    view.getMenuBox().setOpacity(0);
    view.getMenuBox().setTranslateY(-6);

    view.getExitButton().setOpacity(0);
    setExitVisible(true);

    Node avatarNode = view.getAvatarNode();
    avatarNode.setOpacity(1);
    setAvatarVisible(true);

    currentAnimation = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(animatedWidth, animatedWidth.get(), Interpolator.LINEAR),
            new KeyValue(animatedHeight, animatedHeight.get(), Interpolator.LINEAR)
        ),
        new KeyFrame(ANIMATION_DURATION,
            new KeyValue(animatedWidth, expandedWidth, Interpolator.EASE_OUT),
            new KeyValue(animatedHeight, expandedHeight, Interpolator.EASE_OUT),
            new KeyValue(view.getMenuBox().opacityProperty(), 1, Interpolator.EASE_OUT),
            new KeyValue(view.getMenuBox().translateYProperty(), 0, Interpolator.EASE_OUT),
            new KeyValue(avatarNode.opacityProperty(), 0, Interpolator.EASE_OUT),
            new KeyValue(view.getExitButton().opacityProperty(), 1, Interpolator.EASE_OUT)
        )
    );

    currentAnimation.setOnFinished(event -> {
      setAvatarVisible(false);
      expanded = true;
      animationRunning = false;
    });

    currentAnimation.play();
  }

  /**
   * Klappt das Startfenster animiert ein.
   * Solange ein Menü oder Untermenü gesperrt offen ist, wird nicht kollabiert.
   */
  private void collapse() {
    if (!expanded || animationRunning || overlayInteractionLocked) {
      return;
    }

    animationRunning = true;
    stopCurrentAnimation();

    Node avatarNode = view.getAvatarNode();
    setAvatarVisible(true);
    avatarNode.setOpacity(0);

    view.getMenuBox().setOpacity(1);
    view.getExitButton().setOpacity(1);

    currentAnimation = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(animatedWidth, animatedWidth.get(), Interpolator.LINEAR),
            new KeyValue(animatedHeight, animatedHeight.get(), Interpolator.LINEAR)
        ),
        new KeyFrame(ANIMATION_DURATION,
            new KeyValue(animatedWidth, collapsedWidth, Interpolator.EASE_OUT),
            new KeyValue(animatedHeight, collapsedHeight, Interpolator.EASE_OUT),
            new KeyValue(view.getMenuBox().opacityProperty(), 0, Interpolator.EASE_OUT),
            new KeyValue(view.getMenuBox().translateYProperty(), -6, Interpolator.EASE_OUT),
            new KeyValue(avatarNode.opacityProperty(), 1, Interpolator.EASE_OUT),
            new KeyValue(view.getExitButton().opacityProperty(), 0, Interpolator.EASE_OUT)
        )
    );

    currentAnimation.setOnFinished(event -> {
      setMenuVisible(false);
      setExitVisible(false);
      hideAllPopups();
      expanded = false;
      animationRunning = false;
    });

    currentAnimation.play();
  }

  private void stopCurrentAnimation() {
    if (currentAnimation != null) {
      currentAnimation.stop();
    }
  }

  private void setMenuVisible(boolean visible) {
    view.getMenuBox().setVisible(visible);
    view.getMenuBox().setManaged(visible);
  }

  private void setExitVisible(boolean visible) {
    view.getExitButton().setVisible(visible);
    view.getExitButton().setManaged(visible);
  }

  private void setAvatarVisible(boolean visible) {
    view.getAvatarNode().setVisible(visible);
    view.getAvatarNode().setManaged(visible);
  }

  private void enableWindowDragging() {
    // Wir erlauben Dragging nur auf dem Header (wie “Titelleiste”)
    view.getHeaderPane().setOnMousePressed(event -> {
      if (!event.isPrimaryButtonDown()) {
        return;
      }

      // Wenn der Klick auf dem Exit-Button landet, nicht draggen
      if (isClickInsideExitButton(event.getPickResult().getIntersectedNode())) {
        return;
      }

      dragOffsetX = stage.getX() - event.getScreenX();
      dragOffsetY = stage.getY() - event.getScreenY();
    });

    view.getHeaderPane().setOnMouseDragged(event -> {
      if (!event.isPrimaryButtonDown()) {
        return;
      }

      if (isClickInsideExitButton(event.getPickResult().getIntersectedNode())) {
        return;
      }

      stage.setX(event.getScreenX() + dragOffsetX);
      stage.setY(event.getScreenY() + dragOffsetY);
    });
  }

  private boolean isClickInsideExitButton(javafx.scene.Node pickedNode) {
    if (pickedNode == null) {
      return false;
    }

    javafx.scene.Node current = pickedNode;
    while (current != null) {
      if (current == view.getExitButton()) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  /**
   * Öffnet ein neues ZettelWindow für einen Laufzeit-Zettelkasten und
   * registriert es als aktuell offenes Arbeitsfenster.
   *
   * @param entry Laufzeitdaten des Zettelkastens
   */
  private void openZettelkasten(ZettelkastenEntry entry) {
    Stage newStage = new Stage();
    ZettelWindow window = new ZettelWindow();

    entry.stage = newStage;
    zettelkaesten.add(entry);
    rebuildZettelkastenPopupContent();

    newStage.setOnHidden(event -> {
      zettelkaesten.remove(entry);
      rebuildZettelkastenPopupContent();
      unlockOverlayInteractionIfNoPopupShowing();
    });

    ZettelWindow.ZettelWindowHandle handle = window.show(newStage, entry.name, entry.color);
    showCreateZettelkastenDialog(entry, handle);
  }

  /**
   * Zeigt den modalen Anlege-Dialog direkt ueber dem geoeffneten Zettelfenster.
   *
   * @param entry Laufzeitdaten des Zettelkastens
   * @param handle Handle des geoeffneten Zettelfensters
   */
  private void showCreateZettelkastenDialog(ZettelkastenEntry entry, ZettelWindow.ZettelWindowHandle handle) {
    if (entry == null || handle == null || entry.stage == null) {
      return;
    }

    Stage dialogStage = new Stage(StageStyle.UTILITY);
    dialogStage.initOwner(entry.stage);
    dialogStage.initModality(Modality.WINDOW_MODAL);
    dialogStage.setTitle("Zettelkasten");
    dialogStage.setResizable(false);

    TextField nameField = new TextField(entry.name);
    nameField.setPromptText("Name");
    ToggleGroup dialogColorToggleGroup = new ToggleGroup();
    HBox colorRow = createDialogColorRow(dialogColorToggleGroup, entry.color);

    Button okButton = new Button("Anlegen");
    okButton.setDefaultButton(true);
    okButton.setMinWidth(88);
    okButton.setPrefWidth(88);

    Region okSpacer = new Region();
    HBox.setHgrow(okSpacer, Priority.ALWAYS);
    HBox okRow = new HBox(8, okSpacer, okButton);
    okRow.setAlignment(Pos.CENTER_RIGHT);

    HBox nameRow = new HBox(6, new Label("Name:"), nameField);
    nameRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(nameField, Priority.ALWAYS);

    VBox root = new VBox(12, new Label("Zettelkasten"), nameRow, colorRow, okRow);
    root.setPadding(new Insets(12));
    root.setPrefWidth(320);
    applyPopupContainerStyle(root);

    Runnable confirm = () -> {
      applyZettelkastenDialogResult(entry, handle, nameField.getText(), getSelectedColor(dialogColorToggleGroup));
      dialogStage.close();
    };

    okButton.setOnAction(event -> confirm.run());
    root.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleCreateDialogKeyPress(event, dialogStage, confirm));

    dialogStage.setScene(new Scene(root));
    dialogStage.setOnShown(event -> {
      centerDialogOverOwner(dialogStage, entry.stage);
      nameField.requestFocus();
      nameField.selectAll();
    });

    dialogStage.showAndWait();
  }

  /**
   * Behandelt Tastaturaktionen im modalen Anlege-Dialog.
   *
   * @param event Tastaturereignis
   * @param dialogStage Stage des Dialogs
   * @param confirm Bestaetigungsaktion
   */
  private void handleCreateDialogKeyPress(KeyEvent event, Stage dialogStage, Runnable confirm) {
    if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
      confirm.run();
      event.consume();
      return;
    }

    if (event.getCode() == KeyCode.ESCAPE) {
      dialogStage.close();
      event.consume();
    }
  }

  /**
   * Uebernimmt die Eingaben aus dem Anlege-Dialog in Fenster und Startmenue.
   *
   * @param entry Laufzeitdaten des Zettelkastens
   * @param handle Handle des Zettelfensters
   * @param name eingegebener Name
   * @param color gewaehlte Akzentfarbe
   */
  private void applyZettelkastenDialogResult(ZettelkastenEntry entry,
                                             ZettelWindow.ZettelWindowHandle handle,
                                             String name,
                                             String color) {
    entry.applyProfile(name, color);
    handle.applyWindowProfile(entry.name, entry.color);
    rebuildZettelkastenPopupContent();
  }

  /**
   * Erzeugt die Farbauswahl fuer den modalen Anlege-Dialog.
   *
   * @param toggleGroup ToggleGroup der Farbbuttons
   * @param selectedColor aktuell gewaehlte Farbe
   * @return Zeile mit Farbbuttons
   */
  private HBox createDialogColorRow(ToggleGroup toggleGroup, String selectedColor) {
    HBox colorRow = new HBox(8);

    String[] colors = {
        "#ffd6d6",
        "#d6f5f0",
        "#e0d6ff",
        "#fff3cd",
        "#d6ffe6",
        "#d6e4ff",
        "#f8d6ff",
        "#ffe4d6"
    };

    for (String color : colors) {
      ToggleButton button = createDialogColorButton(color, toggleGroup);
      if (color.equals(selectedColor)) {
        button.setSelected(true);
      }
      colorRow.getChildren().add(button);
    }

    return colorRow;
  }

  /**
   * Erzeugt einen einzelnen Farbbutton fuer den modalen Anlege-Dialog.
   *
   * @param color CSS-Farbe
   * @param toggleGroup ToggleGroup der Farbauswahl
   * @return ToggleButton fuer die Farbe
   */
  private ToggleButton createDialogColorButton(String color, ToggleGroup toggleGroup) {
    ToggleButton button = new ToggleButton();
    button.setToggleGroup(toggleGroup);
    button.setUserData(color);
    button.setMinSize(22, 22);
    button.setMaxSize(22, 22);
    applyDialogColorButtonStyle(button, color, false);

    button.selectedProperty().addListener((obs, oldValue, selected) ->
        applyDialogColorButtonStyle(button, color, selected)
    );

    return button;
  }

  /**
   * Aktualisiert die Darstellung eines Farbbuttons.
   *
   * @param button Farbbutton
   * @param color CSS-Farbe
   * @param selected ob die Farbe ausgewaehlt ist
   */
  private void applyDialogColorButtonStyle(ToggleButton button, String color, boolean selected) {
    button.setStyle(
        "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-border-color: " + (selected ? "#ffffff" : "#888") + ";" +
            "-fx-border-width: " + (selected ? "2" : "1") + ";" +
            "-fx-border-radius: 6;"
    );
  }

  /**
   * Gibt die aktuell gewaehlte Farbe einer ToggleGroup zurueck.
   *
   * @param toggleGroup ToggleGroup der Farbauswahl
   * @return CSS-Farbe oder {@code null}
   */
  private String getSelectedColor(ToggleGroup toggleGroup) {
    if (toggleGroup == null || toggleGroup.getSelectedToggle() == null) {
      return null;
    }
    return (String) toggleGroup.getSelectedToggle().getUserData();
  }

  /**
   * Zentriert den modalen Dialog ueber dem zugehoerigen Zettelfenster.
   *
   * @param dialogStage Dialog-Stage
   * @param ownerStage Owner-Stage
   */
  private void centerDialogOverOwner(Stage dialogStage, Stage ownerStage) {
    if (dialogStage == null || ownerStage == null) {
      return;
    }

    dialogStage.setX(ownerStage.getX() + (ownerStage.getWidth() - dialogStage.getWidth()) / 2.0);
    dialogStage.setY(ownerStage.getY() + (ownerStage.getHeight() - dialogStage.getHeight()) / 2.0);
  }

  /**
   * Baut den Inhalt des Zettelkasten-Popups neu auf.
   * Bereits geöffnete Zettelkästen stehen oberhalb des Eintrags zum Neuanlegen.
   */
  private void rebuildZettelkastenPopupContent() {
    zettelkastenPopupContent.getChildren().clear();

    for (ZettelkastenEntry entry : zettelkaesten) {
      StartMenuItem item = new StartMenuItem(entry.name);
      applyPopupMenuItemStyle(item);
      item.setOnMouseClicked(event -> activateOpenZettelkasten(entry));
      zettelkastenPopupContent.getChildren().add(item);
    }

    StartMenuItem createItem = new StartMenuItem("Zettelkasten anlegen");
    applyPopupMenuItemStyle(createItem);
    createItem.setOnMouseClicked(event -> {
      hideZettelkastenPopup();
      hideWindowMenuPopup();
      unlockOverlayInteractionIfNoPopupShowing();
      openZettelkasten(new ZettelkastenEntry(null, null));
    });

    zettelkastenPopupContent.getChildren().add(createItem);
  }
}
