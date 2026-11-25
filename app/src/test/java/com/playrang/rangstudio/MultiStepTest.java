package com.playrang.rangstudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MultiStepTest {
    private static List<CommandParser.Cmd> p(String s) { return CommandParser.parseAll(s); }

    private static void steps(String input, String... types) {
        List<CommandParser.Cmd> cs = p(input);
        assertEquals(input + " → " + cs, types.length, cs.size());
        for (int i = 0; i < types.length; i++) assertEquals(input + " step " + i, types[i], cs.get(i).type);
    }

    @Test public void callThenYoutube() {
        steps("सोनू को कॉल करो और फिर YouTube खोलो", "call_contact", "open_app");
        assertEquals("सोनू", p("सोनू को कॉल करो और फिर YouTube खोलो").get(0).a);
        steps("Sonu ko call karo aur phir youtube kholo", "call_contact", "open_app");
        steps("call mummy then play hanuman chalisa", "call_contact", "youtube");
        steps("टॉर्च जलाओ फिर 10 मिनट का टाइमर लगाओ", "torch", "timer");
    }

    @Test public void andSplitsOnlyBetweenCommands() {
        steps("torch jalao aur volume badhao", "torch", "volume");
        steps("wifi chalu karo aur bluetooth band karo", "settings", "settings");
        // "और" inside a name is not a step
        steps("राम और श्याम को कॉल करो", "call_contact");
        assertEquals("राम और श्याम", p("राम और श्याम को कॉल करो").get(0).a);
        // arithmetic "aur" stays a sum
        steps("2 aur 3", "calc");
    }

    @Test public void messageBodyIsNeverSplit() {
        List<CommandParser.Cmd> cs = p("papa ko whatsapp pe likho ki main aa raha hun aur phir khana khayenge");
        assertEquals(1, cs.size());
        assertEquals("whatsapp_contact", cs.get(0).type);
        assertTrue(cs.get(0).b.contains("aur phir khana"));
    }

    @Test public void redundantOpenIsDropped() {
        steps("YouTube kholo aur arijit singh ke gaane chalao", "youtube");
        steps("whatsapp kholo aur sonu ko message bhejo ki hello", "whatsapp_contact");
    }

    @Test public void threeSteps() {
        steps("टॉर्च जलाओ, फिर आवाज़ बढ़ाओ और फिर बैटरी बताओ", "torch", "volume", "battery");
    }

    @Test public void notCommands() {
        assertTrue(p("mujhe aaj bahut dukh hai aur neend nahi aa rahi").isEmpty());
        assertTrue(p("भारत की राजधानी क्या है").isEmpty());
    }
}
