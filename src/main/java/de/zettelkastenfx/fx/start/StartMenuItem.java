package de.zettelkastenfx.fx.start;

import javafx.geometry.Pos;
import javafx.scene.control.Label;

public final class StartMenuItem extends Label {

  public StartMenuItem(String text) {
    super(text);
    getStyleClass().add("start-window-menu-item");

    setAlignment(Pos.CENTER_LEFT);
    setMaxWidth(Double.MAX_VALUE);

    setMinHeight(22);
    setPrefHeight(22);setFocusTraversable(false);
  }
}
