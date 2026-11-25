package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NewCommandsTest {
    private static void is(String input, String type, String a) {
        CommandParser.Cmd c = CommandParser.parse(input);
        assertNotNull("not understood: " + input, c);
        assertEquals(input, type, c.type);
        if (a != null) assertEquals(input, a, c.a);
    }

    @Test public void volumeAndRinger() {
        is("आवाज़ बढ़ाओ", "volume", "up");
        is("volume kam karo", "volume", "down");
        is("volume full kar do", "volume", "max");
        is("phone silent karo", "ringer", "silent");
        is("phone vibrate pe karo", "ringer", "vibrate");
        is("ভলিউম বাড়াও", "volume", "up");
        assertNull(CommandParser.parse("आवाज़ नहीं आ रही"));
        assertNull(CommandParser.parse("mitra ki awaaz badlo"));
    }

    @Test public void settingsPages() {
        is("wifi chalu karo", "settings", "wifi");
        is("bluetooth band karo", "settings", "bluetooth");
        is("brightness kam karo", "settings", "display");
        is("gps on karo", "settings", "location");
        is("flight mode on karo", "settings", "airplane");
        is("hotspot chalu karo", "settings", "hotspot");
        is("मोबाइल डेटा चालू करो", "settings", "data");
    }

    @Test public void batteryTimeDate() {
        is("बैटरी कितनी है", "battery", null);
        is("battery kitni hai", "battery", null);
        is("what's the time", "time", null);
        is("কটা বাজে", "time", null);
        is("आज की तारीख", "date", null);
    }

    @Test public void remindersNotesUpi() {
        is("कल सुबह 9 बजे याद दिलाना कि दवा लेनी है", "reminder", null);
        is("remind me at 5 pm to call papa", "reminder", null);
        is("यह नोट कर लो कि कल 5 बजे मीटिंग है", "copy", "कल 5 बजे मीटिंग है");
        is("पैसे भेजने हैं", "upi", null);
        is("UPI kholo", "upi", null);
        is("PhonePe kholo", "open_app", "phonepe");
    }

    @Test public void cameraNavCalc() {
        is("selfie lo", "camera", "front");
        is("कैमरा खोलो", "camera", null);
        is("स्टेशन तक ले चलो", "navigate", "स्टेशन");
        is("navigate to Patna junction", "navigate", "Patna junction");
        is("25 गुणा 4 कितना होता है", "calc", "25 × 4 = 100");
        assertNull(CommandParser.parse("location bhejo"));
    }

    @Test public void otherLanguages() {
        is("মাকে ফোন করো", "call_contact", null);
        is("அம்மாவுக்கு கால் பண்ணு", "call_contact", null);
        is("টর্চ জ্বালাও", "torch", "on");
        is("मला सोनूला फोन लाव", "call_contact", null);
    }
}
