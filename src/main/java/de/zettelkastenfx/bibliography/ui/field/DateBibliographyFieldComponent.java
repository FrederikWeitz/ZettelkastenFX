package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;
import de.zettelkastenfx.bibliography.model.StringBibValue;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Bibliographische Feldkomponente für Datumsfelder.
 * Die Komponente kapselt einen DatePicker und überträgt dessen Wert als
 * ISO-Datum in einen StringBibValue.
 */
public class DateBibliographyFieldComponent implements BibliographyFieldComponent {

  private final String mediaTypeBibName;
  private final MediaAttributeDefinition attribute;
  private final BibliographyFieldPresentation presentation;
  private final BooleanProperty editOn = new SimpleBooleanProperty(true);
  private static final double FIELD_LABEL_WIDTH = 170;

  private final DatePicker datePicker = new DatePicker();
  private final HBox root = new HBox(6);
  private final Label label = new Label();

  /**
   * Erzeugt eine Datumsfeld-Komponente.
   *
   * @param request Erzeugungsanfrage
   */
  public DateBibliographyFieldComponent(BibliographyFieldRequest request) {
    Objects.requireNonNull(request, "request");

    this.mediaTypeBibName = request.mediaTypeBibName();
    this.attribute = request.attribute();
    this.presentation = request.presentation();

    datePicker.disableProperty().bind(editOn.not());
    datePicker.setPromptText(resolvePromptText());

    buildRoot(request);
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
    LocalDate value = datePicker.getValue();
    if (value == null) {
      return List.of();
    }

    return List.of(new StringBibValue(bibtexName(), value.toString()));
  }

  @Override
  public void writeValues(Collection<? extends BibValue> values) {
    clear();

    if (values == null || values.isEmpty()) {
      return;
    }

    for (BibValue value : values) {
      if (!(value instanceof StringBibValue(String bibtexName, String value1))) {
        continue;
      }

      if (!bibtexName().equals(sanitize(bibtexName))) {
        continue;
      }

      datePicker.setValue(parseLocalDate(value1));
      return;
    }
  }

  @Override
  public void clear() {
    datePicker.setValue(null);
  }

  @Override
  public boolean isEmpty() {
    return datePicker.getValue() == null;
  }

  /**
   * Liefert den gekapselten DatePicker.
   *
   * @return DatePicker
   */
  public DatePicker datePicker() {
    return datePicker;
  }

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

    HBox.setHgrow(datePicker, Priority.ALWAYS);
    datePicker.setMaxWidth(Double.MAX_VALUE);
    root.getChildren().add(datePicker);

    if (presentation.showRemoveButton()) {
      Button deleteButton = BaseIcon.TAG_BLUE_DELETE.button("Eigenschaft entfernen");
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
   * Parst ein ISO-Datum tolerant.
   *
   * @param value Rohwert
   * @return Datum oder {@code null}
   */
  private LocalDate parseLocalDate(String value) {
    String text = value == null ? "" : value.trim();
    if (text.isBlank()) {
      return null;
    }

    try {
      return LocalDate.parse(text);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Normalisiert Textwerte.
   *
   * @param value Rohwert
   * @return getrimmter Wert
   */
  private String sanitize(String value) {
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
  }
}