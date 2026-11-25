package com.playrang.rangstudio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/** The phone tasks Gemini Live may call, and how each maps onto an existing ActionHandler action. */
final class LiveTools {
    private LiveTools() {}

    /** Result of mapping a function call: the ActionHandler type and its two arguments. */
    static final class Act {
        final String type, a, b;
        /** true when the task opens another app/screen (so Mitra should finish talking first) */
        final boolean leaves;
        Act(String type, String a, String b, boolean leaves) { this.type = type; this.a = a; this.b = b; this.leaves = leaves; }
    }

    private static String g(Map<String, String> m, String... keys) {
        for (String k : keys) { String v = m.get(k); if (v != null && v.trim().length() > 0) return v.trim(); }
        return "";
    }

    private static int digits(String s) { int n = 0; for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) n++; return n; }

    /** Pure mapping (unit-tested). Returns null for current_time (answered directly) or an unknown name. */
    static Act map(String name, Map<String, String> args) {
        if (name == null) return null;
        switch (name) {
            case "call_contact": {
                String who = g(args, "name", "number", "contact");
                return digits(who) >= 3 ? new Act("call_number", who, "", true) : new Act("call_contact", who, "", true);
            }
            case "whatsapp_message": {
                String who = g(args, "name", "number", "contact"), msg = g(args, "message", "text");
                return digits(who) >= 10 ? new Act("whatsapp", who, msg, true) : new Act("whatsapp_contact", who, msg, true);
            }
            case "whatsapp_call": return new Act("whatsapp_call", g(args, "name", "contact"), "", true);
            case "send_sms": {
                String who = g(args, "name", "number", "contact"), msg = g(args, "message", "text");
                return digits(who) >= 10 ? new Act("sms", who, msg, true) : new Act("sms_contact", who, msg, true);
            }
            case "play_youtube": return new Act("youtube", g(args, "query", "song", "video"), "", true);
            case "open_app": return new Act("open_app", g(args, "app_name", "app", "name"), "", true);
            case "web_search": return new Act("search", g(args, "query", "q"), "", true);
            case "open_website": return new Act("website", g(args, "url"), "", true);
            case "set_alarm": {
                String h = g(args, "hour"), m = g(args, "minute");
                String t = g(args, "time");
                if (!h.isEmpty()) t = h + "." + (m.isEmpty() ? "00" : (m.length() == 1 ? "0" + m : m));
                else { int[] c = LiveModels.parseClock(t); if (c != null) t = c[0] + "." + (c[1] < 10 ? "0" : "") + c[1]; }
                return new Act("alarm", t, "", true);
            }
            case "set_timer": {
                String s = g(args, "seconds");
                if (s.isEmpty()) { String mi = g(args, "minutes"); if (!mi.isEmpty()) try { s = String.valueOf((int) Math.round(Double.parseDouble(mi) * 60)); } catch (NumberFormatException ignored) { } }
                if (s.contains(".")) s = s.substring(0, s.indexOf('.'));
                return new Act("timer", s.isEmpty() ? "60" : s, "", true);
            }
            case "open_map": return new Act("maps", g(args, "place", "query", "destination"), "", true);
            case "torch": {
                String on = g(args, "on", "state").toLowerCase(java.util.Locale.ROOT);
                boolean off = on.equals("false") || on.equals("off") || on.equals("0") || on.equals("बंद");
                return new Act("torch", off ? "off" : "on", "", false);
            }
            case "open_camera": {
                String f = g(args, "front", "selfie").toLowerCase(java.util.Locale.ROOT);
                return new Act("camera", f.equals("true") || f.equals("1") || f.equals("yes") ? "front" : "", "", true);
            }
            case "set_volume": {
                String d = g(args, "direction", "level", "action").toLowerCase(java.util.Locale.ROOT);
                if (!d.matches("up|down|mute|unmute|max")) d = d.contains("down") || d.contains("low") || d.contains("कम") ? "down"
                        : d.contains("mute") || d.contains("off") ? "mute" : d.contains("max") || d.contains("full") ? "max" : "up";
                return new Act("volume", d, "", false);
            }
            case "phone_ringer": {
                String m = g(args, "mode").toLowerCase(java.util.Locale.ROOT);
                return new Act("ringer", m.startsWith("vib") ? "vibrate" : m.startsWith("norm") || m.equals("on") || m.equals("ring") ? "normal" : "silent", "", false);
            }
            case "open_settings": return new Act("settings", g(args, "page", "setting", "name"), g(args, "state", "direction"), true);
            case "set_reminder": {
                String what = g(args, "text", "title", "message"), when = g(args, "when", "time", "datetime");
                return new Act("reminder", what, when, true);
            }
            case "copy_to_clipboard": return new Act("copy", g(args, "text", "note"), "", false);
            case "open_upi_app": return new Act("upi", "", "", true);
            case "navigate": return new Act("navigate", g(args, "place", "destination", "query"), "", true);
            case "share_text": return new Act("share", g(args, "text", "message"), "", true);
            default: return null;
        }
    }

    // ------------------------------------------------------------------ declarations for the Live setup message
    private static JSONObject fn(String name, String desc, boolean blocking, String[][] params, String... required) throws Exception {
        JSONObject f = new JSONObject().put("name", name).put("description", desc);
        if (blocking) f.put("behavior", "BLOCKING");
        if (params.length > 0) {
            JSONObject props = new JSONObject();
            for (String[] p : params) props.put(p[0], new JSONObject().put("type", p[1]).put("description", p[2]));
            JSONObject schema = new JSONObject().put("type", "OBJECT").put("properties", props);
            if (required.length > 0) { JSONArray r = new JSONArray(); for (String q : required) r.put(q); schema.put("required", r); }
            f.put("parameters", schema);
        }
        return f;
    }

    static JSONArray declarations(boolean blocking) throws Exception {
        String[][] none = {};
        JSONArray a = new JSONArray();
        a.put(fn("call_contact", "Phone call to a saved contact by name (as the user said it, e.g. 'मम्मी', 'सोनू कुमार') or to a number. "
                + "The phone searches its contacts itself.", blocking,
                new String[][]{{"name", "STRING", "Contact name in the user's own words, or a phone number"}}, "name"));
        a.put(fn("whatsapp_message", "Open WhatsApp with a message ready for a contact (by name) or number; the user taps Send.", blocking,
                new String[][]{{"name", "STRING", "Contact name or phone number"}, {"message", "STRING", "Message text in the user's words"}}, "name"));
        a.put(fn("whatsapp_call", "Open the WhatsApp chat of a contact so the user can make a WhatsApp voice/video call.", blocking,
                new String[][]{{"name", "STRING", "Contact name"}}, "name"));
        a.put(fn("send_sms", "Open an SMS ready to send to a contact (by name) or number.", blocking,
                new String[][]{{"name", "STRING", "Contact name or number"}, {"message", "STRING", "Message text"}}, "name"));
        a.put(fn("play_youtube", "Play a song or video on YouTube.", blocking,
                new String[][]{{"query", "STRING", "What to play, e.g. 'हनुमान चालीसा', 'arijit singh songs'"}}, "query"));
        a.put(fn("open_app", "Open an app on the phone (whatsapp, youtube, camera, gallery, settings, phonepe, paytm, gpay, chrome, play store, calculator, clock …).", blocking,
                new String[][]{{"app_name", "STRING", "App name"}}, "app_name"));
        a.put(fn("web_search", "Open Google search results on the phone screen for the user to see.", blocking,
                new String[][]{{"query", "STRING", "Search words"}}, "query"));
        a.put(fn("set_alarm", "Set an alarm on the phone's clock app.", blocking,
                new String[][]{{"hour", "INTEGER", "Hour in 24-hour time, 0-23"}, {"minute", "INTEGER", "Minute 0-59"}}, "hour"));
        a.put(fn("set_timer", "Start a countdown timer.", blocking,
                new String[][]{{"seconds", "INTEGER", "Timer length in seconds"}}, "seconds"));
        a.put(fn("open_map", "Show a place or the route to it in Maps.", blocking,
                new String[][]{{"place", "STRING", "Place or address"}}, "place"));
        a.put(fn("torch", "Turn the phone's flashlight on or off.", blocking,
                new String[][]{{"on", "BOOLEAN", "true to switch on, false to switch off"}}, "on"));
        a.put(fn("open_camera", "Open the camera to take a photo (front = selfie camera).", blocking,
                new String[][]{{"front", "BOOLEAN", "true for the selfie (front) camera"}}));
        a.put(fn("share_text", "Share some text through any app (the user picks the app).", blocking,
                new String[][]{{"text", "STRING", "Text to share"}}, "text"));
        a.put(fn("current_time", "Get the phone's current local time and date.", blocking, none));
        a.put(fn("battery_status", "Get the phone's battery percentage and whether it is charging.", blocking, none));
        a.put(fn("set_volume", "Change the phone's media volume.", blocking,
                new String[][]{{"direction", "STRING", "One of: up, down, mute, unmute, max"}}, "direction"));
        a.put(fn("phone_ringer", "Put the phone on silent, vibrate or normal ring.", blocking,
                new String[][]{{"mode", "STRING", "One of: silent, vibrate, normal"}}, "mode"));
        a.put(fn("open_settings", "Open a phone settings screen (apps cannot switch these themselves; the user flips the switch).", blocking,
                new String[][]{{"page", "STRING", "One of: wifi, bluetooth, display (brightness), location, airplane, data, hotspot, battery, sound, apps, storage, language, notifications, security"},
                        {"state", "STRING", "What the user wants: on, off, up, down (optional)"}}, "page"));
        a.put(fn("set_reminder", "Create a reminder (calendar event with an alert) the user confirms with Save.", blocking,
                new String[][]{{"text", "STRING", "What to remind about, in the user's words"},
                        {"when", "STRING", "When, as the user said it, e.g. 'कल सुबह 9 बजे', '10 minute baad', 'tomorrow 5 pm'"}}, "text"));
        a.put(fn("copy_to_clipboard", "Note something down by copying the text to the clipboard (user can paste it anywhere).", blocking,
                new String[][]{{"text", "STRING", "Text to copy"}}, "text"));
        a.put(fn("open_upi_app", "Open the user's UPI payment app (PhonePe / Google Pay / Paytm / BHIM). Never makes a payment itself.", blocking, none));
        a.put(fn("navigate", "Start turn-by-turn navigation to a place in Maps.", blocking,
                new String[][]{{"place", "STRING", "Destination"}}, "place"));
        return a;
    }
}
