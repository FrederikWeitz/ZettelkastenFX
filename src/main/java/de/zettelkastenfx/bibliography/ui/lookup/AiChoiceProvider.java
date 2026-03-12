package de.zettelkastenfx.bibliography.ui.lookup;

import java.util.List;

/**
 * Liefert Vorschläge und Verlaufseinträge für Anbieter und Modelle von KI-Quellen.
 */
public interface AiChoiceProvider {

  /**
   * Leerer Fallback-Provider.
   */
  AiChoiceProvider NONE = new AiChoiceProvider() {
    @Override
    public List<String> findProviders(String prefix, String modelFilter, int limit) {
      return List.of();
    }

    @Override
    public List<String> findModels(String prefix, String providerFilter, int limit) {
      return List.of();
    }

    @Override
    public List<String> loadRecentProviders(int limit) {
      return List.of();
    }

    @Override
    public List<String> loadRecentModels(int limit) {
      return List.of();
    }
  };

  /**
   * Sucht Anbieter.
   *
   * @param prefix Präfix ab 2 Zeichen
   * @param modelFilter optionaler Modellfilter
   * @param limit maximale Anzahl
   * @return Trefferliste
   */
  List<String> findProviders(String prefix, String modelFilter, int limit);

  /**
   * Sucht Modelle.
   *
   * @param prefix Präfix ab 2 Zeichen
   * @param providerFilter optionaler Anbieterfilter
   * @param limit maximale Anzahl
   * @return Trefferliste
   */
  List<String> findModels(String prefix, String providerFilter, int limit);

  /**
   * Lädt die zuletzt verwendeten Anbieter.
   *
   * @param limit maximale Anzahl
   * @return zuletzt verwendete Anbieter
   */
  List<String> loadRecentProviders(int limit);

  /**
   * Lädt die zuletzt verwendeten Modelle.
   *
   * @param limit maximale Anzahl
   * @return zuletzt verwendete Modelle
   */
  List<String> loadRecentModels(int limit);
}
