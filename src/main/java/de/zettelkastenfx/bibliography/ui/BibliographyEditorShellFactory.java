package de.zettelkastenfx.bibliography.ui;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.MediaTypeDefinition;
import de.zettelkastenfx.bibliography.ui.lookup.AiChoiceProvider;
import de.zettelkastenfx.bibliography.ui.lookup.DynamicLookupCoordinator;

import java.util.List;
import java.util.Objects;

/**
 * Erzeugt vollständig konfigurierte Bibliographie-Shells für verschiedene
 * Einbettungskontexte, ohne dass der aufrufende Controller selbst die gesamte
 * Verdrahtung kennen muss.
 */
public class BibliographyEditorShellFactory {

  private final BibliographyService bibliographyService;

  /**
   * Callback-Schnittstelle für Shell-Aktionen.
   */
  public interface ShellCallbacks {
    /**
     * Speichert den aktuell sichtbaren Eintrag.
     *
     * @param shell verwendete Shell
     */
    void onSave(BibliographyEditorShell shell);

    /**
     * Reagiert auf Löschen innerhalb der Shell.
     *
     * @param shell verwendete Shell
     */
    default void onDelete(BibliographyEditorShell shell) {
    }

    /**
     * Reagiert auf den Zustand ohne Auswahl.
     *
     * @param shell verwendete Shell
     */
    default void onNoneSelected(BibliographyEditorShell shell) {
    }

    /**
     * Optionaler Callback zum Anlegen eines Related-Zielmediums.
     *
     * @param shell verwendete Ursprungsshell
     * @param relatedBibtexName technischer BibTeX-Name des Related-Feldes
     * @param targetMediaTypeBibName technischer Ziel-Medientypname
     * @param displayText aktueller Feldtext der Ursprungsshell
     */
    default void onRequestCreateRelated(BibliographyEditorShell shell,
                                        String relatedBibtexName,
                                        String targetMediaTypeBibName,
                                        String displayText) {
    }
  }

  /**
   * Gebündeltes Ergebnis aus Shell und zugehörigem Adapter.
   *
   * @param shell erzeugte Shell
   */
  public record ShellBundle(BibliographyEditorShell shell) {
  }

  /**
   * Erzeugt eine neue Factory.
   *
   * @param bibliographyService Bibliographie-Fassade
   * @param dataSource derzeit noch aus Kompatibilitätsgründen übergeben
   */
  public BibliographyEditorShellFactory(BibliographyService bibliographyService,
                                        javax.sql.DataSource dataSource) {
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");
    Objects.requireNonNull(dataSource, "dataSource");
  }

  /**
   * Erzeugt eine vollständig konfigurierte Shell.
   *
   * @param callbacks Callback-Implementierung für Speichern, Löschen und
   *                  optionale Untereditoren
   * @return Shell-Bundle
   */
  public ShellBundle create(ShellCallbacks callbacks) {
    Objects.requireNonNull(callbacks, "callbacks");

    var shell = new BibliographyEditorShell();
    shell.setBibliographyService(bibliographyService);

    List<BibliographyEditorShell.MediaTypeOption> options =
        bibliographyService.listMediaTypes().stream()
            .map(this::toOption)
            .toList();

    shell.setDynamicLookupCoordinator(
        new DynamicLookupCoordinator(bibliographyService)
    );
    shell.setMediaTypeOptions(options);

    shell.setAiChoiceProvider(new AiChoiceProvider() {
      @Override
      public List<String> findProviders(String prefix, String modelFilter, int limit) {
        return bibliographyService.findAiProviders(prefix, modelFilter, limit);
      }

      @Override
      public List<String> findModels(String prefix, String providerFilter, int limit) {
        return bibliographyService.findAiModels(prefix, providerFilter, limit);
      }

      @Override
      public List<String> loadRecentProviders(int limit) {
        return bibliographyService.loadRecentAiProviders(limit);
      }

      @Override
      public List<String> loadRecentModels(int limit) {
        return bibliographyService.loadRecentAiModels(limit);
      }
    });

    shell.setOnAdd(() -> callbacks.onSave(shell));
    shell.setOnDelete(() -> callbacks.onDelete(shell));
    shell.setOnNoneSelected(() -> callbacks.onNoneSelected(shell));
    shell.setOnRequestCreateRelated((relatedBibtexName, targetMediaTypeBibName, displayText) ->
                                        callbacks.onRequestCreateRelated(
                                            shell,
                                            relatedBibtexName,
                                            targetMediaTypeBibName,
                                            displayText
                                        )
    );

    return new ShellBundle(shell);
  }

  /**
   * Wandelt einen Medientyp in eine sichtbare Shell-Option um.
   *
   * @param type Medientypdefinition
   * @return Shell-Option
   */
  private BibliographyEditorShell.MediaTypeOption toOption(MediaTypeDefinition type) {
    return new BibliographyEditorShell.MediaTypeOption(
        type.bibName(),
        type.name()
    );
  }
}