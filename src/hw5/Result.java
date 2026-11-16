package hw5;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What an expression returned, plus the parameters that were really used and the optional post-operations. */
public class Result {
    public final Query query;
    public final Map<String, String> usedParams = new LinkedHashMap<>();
    public List<Item> items = new ArrayList<>();   // for node sets
    public String scalar;                          // for number / string results
    public String sort, limit, agg, aggValue;      // post-operations (see Operations)

    public Result(Query query) { this.query = query; }

    public int count() { return query.result().equals("nodeset") ? items.size() : 1; }
}
