package org.fit.ssapp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.ValidationResult;
import net.objecthunter.exp4j.function.Function;
import org.fit.ssapp.ss.gt.NormalPlayer;
import org.fit.ssapp.ss.gt.Strategy;

/**
 * A utility class to evaluate mathematical expressions in string format.
 * Supports variable replacement, basic arithmetic operations, and custom
 * functions.
 */
public class StringExpressionEvaluator {

    public static Pattern nonRelativePattern = Pattern.compile("p[0-9]+");
    public static Pattern fitnessPattern = Pattern.compile("u[0-9]+");

    public enum DefaultFunction {
        SUM, AVERAGE, MIN, MAX, PRODUCT, MEDIAN, RANGE
    }

    static DecimalFormat decimalFormat = new DecimalFormat(
            "#.##############",
            DecimalFormatSymbols.getInstance(Locale.US)
    );

    /**
     * Evaluates a payoff function relative to other players.
     */
    public static BigDecimal evaluatePayoffFunctionWithRelativeToOtherPlayers(
            Strategy strategy,
            String payoffFunction,
            List<NormalPlayer> normalPlayers,
            int[] chosenStrategyIndices
    ) {
        if (isDefaultOrBlank(payoffFunction)) {
            return calculateByDefault(strategy.getProperties(), null);
        }

        payoffFunction = payoffFunction.trim();

        if (checkIfIsDefaultFunction(payoffFunction)) {
            return calculateByDefault(strategy.getProperties(), payoffFunction);
        }

        String expression = payoffFunction;

        Pattern generalPattern = Pattern.compile("(P[0-9]+)?p[0-9]+");
        Matcher generalMatcher = generalPattern.matcher(expression);

        StringBuffer result = new StringBuffer();

        while (generalMatcher.find()) {
            String placeholder = generalMatcher.group();
            double propertyValue;

            if (placeholder.contains("P")) {
                int[] ji = Arrays.stream(placeholder
                        .substring(1)
                        .split("p"))
                        .mapToInt(Integer::parseInt)
                        .map(x -> x - 1)
                        .toArray();

                NormalPlayer otherPlayer = normalPlayers.get(ji[0]);
                Strategy otherPlayerStrategy
                        = otherPlayer.getStrategyAt(chosenStrategyIndices[ji[0]]);

                propertyValue = otherPlayerStrategy.getProperties().get(ji[1]);
            } else {
                int index = Integer.parseInt(placeholder.substring(1)) - 1;
                propertyValue = strategy.getProperties().get(index);
            }

            generalMatcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(formatDouble(propertyValue))
            );
        }

        generalMatcher.appendTail(result);

