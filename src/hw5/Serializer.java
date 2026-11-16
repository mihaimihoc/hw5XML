package hw5;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a Result (or the catalog) into a well-formed XML message or a JSON message.
 * Written by hand, no libraries: the structure is small.
 */
public final class Serializer {

    private Serializer() {}

    // ============================ RESULT -> XML ============================
    public static String resultToXml(Result r) throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = d.createElement("result");
        d.appendChild(root);
        root.setAttribute("query", r.query.id());
        root.setAttribute("tier", r.query.tier());
        root.setAttribute("type", r.query.result());
        root.setAttribute("count", String.valueOf(r.count()));

        text(d, root, "title", r.query.title());
        text(d, root, "xpath", r.query.xpath());

        Element params = d.createElement("params");
        root.appendChild(params);
        for (Map.Entry<String, String> e : r.usedParams.entrySet()) {
            Element p = d.createElement("param");
            p.setAttribute("name", e.getKey());
            p.setTextContent(e.getValue());
            params.appendChild(p);
        }

        if (r.query.result().equals("nodeset")) {
            Element items = d.createElement("items");
            root.appendChild(items);
            for (Item it : r.items) {
                Element e = d.createElement("item");
                e.setAttribute("kind", it.kind());
                e.setAttribute("name", it.name());
                if (it.kind().equals("element")) {
                    // an element item carries a COPY of the real element, so the client gets real XML nodes
                    e.appendChild(d.importNode(it.node(), true));
                } else {
                    e.setTextContent(it.value());
                }
                items.appendChild(e);
            }
        } else {
            text(d, root, "value", r.scalar);
        }
        if (r.agg != null) {
            Element a = text(d, root, "aggregate", r.aggValue);
            a.setAttribute("op", r.agg);
        }
        return print(d);
    }

    // ============================ RESULT -> JSON ============================
    public static String resultToJson(Result r) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"query\": ").append(q(r.query.id())).append(",\n");
        sb.append("  \"tier\": ").append(q(r.query.tier())).append(",\n");
        sb.append("  \"title\": ").append(q(r.query.title())).append(",\n");
        sb.append("  \"xpath\": ").append(q(r.query.xpath())).append(",\n");
        sb.append("  \"type\": ").append(q(r.query.result())).append(",\n");
        sb.append("  \"params\": {");
        int k = 0;
        for (Map.Entry<String, String> e : r.usedParams.entrySet())
            sb.append(k++ > 0 ? ", " : "").append(q(e.getKey())).append(": ").append(q(e.getValue()));
        sb.append("},\n");
        sb.append("  \"count\": ").append(r.count());
        if (r.query.result().equals("nodeset")) {
            sb.append(",\n  \"items\": [");
            for (int i = 0; i < r.items.size(); i++) {
                sb.append(i > 0 ? ",\n    " : "\n    ").append(itemJson(r.items.get(i)));
            }
            sb.append(r.items.isEmpty() ? "]" : "\n  ]");
        } else {
            sb.append(",\n  \"value\": ").append(q(r.scalar));
        }
        if (r.agg != null) {
            sb.append(",\n  \"aggregate\": {\"op\": ").append(q(r.agg)).append(", \"value\": ").append(q(r.aggValue)).append("}");
        }
        return sb.append("\n}\n").toString();
    }

    private static String itemJson(Item it) {
        StringBuilder sb = new StringBuilder("{\"kind\": ").append(q(it.kind())).append(", \"name\": ").append(q(it.name()));
        if (it.kind().equals("element")) {
            Element e = (Element) it.node();
            if (hasChildElements(e)) sb.append(", \"attributes\": ").append(attrsJson(e))
                    .append(", \"children\": ").append(childrenJson(e));
            else sb.append(", \"attributes\": ").append(attrsJson(e)).append(", \"value\": ").append(q(it.value()));
        } else {
            sb.append(", \"value\": ").append(q(it.value()));
        }
        return sb.append("}").toString();
    }

    private static String attrsJson(Element e) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < e.getAttributes().getLength(); i++) {
            Node a = e.getAttributes().item(i);
            sb.append(i > 0 ? ", " : "").append(q(a.getNodeName())).append(": ").append(q(a.getNodeValue()));
        }
        return sb.append("}").toString();
    }

    /** children as an object: simple children -> text, repeated names -> arrays */
    private static String childrenJson(Element e) {
        Map<String, List<Element>> byName = new LinkedHashMap<>();
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
            if (kids.item(i) instanceof Element c) byName.computeIfAbsent(c.getNodeName(), x -> new java.util.ArrayList<>()).add(c);
        StringBuilder sb = new StringBuilder("{");
        int k = 0;
        for (Map.Entry<String, List<Element>> en : byName.entrySet()) {
            sb.append(k++ > 0 ? ", " : "").append(q(en.getKey())).append(": ");
            List<Element> l = en.getValue();
            if (l.size() == 1) sb.append(valueJson(l.get(0)));
            else {
                sb.append("[");
                for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? ", " : "").append(valueJson(l.get(i)));
                sb.append("]");
            }
        }
        return sb.append("}").toString();
    }

    private static String valueJson(Element e) {
        if (hasChildElements(e)) return childrenJson(e);
        return q(e.getTextContent().trim());
    }

    private static boolean hasChildElements(Element e) {
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) if (kids.item(i) instanceof Element) return true;
        return false;
    }

    // ============================ CATALOG (what operations exist) ============================
    public static String catalogToXml() throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = d.createElement("catalog");
        d.appendChild(root);
        for (Query q : Catalog.ALL) {
            Element e = d.createElement("query");
            e.setAttribute("id", q.id());
            e.setAttribute("tier", q.tier());
            e.setAttribute("type", q.result());
            text(d, e, "title", q.title());
            text(d, e, "description", q.description());
            text(d, e, "xpath", q.xpath());
            Element ps = d.createElement("params");
            for (Param p : q.params()) {
                Element pe = d.createElement("param");
                pe.setAttribute("name", p.name());
                pe.setAttribute("type", p.type());
                pe.setAttribute("default", p.defaultValue());
                pe.setTextContent(p.description());
                ps.appendChild(pe);
            }
            e.appendChild(ps);
            root.appendChild(e);
        }
        return print(d);
    }

    public static String catalogToJson() {
        StringBuilder sb = new StringBuilder("{\"queries\": [");
        for (int i = 0; i < Catalog.ALL.size(); i++) {
            Query q = Catalog.ALL.get(i);
            sb.append(i > 0 ? ",\n  " : "\n  ");
            sb.append("{\"id\": ").append(q(q.id())).append(", \"tier\": ").append(q(q.tier()))
              .append(", \"type\": ").append(q(q.result())).append(", \"title\": ").append(q(q.title()))
              .append(", \"xpath\": ").append(q(q.xpath())).append(", \"params\": [");
            for (int j = 0; j < q.params().size(); j++) {
                Param p = q.params().get(j);
                sb.append(j > 0 ? ", " : "").append("{\"name\": ").append(q(p.name())).append(", \"type\": ").append(q(p.type()))
                  .append(", \"default\": ").append(q(p.defaultValue())).append("}");
            }
            sb.append("]}");
        }
        return sb.append("\n]}\n").toString();
    }

    // ============================ errors ============================
    public static String errorXml(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<error>" + esc(message) + "</error>\n";
    }

    public static String errorJson(String message) {
        return "{\"error\": " + q(message) + "}\n";
    }

    // ============================ helpers ============================
    private static Element text(Document d, Element parent, String name, String value) {
        Element e = d.createElement(name);
        e.setTextContent(value);
        parent.appendChild(e);
        return e;
    }

    private static String print(Document d) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(d), new StreamResult(w));
        return w.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** JSON string literal with escaping */
    static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append("\"").toString();
    }
}
