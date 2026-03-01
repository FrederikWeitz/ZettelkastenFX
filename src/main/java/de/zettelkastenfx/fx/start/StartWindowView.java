package de.zettelkastenfx.fx.start;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.io.InputStream;

public class StartWindowView {

  private static final double AVATAR_SIZE = 44;

  private final StackPane root;
  private final VBox layout;

  private final StackPane headerPane;
  private final Node avatarNode;
  private final Button exitButton;

  private final VBox menuBox;
  private final StartMenuItem itemOne;
  private final StartMenuItem itemTwo;
  private final StartMenuItem itemThree;

  public StartWindowView() {
    root = new StackPane();
    root.getStyleClass().add("start-window-root");

    layout = new VBox();
    layout.getStyleClass().add("start-window-layout");
    layout.setPadding(new Insets(10));
    layout.setSpacing(10);

    headerPane = new StackPane();
    headerPane.getStyleClass().add("start-window-header");
    headerPane.setMinHeight(48);
    headerPane.setPrefHeight(48);
    headerPane.setMaxHeight(48);

    avatarNode = createAvatarNode();
    exitButton = createExitButton();

    headerPane.getChildren().addAll(avatarNode, exitButton);
    StackPane.setAlignment(exitButton, Pos.TOP_RIGHT);

    menuBox = new VBox();
    menuBox.getStyleClass().add("start-window-menu");
    menuBox.setSpacing(0);
    menuBox.setPadding(new Insets(2, 0, 0, 0));

    itemOne = new StartMenuItem("Neuer Zettel");
    itemTwo = new StartMenuItem("Datenbank");
    itemThree = new StartMenuItem("Schließen");

    menuBox.getChildren().addAll(itemOne, itemTwo, itemThree);

    layout.getChildren().addAll(headerPane, menuBox);
    root.getChildren().add(layout);
  }

  public StackPane getRoot() {
    return root;
  }

  public VBox getLayout() {
    return layout;
  }

  public StackPane getHeaderPane() {
    return headerPane;
  }

  public Node getAvatarNode() {
    return avatarNode;
  }

  public Button getExitButton() {
    return exitButton;
  }

  public VBox getMenuBox() {
    return menuBox;
  }

  public StartMenuItem getItemOne() {
    return itemOne;
  }

  public StartMenuItem getItemTwo() {
    return itemTwo;
  }

  public StartMenuItem getItemThree() {
    return itemThree;
  }

  private Node createAvatarNode() {
    InputStream imageStream = getClass().getResourceAsStream("/images/start-avatar.png");
    if (imageStream == null) {
      // Fallback: leerer runder Platzhalter
      Circle placeholder = new Circle(AVATAR_SIZE / 2);
      placeholder.getStyleClass().add("start-window-avatar-placeholder");
      return new StackPane(placeholder);
    }

    Image image = new Image(imageStream);
    ImageView imageView = new ImageView(image);
    imageView.setFitWidth(AVATAR_SIZE);
    imageView.setFitHeight(AVATAR_SIZE);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);

    // Rund clippen
    Circle clipCircle = new Circle(AVATAR_SIZE / 2);
    clipCircle.setCenterX(AVATAR_SIZE / 2);
    clipCircle.setCenterY(AVATAR_SIZE / 2);
    imageView.setClip(clipCircle);

    // Rand-Kreis
    Circle borderCircle = new Circle(AVATAR_SIZE / 2);
    borderCircle.getStyleClass().add("start-window-avatar-border");

    StackPane avatarPane = new StackPane(imageView, borderCircle);
    avatarPane.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
    avatarPane.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
    avatarPane.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);
    avatarPane.getStyleClass().add("start-window-avatar");

    return avatarPane;
  }

  private Button createExitButton() {
    Button button = new Button("✕");
    button.getStyleClass().add("start-window-exit-button");
    return button;
  }
}