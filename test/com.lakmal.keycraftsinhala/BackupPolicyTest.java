package com.lakmal.keycraftsinhala;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class BackupPolicyTest
{
  private static Document parse(String path) throws Exception
  {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(new File(path));
  }

  @Test
  public void backupIncludesOnlyUserConfigurationAndExcludesLearnedAndTransientStores()
      throws Exception
  {
    Document manifest = parse("AndroidManifest.xml");
    Element application = (Element) manifest.getElementsByTagName("application").item(0);
    assertEquals("true", application.getAttribute("android:allowBackup"));
    assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"));
    assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"));
    Element service = (Element) manifest.getElementsByTagName("service").item(0);
    assertEquals("android.permission.BIND_INPUT_METHOD", service.getAttribute("android:permission"));
    assertEquals("true", service.getAttribute("android:exported"));
    NodeList actions = service.getElementsByTagName("action");
    assertEquals(1, actions.getLength());
    assertEquals("android.view.InputMethod", ((Element) actions.item(0)).getAttribute("android:name"));

    Document legacy = parse("res/xml/backup_rules.xml");
    NodeList legacyIncludes = legacy.getDocumentElement().getElementsByTagName("include");
    assertEquals(2, legacyIncludes.getLength());
    boolean credentialPreferencesIncluded = false;
    boolean devicePreferencesIncluded = false;
    for (int i = 0; i < legacyIncludes.getLength(); i++)
    {
      Element include = (Element) legacyIncludes.item(i);
      assertEquals("com.lakmal.keycraftsinhala_preferences.xml",
          include.getAttribute("path"));
      credentialPreferencesIncluded |= "sharedpref".equals(include.getAttribute("domain"));
      devicePreferencesIncluded |= "device_sharedpref".equals(include.getAttribute("domain"));
    }
    assertTrue(credentialPreferencesIncluded);
    assertTrue(devicePreferencesIncluded);

    Document modern = parse("res/xml/data_extraction_rules.xml");
    NodeList modernIncludes = modern.getElementsByTagName("include");
    assertEquals(2, modernIncludes.getLength());
    for (int i = 0; i < modernIncludes.getLength(); i++)
    {
      Element include = (Element) modernIncludes.item(i);
      assertEquals("device_sharedpref", include.getAttribute("domain"));
      assertEquals("com.lakmal.keycraftsinhala_preferences.xml",
          include.getAttribute("path"));
    }
    assertEquals("No other app-data domains may be backed up",
        0, modern.getElementsByTagName("exclude").getLength());
  }
}
