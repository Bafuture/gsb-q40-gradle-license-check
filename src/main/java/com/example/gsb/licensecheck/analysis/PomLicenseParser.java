package com.example.gsb.licensecheck.analysis;

import com.example.gsb.licensecheck.model.DeclaredLicense;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts {@code <licenses>/<license>} entries from a Maven POM.
 */
public final class PomLicenseParser {

    private PomLicenseParser() {
    }

    public static List<DeclaredLicense> parse(File pomFile) {
        List<DeclaredLicense> licenses = new ArrayList<>();
        if (pomFile == null || !pomFile.isFile()) {
            return licenses;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(pomFile);
            NodeList licenseNodes = document.getElementsByTagName("license");
            for (int i = 0; i < licenseNodes.getLength(); i++) {
                Node node = licenseNodes.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                String name = textOf(element, "name");
                String url = textOf(element, "url");
                DeclaredLicense license = new DeclaredLicense(name, url);
                if (!license.isEmpty()) {
                    licenses.add(license);
                }
            }
        } catch (Exception ignored) {
            return licenses;
        }
        return licenses;
    }

    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? "" : text.trim();
    }
}
