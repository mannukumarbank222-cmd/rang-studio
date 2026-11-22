package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandParserTest {

    private static CommandParser.Cmd p(String s) {
        CommandParser.Cmd c = CommandParser.parse(s);
        assertNotNull("not understood: " + s, c);
        return c;
    }

    private static void is(String input, String type, String a) {
        CommandParser.Cmd c = p(input);
        assertEquals(input, type, c.type);
        if (a != null) assertEquals(input, a.toLowerCase(), c.a.toLowerCase());
    }

    private static void is(String input, String type, String a, String b) {
        is(input, type, a);
        assertEquals(input, b.toLowerCase(), CommandParser.parse(input).b.toLowerCase());
    }

    @Test public void callByName() {
        is("call app mein jakar Sonu Kumar ko call karo", "call_contact", "Sonu Kumar");
        assertEquals("Sonu Kumar", p("call app mein jakar Sonu Kumar ko call karo").a); // keeps the user's spelling
        is("सोनू कुमार को फोन लगाओ", "call_contact", "सोनू कुमार");
        is("Sonu Kumar ko call karo", "call_contact", "Sonu Kumar");
        is("call Sonu", "call_contact", "Sonu");
        is("mummy ko call", "call_contact", "mummy");
        is("ज़रा सोनू को फ़ोन लगाओ", "call_contact", "सोनू");
        is("Sonu ka number milao", "call_contact", "Sonu");
        is("please papa ko phone kar do", "call_contact", "papa");
        is("bhai ko call karo", "call_contact", "bhai");
        is("कॉल ऐप में जाकर राहुल को कॉल करो", "call_contact", "राहुल");
        is("Rahul se baat karao", "call_contact", "Rahul");
        is("मम्मी को कॉल कीजिए", "call_contact", "मम्मी");
    }

    @Test public void callNumberAndFollowUps() {
        is("9876828610 pe call karo", "call_number", "9876828610");
        is("98768 28610 par phone lagao", "call_number", "9876828610");
        is("usko call karo", "call_contact", CommandParser.LAST);
        is("उसे फोन लगाओ", "call_contact", CommandParser.LAST);
        is("dobara call karo", "call_contact", CommandParser.LAST);
        is("call karo", "ask_name", "call");
        is("किसी को कॉल करो", "ask_name", "call");
        is("phone app kholo", "dial_open", "");
        is("call app kholo", "dial_open", "");
        is("Sonu ka number batao", "contact_number", "Sonu");
    }

    @Test public void whatsappAndSms() {
        is("Sonu ko WhatsApp par likho ki main aa raha hoon", "whatsapp_contact", "Sonu", "main aa raha hoon");
        is("mummy ko whatsapp par bolo khana bana do", "whatsapp_contact", "mummy", "khana bana do");
        is("WhatsApp par Rahul ko message bhejo ki kal milte hain", "whatsapp_contact", "Rahul", "kal milte hain");
        is("सोनू को व्हाट्सएप पर लिखो कि मैं आ रहा हूँ", "whatsapp_contact", "सोनू", "मैं आ रहा हूँ");
        is("Sonu ko WhatsApp call karo", "whatsapp_call", "Sonu");
        is("whatsapp kholo", "open_app", "whatsapp");
        is("9876828610 ko whatsapp par hello bhejo", "whatsapp", "9876828610", "hello");
        is("Sonu ko SMS karo ki main late hoon", "sms_contact", "Sonu", "main late hoon");
        is("papa ko message bhejo ki ghar aa gaya", "message_contact", "papa", "ghar aa gaya");
    }

    @Test public void otherActions() {
        is("youtube par bhojpuri gana chalao", "youtube", "bhojpuri gana");
        is("YouTube पर भजन चलाओ", "youtube", "भजन");
        is("सुबह 6 बजे का अलार्म लगाओ", "alarm", null);
        is("टॉर्च जलाओ", "torch", "on");
        is("torch band karo", "torch", "off");
        is("5 minute ka timer lagao", "timer", "300");
        is("camera kholo", "camera", null);
        is("phonepe kholo", "open_app", "phonepe");
        is("फोन पे खोलो", "open_app", "phonepe");
        is("instagram kholo", "open_app", "instagram");
        is("aaj ki tarikh batao", "date", null);
        is("abhi kitne baje hain", "time", null);
        is("patna ka rasta dikhao", "maps", "patna");
        is("google par mausam search karo", "search", "mausam");
    }

    @Test public void notCommands() {
        assertNull(CommandParser.parse("mera phone kho gaya hai"));
        assertNull(CommandParser.parse("phone ki battery jaldi kaise khatam hoti hai"));
        assertNull(CommandParser.parse("whatsapp kaise chalate hain"));
        assertNull(CommandParser.parse("पढ़ाई में मन कैसे लगाएं?"));
        assertNull(CommandParser.parse("मुझे एक चुटकुला सुनाओ"));
    }

    @Test public void ordinalsAndCancel() {
        assertEquals(0, CommandParser.ordinal("पहला"));
        assertEquals(1, CommandParser.ordinal("dusre wale ko"));
        assertEquals(2, CommandParser.ordinal("तीसरा वाला"));
        assertEquals(-1, CommandParser.ordinal("सोनू कुमार"));
        assertTrue(CommandParser.isCancel("रहने दो"));
    }

    @Test public void nameMatching() {
        assertTrue(NameMatcher.score("सोनू कुमार", "Sonu Kumar") > 0.9);
        assertTrue(NameMatcher.score("Sonu Kumar", "सोनू कुमार") > 0.9);
        assertTrue(NameMatcher.score("sonu", "Sonu Kumar") > 0.8);
        assertTrue(NameMatcher.score("mummy", "Maa") > 0.8);
        assertTrue(NameMatcher.score("मम्मी", "Mummy") > 0.8);
        assertTrue(NameMatcher.score("राहुल", "Rahul Sharma") > 0.8);
        assertTrue(NameMatcher.score("sonu kumar", "Sonu Kumar") > NameMatcher.score("sonu kumar", "Sonu Singh"));
        assertTrue(NameMatcher.score("sonukumar", "Sonu Kumar") > 0.8);
        assertTrue(NameMatcher.score("ramesh", "Suresh") < 0.7);
        assertTrue(NameMatcher.score("ram", "Raj") < 0.7);
        assertTrue(NameMatcher.score("vikash", "Vikas Yadav") > 0.8);
        assertTrue(NameMatcher.score("प्रकाश", "Prakash") > 0.9);
    }

    @Test public void otherIndianLanguages() {
        is("सोनू के फोन लगावऽ", "call_contact", "सोनू");              // Bhojpuri
        is("मम्मी के फोन लगा द", "call_contact", "मम्मी");             // Bhojpuri
        is("ਸੋਨੂੰ ਨੂੰ ਕਾਲ ਕਰੋ", "call_contact", "ਸੋਨੂੰ");               // Punjabi
        is("Sonu nu call karo", "call_contact", "Sonu");              // Punjabi (Roman)
        is("સોનુ ને ફોન કરો", "call_contact", "સોનુ");                 // Gujarati
        is("सोनूला कॉल कर", "call_contact", "सोनूला");                 // Marathi (suffix removed while matching)
        is("সোনুকে ফোন করো", "call_contact", "সোনুকে");               // Bengali
        is("ਯੂਟਿਊਬ ਤੇ ਗੁਰਬਾਣੀ ਚਲਾਓ", "youtube", "ਗੁਰਬਾਣੀ");
        is("ইউটিউবে গান চালাও", "youtube", null);
        is("વોટ્સએપ ખોલો", "open_app", "whatsapp");
        is("व्हॉट्सअॅप उघडा", "open_app", "whatsapp");
        assertTrue(NameMatcher.bestScore("सोनूला", "Sonu") > 0.9);
        assertTrue(NameMatcher.bestScore("সোনুকে", "Sonu") > 0.9);
        assertTrue(NameMatcher.bestScore("ਸੋਨੂੰ", "Sonu") > 0.8);
        assertTrue(NameMatcher.bestScore("સોનુ", "Sonu") > 0.9);
        assertTrue(NameMatcher.score("mama", "Mummy") < 0.7);
    }

    @Test public void languages() {
        assertEquals("pa-IN", Lang.ttsForText("ਸਤ ਸ੍ਰੀ ਅਕਾਲ", Lang.get("auto")));
        assertEquals("ta-IN", Lang.ttsForText("வணக்கம்", Lang.get("auto")));
        assertEquals("hi-IN", Lang.ttsForText("नमस्ते", Lang.get("bho")));
        assertEquals("mr-IN", Lang.ttsForText("नमस्कार", Lang.get("mr")));
        assertEquals("hi-IN", Lang.get("bho").sr);
        assertEquals("auto", Lang.get("xx").id);
    }
}
