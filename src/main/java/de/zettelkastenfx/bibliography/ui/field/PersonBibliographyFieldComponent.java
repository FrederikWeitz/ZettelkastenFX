package de.zettelkastenfx.bibliography.ui.field;

import de.zettelkastenfx.base.BaseIcon;
import de.zettelkastenfx.bibliography.model.Author;
import de.zettelkastenfx.bibliography.model.BibValue;
import de.zettelkastenfx.bibliography.model.MediaAttributeDefinition;
import de.zettelkastenfx.bibliography.model.PersonBibValue;
import de.zettelkastenfx.bibliography.model.PersonRef;
import de.zettelkastenfx.bibliography.ui.lookup.AuthorSuggestion;
import de.zettelkastenfx.bibliography.ui.support.BibliographyAuthorsView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bibliographische Feldkomponente für Personenfelder wie Autor, Herausgeber
 * oder Übersetzer.
 * Die Komponente kapselt die vorhandene BibliographyAuthorsView und übersetzt
 * zwischen UI-Autoren und PersonBibValue.
 */
public class PersonBibliographyFieldComponent implements BibliographyFieldComponent {

  private final String mediaTypeBibName;
  private final MediaAttributeDefinition attribute;
  private final BibliographyFieldPresentation presentation;
  private final BooleanProperty editOn = new SimpleBooleanProperty(true);
  private static final double FIELD_LABEL_WIDTH = 170;

  private final BibliographyAuthorsView authorsView;
  private final VBox root = new VBox(4);
  private final HBox header = new HBox(6);
  private final Label label = new Label();
  private Button deleteButton;

  /**
   * Erzeugt eine Personenfeld-Komponente.
   *
   * @param request Erzeugungsanfrage
   * @param suggestionProvider Provider für Autorenvorschläge
   * @param authorsEditedHandler Callback nach Bearbeitung einer Personenzeile
   * @param refreshSelectionHandler Callback zur Aktualisierung abhängiger Auswahlzustände
   * @param suppressLookupCallbackSupplier liefert, ob Lookup-Callbacks gerade unterdrückt werden
   * @param selectedEntryUpdater Callback zum Aktualisieren der selektierten Eintrags-ID
   * @param visibleRowCount sichtbare Zeilenzahl der Personenliste
   */
  public PersonBibliographyFieldComponent(
      BibliographyFieldRequest request,
      Function<BibliographyAuthorsView.AuthorRow, List<AuthorSuggestion>> suggestionProvider,
      java.util.function.Consumer<String> authorsEditedHandler,
      Runnable refreshSelectionHandler,
      java.util.function.Supplier<Boolean> suppressLookupCallbackSupplier,
      java.util.function.BiConsumer<String, Integer> selectedEntryUpdater,
      int visibleRowCount
  ) {
    Objects.requireNonNull(request, "request");

    this.mediaTypeBibName = request.mediaTypeBibName();
    this.attribute = request.attribute();
    this.presentation = request.presentation();

    this.authorsView = new BibliographyAuthorsView(
        mediaTypeBibName,
        editOn,
        suggestionProvider,
        authorsEditedHandler,
        refreshSelectionHandler,
        suppressLookupCallbackSupplier,
        selectedEntryUpdater,
        visibleRowCount
    );

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
    List<PersonRef> persons = mapAuthors(authorsView.toAuthors());
    if (persons.isEmpty()) {
      return List.of();
    }

    return List.of(new PersonBibValue(bibtexName(), persons));
  }

  @Override
  public void writeValues(Collection<? extends BibValue> values) {
    clear();

    if (values == null || values.isEmpty()) {
      return;
    }

    for (BibValue value : values) {
      if (!(value instanceof PersonBibValue(String bibtexName, List<PersonRef> value1))) {
        continue;
      }

      if (!bibtexName().equals(sanitize(bibtexName))) {
        continue;
      }

      authorsView.fromAuthors(mapPersons(value1));
      return;
    }
  }

  @Override
  public void clear() {
    authorsView.fromAuthors(List.of());
  }

  @Override
  public boolean isEmpty() {
    return authorsView.toAuthors().stream()
               .noneMatch(author -> author != null
                                        && (!sanitize(author.getFirstName()).isBlank()
                                                || !sanitize(author.getLastName()).isBlank()));
  }

  /**
   * Liefert die gekapselte Autorenansicht.
   *
   * @return Autorenansicht
   */
  public BibliographyAuthorsView authorsView() {
    return authorsView;
  }

  /**
   * Baut die sichtbare Komponente einschließlich optionalem Label und
   * optionalem Entfernen-Button auf.
   *
   * @param request Erzeugungsanfrage
   */
  private void buildRoot(BibliographyFieldRequest request) {
    root.getStyleClass().add("biblio-person-field-root");

    header.getStyleClass().add("biblio-person-field-header");

    label.setText(resolveLabelText());
    label.getStyleClass().add("biblio-field-label");
    label.setMinWidth(FIELD_LABEL_WIDTH);
    label.setPrefWidth(FIELD_LABEL_WIDTH);
    header.getChildren().add(label);

    HBox spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    header.getChildren().add(spacer);

    if (presentation.showRemoveButton()) {
      deleteButton = BaseIcon.TAG_BLUE_DELETE.button("Eigenschaft entfernen");
      deleteButton.setFocusTraversable(false);
      deleteButton.setOnAction(event -> request.onRemove().run());
      header.getChildren().add(deleteButton);
    }

    root.getChildren().addAll(header, authorsView);

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
   * Wandelt UI-Autoren in PersonRef-Werte des dynamischen Modells um.
   *
   * @param authors UI-Autoren
   * @return Personenreferenzen
   */
  private List<PersonRef> mapAuthors(List<Author> authors) {
    if (authors == null || authors.isEmpty()) {
      return List.of();
    }

    List<PersonRef> result = new ArrayList<>();
    int position = 1;

    for (Author author : authors) {
      if (author == null) {
        continue;
      }

      String firstName = sanitize(author.getFirstName());
      String lastName = sanitize(author.getLastName());

      if (firstName.isBlank() && lastName.isBlank()) {
        continue;
      }

      result.add(new PersonRef(
          author.getId(),
          firstName,
          lastName,
          author.getPosition() > 0 ? author.getPosition() : position
      ));
      position++;
    }

    return result;
  }

  /**
   * Wandelt PersonRef-Werte des dynamischen Modells in UI-Autoren um.
   *
   * @param persons Personenreferenzen
   * @return UI-Autoren
   */
  private List<Author> mapPersons(List<PersonRef> persons) {
    if (persons == null || persons.isEmpty()) {
      return List.of();
    }

    List<Author> result = new ArrayList<>();
    int position = 1;

    for (PersonRef person : persons) {
      if (person == null) {
        continue;
      }

      Author author = new Author();
      author.setId(person.id());
      author.setFirstName(person.firstName());
      author.setLastName(person.lastName());
      author.setPosition(person.position() != null && person.position() > 0 ? person.position() : position);
      result.add(author);
      position++;
    }

    return result;
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

    boolean showHeader = showLabel || deleteButton != null;
    header.setVisible(showHeader);
    header.setManaged(showHeader);

    if (deleteButton != null) {
      deleteButton.setVisible(true);
      deleteButton.setManaged(true);
    }
  }
}