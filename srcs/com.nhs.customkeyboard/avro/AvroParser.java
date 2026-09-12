package com.nhs.customkeyboard.avro;

import java.util.HashMap;
import java.util.Map;

/**
 * High-performance Avro Phonetic parser for Bengali transliteration.
 * Converts English phonetic input (e.g. "hi", "hu", "ami", "bangla", "kkh", "gg")
 * into proper Bengali Unicode text.
 */
public final class AvroParser
{
  private static final Map<String, String> TWO_CHAR_CONSONANTS = new HashMap<>();
  private static final Map<Character, String> ONE_CHAR_CONSONANTS = new HashMap<>();
  private static final Map<String, VowelPair> VOWELS = new HashMap<>();
  private static final Map<String, String> COMMON_AUTOCORRECT = new HashMap<>();

  private static class VowelPair
  {
    final String full;
    final String kar;
    VowelPair(String full, String kar)
    {
      this.full = full;
      this.kar = kar;
    }
  }

  static
  {
    // Multi-character consonants (Case-sensitive where specified in Avro chart)
    TWO_CHAR_CONSONANTS.put("kh", "\u0996"); // খ
    TWO_CHAR_CONSONANTS.put("gh", "\u0998"); // ঘ
    TWO_CHAR_CONSONANTS.put("ch", "\u099B"); // ছ
    TWO_CHAR_CONSONANTS.put("jh", "\u099D"); // ঝ
    TWO_CHAR_CONSONANTS.put("Th", "\u09A0"); // ঠ
    TWO_CHAR_CONSONANTS.put("Dh", "\u09A2"); // ঢ
    TWO_CHAR_CONSONANTS.put("th", "\u09A5"); // থ
    TWO_CHAR_CONSONANTS.put("dh", "\u09A7"); // ধ
    TWO_CHAR_CONSONANTS.put("ph", "\u09AB"); // ফ
    TWO_CHAR_CONSONANTS.put("bh", "\u09AD"); // ভ
    TWO_CHAR_CONSONANTS.put("sh", "\u09B6"); // শ
    TWO_CHAR_CONSONANTS.put("Sh", "\u09B7"); // ষ
    TWO_CHAR_CONSONANTS.put("Rh", "\u09DD"); // ঢ়

    // Single-character consonants
    ONE_CHAR_CONSONANTS.put('k', "\u0995"); // ক
    ONE_CHAR_CONSONANTS.put('K', "\u0995");
    ONE_CHAR_CONSONANTS.put('g', "\u0997"); // গ
    ONE_CHAR_CONSONANTS.put('G', "\u0997");
    ONE_CHAR_CONSONANTS.put('c', "\u099A"); // চ
    ONE_CHAR_CONSONANTS.put('C', "\u099A");
    ONE_CHAR_CONSONANTS.put('j', "\u099C"); // জ
    ONE_CHAR_CONSONANTS.put('J', "\u099C");
    ONE_CHAR_CONSONANTS.put('T', "\u099F"); // ট
    ONE_CHAR_CONSONANTS.put('t', "\u09A4"); // ত
    ONE_CHAR_CONSONANTS.put('D', "\u09A1"); // ড
    ONE_CHAR_CONSONANTS.put('d', "\u09A6"); // দ
    ONE_CHAR_CONSONANTS.put('N', "\u09A3"); // ণ
    ONE_CHAR_CONSONANTS.put('n', "\u09A8"); // ন
    ONE_CHAR_CONSONANTS.put('p', "\u09AA"); // প
    ONE_CHAR_CONSONANTS.put('P', "\u09AA");
    ONE_CHAR_CONSONANTS.put('f', "\u09AB"); // ফ
    ONE_CHAR_CONSONANTS.put('F', "\u09AB");
    ONE_CHAR_CONSONANTS.put('b', "\u09AC"); // ব
    ONE_CHAR_CONSONANTS.put('B', "\u09AC");
    ONE_CHAR_CONSONANTS.put('v', "\u09AD"); // ভ
    ONE_CHAR_CONSONANTS.put('V', "\u09AD");
    ONE_CHAR_CONSONANTS.put('m', "\u09AE"); // ম
    ONE_CHAR_CONSONANTS.put('M', "\u09AE");
    ONE_CHAR_CONSONANTS.put('z', "\u09AF"); // য
    ONE_CHAR_CONSONANTS.put('r', "\u09B0"); // র
    ONE_CHAR_CONSONANTS.put('l', "\u09B2"); // ল
    ONE_CHAR_CONSONANTS.put('L', "\u09B2");
    ONE_CHAR_CONSONANTS.put('S', "\u09B6"); // শ
    ONE_CHAR_CONSONANTS.put('s', "\u09B8"); // স
    ONE_CHAR_CONSONANTS.put('h', "\u09B9"); // হ
    ONE_CHAR_CONSONANTS.put('H', "\u09B9");
    ONE_CHAR_CONSONANTS.put('R', "\u09DC"); // ড়
    ONE_CHAR_CONSONANTS.put('y', "\u09DF"); // য়
    ONE_CHAR_CONSONANTS.put('Y', "\u09DF");

    // Vowels (full form vs. kar form)
    VOWELS.put("rri", new VowelPair("\u098B", "\u09C3")); // ঋ, ৃ
    VOWELS.put("OI", new VowelPair("\u0990", "\u09C8"));  // ঐ, ৈ
    VOWELS.put("oi", new VowelPair("\u0990", "\u09C8"));
    VOWELS.put("OU", new VowelPair("\u0994", "\u09CC"));  // ঔ, ৌ
    VOWELS.put("ou", new VowelPair("\u0994", "\u09CC"));

    VOWELS.put("a", new VowelPair("\u0986", "\u09BE"));   // আ, া
    VOWELS.put("A", new VowelPair("\u0986", "\u09BE"));
    VOWELS.put("i", new VowelPair("\u0987", "\u09BF"));   // ই, ি
    VOWELS.put("I", new VowelPair("\u0988", "\u09C0"));   // ঈ, ী
    VOWELS.put("u", new VowelPair("\u0989", "\u09C1"));   // উ, ু
    VOWELS.put("U", new VowelPair("\u098A", "\u09C2"));   // ঊ, ূ
    VOWELS.put("e", new VowelPair("\u098F", "\u09C7"));   // এ, ে
    VOWELS.put("E", new VowelPair("\u098F", "\u09C7"));
    VOWELS.put("O", new VowelPair("\u0993", "\u09CB"));   // ও, ো
    VOWELS.put("o", new VowelPair("\u0985", ""));         // অ, inherent (no kar after consonant)

    // Frequently misspelled phonetic words
    COMMON_AUTOCORRECT.put("dhonnobad", "dhonyobad");
    COMMON_AUTOCORRECT.put("abong", "ebong");
    COMMON_AUTOCORRECT.put("abiskar", "abiShkar");
    COMMON_AUTOCORRECT.put("academic", "ekaDemik");
  }

