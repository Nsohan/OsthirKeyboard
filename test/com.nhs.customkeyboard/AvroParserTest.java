package com.nhs.customkeyboard;

import com.nhs.customkeyboard.avro.AvroParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class AvroParserTest
{
  @Test
  public void testBasicVowelAttachment()
  {
    assertEquals("হি", AvroParser.parse("hi"));
    assertEquals("হু", AvroParser.parse("hu"));
    assertEquals("হা", AvroParser.parse("ha"));
    assertEquals("হে", AvroParser.parse("he"));
    assertEquals("হো", AvroParser.parse("hO"));
    assertEquals("হ", AvroParser.parse("ho"));
  }

  @Test
  public void testInitialVowels()
  {
    assertEquals("আমি", AvroParser.parse("ami"));
    assertEquals("তুমি", AvroParser.parse("tumi"));
    assertEquals("ইতি", AvroParser.parse("iti"));
    assertEquals("অনেক", AvroParser.parse("onek"));
    assertEquals("উপরে", AvroParser.parse("upore"));
    assertEquals("এক", AvroParser.parse("ek"));
  }

  @Test
  public void testConsonantClustersAndSpecialSigns()
  {
    assertEquals("ক্ষ", AvroParser.parse("kkh"));
    assertEquals("জ্ঞ", AvroParser.parse("gg"));
    assertEquals("বাংলা", AvroParser.parse("bangla"));
    assertEquals("প্র", AvroParser.parse("pro"));
    assertEquals("কর্ম", AvroParser.parse("kormo"));
    assertEquals("বাক্য", AvroParser.parse("bakyo"));
    assertEquals("রক্ত", AvroParser.parse("rokt"));
    assertEquals("বন্ধু", AvroParser.parse("bondhu"));
    assertEquals("ধন্যবাদ", AvroParser.parse("dhonnobad"));
    assertEquals("ধন্যবাদ", AvroParser.parse("dhonyobad"));
  }
}
