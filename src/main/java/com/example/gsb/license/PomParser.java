package com.example.gsb.license;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code <licenses>} entries from a Maven POM file.
 *
 * <p>License data is taken from the component's own POM only; parent POMs are
 * not consulted (Maven itself never inherits license elements). Namespace
 * agnostic so it handles both the Maven 4 namespace and classic POMs.
 */
public class PomParser {

    public List<LicenseInfo> parseLicenses(File pomFile) {
        List<LicenseInfo> licenses = new ArrayList<>();
        if (pomFile == null || !pomFile.isFile()) {
            return licenses;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new org.xml.sax.SAXException("External entities are not allowed");
            });
            Document document = builder.parse(pomFile);

            NodeList licenseNodes = document.getElementsByTagName("license");
            for (int i = 0; i < licenseNodes.getLength(); i++) {
                Element licenseElement = (Element) licenseNodes.item(i);
                String name = childText(licenseElement, "name");
                String url = childText(licenseElement, "url");
                if (name != null || url != null) {
                    licenses.add(new LicenseInfo(
                            name == null ? "" : name,
                            url == null ? "" : url));
                }
            }
        } catch (Exception e) {
            // Unreadable/parse-broken POMs degrade to "unknown" for the component;
            // the caller keeps the component in the report regardless.
            return new ArrayList<>();
        }
        return licenses;
    }

    private String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
