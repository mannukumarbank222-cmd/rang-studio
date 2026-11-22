package com.playrang.rangstudio;

import java.util.Calendar;

/** Time and date answered on the device, in Hindi. */
final class LocalInfo {
    private static final String[] DAYS = {"रविवार", "सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार"};
    private static final String[] MONTHS = {"जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"};

    private LocalInfo() {}

    static String todayText() {
        Calendar c = Calendar.getInstance();
        return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " "
                + MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    static String dateReply() {
        return "आज " + todayText() + " है।";
    }

    static String timeReply() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        String part;
        if (h < 4) part = "रात";
        else if (h < 12) part = "सुबह";
        else if (h < 16) part = "दोपहर";
        else if (h < 20) part = "शाम";
        else part = "रात";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        String mm = m < 10 ? "0" + m : String.valueOf(m);
        return "अभी " + part + " के " + h12 + ":" + mm + " बजे हैं।";
    }
}
