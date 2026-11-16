package hw5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**.
 * Data: movies / people / genres, linked with
 *   movie/@directorid -> person/@id (IDREF), movie/@actorids -> person/@id (IDREFS), movie/@genreids -> genre/@id (IDREFS)
 */
public final class Catalog {

    private Catalog() {}

    // helpers to write case-insensitive comparisons in XPath 1.0
    private static final String UP  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOW = "abcdefghijklmnopqrstuvwxyz";

    public static final List<Query> ALL = new ArrayList<>();

    private static void add(String id, String tier, String title, String description,
                            String xpath, String result, Param... params) {
        ALL.add(new Query(id, tier, title, description, xpath, result, List.of(params)));
    }

    public static Optional<Query> find(String id) {
        return ALL.stream().filter(q -> q.id().equalsIgnoreCase(id)).findFirst();
    }

    static {
        // SIMPLE: no predicates at all. They show the axes, the shortcuts and the node types.
        add("S1", "simple", "All movie titles",
            "child axis, absolute path",
            "/moviedb/movies/movie/title", "nodeset");

        add("S2", "simple", "All person names",
            "shortcut // = /descendant-or-self::node()/ (finds 'person' at any depth)",
            "//person/name", "nodeset");

        add("S3", "simple", "The director reference of every movie",
            "attribute axis, shortcut @ (a foreign key = an IDREF)",
            "/moviedb/movies/movie/@directorid", "nodeset");

        add("S4", "simple", "Text nodes of the genre names",
            "LONG syntax of the axes (child::, attribute::) and the node test text()",
            "/child::moviedb/child::genres/child::genre/child::name/child::text()", "nodeset");

        add("S5", "simple", "The parent of every <year> element",
            "shortcut .. = parent::node()  (the result is the 5 movie elements)",
            "//year/..", "nodeset");

        add("S6", "simple", "Ancestors of the genre elements",
            "ancestor axis (the result is <genres> and <moviedb>, each once)",
            "/moviedb/genres/genre/ancestor::*", "nodeset");

        add("S7", "simple", "Siblings of <people>",
            "preceding-sibling and following-sibling axes joined with the union operator |",
            "/moviedb/people/preceding-sibling::* | /moviedb/people/following-sibling::*", "nodeset");

        add("S8", "simple", "Comment and processing instruction before the root",
            "node tests comment() and processing-instruction() on the document node (/)",
            "/comment() | /processing-instruction()", "nodeset");

        add("S9", "simple", "Namespace nodes of the root element",
            "namespace axis (xml + the xsi declaration used for the XSD link)",
            "/moviedb/namespace::*", "nodeset");

        add("S10", "simple", "Size of the database (a STRING)",
            "functions name(), count(), concat(): the result is not a node set",
            "concat(name(/*), ': ', count(/moviedb/movies/movie), ' movies, ', "
                + "count(/moviedb/people/person), ' people, ', count(/moviedb/genres/genre), ' genres')",
            "string");

        add("S11", "simple", "Total box office (a NUMBER)",
            "function sum() over a node set of numbers",
            "sum(/moviedb/movies/movie/boxoffice)", "number");

        // MEDIUM: predicates, functions, full syntax of a step (axis::test[predicate])
        add("M1", "medium", "The n-th movie",
            "positional predicate with a parameter",
            "/moviedb/movies/movie[$n]/title", "nodeset",
            new Param("n", "number", "3", "position of the movie (1 = first)"));

        add("M2", "medium", "A movie by its id",
            "predicate on an attribute: the whole <movie> element is returned",
            "/moviedb/movies/movie[@id = $id]", "nodeset",
            new Param("id", "string", "m2", "movie id, for example m1"));

        add("M3", "medium", "Good and short movies",
            "two numeric comparisons joined with 'and'",
            "/moviedb/movies/movie[rating >= $minrating and runtime <= $maxruntime]/title", "nodeset",
            new Param("minrating", "number", "8.7", "minimum rating"),
            new Param("maxruntime", "number", "150", "maximum runtime in minutes"));

        add("M4", "medium", "People whose name contains a text (ignoring the case)",
            "contains() + translate() used as lower-case()",
            "/moviedb/people/person[contains(translate(name,'" + UP + "','" + LOW + "'), "
                + "translate($text,'" + UP + "','" + LOW + "'))]/name", "nodeset",
            new Param("text", "string", "AN", "text to look for"));

        add("M5", "medium", "Movies with a huge return on investment",
            "arithmetic inside the predicate: (boxoffice - budget) div budget",
            "/moviedb/movies/movie[(boxoffice - budget) div budget > $roi]/title", "nodeset",
            new Param("roi", "number", "5", "minimum profit / budget ratio (5 = 500%)"));

        add("M6", "medium", "Movies with at least n genres",
            "full step syntax + string functions: counts the tokens of the IDREFS attribute @genreids",
            "/child::moviedb/child::movies/child::movie["
                + "string-length(normalize-space(@genreids)) - string-length(translate(normalize-space(@genreids), ' ', '')) + 1"
                + " >= $mingenres]/child::title", "nodeset",
            new Param("mingenres", "number", "3", "minimum number of genres"));

        // COMPLEX: the predicate contains other path expressions = joins over the ID / IDREF(S) links
        add("C1", "complex", "Movies of a director (by name)",
            "join: the name -> the person id (inner path) -> movies with that @directorid",
            "/moviedb/movies/movie[@directorid = /moviedb/people/person[name = $director]/@id]/title", "nodeset",
            new Param("director", "string", "Christopher Nolan", "full name of the director"));

        add("C2", "complex", "The director of a movie (by title)",
            "the same join, in the opposite direction: movie -> @directorid -> person",
            "/moviedb/people/person[@id = /moviedb/movies/movie[title = $title]/@directorid]/name", "nodeset",
            new Param("title", "string", "Pulp Fiction", "movie title"));

        add("C3", "complex", "The cast of a movie (IDREFS)",
            "an IDREFS list is searched as ' p2 p3 ' with contains(): padding with spaces avoids p1 matching p10",
            "/moviedb/people/person[contains(concat(' ', /moviedb/movies/movie[title = $title]/@actorids, ' '), "
                + "concat(' ', @id, ' '))]/name", "nodeset",
            new Param("title", "string", "Inception", "movie title"));

        add("C4", "complex", "Movies of a genre (by genre name)",
            "genre name -> genre id (inner path) -> searched inside the IDREFS @genreids",
            "/moviedb/movies/movie[contains(concat(' ', @genreids, ' '), "
                + "concat(' ', /moviedb/genres/genre[name = $genre]/@id, ' '))]/title", "nodeset",
            new Param("genre", "string", "Sci-Fi", "genre name"));

        add("C5", "complex", "Movies of a director in a genre",
            "the query of homework 1 / 4, as ONE expression: two joins combined with 'and'",
            "/moviedb/movies/movie[@directorid = /moviedb/people/person[name = $director]/@id and "
                + "contains(concat(' ', @genreids, ' '), concat(' ', /moviedb/genres/genre[name = $genre]/@id, ' '))]/title",
            "nodeset",
            new Param("director", "string", "Christopher Nolan", "full name of the director"),
            new Param("genre", "string", "Sci-Fi", "genre name"));

        add("C6", "complex", "Movies whose director made another movie too",
            "axes inside the predicate: node-set comparison with the sibling movies (no parameters)",
            "/moviedb/movies/movie[@directorid = preceding-sibling::movie/@directorid "
                + "or @directorid = following-sibling::movie/@directorid]/title", "nodeset");
    }
}
