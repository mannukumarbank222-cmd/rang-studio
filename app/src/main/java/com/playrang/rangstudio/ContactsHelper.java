package com.playrang.rangstudio;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the phone's own contacts (on the device only) to find the number of the person the user named.
 * Nothing read here is stored, uploaded, or sent to the AI.
 */
final class ContactsHelper {
    private ContactsHelper() {}

    static final class Contact {
        final long id;
        final String name;
        String number;
        int rank;
        int type;
        double score;
        Contact(long id, String name, String number, int rank, int type) {
            this.id = id; this.name = name; this.number = number; this.rank = rank; this.type = type;
        }
        String typeLabel() {
            switch (type) {
                case Phone.TYPE_MOBILE: return "मोबाइल";
                case Phone.TYPE_HOME: return "घर";
                case Phone.TYPE_WORK: case Phone.TYPE_WORK_MOBILE: return "ऑफ़िस";
                default: return "";
            }
        }
    }

    /** All contacts that have a phone number; one entry per contact with its best number (primary, then mobile). */
    static List<Contact> load(Context ctx) {
        Map<Long, Contact> byId = new HashMap<>();
        ContentResolver cr = ctx.getContentResolver();
        String[] proj = {Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY, Phone.IS_PRIMARY};
        Cursor c = null;
        try {
            c = cr.query(Phone.CONTENT_URI, proj, null, null, null);
            if (c == null) return new ArrayList<>();
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                String num = c.getString(2);
                if (name == null || num == null || num.trim().isEmpty()) continue;
                int type = c.isNull(3) ? 0 : c.getInt(3);
                int rank = (c.getInt(4) != 0 ? 8 : 0) + (c.getInt(5) != 0 ? 4 : 0) + (type == Phone.TYPE_MOBILE ? 2 : 0)
                        + (type == Phone.TYPE_WORK_MOBILE ? 1 : 0);
                Contact old = byId.get(id);
                if (old == null) byId.put(id, new Contact(id, name, num, rank, type));
                else if (rank > old.rank) { old.number = num; old.rank = rank; old.type = type; }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return new ArrayList<>(byId.values());
    }

    static String last10(String n) {
        String d = CommandParser.digits(n);
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    /** Contacts whose name matches the query, best first (duplicates of the same name+number removed). */
    static List<Contact> find(Context ctx, String query) {
        List<Contact> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Contact k : load(ctx)) {
            double s = NameMatcher.bestScore(query, k.name);
            if (s < NameMatcher.TOKEN_OK) continue;
            k.score = s;
            out.add(k);
        }
        Collections.sort(out, (a, b) -> Double.compare(b.score, a.score));
        List<Contact> dedup = new ArrayList<>();
        for (Contact k : out) {
            String key = NameMatcher.clean(k.name) + "|" + last10(k.number);
            if (seen.add(key)) dedup.add(k);
        }
        return dedup;
    }

    /** Number in international form for wa.me (India +91 assumed for 10-digit numbers). */
    static String intl(String number) {
        String d = CommandParser.digits(number == null ? "" : number);
        if (d.startsWith("00")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = "91" + d.substring(1);
        if (d.length() == 10) d = "91" + d;
        return d;
    }
}
