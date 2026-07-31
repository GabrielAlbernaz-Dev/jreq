package com.jreq.request.presentation;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Optional;

final class XmlResponseContentFormatter implements ResponseContentFormatter {
    private static final String INDENT_AMOUNT = "{http://xml.apache.org/xslt}indent-amount";

    @Override
    public ResponseBodyFormat format() {
        return ResponseBodyFormat.XML;
    }

    @Override
    public boolean supports(String mediaType) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return !normalized.equals("application/xhtml+xml")
                && (normalized.equals("application/xml")
                || normalized.equals("text/xml")
                || normalized.endsWith("+xml"));
    }

    @Override
    public boolean looksLike(String body) {
        String stripped = body.stripLeading();
        return stripped.startsWith("<") && !HtmlResponseContentFormatter.hasHtmlSignature(stripped);
    }

    @Override
    public Optional<String> prettyPrint(String body) {
        try {
            var documentBuilder = documentBuilderFactory().newDocumentBuilder();
            documentBuilder.setErrorHandler(new DefaultHandler());
            var document = documentBuilder.parse(new InputSource(new StringReader(body)));
            Transformer transformer = transformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(INDENT_AMOUNT, "2");
            transformer.setOutputProperty(
                    OutputKeys.OMIT_XML_DECLARATION,
                    body.stripLeading().startsWith("<?xml") ? "no" : "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return Optional.of(output.toString().stripTrailing());
        } catch (ParserConfigurationException | SAXException | IOException
                 | TransformerException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private DocumentBuilderFactory documentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private TransformerFactory transformerFactory() {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return factory;
    }
}
