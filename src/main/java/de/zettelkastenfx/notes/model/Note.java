package de.zettelkastenfx.notes.model;

import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import org.fxmisc.richtext.InlineCssTextArea;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Note {
  private int id;
  private String title = "";
  byte[] bodyBlob = new byte[0];
  String bodyCodec = InlineCssRtfxBlobCodec.CODEC_NAME;
  private Integer bibliographyRefId;

  private List<String> keywords = new ArrayList<>();
  private List<NoteLink> successors = new ArrayList<>();
  private List<String> projects = new ArrayList<>();
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * Liefert die Zettel-ID.
   *
   * @return Zettel-ID
   */
  public int getId() { return id; }

  /**
   * Setzt die Zettel-ID.
   *
   * @param id Zettel-ID
   */
  public void setId(int id) { this.id = id; }

  /**
   * Liefert den Zetteltitel.
   *
   * @return Titel
   */
  public String getTitle() { return title; }

  public void setTitle(String title) { this.title = title == null ? "" : title; }

  /**
   * Liefert den serialisierten Zettelinhalt.
   *
   * @return Inhaltsblob
   */
  public byte[] getBodyBlob() { return bodyBlob; }

  public void setBodyBlob(byte[] bodyBlob) { this.bodyBlob = bodyBlob == null ? new byte[0] : bodyBlob; }

  /**
   * Liefert den Codec des Zettelinhalts.
   *
   * @return Codec-Name
   */
  public String getBodyCodec() { return bodyCodec; }

  public void setBodyCodec(String bodyCodec) {
    this.bodyCodec = (bodyCodec == null || bodyCodec.isBlank())
                         ? InlineCssRtfxBlobCodec.CODEC_NAME
                         : bodyCodec;
  }

  /**
   * Liefert die verknüpfte Bibliographie-ID.
   *
   * @return Bibliographie-ID oder {@code null}
   */
  public Integer getBibliographyRefId() { return bibliographyRefId; }

  /**
   * Setzt die verknüpfte Bibliographie-ID.
   *
   * @param bibliographyRefId Bibliographie-ID oder {@code null}
   */
  public void setBibliographyRefId(Integer bibliographyRefId) { this.bibliographyRefId = bibliographyRefId; }

  /**
   * Liefert die Schlagwörter.
   *
   * @return veränderbare Schlagwortliste
   */
  public List<String> getKeywords() { return keywords; }

  public void setKeywords(List<String> keywords) {
    this.keywords.clear();
    if (keywords != null) {
      this.keywords.addAll(keywords);
    }
  }

  /**
   * Liefert die ausgehenden Folgezettel.
   *
   * @return Folgezettel-Liste
   */
  public List<NoteLink> getSuccessors() { return successors; }

  /**
   * Setzt die ausgehenden Folgezettel.
   *
   * @param successors Folgezettel-Liste
   */
  public void setSuccessors(List<NoteLink> successors) {
    this.successors = successors == null ? new ArrayList<>() : successors;
  }

  /**
   * Liefert die Projektzuordnungen.
   *
   * @return Projektliste
   */
  public List<String> getProjects() { return projects; }

  /**
   * Setzt die Projektzuordnungen.
   *
   * @param projects Projektliste
   */
  public void setProjects(List<String> projects) {
    this.projects = projects == null ? new ArrayList<>() : projects;
  }

  /**
   * Liefert den Erstellungszeitpunkt.
   *
   * @return Erstellungszeitpunkt
   */
  public Instant getCreatedAt() { return createdAt; }

  /**
   * Setzt den Erstellungszeitpunkt.
   *
   * @param createdAt Erstellungszeitpunkt
   */
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  /**
   * Liefert den Änderungszeitpunkt.
   *
   * @return Änderungszeitpunkt
   */
  public Instant getUpdatedAt() { return updatedAt; }

  /**
   * Setzt den Änderungszeitpunkt.
   *
   * @param updatedAt Änderungszeitpunkt
   */
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public void captureFrom(NoteEditorPane editor,
                          KeywordsTablePane keywordsPane) {
    setTitle(editor.getTitleEditorArea().getText());
    captureBodyFrom(editor.getBodyArea());

    getKeywords().clear();
    getKeywords().addAll(keywordsPane.getKeywords());
  }

  public void applyTo(NoteEditorPane editor,
                      KeywordsTablePane keywordsPane) {
    editor.getTitleEditorArea().setText(getTitle());
    applyBodyTo(editor.getBodyArea());
    keywordsPane.setKeywords(getKeywords());
    editor.setHeaderNoteNumber(id);
    editor.setHeaderCreatedAt(createdAt);
  }

  // Body aus UI “einsammeln”
  public void captureBodyFrom(InlineCssTextArea area) {
    setBodyCodec(InlineCssRtfxBlobCodec.CODEC_NAME);
    setBodyBlob(InlineCssRtfxBlobCodec.encodeToGzip(area));
  }

  // Body in UI “ausspielen”
  public void applyBodyTo(InlineCssTextArea area) {
    if (bodyBlob == null || bodyBlob.length == 0) {
      area.clear();
      return;
    }
    InlineCssRtfxBlobCodec.decodeFromGzipInto(area, bodyBlob);
  }
}
