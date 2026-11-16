package hw5;

import org.w3c.dom.Node;

/** One item of a result: the kind of node (element, attribute, text...), its name and its text value. */
public record Item(String kind, String name, String value, Node node) {}
