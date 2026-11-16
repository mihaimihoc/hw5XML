package hw5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Simple operations over the returned node set" (homework 5, Part A, last paragraph).
 * They work on the VALUES of the returned nodes, whatever the expression was:
 *
 *   sort = asc | desc     sorts the items (numerically if all values are numbers, else as text)
 *   limit = n             keeps the first n items (after sorting)
 *   agg = count | sum | avg | min | max | distinct
 */
public final class Operations {

    private Operations() {}

    public static void apply(Result r, String sort, String limit, String agg) {
        if (!r.query.result().equals("nodeset")) return;   // only node sets have items

        if (sort != null && !sort.isBlank()) {
            boolean numeric = r.items.stream().allMatch(i -> isNumber(i.value()));
            Comparator<Item> c = numeric
                    ? Comparator.comparingDouble(i -> Double.parseDouble(i.value()))
                    : Comparator.comparing(i -> i.value().toLowerCase());
            if (sort.equalsIgnoreCase("desc")) c = c.reversed();
            else if (!sort.equalsIgnoreCase("asc")) throw new IllegalArgumentException("sort must be asc or desc");
            List<Item> sorted = new ArrayList<>(r.items);
            sorted.sort(c);
            r.items = sorted;
            r.sort = sort.toLowerCase();
        }

        if (limit != null && !limit.isBlank()) {
            int n;
            try { n = Integer.parseInt(limit.trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("limit must be an integer"); }
            if (n < 0) throw new IllegalArgumentException("limit must be >= 0");
            if (n < r.items.size()) r.items = new ArrayList<>(r.items.subList(0, n));
            r.limit = String.valueOf(n);
        }

        if (agg != null && !agg.isBlank()) {
            r.agg = agg.toLowerCase();
            r.aggValue = aggregate(r.items, r.agg);
        }
    }

    private static String aggregate(List<Item> items, String agg) {
        if (agg.equals("count")) return String.valueOf(items.size());
        if (agg.equals("distinct")) return String.valueOf(items.stream().map(Item::value).distinct().count());

        List<Double> nums = new ArrayList<>();
        for (Item i : items) if (isNumber(i.value())) nums.add(Double.parseDouble(i.value()));
        if (nums.isEmpty()) throw new IllegalArgumentException("agg=" + agg + " needs numeric values, but the result has none");

        double v;
        switch (agg) {
            case "sum" -> v = nums.stream().mapToDouble(Double::doubleValue).sum();
            case "avg" -> v = nums.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            case "min" -> v = nums.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            case "max" -> v = nums.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            default -> throw new IllegalArgumentException("agg must be count, sum, avg, min, max or distinct");
        }
        return (v == Math.rint(v)) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isBlank()) return false;
        try { Double.parseDouble(s.trim()); return true; } catch (NumberFormatException e) { return false; }
    }
}
