package de.zettelkastenfx.export;

import de.zettelkastenfx.bibliography.api.BibliographyService;
import de.zettelkastenfx.export.model.ExportBibliographyEntry;
import de.zettelkastenfx.export.model.ExportDocument;
import de.zettelkastenfx.export.model.ExportNote;
import de.zettelkastenfx.notes.model.Note;
import de.zettelkastenfx.persistence.NoteRepository;

import java.nio.file.Path;
import java.util.*;

/**
 * Standardimplementierung des modularen Zettelexports.
 */
public class DefaultNoteExportService implements NoteExportService {

  private final NoteRepository noteRepository;
  private final NoteBodyTextExtractor bodyTextExtractor;
  private final BibliographyExportFormatter bibliographyFormatter;
  private final Map<ExportDocumentType, NoteExportWriter> writers;

  /**
   * Erzeugt den Exportservice mit den bestehenden Repositories und Services.
   *
   * @param noteRepository Zettel-Repository
   * @param bibliographyService Bibliographie-Fassade
   */
  public DefaultNoteExportService(NoteRepository noteRepository,
                                  BibliographyService bibliographyService) {
    this(
        noteRepository,
        new NoteBodyTextExtractor(),
        new BibliographyExportFormatter(bibliographyService),
        Map.of(
            ExportDocumentType.DOCX, new DocxExportWriter(),
            ExportDocumentType.RTF, new RtfExportWriter(),
            ExportDocumentType.PDF, new PdfExportWriter()
        )
    );
  }

  /**
   * Erzeugt den Exportservice mit expliziten Abhaengigkeiten fuer Tests.
   *
   * @param noteRepository Zettel-Repository
   * @param bodyTextExtractor Textdekodierer
   * @param bibliographyFormatter Bibliographie-Formatter
   * @param writers Zielformat-Writer
   */
  public DefaultNoteExportService(NoteRepository noteRepository,
                                  NoteBodyTextExtractor bodyTextExtractor,
                                  BibliographyExportFormatter bibliographyFormatter,
                                  Map<ExportDocumentType, NoteExportWriter> writers) {
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
    this.bodyTextExtractor = Objects.requireNonNull(bodyTextExtractor, "bodyTextExtractor");
    this.bibliographyFormatter = Objects.requireNonNull(bibliographyFormatter, "bibliographyFormatter");
    this.writers = Map.copyOf(Objects.requireNonNull(writers, "writers"));
  }

  /**
   * Fuehrt den Export aus, inklusive optionaler Aufteilung.
   *
   * @param request Exportanfrage
   * @return erzeugte Dateien
   */
  @Override
  public ExportResult export(ExportRequest request) {
    Objects.requireNonNull(request, "request");
    List<Note> sourceNotes = noteRepository.loadForExport(request.noteIds());
    List<List<Note>> chunks = split(sourceNotes, request.splitAfterNotes());
    NoteExportWriter writer = resolveWriter(request.documentType());

    List<Path> writtenFiles = new ArrayList<>();
    for (int i = 0; i < chunks.size(); i++) {
      ExportDocument document = buildDocument(chunks.get(i), request.selection());
      Path target = resolveTargetPath(request.outputPath(), request.documentType(), chunks.size(), i + 1);
      writer.write(document, target);
      writtenFiles.add(target);
    }
    return new ExportResult(writtenFiles);
  }

  /**
   * Baut das neutrale Exportdokument fuer einen Zettelblock.
   *
   * @param notes Zettelblock
   * @param selection Exportauswahl
   * @return neutrales Exportdokument
   */
  private ExportDocument buildDocument(List<Note> notes, ExportSelection selection) {
    List<ExportNote> exportNotes = new ArrayList<>();
    LinkedHashMap<Integer, ExportBibliographyEntry> endBibliography = new LinkedHashMap<>();

    for (Note note : notes) {
      ExportBibliographyEntry bibliographyEntry =
          bibliographyFormatter.format(note.getBibliographyRefId(), selection.bibliographyLevel())
              .orElse(null);

      if (selection.bibliographyPlacement() == BibliographyPlacement.END_OF_DOCUMENT
          && bibliographyEntry != null) {
        endBibliography.putIfAbsent(bibliographyEntry.entryId(), bibliographyEntry);
      }

      exportNotes.add(new ExportNote(
          note.getId(),
          note.getTitle(),
          bodyTextExtractor.extractPlainText(note),
          note.getKeywords(),
          bibliographyEntry
      ));
    }

    return new ExportDocument(exportNotes, new ArrayList<>(endBibliography.values()), selection);
  }

  /**
   * Teilt eine Zettelliste gemaess Exportanfrage in Dokumentbloecke auf.
   *
   * @param notes Zettel
   * @param splitAfterNotes maximale Zettelanzahl je Dokument
   * @return Dokumentbloecke
   */
  private List<List<Note>> split(List<Note> notes, int splitAfterNotes) {
    if (notes == null || notes.isEmpty()) {
      return List.of(List.of());
    }
    if (splitAfterNotes <= 0 || notes.size() <= splitAfterNotes) {
      return List.of(notes);
    }

    List<List<Note>> chunks = new ArrayList<>();
    for (int from = 0; from < notes.size(); from += splitAfterNotes) {
      int to = Math.min(notes.size(), from + splitAfterNotes);
      chunks.add(List.copyOf(notes.subList(from, to)));
    }
    return chunks;
  }

  /**
   * Loest den Writer fuer den angeforderten Dokumenttyp auf.
   *
   * @param type Dokumenttyp
   * @return passender Writer
   */
  private NoteExportWriter resolveWriter(ExportDocumentType type) {
    NoteExportWriter writer = writers.get(type);
    if (writer == null) {
      throw new IllegalArgumentException("Kein Writer fuer Exporttyp: " + type);
    }
    return writer;
  }

  /**
   * Bestimmt den Zielpfad eines Exportblocks.
   *
   * @param outputPath angeforderter Zielpfad
   * @param type Dokumenttyp
   * @param chunkCount Anzahl der Exportbloecke
   * @param chunkIndex laufende Blocknummer ab 1
   * @return konkreter Zielpfad
   */
  private Path resolveTargetPath(Path outputPath,
                                 ExportDocumentType type,
                                 int chunkCount,
                                 int chunkIndex) {
    Path normalized = ensureExtension(outputPath, type.extension());
    if (chunkCount <= 1) {
      return normalized;
    }

    String fileName = normalized.getFileName().toString();
    String extension = type.extension();
    String base = fileName.substring(0, fileName.length() - extension.length());
    String chunkName = base + "-" + String.format(Locale.ROOT, "%03d", chunkIndex) + extension;
    Path parent = normalized.getParent();
    return parent == null ? Path.of(chunkName) : parent.resolve(chunkName);
  }

  /**
   * Stellt sicher, dass der Zielpfad die passende Dateiendung besitzt.
   *
   * @param outputPath angeforderter Zielpfad
   * @param extension benoetigte Dateiendung
   * @return Zielpfad mit Dateiendung
   */
  private Path ensureExtension(Path outputPath, String extension) {
    String fileName = outputPath.getFileName().toString();
    if (fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
      return outputPath;
    }
    Path parent = outputPath.getParent();
    Path renamed = Path.of(fileName + extension);
    return parent == null ? renamed : parent.resolve(renamed);
  }
}
