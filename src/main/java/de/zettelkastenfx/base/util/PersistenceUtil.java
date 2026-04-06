package de.zettelkastenfx.base.util;

import de.zettelkastenfx.persistence.NoteRepository;
import lombok.Getter;

import javax.sql.DataSource;

public class PersistenceUtil {

  @Getter static NoteRepository noteRepository;
  @Getter static DataSource dataSource;

  public static void setDatasource(DataSource ds) {
    dataSource = ds;
    noteRepository = new NoteRepository(ds);
  }
}
