package de.zettelkastenfx.base.util;

public class DateUtil {

  public static String formatHeaderDate(java.time.LocalDateTime dt) {
    if (dt == null) return "—";

    String day = String.format("%02d", dt.getDayOfMonth());

    // Java liefert in de_DE oft "Mär." usw. Wir wollen dreistellig ohne Punkt.
    java.time.format.DateTimeFormatter mf =
        java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.GERMAN);

    String month = dt.format(mf).replace(".", "").trim();
    if (month.length() > 3) month = month.substring(0, 3);

    String year = String.format("%02d", dt.getYear() % 100);

    return day + " " + month + " " + year;
  }
}
