package com.nhs.customkeyboard.textexpander;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Evaluates mathematical expressions containing operators between numbers.
 * Supports +, -, *, /, %, ^, parentheses (), unary minus, and decimal numbers.
 * Enforces that at least one binary operator between numbers exists.
 */
public final class MathEvaluator
{
    private final String _expr;
    private int _pos = -1;
    private int _ch = -1;
    private int _binary_op_count = 0;

    private MathEvaluator(String expr)
    {
        _expr = expr;
    }

    /**
     * Extracts a candidate trailing math expression from the text preceding the trigger.
     * Scans backwards until encountering a non-math character (letters, delimiters, newlines).
     */
    public static String extract_trailing_math_expr(String s)
    {
        if (s == null || s.isEmpty())
            return null;

        int end = s.length() - 1;
        while (end >= 0 && Character.isWhitespace(s.charAt(end)))
            end--;

        if (end < 0)
            return null;

        int start = end;
        while (start >= 0)
        {
            char c = s.charAt(start);
            if (is_math_char(c))
            {
                start--;
            }
            else
            {
                break;
            }
        }

        String candidate = s.substring(start + 1, end + 1).trim();
        return candidate.isEmpty() ? null : candidate;
    }

    private static boolean is_math_char(char c)
    {
        return (c >= '0' && c <= '9')
                || c == '.' || c == '+' || c == '-' || c == '*' || c == '×'
                || c == '/' || c == '÷' || c == '%' || c == '^'
                || c == '(' || c == ')' || c == ' ' || c == '\t';
    }

    /**
     * Evaluates the given math expression and returns the formatted result string,
     * or null if the expression is invalid, divides by zero, or has no operator.
     */
    public static String evaluate(String expression)
    {
        if (expression == null)
            return null;

        String cleaned = expression.replace('×', '*').replace('÷', '/').trim();
        if (cleaned.isEmpty())
            return null;

        try
        {
            MathEvaluator parser = new MathEvaluator(cleaned);
            parser.nextChar();
            double result = parser.parseExpression();

            // Must consume entire string, must have at least one binary operator, and result must be finite
            if (parser._pos < parser._expr.length() || parser._binary_op_count < 1
                    || Double.isNaN(result) || Double.isInfinite(result))
            {
                return null;
            }

            return format_number(result);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private void nextChar()
    {
        _pos++;
        _ch = (_pos < _expr.length()) ? _expr.charAt(_pos) : -1;
    }

    private boolean eat(int charToEat)
    {
        while (_ch == ' ' || _ch == '\t')
            nextChar();

        if (_ch == charToEat)
        {
            nextChar();
            return true;
        }
        return false;
    }

    // expression = term (('+' | '-') term)*
    private double parseExpression()
    {
        double x = parseTerm();
        for (;;)
        {
            if (eat('+'))
            {
                _binary_op_count++;
                x += parseTerm();
            }
            else if (eat('-'))
            {
                _binary_op_count++;
                x -= parseTerm();
            }
            else
            {
                return x;
            }
        }
    }

    // term = factor (('*' | '/' | '%') factor)*
    private double parseTerm()
    {
        double x = parseFactor();
        for (;;)
        {
            if (eat('*'))
            {
                _binary_op_count++;
                x *= parseFactor();
            }
            else if (eat('/'))
            {
                _binary_op_count++;
                double divisor = parseFactor();
                if (divisor == 0.0)
                    throw new ArithmeticException("Division by zero");
                x /= divisor;
            }
            else if (eat('%'))
            {
                _binary_op_count++;
                double divisor = parseFactor();
                if (divisor == 0.0)
                    throw new ArithmeticException("Modulo by zero");
                x %= divisor;
            }
            else
            {
                return x;
            }
        }
    }

    // factor = unary ('^' factor)*
    private double parseFactor()
    {
        double x = parseUnary();
        if (eat('^'))
        {
            _binary_op_count++;
            x = Math.pow(x, parseFactor());
        }
        return x;
    }

    // unary = ('+' | '-') unary | primary
    private double parseUnary()
    {
        if (eat('+'))
            return parseUnary();
        if (eat('-'))
            return -parseUnary();

        return parsePrimary();
    }

    // primary = '(' expression ')' | NUMBER
    private double parsePrimary()
    {
        while (_ch == ' ' || _ch == '\t')
            nextChar();

        if (eat('('))
        {
            double x = parseExpression();
            if (!eat(')'))
                throw new IllegalArgumentException("Missing closing parenthesis");
            return x;
        }

        int startPos = _pos;
        if ((_ch >= '0' && _ch <= '9') || _ch == '.')
        {
            boolean hasDot = false;
            while ((_ch >= '0' && _ch <= '9') || _ch == '.')
            {
                if (_ch == '.')
                {
                    if (hasDot)
                        throw new IllegalArgumentException("Multiple decimal points");
                    hasDot = true;
                }
                nextChar();
            }
            return Double.parseDouble(_expr.substring(startPos, _pos));
        }

        throw new IllegalArgumentException("Unexpected character: " + (char) _ch);
    }

    /** Formats double value cleanly, removing unnecessary decimals and floating-point errors. */
    public static String format_number(double val)
    {
        if (Double.isNaN(val) || Double.isInfinite(val))
            return null;

        BigDecimal bd = new BigDecimal(Double.toString(val));
        bd = bd.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }
}

