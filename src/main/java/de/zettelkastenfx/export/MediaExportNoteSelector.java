package de.zettelkastenfx.export;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.bibliography.model.*;
import de.zettelkastenfx.persistence.NoteRepository;

import java.util.*;

/**
 * Ermittelt Exportzettel ueber bibliographische Autoren- und Titelangaben.
 */
public class MediaExportNoteSelector {

  private final NoteRepository noteRepository;
  private final BibliographyService bibliographyService;

  /**
   * Erzeugt den Selektor.
   *
   * @param noteRepository Zettel-Repository
   * @param bibliographyService Bibliographie-Fassade
   */
  public MediaExportNoteSelector(NoteRepository noteRepository, BibliographyService bibliographyService) {
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
    this.bibliographyService = Objects.requireNonNull(bibliographyService, "bibliographyService");
  }

  /**
   * Sucht Zettel ueber Autor- und Titelfilter.
   *
   * @param authorQueries Autorenfilter; mehrere Autoren werden mit UND verknuepft
   * @param titleQuery Titelsuchtext
   * @return passende Zettelnummern
   */
  public List<Integer> selectNoteIds(List<Author> authorQueries, String titleQuery) {
    return selectNoteIds(authorQueries, titleQuery, null);
  }

  /**
   * Sucht Zettel ueber Autor-, Titel- und optional bereits aufgeloeste Medien-ID.
   *
   * @param authorQueries Autorenfilter; mehrere Autoren werden mit UND verknuepft
   * @param titleQuery Titelsuchtext
   * @param selectedEntryId bereits eindeutig ausgewaehlte Bibliographie-ID oder {@code null}
   * @return passende Zettelnummern
   */
  public List<Integer> selectNoteIds(List<Author> authorQueries, String titleQuery, Integer selectedEntryId) {
    List<Author> authors = normalizeAuthors(authorQueries);
    String title = normalize(titleQuery);
    if (selectedEntryId != null && selectedEntryId > 0) {
      Set<Integer> directEntries = expandIndirectEntries(new LinkedHashSet<>(List.of(selectedEntryId)));
      return noteIdsForEntries(directEntries);
    }
    if (authors.isEmpty() && title.isBlank()) {
      return allNoteIds();
    }

    if (title.isBlank()) {
      List<Integer> matchingEntryIds = bibliographyService.searchMediaEntryIdsByAuthors("", authors, 10_000);
      List<Integer> directEntryIds = bibliographyService.resolveDirectEntryIds(matchingEntryIds);
      return noteIdsForEntries(new LinkedHashSet<>(directEntryIds));
    }

    Set<Integer> matchingEntries = findMatchingEntryIds(authors, title);
    Set<Integer> directEntries = expandIndirectEntries(matchingEntries);
    return noteIdsForEntries(directEntries);
  }

  /**
   * Liefert alle Zettelnummern in Exportreihenfolge.
   *
   * @return alle Zettelnummern
   */
  private List<Integer> allNoteIds() {
    return noteRepository.loadAllNoteIdsForExport();
  }

  private Set<Integer> findMatchingEntryIds(List<Author> authors, String title) {
    List<BibliographyReference> references = findCandidateReferences(authors, title);
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    for (BibliographyReference reference : references) {
      if (reference == null || reference.entryId() <= 0) {
        continue;
      }
      DynamicBibliographyEntry entry = bibliographyService.loadEntry(reference.entryId()).orElse(null);
      if (entry != null && matchesAuthors(entry, authors) && matchesTitle(entry, title, reference.displayText())) {
        ids.add(entry.getId());
      }
    }
    return ids;
  }

  /**
   * Ermittelt passende Medienkandidaten ueber Titel oder, wenn kein Titel
   * angegeben ist, ueber den ersten Autor.
   *
   * @param authors normalisierte Autorensuchtexte
   * @param title normalisierter Titelsuchtext
   * @return Kandidatenreferenzen
   */
  private List<BibliographyReference> findCandidateReferences(List<Author> authors, String title) {
    if (!title.isBlank()) {
      return bibliographyService.searchMediaReferences("", title, 10_000);
    }
    if (!authors.isEmpty()) {
      return bibliographyService.searchMediaReferencesByAuthors("", authors, 10_000);
    }
    return List.of();
  }

