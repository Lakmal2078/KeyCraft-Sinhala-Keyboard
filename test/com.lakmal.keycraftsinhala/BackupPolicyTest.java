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
  public void backupExcludesAllKeyboardUserData()
      throws Exception
  {
    Document manifest = parse("AndroidManifest.xml");
    Element application = (Element) manifest.getElementsByTagName("application").item(0);
    assertEquals("false", application.getAttribute("android:allowBackup"));
    assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"));
    assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"));
    Element service = (Element) manifest.getElementsByTagName("service").item(0);
    assertEquals("android.permission.BIND_INPUT_METHOD", service.getAttribute("android:permission"));
    assertEquals("true", service.getAttribute("android:exported"));
    NodeList actions = service.getElementsByTagName("action");
    assertEquals(1, actions.getLength());
    assertEquals("android.view.InputMethod", ((Element) actions.item(0)).getAttribute("android:name"));

    Document legacy = parse("res/xml/backup_rules.xml");
    assertEquals(0, legacy.getElementsByTagName("include").getLength());
    assertEquals(2, legacy.getElementsByTagName("exclude").getLength());
    Document modern = parse("res/xml/data_extraction_rules.xml");
    assertEquals(0, modern.getElementsByTagName("include").getLength());
    assertEquals(4, modern.getElementsByTagName("exclude").getLength());
    NodeList excludes = modern.getElementsByTagName("exclude");
    for (int i = 0; i < excludes.getLength(); i++)
      assertEquals(".", ((Element) excludes.item(i)).getAttribute("path"));
  }
}
