package de.zettelkastenfx.fx.start;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

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

  public StartWindowController(Stage stage, StartWindowView view) {
    this.stage = stage;
    this.view = view;

    // “Normales” kleines Fenster: leicht abgerundet, hell
    this.collapsedWidth = 72;
    this.collapsedHeight = 72;

    // Aufklappen: deutlich mehr Höhe, etwas mehr Breite
    this.expandedWidth = 120;
    this.expandedHeight = 150;

    this.animatedWidth = new SimpleDoubleProperty(collapsedWidth);
    this.animatedHeight = new SimpleDoubleProperty(collapsedHeight);

    this.windowShape = new Rectangle(collapsedWidth, collapsedHeight);
    this.windowShape.setArcWidth(16);
    this.windowShape.setArcHeight(16);

    wireSizing();
    wireEvents();
  }

  public double getCollapsedDiameter() {
    // bleibt kompatibel zu StartWindow (Scene init); wir nutzen jetzt width statt “diameter”
    return collapsedWidth;
  }

  public double getHeight() {
    return collapsedHeight;
  }

  public void applyInitialState() {
    setMenuVisible(false);
    setExitVisible(false);
    setAvatarVisible(true);

    animatedWidth.set(collapsedWidth);
    animatedHeight.set(collapsedHeight);
    applySize(collapsedWidth, collapsedHeight);
  }

  private void wireEvents() {
    Node root = view.getRoot();
    root.setOnMouseEntered(event -> expand());
    root.setOnMouseExited(event -> collapse());

    view.getExitButton().setOnAction(event -> Platform.exit());

    // Dummies: nur als Klick-Test
    view.getItemOne().setOnMouseClicked(event -> openNewZettelWindow());
    view.getItemTwo().setOnMouseClicked(event -> System.out.println("DB-Dialog kommt später"));
    view.getItemThree().setOnMouseClicked(event -> Platform.exit());

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

  private void applySize(double width, double height) {
    view.getRoot().setPrefSize(width, height);
    view.getRoot().setMinSize(width, height);
    view.getRoot().setMaxSize(width, height);

    windowShape.setWidth(width);
    windowShape.setHeight(height);

    stage.setWidth(width);
    stage.setHeight(height);
  }

  private void expand() {
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

    currentAnimation.setOnFinished(event -> setAvatarVisible(false));
    currentAnimation.play();
  }

  private void collapse() {
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

  private void openNewZettelWindow() {
    javafx.stage.Stage newStage = new javafx.stage.Stage();
    new de.zettelkastenfx.notes.controller.ZettelWindow().show(newStage);
  }
}