  private AvroParser() {}

  /**
   * Translates an English phonetic token into Bengali Unicode text.
   */
  public static String parse(String text)
  {
    if (text == null || text.isEmpty())
      return "";

    // Check autocorrect dictionary replacement for exact match
    String lower = text.toLowerCase();
    if (COMMON_AUTOCORRECT.containsKey(lower))
    {
      text = COMMON_AUTOCORRECT.get(lower);
    }

    StringBuilder out = new StringBuilder();
    int len = text.length();
    int i = 0;
    boolean lastWasConsonant = false;

    while (i < len)
    {
      // 1. Triple-char conjuncts & vowels
      if (i + 3 <= len)
      {
        String sub3 = text.substring(i, i + 3);
        if ("kkh".equalsIgnoreCase(sub3))
        {
          if (lastWasConsonant) out.append("\u09CD");
          out.append("\u0995\u09CD\u09B7"); // ক্ষ
          i += 3;
          lastWasConsonant = true;
          continue;
        }
        if ("rri".equalsIgnoreCase(sub3))
        {
          out.append(lastWasConsonant ? "\u09C3" : "\u098B"); // ঋ / ৃ
          i += 3;
          lastWasConsonant = false;
          continue;
        }
      }

      // 2. Double-char special signs, conjuncts & diphthongs
      if (i + 2 <= len)
      {
        String sub2 = text.substring(i, i + 2);

        // জ্ঞ (gg)
        if ("gg".equalsIgnoreCase(sub2))
        {
          if (lastWasConsonant) out.append("\u09CD");
          out.append("\u099C\u09CD\u099E"); // জ্ঞ
          i += 2;
          lastWasConsonant = true;
          continue;
        }

        // Anusvara: ng -> ং
        if ("ng".equals(sub2))
        {
          out.append("\u0982"); // ং
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // ঙ (Ng)
        if ("Ng".equals(sub2))
        {
          if (lastWasConsonant) out.append("\u09CD");
          out.append("\u0999"); // ঙ
          i += 2;
          lastWasConsonant = true;
          continue;
        }

        // ঞ (NG)
        if ("NG".equals(sub2))
        {
          if (lastWasConsonant) out.append("\u09CD");
          out.append("\u099E"); // ঞ
          i += 2;
          lastWasConsonant = true;
          continue;
        }

        // ৎ (TH)
        if ("TH".equals(sub2))
        {
          out.append("\u09CE"); // ৎ
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // ঃ (HH)
        if ("HH".equals(sub2))
        {
          out.append("\u0983"); // ঃ
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // ঁ (qq, cb)
        if ("qq".equalsIgnoreCase(sub2) || "cb".equalsIgnoreCase(sub2))
        {
          out.append("\u0981"); // ঁ
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // Explicit hasanta (hs or ,,)
        if ("hs".equalsIgnoreCase(sub2) || ",,".equals(sub2))
        {
          out.append("\u09CD"); // ্
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // Diphthongs: OI / oi / OU / ou
        if (VOWELS.containsKey(sub2))
        {
          VowelPair vp = VOWELS.get(sub2);
          out.append(lastWasConsonant ? vp.kar : vp.full);
          i += 2;
          lastWasConsonant = false;
          continue;
        }

        // Double-character consonants: kh, gh, ch, jh, Th, Dh, th, dh, ph, bh, sh, Sh, Rh
        if (TWO_CHAR_CONSONANTS.containsKey(sub2))
        {
          if (lastWasConsonant) out.append("\u09CD");
          out.append(TWO_CHAR_CONSONANTS.get(sub2));
          i += 2;
          lastWasConsonant = true;
          continue;
        }
      }

      char c = text.charAt(i);

      // 3. Single-char special signs
      if (c == '^')
      {
        out.append("\u0981"); // ঁ (Chandrabindu)
        i++;
        lastWasConsonant = false;
        continue;
      }
      if (c == '`')
      {
        // Root / escape character in Avro - suppresses linking
        i++;
        lastWasConsonant = false;
        continue;
      }

      // 4. Reph rule: 'r' followed by a consonant (e.g., rk, rm, rb) at start of cluster
      if ((c == 'r' || c == 'R') && (i + 1 < len) && !lastWasConsonant)
      {
        char next = text.charAt(i + 1);
        if (isConsonantLetter(next))
        {
          out.append("\u09B0\u09CD"); // র + ্ = Reph
          i++;
          lastWasConsonant = false;
          continue;
        }
      }

      // 5. Post-consonant Ya-fola ('y' or 'Z'), Ba-fola ('w'), and Ra-fola ('r')
      if (lastWasConsonant && (c == 'y' || c == 'Y' || c == 'Z'))
      {
        out.append("\u09CD\u09AF"); // ্য (Ya-fola)
        i++;
        lastWasConsonant = true;
        continue;
      }
      if (lastWasConsonant && (c == 'w' || c == 'W'))
      {
        out.append("\u09CD\u09AC"); // ্ব (Ba-fola)
        i++;
        lastWasConsonant = true;
        continue;
      }

      // 6. Single consonants
      if (ONE_CHAR_CONSONANTS.containsKey(c))
      {
        if (lastWasConsonant)
        {
          out.append("\u09CD"); // Insert hasanta for consonant clusters
        }
        out.append(ONE_CHAR_CONSONANTS.get(c));
        i++;
        lastWasConsonant = true;
        continue;
      }

      // 7. Single vowels
      String charStr = String.valueOf(c);
      if (VOWELS.containsKey(charStr))
      {
        VowelPair vp = VOWELS.get(charStr);
        if (lastWasConsonant)
        {
          if (!vp.kar.isEmpty())
          {
            out.append(vp.kar);
          }
        }
        else
        {
          out.append(vp.full);
        }
        i++;
        lastWasConsonant = false;
        continue;
      }

      // 8. Other characters (digits, spaces, punctuation): pass through
      out.append(c);
      i++;
      lastWasConsonant = false;
    }

    return out.toString();
  }

  private static boolean isConsonantLetter(char c)
  {
    char lower = Character.toLowerCase(c);
    return (lower >= 'a' && lower <= 'z') &&
           lower != 'a' && lower != 'e' && lower != 'i' && lower != 'o' && lower != 'u';
  }
}
