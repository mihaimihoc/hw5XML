package hw5;

/**
 * One parameter of an XPath expression (it appears as $name inside the expression).
 *
 * type = "string" -> the value is passed to XPath as a string
 * type = "number" -> the value is passed as a number. This matters: movie[$n] means "the n-th movie"
 *                    only when $n is a number. With a string, the predicate would be always true.
 */
public record Param(String name, String type, String defaultValue, String description) {}
