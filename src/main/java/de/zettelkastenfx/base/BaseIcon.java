package de.zettelkastenfx.base;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum BaseIcon {

  ADD("add", "Hinzufügen"),

  ARROW_REFRESH("arrow_refresh", "Aktualisieren"),

  BOOK("book", "Bibliografie"),
  BOOK_ADD("book_add", "Buch hinzufügen"),
  BOOK_DELETE("book_delete", "Buch löschen"),
  BOOK_EDIT("book_edit", "Buch editieren"),

  BULLET_ARROW_LEFT("bullet_arrow_left", "Zurück"),
  BULLET_ARROW_RIGHT("bullet_arrow_right", "Vor"),
  BULLET_GO("bullet_go", "Weiter"),
  BULLET_GREEN("bullet_green", "geöffnet"),
  BULLET_RED("bullet_red", "gesperrt"),

  CLEAR_FORMATTING("clear_formatting", "Formatierung löschen"),

  CROSS("cross", "Schließen"),

  DELETE("delete", "Löschen"),

  DOCUMENT_PAGE_PREVIOUS("document_page_previous", "Zurück"),
  DOCUMENT_PAGE_NEXT("document_page_next", "Vor"),

  INFORMATION("information", "Info"),

  KEY("key", "Schlagwörter"),

  LAYOUTS_JOIN("layouts_join", "Zuklappen"),
  LAYOUTS_SPLIT("layouts_split", "Aufklappen"),

  LINK_EDIT("link_edit", "Verweise"),
  LIST("list", "Liste"),

  MAGNIFY_LINK("magnify_link", "Verweise"),

  PAGE_ADD("page_add", "Neu"),
  PAGE_COPY("page_copy", "Kopieren"),
  PAGE_FIND("page_find", "Suchen"),
  PAGE_RED("page_red", "Kopieren bibliographische Angaben"),

  PAGE_LINK("page_link", "Folgezettel"),
  PAGE_BULB_ON("page_bulb_on", "Idee"),
  QUESTION("question", "Fragen"),
  PAGE_LIGHTNING("page_lightning", "Kritik"),
  PAGE_EDIT("page_edit", "Aufgabe"),

  PERFORMANCE_ANALYSIS("performance_analysis", "Cluster"),

  TEXT_ALIGN_CENTER("text_align_center", "Zentrieren"),
  TEXT_ALIGN_JUSTIFY("text_align_justify", "Blocksatz"),
  TEXT_ALIGN_LEFT("text_align_left", "Linksbündig"),
  TEXT_ALIGN_RIGHT("text_align_right", "Rechtsbündig"),

  TEXT_BOLD("text_bold", "Fett"),
  TEXT_ITALIC("text_italic", "Kursiv"),
  TEXT_UNDERLINE("text_underline", "Unterstreichen"),
  TEXT_STRIKETHROUGH("text_strikethrough", "Durchstreichen"),

  TEXT_SUBSCRIPT("text_subscript", "Tiefgestellt"),
  TEXT_SUPERSCRIPT("text_superscript", "Hochgestellt"),

  TEXT_HEADING_1("text_heading_1", "Überschrift 1"),
  TEXT_HEADING_2("text_heading_2", "Überschrift 2"),

  TEXT_INDENT("text_indent", "Einzug erhöhen"),
  TEXT_INDENT_REMOVE("text_indent_remove", "Einzug verringern"),

  TEXT_LIST_BULLETS("text_list_bullets", "Aufzählung"),
  TEXT_LIST_NUMBERS("text_list_numbers", "Nummerierung"),

  WWW_PAGE("www_page", "Website");

  private static final String ICONS_BASE_PATH = "/button-icons/";
  private static final String[] SUPPORTED_EXTENSIONS = { ".png", ".gif", ".jpg", ".jpeg" };
  private static final double DEFAULT_SIZE = 16.0;
  private static final String ICON_BUTTON_STYLESHEET = "/styles/icon-button.css";

  private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

  private final String fileBaseName;
  private final String tooltipText;

  BaseIcon(String fileBaseName, String tooltipText) {
    this.fileBaseName = requireNonBlank(fileBaseName, "fileBaseName");
    this.tooltipText = requireNonBlank(tooltipText, "tooltipText");
  }

  /**
   * Liefert das Icon als JavaFX Image (wird intern gecached).
   */
  public Image image() {
    String resourcePath = resolveResourcePath(fileBaseName);
    return IMAGE_CACHE.computeIfAbsent(resourcePath, BaseIcon::loadImageByPath);
  }

  /**
   * Liefert eine neue ImageView (Standardgröße 16x16).
   * Hinweis: ImageViews sollten nicht geteilt werden, deshalb wird pro Aufruf eine neue erzeugt.
   */
  public ImageView imageView() {
    ImageView imageView = new ImageView(image());
    imageView.setFitWidth(DEFAULT_SIZE);
    imageView.setFitHeight(DEFAULT_SIZE);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);
    return imageView;
  }

  /**
   * Liefert den Tooltip-Text zum Icon.
   */
  public String tooltip() {
    return tooltipText;
  }

  /**
   * Button mit vorgefertigtem Tooltip
   *
   * @return Button mit Imageview und vorgefertigtem Tooltip
   */
  public Button button() {
    Button button = new Button();
    button.getStyleClass().add("icon-button");
    button.setGraphic(imageView());
    button.setTooltip(new Tooltip(tooltip()));
    return button;
  }

  /**
   * Button mit manuell eingefügtem Tooltip. Wenn tooltip = null, dann ohne Tooltip
   *
   * @param tooltip Text für den Tooltip
   * @return Button mit Imageview und individuellem Tooltip
   */
  public Button button(String tooltip) {
    Button button = new Button();
    button.getStyleClass().add("icon-button");
    button.setGraphic(imageView());
    if (tooltip != null) button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static Image loadImageByPath(String resourcePath) {
    try (InputStream stream = BaseIcon.class.getResourceAsStream(resourcePath)) {
      if (stream == null) {
        throw new IllegalStateException("Icon-Resource nicht gefunden: " + resourcePath);
      }
      return new Image(stream);
    } catch (Exception exception) {
      throw new IllegalStateException("Konnte Icon nicht laden: " + resourcePath, exception);
    }
  }

  private static String resolveResourcePath(String baseName) {
    if (hasFileExtension(baseName)) {
      String directPath = ICONS_BASE_PATH + baseName;
      requireResourceExists(directPath);
      return directPath;
    }

    for (String extension : SUPPORTED_EXTENSIONS) {
      String candidate = ICONS_BASE_PATH + baseName + extension;
      if (resourceExists(candidate)) {
        return candidate;
      }
    }

    throw new IllegalArgumentException(
        "Icon nicht gefunden: " + baseName + " (gesucht unter " + ICONS_BASE_PATH + " mit Endungen png/gif/jpg/jpeg)"
    );
  }

  private static boolean resourceExists(String resourcePath) {
    try (InputStream stream = BaseIcon.class.getResourceAsStream(resourcePath)) {
      return stream != null;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void requireResourceExists(String resourcePath) {
    if (!resourceExists(resourcePath)) {
      throw new IllegalArgumentException("Icon-Resource nicht gefunden: " + resourcePath);
    }
  }

  private static boolean hasFileExtension(String name) {
    int lastDotIndex = name.lastIndexOf('.');
    return lastDotIndex > 0 && lastDotIndex < name.length() - 1;
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " darf nicht leer sein.");
    }
    return value;
  }
}
