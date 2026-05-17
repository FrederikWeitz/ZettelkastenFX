package de.zettelkastenfx.notes.bottom;

import de.zettelkastenfx.base.BaseIcon;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.function.UnaryOperator;

public class BottomToolbar {

  private final HBox root = new HBox(8);

  private final Label lblZettel = new Label("Zettel");
  private final TextField txtNoteId = new TextField();

  private final Button btnBack = BaseIcon.BULLET_ARROW_LEFT.button(null);
  private final Button btnForward = BaseIcon.BULLET_ARROW_RIGHT.button(null);

  private final ToggleButton btnBook = iconToggle(BaseIcon.BOOK, "Bibliografie");

  public BottomToolbar() {
    root.getStyleClass().add("zettel-bottom-toolbar");

    txtNoteId.setPrefColumnCount(5);

    // Nur Zahlen zulassen
    UnaryOperator<TextFormatter.Change> filter = change -> {
      String next = change.getControlNewText();
      return next.matches("\\d*") ? change : null;
    };
    txtNoteId.setTextFormatter(new TextFormatter<>(filter));

    btnBack.getStyleClass().add("nav-button");
    btnForward.getStyleClass().add("nav-button");
    btnBook.setSelected(true);

    root.getChildren().addAll(
        lblZettel, txtNoteId,
        btnBack, btnForward,
        spacer(),
        btnBook
    );
  }

  private static Node spacer() {
    javafx.scene.layout.Region r = new javafx.scene.layout.Region();
    r.setMinWidth(8);
    r.setPrefWidth(8);
    r.setMaxWidth(8);
    return r;
  }

  private static ToggleButton iconToggle(BaseIcon icon, String tooltip) {
    ToggleButton b = new ToggleButton();
    b.getStyleClass().add("icon-toggle");   // eigene Klasse (oder icon-button, wenn du willst)
    b.setGraphic(icon.imageView());
    b.setTooltip(new Tooltip(tooltip));
    b.setFocusTraversable(false);
    return b;
  }

  public Node getRoot() { return root; }

  public TextField noteIdField() { return txtNoteId; }
  public Button backButton() { return btnBack; }
  public Button forwardButton() { return btnForward; }
  public ToggleButton bookToggle() { return btnBook; }
}
