package de.zettelkastenfx.bibliography.ui.lookup;

import de.zettelkastenfx.bibliography.model.Author;
import de.zettelkastenfx.bibliography.ui.TypeChoice;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Bündelt den Zugriff auf zustandsbezogene Lookup-Daten der Editor-Shell.
 *
 * @param authorsProvider liefert aktuelle Autoren eines Typs
 * @param selectedEntryIdSetter setzt die selektierte Eintrags-ID
 * @param sourceEntryIdSetter setzt die Ursprungs-ID des geladenen Eintrags
 * @param autoFillLockedGetter liefert den Autofill-Lock-Zustand
 * @param autoFillLockedSetter setzt den Autofill-Lock-Zustand
 * @param autoFilledGetter liefert den Marker für automatisches Befüllen
 * @param autoFilledSetter setzt den Marker für automatisches Befüllen
 */
public record LookupStateAccess (
    Function<TypeChoice, List<Author>> authorsProvider,
    BiConsumer<TypeChoice, Integer> selectedEntryIdSetter,
    BiConsumer<TypeChoice, Integer> sourceEntryIdSetter,
    Function<TypeChoice, Boolean> autoFillLockedGetter,
    BiConsumer<TypeChoice, Boolean> autoFillLockedSetter,
    Function<TypeChoice, Boolean> autoFilledGetter,
    BiConsumer<TypeChoice, Boolean> autoFilledSetter
){

  /**
   * Liefert die aktuellen Autoren eines Bibliographietyps.
   *
   * @param type Bibliographietyp
   * @return aktuelle Autorenliste
   */
  public List<Author> getAuthors(TypeChoice type) {
    return authorsProvider.apply(type);
  }

  /**
   * Setzt die aktuell selektierte Eintrags-ID.
   *
   * @param type Bibliographietyp
   * @param entryId Eintrags-ID oder {@code null}
   */
  public void setSelectedEntryId(TypeChoice type, Integer entryId) {
    selectedEntryIdSetter.accept(type, entryId);
  }

  /**
   * Setzt die Ursprungs-ID eines geladenen Eintrags.
   *
   * @param type Bibliographietyp
   * @param entryId Eintrags-ID oder {@code null}
   */
  public void setSourceEntryId(TypeChoice type, Integer entryId) {
    sourceEntryIdSetter.accept(type, entryId);
  }

  /**
   * Prüft, ob der Autofill-Lock aktiv ist.
   *
   * @param type Bibliographietyp
   * @return {@code true}, wenn gesperrt
   */
  public boolean isAutoFillLocked(TypeChoice type) {
    return Boolean.TRUE.equals(autoFillLockedGetter.apply(type));
  }

  /**
   * Setzt den Autofill-Lock-Zustand.
   *
   * @param type Bibliographietyp
   * @param value neuer Zustand
   */
  public void setAutoFillLocked(TypeChoice type, boolean value) {
    autoFillLockedSetter.accept(type, value);
  }

  /**
   * Prüft, ob ein Feld automatisch befüllt wurde.
   *
   * @param type Bibliographietyp
   * @return {@code true}, wenn automatisch befüllt
   */
  public boolean wasAutoFilled(TypeChoice type) {
    return Boolean.TRUE.equals(autoFilledGetter.apply(type));
  }

  /**
   * Setzt den Marker für automatisches Befüllen.
   *
   * @param type Bibliographietyp
   * @param value neuer Zustand
   */
  public void setAutoFilled(TypeChoice type, boolean value) {
    autoFilledSetter.accept(type, value);
  }
}