package hw5;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *   GET .../api
 *   GET .../api?q=C5&director=Christopher+Nolan&genre=Sci-Fi
 *   GET .../api?q=M3&minrating=8&sort=desc&limit=2&agg=count&format=json
 */
public class ApiServlet extends HttpServlet {

    private File dataFile;
    private Document cached;
    private long cachedStamp = -1;

    @Override
    public void init() throws ServletException {
        String path = getInitParameter("dataFile");
        if (path == null || path.isBlank()) throw new ServletException("init-param 'dataFile' is missing in web.xml");
        dataFile = new File(path);
    }

    /** parse the file again only if it changed */
    private synchronized Document data() throws Exception {
        if (!dataFile.exists()) throw new IllegalStateException("data file not found: " + dataFile);
        if (cached == null || dataFile.lastModified() != cachedStamp) {
            cached = XPathEngine.load(dataFile);
            cachedStamp = dataFile.lastModified();
        }
        return cached;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean json = "json".equalsIgnoreCase(req.getParameter("format"));
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType(json ? "application/json" : "application/xml");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Cache-Control", "no-cache");

        String body;
        int status = 200;
        try {
            String q = req.getParameter("q");
            if (q == null || q.isBlank()) {
                body = json ? Serializer.catalogToJson() : Serializer.catalogToXml();
            } else {
                Optional<Query> query = Catalog.find(q);
                if (query.isEmpty()) {
                    status = 404;
                    body = fail(json, "unknown query '" + q + "' (call the service without q to see the catalog)");
                } else {
                    Map<String, String> args = new HashMap<>();
                    for (Param p : query.get().params()) args.put(p.name(), req.getParameter(p.name()));
                    Result r = XPathEngine.run(data(), query.get(), args);
                    Operations.apply(r, req.getParameter("sort"), req.getParameter("limit"), req.getParameter("agg"));
                    body = json ? Serializer.resultToJson(r) : Serializer.resultToXml(r);
                }
            }
        } catch (IllegalArgumentException | XPathExpressionException e) {
            status = 400;
            body = fail(json, e.getMessage());
        } catch (Exception e) {
            status = 500;
            body = fail(json, e.getClass().getSimpleName() + ": " + e.getMessage());
            log("API error", e);
        }
        resp.setStatus(status);
        resp.getWriter().write(body);
    }

    private static String fail(boolean json, String msg) {
        return json ? Serializer.errorJson(msg) : Serializer.errorXml(msg);
    }
}
