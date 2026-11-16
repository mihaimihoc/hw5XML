package hw5;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Console demo for homework 5
 *   Main                         -> runs ALL expressions of the catalog with their default parameters
 *   Main list                    -> lists the catalog
 *   Main run C5 director="Quentin Tarantino" genre=Drama
 *   Main run M3 minrating=8 sort=desc limit=2 agg=count
 * Optional first argument  file=path/to/movies.xml  (default: src/data/movies.xml)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        File xml = findData();
        for (String a : args) if (a.startsWith("file=")) xml = new File(a.substring(5));

        org.w3c.dom.Document doc = XPathEngine.load(xml);

        if (args.length == 0) { runAll(doc, xml); return; }

        switch (args[0]) {
            case "list" -> {
                for (Query q : Catalog.ALL)
                    System.out.printf("%-4s %-8s %-8s %s%n", q.id(), q.tier(), q.result(), q.title());
            }
            case "run" -> {
                if (args.length < 2) { System.out.println("Usage: Main run <ID> [name=value ...]"); return; }
                Query q = Catalog.find(args[1]).orElse(null);
                if (q == null) { System.out.println("Unknown query: " + args[1]); return; }
                Map<String, String> p = new LinkedHashMap<>();
                for (int i = 2; i < args.length; i++) {
                    int eq = args[i].indexOf('=');
                    if (eq > 0) p.put(args[i].substring(0, eq), args[i].substring(eq + 1));
                }
                Result r = XPathEngine.run(doc, q, p);
                Operations.apply(r, p.get("sort"), p.get("limit"), p.get("agg"));
                String format = p.getOrDefault("format", "text");
                switch (format) {
                    case "xml" -> System.out.println(Serializer.resultToXml(r));
                    case "json" -> System.out.println(Serializer.resultToJson(r));
                    default -> printText(r);
                }
            }
            default -> System.out.println("Commands: (none) | list | run <ID> [name=value ...]");
        }
    }

    static void runAll(org.w3c.dom.Document doc, File xml) throws Exception {
        System.out.println("Database: " + xml.getPath());
        String tier = "";
        int failed = 0;
        for (Query q : Catalog.ALL) {
            if (!q.tier().equals(tier)) {
                tier = q.tier();
                System.out.println("\n==================== " + tier.toUpperCase() + " ====================");
            }
            try {
                printText(XPathEngine.run(doc, q, Map.of()));
            } catch (Exception e) {
                failed++;
                System.out.println(q.id() + " FAILED: " + e.getMessage());
            }
        }

        System.out.println("\n==================== OPERATIONS OVER A NODE SET ====================");
        demo(doc, "M3", Map.of("minrating", "8", "maxruntime", "170", "sort", "asc", "limit", "3"));
        demo(doc, "C1", Map.of("director", "Christopher Nolan", "agg", "count"));
        demo(doc, "S1", Map.of("sort", "desc", "limit", "2"));

        System.out.println("\n==================== WHAT THE WEB SERVICE SENDS (query C5, XML and JSON) ====================");
        Result r = XPathEngine.run(doc, Catalog.find("C5").get(), Map.of("director", "Quentin Tarantino", "genre", "Drama"));
        System.out.println(Serializer.resultToXml(r));
        System.out.println(Serializer.resultToJson(r));

        System.out.println(failed == 0 ? "All " + Catalog.ALL.size() + " expressions ran." : failed + " expression(s) failed!");
    }

    private static void demo(org.w3c.dom.Document doc, String id, Map<String, String> p) throws Exception {
        Result r = XPathEngine.run(doc, Catalog.find(id).get(), p);
        Operations.apply(r, p.get("sort"), p.get("limit"), p.get("agg"));
        System.out.println("\n(operations: " + p.entrySet().stream()
                .filter(e -> List.of("sort", "limit", "agg").contains(e.getKey()))
                .map(e -> e.getKey() + "=" + e.getValue()).toList() + ")");
        printText(r);
    }

    static void printText(Result r) {
        Query q = r.query;
        System.out.println("\n" + q.id() + "  " + q.title() + "   [" + q.tier() + ", returns " + q.result() + "]");
        System.out.println("  xpath : " + q.xpath());
        if (!r.usedParams.isEmpty()) System.out.println("  params: " + r.usedParams);
        if (q.result().equals("nodeset")) {
            System.out.println("  " + r.items.size() + " node(s):");
            for (Item i : r.items) {
                String v = i.value().length() > 70 ? i.value().substring(0, 67) + "..." : i.value();
                String id = (i.node() instanceof org.w3c.dom.Element e && e.hasAttribute("id")) ? " (id=" + e.getAttribute("id") + ")" : "";
                System.out.println("    - [" + i.kind() + "] " + i.name() + id + (v.isEmpty() ? "" : " = " + v));
            }
        } else {
            System.out.println("  value : " + r.scalar);
        }
        if (r.agg != null) System.out.println("  " + r.agg + " = " + r.aggValue);
    }

    private static File findData() {
        for (String c : new String[]{"src/data", "data", "."})
            if (new File(c, "movies.xml").exists()) return new File(c, "movies.xml");
        return new File("src/data/movies.xml");
    }
}