  private Set<Integer> expandIndirectEntries(Set<Integer> selectedEntryIds) {
    Map<String, Boolean> directLinkByMediaType = new LinkedHashMap<>();
    for (MediaTypeDefinition mediaType : bibliographyService.listMediaTypes()) {
      directLinkByMediaType.put(normalize(mediaType.bibName()), mediaType.directLink());
    }

    LinkedHashSet<Integer> expanded = new LinkedHashSet<>();
    for (Integer entryId : selectedEntryIds) {
      DynamicBibliographyEntry entry = bibliographyService.loadEntry(entryId).orElse(null);
      if (entry == null) {
        continue;
      }
      if (directLinkByMediaType.getOrDefault(normalize(entry.getMediaTypeBibName()), true)) {
        expanded.add(entryId);
      } else {
        expanded.addAll(findContainedDirectEntries(entryId));
      }
    }
    return expanded;
  }

  private List<Integer> findContainedDirectEntries(int containerEntryId) {
    LinkedHashSet<Integer> contained = new LinkedHashSet<>();
    for (BibliographyReference reference : bibliographyService.searchMediaReferences("", "", 10_000)) {
      DynamicBibliographyEntry entry = bibliographyService.loadEntry(reference.entryId()).orElse(null);
      if (entry == null) {
        continue;
      }
      for (BibValue value : entry.getOrderedValues()) {
        if (value instanceof RelatedBibValue relatedValue
            && Objects.equals(relatedValue.relatedEntryId(), containerEntryId)) {
          contained.add(entry.getId());
        }
      }
    }
    return new ArrayList<>(contained);
  }

  private List<Integer> noteIdsForEntries(Set<Integer> entryIds) {
    if (entryIds.isEmpty()) {
      return List.of();
    }
    return noteRepository.loadNoteIdsForBibliographyEntries(entryIds);
  }

  private boolean matchesAuthors(DynamicBibliographyEntry entry, List<Author> authors) {
    if (authors.isEmpty()) {
      return true;
    }
    List<String> entryAuthors = new ArrayList<>();
    for (BibValue value : entry.getOrderedValues()) {
      if (value instanceof PersonBibValue personValue) {
        for (PersonRef person : personValue.value()) {
          entryAuthors.add(normalize(person.displayName()));
          entryAuthors.add(normalize(person.lastName()));
        }
      }
    }
    for (Author author : authors) {
      String lastName = normalize(author.getLastName());
      String firstName = normalize(author.getFirstName());
      boolean found = entryAuthors.stream().anyMatch(value ->
          (lastName.isBlank() || value.contains(lastName))
              && (firstName.isBlank() || value.contains(firstName))
      );
      if (!found) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesTitle(DynamicBibliographyEntry entry, String title, String displayText) {
    if (title.isBlank()) {
      return true;
    }
    String display = normalize(displayText);
    if (display.contains(title)) {
      return true;
    }
    return entry.findValue("title")
               .map(BibValue::value)
               .map(String::valueOf)
               .map(this::normalize)
               .orElse("")
               .contains(title);
  }

  private List<Author> normalizeAuthors(List<Author> values) {
    if (values == null) {
      return List.of();
    }
    List<Author> normalized = new ArrayList<>();
    for (Author value : values) {
      if (value == null) {
        continue;
      }
      String firstName = value.getFirstName() == null ? "" : value.getFirstName().trim();
      String lastName = value.getLastName() == null ? "" : value.getLastName().trim();
      if (!firstName.isBlank() || !lastName.isBlank()) {
        Author author = new Author();
        author.setFirstName(firstName);
        author.setLastName(lastName);
        normalized.add(author);
      }
    }
    return normalized;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
