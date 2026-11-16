package hw5;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the XML database and runs the catalog expressions on it.
 *
 * The expressions contain $variables. The values come from the caller (command line / URL / XForms),
 * so one expression serves many use cases and the values are never pasted into the expression text
 */
public final class XPathEngine {

    private XPathEngine() {}

    public static Document load(File xml) throws IOException, SAXException, javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setIgnoringComments(false);
        return f.newDocumentBuilder().parse(xml);
    }

    public static Result run(Document doc, Query q, Map<String, String> args) throws XPathExpressionException {
        Result r = new Result(q);

        // 1. bind the parameters: given value or default; numbers are converted to real numbers
        Map<String, Object> vars = new HashMap<>();
        for (Param p : q.params()) {
            String raw = args.get(p.name());
            if (raw == null || raw.isBlank()) raw = p.defaultValue();
            r.usedParams.put(p.name(), raw);
            if (p.type().equals("number")) {
                try { vars.put(p.name(), Double.valueOf(raw.trim())); }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException("parameter '" + p.name() + "' must be a number, got: " + raw);
                }
            } else {
                vars.put(p.name(), raw);
            }
        }

        // 2. compile with a variable resolver and evaluate
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setXPathVariableResolver((QName name) -> vars.get(name.getLocalPart()));
        XPathExpression ex = xp.compile(q.xpath());

        switch (q.result()) {
            case "nodeset" -> {
                NodeList nodes = (NodeList) ex.evaluate(doc, XPathConstants.NODESET);
                for (int i = 0; i < nodes.getLength(); i++) r.items.add(toItem(nodes.item(i)));
            }
            case "number" -> {
                double d = (Double) ex.evaluate(doc, XPathConstants.NUMBER);
                r.scalar = (d == Math.rint(d) && !Double.isInfinite(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> r.scalar = (String) ex.evaluate(doc, XPathConstants.STRING);
        }
        return r;
    }

    static Item toItem(Node n) {
        switch (n.getNodeType()) {
            case Node.ELEMENT_NODE: {
                // an element that contains other elements has no useful "own" text, so its value stays empty
                boolean container = false;
                for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling())
                    if (c.getNodeType() == Node.ELEMENT_NODE) { container = true; break; }
                return new Item("element", n.getNodeName(),
                        container ? "" : n.getTextContent().trim().replaceAll("\\s+", " "), n);
            }
            case Node.ATTRIBUTE_NODE: {
                Attr a = (Attr) n;
                // namespace declarations (xmlns:xsi) are attributes in DOM but "namespace nodes" in XPath
                boolean ns = a.getName().equals("xmlns") || a.getName().startsWith("xmlns:");
                return new Item(ns ? "namespace" : "attribute", a.getName(), a.getValue(), n);
            }
            case Node.TEXT_NODE:
            case Node.CDATA_SECTION_NODE:
                return new Item("text", "#text", n.getNodeValue().trim(), n);
            case Node.COMMENT_NODE:
                return new Item("comment", "#comment", n.getNodeValue().trim(), n);
            case Node.PROCESSING_INSTRUCTION_NODE:
                return new Item("processing-instruction", n.getNodeName(), n.getNodeValue(), n);
            case Node.DOCUMENT_NODE:
                return new Item("document", "#document", "", n);
            default:
                return new Item("other(" + n.getNodeType() + ")", n.getNodeName(), String.valueOf(n.getNodeValue()), n);
        }
    }
}
