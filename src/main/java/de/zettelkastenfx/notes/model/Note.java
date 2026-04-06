package de.zettelkastenfx.notes.model;

import de.zettelkastenfx.notes.editor.NoteEditorPane;
import de.zettelkastenfx.notes.keywords.KeywordsTablePane;
import de.zettelkastenfx.persistence.InlineCssRtfxBlobCodec;
import lombok.Getter;
import lombok.Setter;
import org.fxmisc.richtext.InlineCssTextArea;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Note {
  @Setter @Getter private int id;
  @Getter private String title = "";
  @Getter byte[] bodyBlob = new byte[0];
  @Getter String bodyCodec = InlineCssRtfxBlobCodec.CODEC_NAME;
  @Setter @Getter private Integer bibliographyRefId;

  @Getter private List<String> keywords = new ArrayList<>();
  @Setter @Getter private List<NoteLink> successors = new ArrayList<>();
  @Setter @Getter private List<String> projects = new ArrayList<>();
  @Setter @Getter private Instant createdAt;
  @Setter @Getter private Instant updatedAt;

  public void setTitle(String title) { this.title = title == null ? "" : title; }
  public void setBodyBlob(byte[] bodyBlob) { this.bodyBlob = bodyBlob == null ? new byte[0] : bodyBlob; }
  public void setBodyCodec(String bodyCodec) {
    this.bodyCodec = (bodyCodec == null || bodyCodec.isBlank())
                         ? InlineCssRtfxBlobCodec.CODEC_NAME
                         : bodyCodec;
  }

  public void setKeywords(List<String> keywords) {
    this.keywords.clear();
    if (keywords != null) {
      this.keywords.addAll(keywords);
    }
  }

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