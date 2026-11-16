package hw5;

import java.util.List;

/**
 * One XPath expression of the catalog.
 *
 * tier   = simple | medium | complex   (the three categories asked in the homework)
 * result = nodeset | number | string   (what the expression returns - "not necessarily an XML document")
 */
public record Query(String id, String tier, String title, String description,
                    String xpath, String result, List<Param> params) {

    public boolean hasParams() { return !params.isEmpty(); }
}