        double val = evaluateExpression(result.toString());
        return new BigDecimal(val).setScale(10, RoundingMode.HALF_UP);
    }

    /**
     * Evaluates a payoff function without relative variables.
     */
    public static BigDecimal evaluatePayoffFunctionNoRelative(
            Strategy strategy,
            String payoffFunction
    ) {
        if (isDefaultOrBlank(payoffFunction)) {
            return calculateByDefault(strategy.getProperties(), null);
        }

        payoffFunction = payoffFunction.trim();

        if (checkIfIsDefaultFunction(payoffFunction)) {
            return calculateByDefault(strategy.getProperties(), payoffFunction);
        }

        String expression = payoffFunction;

        Matcher nonRelativeMatcher = nonRelativePattern.matcher(expression);
        StringBuffer result = new StringBuffer();

        while (nonRelativeMatcher.find()) {
            String placeholder = nonRelativeMatcher.group();
            int index = Integer.parseInt(placeholder.substring(1)) - 1;
            double propertyValue = strategy.getProperties().get(index);

            nonRelativeMatcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(formatDouble(propertyValue))
            );
        }

        nonRelativeMatcher.appendTail(result);
        expression = result.toString();

        double val = evaluateExpression(expression);
        return new BigDecimal(val).setScale(10, RoundingMode.HALF_UP);
    }

    /**
     * Evaluates the fitness function based on given payoffs.
     */
    public static BigDecimal evaluateFitnessValue(
            double[] payoffs,
            String fitnessFunction
    ) {
        List<Double> payoffList = new ArrayList<>();

        for (double payoff : payoffs) {
            payoffList.add(payoff);
        }

        if (isDefaultOrBlank(fitnessFunction)) {
            return calculateByDefault(payoffList, null);
        }

        fitnessFunction = fitnessFunction.trim();

        if (checkIfIsDefaultFunction(fitnessFunction)) {
            return calculateByDefault(payoffList, fitnessFunction);
        }

        String expression = fitnessFunction;

        Matcher fitnessMatcher = fitnessPattern.matcher(expression);
        StringBuffer result = new StringBuffer();

        while (fitnessMatcher.find()) {
            String placeholder = fitnessMatcher.group();
            int index = Integer.parseInt(placeholder.substring(1)) - 1;
            double propertyValue = payoffs[index];

            fitnessMatcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(formatDouble(propertyValue))
            );
        }

        fitnessMatcher.appendTail(result);
        expression = result.toString();

        double val = evaluateExpression(expression);
        return new BigDecimal(val).setScale(10, RoundingMode.HALF_UP);
    }

    public static int afterTokenLength(String function, int startIndex) {
        int length = 0;

        for (int c = startIndex + 1; c < function.length(); c++) {
            char ch = function.charAt(c);

            if (isNumericValue(ch)) {
                length++;
            } else {
                return length;
            }
        }

        return length;
    }

    public static boolean isNumericValue(char c) {
        return c >= '0' && c <= '9';
    }

    public static String convertToStringWithoutScientificNotation(double value) {
        String stringValue;

        if (value > 9999999) {
            stringValue = String.format("%.15f", value);
        } else {
            stringValue = Double.toString(value);
        }

        stringValue = stringValue.replaceAll("0*$", "").replaceAll(",", ".");
        return stringValue;
    }

    private static String formatDouble(double propertyValue) {
        return decimalFormat.format(propertyValue);
    }

    private static boolean isDefaultOrBlank(String function) {
        return function == null
                || function.trim().isBlank()
                || function.trim().equalsIgnoreCase("DEFAULT");
    }

    private static boolean checkIfIsDefaultFunction(String function) {
        if (function == null) {
            return false;
        }

        String normalizedFunction = function.trim();

        return Arrays
                .stream(DefaultFunction.values())
                .anyMatch(f -> f.name().equalsIgnoreCase(normalizedFunction));
    }

    private static double calSum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static double calProduct(List<Double> values) {
        return values.stream().reduce(1.0, (a, b) -> a * b);
    }

    private static double calMax(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElseThrow(()
                        -> new IllegalArgumentException("Cannot calculate maximum of empty list")
                );
    }

    private static double calMin(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElseThrow(()
                        -> new IllegalArgumentException("Cannot calculate minimum of empty list")
                );
    }

    private static double calAverage(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private static double calMedian(List<Double> values) {
        double[] arr = values.stream()
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();

        int n = arr.length;

        if (n % 2 == 0) {
            return (arr[n / 2] + arr[n / 2 - 1]) / 2;
        } else {
            return arr[n / 2];
        }
    }

    private static double calRange(List<Double> values) {
        double[] arr = values.stream()
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();

        return arr[arr.length - 1] - arr[0];
    }

    public static BigDecimal calculateByDefault(
            List<Double> values,
            String defaultFunction
    ) {
        DefaultFunction function;

        if (isDefaultOrBlank(defaultFunction)) {
            function = DefaultFunction.SUM;
        } else {
            function = DefaultFunction.valueOf(defaultFunction.trim().toUpperCase());
        }

        double val = switch (function) {
            case PRODUCT ->
                calProduct(values);
            case MAX ->
                calMax(values);
            case MIN ->
                calMin(values);
            case AVERAGE ->
                calAverage(values);
            case MEDIAN ->
                calMedian(values);
            case RANGE ->
                calRange(values);
            default ->
                calSum(values);
        };

        return new BigDecimal(val).setScale(10, RoundingMode.HALF_UP);
    }

    /**
     * Evaluates a mathematical string expression.
     */
    private static double evaluateExpression(String expression) {
        String formattedExpression = expression
                .replaceAll("NaN", "0")
                .replaceAll("\\s+", "");

        Expression expr = getExpression(formattedExpression);

        ValidationResult validationResult = expr.validate();

        if (!validationResult.isValid()) {
            throw new RuntimeException(
                    "Invalid expression: " + validationResult.getErrors().toString()
                    + " | Expression: " + formattedExpression
            );
        }

        return expr.evaluate();
    }

    private static Expression getExpression(String formattedExpression) {
        ExpressionBuilder builder = new ExpressionBuilder(formattedExpression);

        Function logFunction = new Function("logb", 2) {
            @Override
            public double apply(double... args) {
                if (args[0] <= 0 || args[1] <= 0) {
                    throw new IllegalArgumentException(
                            "Logarithm base and argument must be positive"
                    );
                }

                return Math.log(args[1]) / Math.log(args[0]);
            }
        };

        Function maxFunction = new Function("max", 2) {
            @Override
            public double apply(double... args) {
                return Math.max(args[0], args[1]);
            }
        };

        Function minFunction = new Function("min", 2) {
            @Override
            public double apply(double... args) {
                return Math.min(args[0], args[1]);
            }
        };

        Function maxUpperFunction = new Function("MAX", 2) {
            @Override
            public double apply(double... args) {
                return Math.max(args[0], args[1]);
            }
        };

        Function minUpperFunction = new Function("MIN", 2) {
            @Override
            public double apply(double... args) {
                return Math.min(args[0], args[1]);
            }
        };

        builder.function(logFunction);
        builder.function(maxFunction);
        builder.function(minFunction);
        builder.function(maxUpperFunction);
        builder.function(minUpperFunction);

        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println(
                convertToStringWithoutScientificNotation(222222222222.2222222222222)
        );
    }
}
