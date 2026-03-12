package de.zettelkastenfx.base.util;

import de.zettelkastenfx.bibliography.persistence.BibliographyRepository;
import de.zettelkastenfx.persistence.NoteRepository;
import lombok.Getter;

import javax.sql.DataSource;

public class PersistenceUtil {

  @Getter static BibliographyRepository bibliographyRepository;
  @Getter static NoteRepository noteRepository;

  public static void setDatasource(DataSource ds) {
    noteRepository = new NoteRepository(ds);
    bibliographyRepository = new BibliographyRepository(ds);
  }
}
