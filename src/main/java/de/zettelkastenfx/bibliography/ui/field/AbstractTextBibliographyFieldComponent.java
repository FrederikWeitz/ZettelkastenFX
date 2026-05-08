package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;
import de.zettelkastenfx.bibliography.ui.support.BibliographyFieldView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Gemeinsame Basisklasse für einfache textbasierte bibliographische Felder.
 * Die Klasse kapselt Zeilenaufbau, Label-/Löschbutton-Präsentation und
 * Editierbarkeit, enthält aber keine konkrete BibValue-Erzeugung.
 */
public abstract class AbstractTextBibliographyFieldComponent implements BibliographyFieldComponent {

  private final String mediaTypeBibName;
  private final MediaAttributeDefinition attribute;
  private final BibliographyFieldPresentation presentation;
  private final BooleanProperty editOn = new SimpleBooleanProperty(true);
  private static final double FIELD_LABEL_WIDTH = 170;

  private final BibliographyFieldView fieldView;
  private final HBox root = new HBox(6);
  private final Label label = new Label();
  private Button deleteButton;

  /**
   * Erzeugt eine textbasierte Feldkomponente.
   *
   * @param request Erzeugungsanfrage
   */
  protected AbstractTextBibliographyFieldComponent(BibliographyFieldRequest request) {
    Objects.requireNonNull(request, "request");

    this.mediaTypeBibName = request.mediaTypeBibName();
    this.attribute = request.attribute();
    this.presentation = request.presentation();
    this.fieldView = new BibliographyFieldView(resolvePromptText(), editOn);

    buildRoot(request);
  }

  /**
   * Liefert die gekapselte Feldansicht.
   *
   * @return Feldansicht
   */
  public BibliographyFieldView fieldView() {
    return fieldView;
  }

  @Override
  public String mediaTypeBibName() {
    return mediaTypeBibName;
  }

  @Override
  public String bibtexName() {
    if (attribute.fieldDefinition() == null) {
      return "";
    }
    return sanitize(attribute.fieldDefinition().bibtexName());
  }

  @Override
  public MediaAttributeDefinition attribute() {
    return attribute;
  }

  @Override
  public Node getNode() {
    return root;
  }

  @Override
  public void setEditable(boolean editable) {
    editOn.set(editable);
  }

  @Override
  public Collection<BibValue> readValues() {
    BibValue value = readSingleValue();
    return value == null ? List.of() : List.of(value);
  }

  @Override
  public void writeValues(Collection<? extends BibValue> values) {
    clear();

    if (values == null || values.isEmpty()) {
      return;
    }

    for (BibValue value : values) {
      if (value == null) {
        continue;
      }

      if (bibtexName().equals(sanitize(value.bibtexName()))) {
        writeSingleValue(value);
        return;
      }
    }
  }

  @Override
  public void clear() {
    fieldView.setText("");
  }

  @Override
  public boolean isEmpty() {
    return fieldView.getText().isBlank();
  }

  /**
   * Liest den konkreten BibValue-Typ aus der UI.
   *
   * @return BibValue oder {@code null}
   */
  protected abstract BibValue readSingleValue();

  /**
   * Schreibt einen konkreten BibValue-Typ in die UI.
   *
   * @param value Wert
   */
  protected abstract void writeSingleValue(BibValue value);

  /**
   * Baut die sichtbare Feldzeile auf.
   *
   * @param request Erzeugungsanfrage
   */
  private void buildRoot(BibliographyFieldRequest request) {
    root.getStyleClass().add("biblio-field-row");

    label.setText(resolveLabelText());
    label.getStyleClass().add("biblio-field-label");
    label.setMinWidth(FIELD_LABEL_WIDTH);
    label.setPrefWidth(FIELD_LABEL_WIDTH);

    root.getChildren().add(label);

    HBox.setHgrow(fieldView, Priority.ALWAYS);
    root.getChildren().add(fieldView);

    if (presentation.showRemoveButton()) {
      deleteButton = BaseIcon.TAG_BLUE_DELETE.button("Eigenschaft entfernen");
      deleteButton.setFocusTraversable(false);
      deleteButton.setOnAction(event -> request.onRemove().run());
      root.getChildren().add(deleteButton);
    }

    applyPresentation(presentation);
  }

  /**
   * Ermittelt den sichtbaren Labeltext.
   *
   * @return Labeltext
   */
  private String resolveLabelText() {
    if (attribute.fieldDefinition() == null) {
      return "";
    }
    return sanitize(attribute.fieldDefinition().displayName());
  }

  /**
   * Ermittelt den Prompttext.
   *
   * @return Prompttext
   */
  private String resolvePromptText() {
    String label = resolveLabelText();
    return label.isBlank() ? bibtexName() : label;
  }

  /**
   * Normalisiert technischen oder sichtbaren Text.
   *
   * @param value Rohwert
   * @return getrimmter Wert
   */
  protected String sanitize(String value) {
    return value == null ? "" : value.trim();
  }

  @Override
  public void applyPresentation(BibliographyFieldPresentation presentation) {
    if (presentation == null) {
      return;
    }

    boolean showLabel = presentation.showLabel();

    label.setVisible(showLabel);
    label.setManaged(showLabel);

    if (showLabel) {
      label.setMinWidth(FIELD_LABEL_WIDTH);
      label.setPrefWidth(FIELD_LABEL_WIDTH);
      label.setMaxWidth(FIELD_LABEL_WIDTH);
    } else {
      label.setMinWidth(0);
      label.setPrefWidth(0);
      label.setMaxWidth(0);
    }

    if (deleteButton != null) {
      deleteButton.setVisible(true);
      deleteButton.setManaged(true);
    }
  }
}
