import norswap.autumn.AutumnTestFixture;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.uranium.Reactor;
import norswap.uranium.UraniumTestFixture;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;

/**
 * NOTE(norswap): These tests were derived from the {@link InterpreterTests} and don't test anything
 * more, but show how to idiomatically test semantic analysis. using {@link UraniumTestFixture}.
 */
public final class SemanticAnalysisTests extends UraniumTestFixture
{
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.rule = grammar.root();
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    private String input;

    @Override protected Object parse (String input) {
        this.input = input;
        return autumnFixture.success(input).topValue();
    }

    @Override protected String astNodeToString (Object ast) {
        LineMapString map = new LineMapString("<test>", input);
        return ast.toString() + " (" + ((SighNode) ast).span.startString(map) + ")";
    }

    // ---------------------------------------------------------------------------------------------

    @Override protected void configureSemanticAnalysis (Reactor reactor, Object ast) {
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        walker.walk(((SighNode) ast));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testLiteralsAndUnary() {
        successInput("return 42");
        successInput("return 42.0");
        successInput("return \"hello\"");
        successInput("return (42)");
        successInput("return [1, 2, 3]");
        successInput("return true");
        successInput("return false");
        successInput("return null");
        successInput("return !false");
        successInput("return !true");
        successInput("return !!true");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericBinary() {
        successInput("return 1 + 2");
        successInput("return 2 - 1");
        successInput("return 2 * 3");
        successInput("return 2 / 3");
        successInput("return 3 / 2");
        successInput("return 2 % 3");
        successInput("return 3 % 2");

        successInput("return 1.0 + 2.0");
        successInput("return 2.0 - 1.0");
        successInput("return 2.0 * 3.0");
        successInput("return 2.0 / 3.0");
        successInput("return 3.0 / 2.0");
        successInput("return 2.0 % 3.0");
        successInput("return 3.0 % 2.0");

        successInput("return 1 + 2.0");
        successInput("return 2 - 1.0");
        successInput("return 2 * 3.0");
        successInput("return 2 / 3.0");
        successInput("return 3 / 2.0");
        successInput("return 2 % 3.0");
        successInput("return 3 % 2.0");

        successInput("return 1.0 + 2");
        successInput("return 2.0 - 1");
        successInput("return 2.0 * 3");
        successInput("return 2.0 / 3");
        successInput("return 3.0 / 2");
        successInput("return 2.0 % 3");
        successInput("return 3.0 % 2");

        failureInputWith("return 2 + true", "Trying to add Int with Bool");
        failureInputWith("return true + 2", "Trying to add Bool with Int");
        failureInputWith("return 2 + [1]", "Trying to add Int with Int[]");
        // todo : why does this test fail ?
        //failureInputWith("return [1] + 2", "Trying to add Int[] with Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericArrayOperation() {
        // multiplication
        successInput("return [1, 2, 7] * [8, 5, 7]");
        successInput("return [-1, 3265, -3] * [4, -6985, 0]");

        successInput("return [1.3, 0.0, -45.369] * [4, -6985, 0]");
        successInput("return [4, -6985, 0] * [1.3, 0.0, -45.369]");
        successInput("return [0.0, -2589.36452, 8957.2] * [1.3, 0.0, -45.369]");

        failureInputWith("return [true, false, true] * [8, 5, 7]", "Trying to multiply Bool[] with Int[]");
        failureInputWith("return [-1, 0, 547] * [true, false, true]", "Trying to multiply Int[] with Bool[]");
        failureInputWith("return [null, null, null] * [8, 5, 7]", "Trying to multiply Null[] with Int[]");
        failureInputWith("return [-1, 0, 547] * [null, null, null]", "Trying to multiply Int[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] * [8, 5, 7]", "Trying to multiply String[] with Int[]");
        failureInputWith("return [-1, 0, 547] * [\"oui\", \"\", \"123\"]", "Trying to multiply Int[] with String[]");

        failureInputWith("return [true, false, true] * [1.3, 0.0, -45.369]", "Trying to multiply Bool[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] * [true, false, true]", "Trying to multiply Float[] with Bool[]");
        failureInputWith("return [null, null, null] * [1.3, 0.0, -45.369]", "Trying to multiply Null[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] * [null, null, null]", "Trying to multiply Float[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] * [1.3, 0.0, -45.369]", "Trying to multiply String[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] * [\"oui\", \"\", \"123\"]", "Trying to multiply Float[] with String[]");

        // division
        successInput("return [1, 2, 7] / [8, 5, 7]");
        successInput("return [-1, 3265, -3] / [4, -6985, 1]");

        successInput("return [1.3, 0.0, -45.369] / [4, -6985, 1]");
        successInput("return [4, -6985, 0] / [1.3, 5.36, -45.369]");
        successInput("return [0.0, -2589.36452, 8957.2] / [1.3, 5.36, -45.369]");

        failureInputWith("return [true, false, true] / [8, 5, 7]", "Trying to divide Bool[] with Int[]");
        failureInputWith("return [-1, 0, 547] / [true, false, true]", "Trying to divide Int[] with Bool[]");
        failureInputWith("return [null, null, null] / [8, 5, 7]", "Trying to divide Null[] with Int[]");
        failureInputWith("return [-1, 0, 547] / [null, null, null]", "Trying to divide Int[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] / [8, 5, 7]", "Trying to divide String[] with Int[]");
        failureInputWith("return [-1, 0, 547] / [\"oui\", \"\", \"123\"]", "Trying to divide Int[] with String[]");

        failureInputWith("return [true, false, true] / [1.3, 0.0, -45.369]", "Trying to divide Bool[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] / [true, false, true]", "Trying to divide Float[] with Bool[]");
        failureInputWith("return [null, null, null] / [1.3, 0.0, -45.369]", "Trying to divide Null[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] / [null, null, null]", "Trying to divide Float[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] / [1.3, 0.0, -45.369]", "Trying to divide String[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] / [\"oui\", \"\", \"123\"]", "Trying to divide Float[] with String[]");

        // addition
        successInput("return [1, 2, 7] + [8, 5, 7]");
        successInput("return [-1, 3265, -3] + [4, -6985, 0]");

        successInput("return [1.3, 0.0, -45.369] + [4, -6985, 0]");
        successInput("return [4, -6985, 0] + [1.3, 0.0, -45.369]");
        successInput("return [0.0, -2589.36452, 8957.2] + [1.3, 0.0, -45.369]");

        failureInputWith("return [true, false, true] + [8, 5, 7]", "Trying to add Bool[] with Int[]");
        failureInputWith("return [-1, 0, 547] + [true, false, true]", "Trying to add Int[] with Bool[]");
        failureInputWith("return [null, null, null] + [8, 5, 7]", "Trying to add Null[] with Int[]");
        failureInputWith("return [-1, 0, 547] + [null, null, null]", "Trying to add Int[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] + [8, 5, 7]", "Trying to add String[] with Int[]");
        failureInputWith("return [-1, 0, 547] + [\"oui\", \"\", \"123\"]", "Trying to add Int[] with String[]");

        failureInputWith("return [true, false, true] + [1.3, 0.0, -45.369]", "Trying to add Bool[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] + [true, false, true]", "Trying to add Float[] with Bool[]");
        failureInputWith("return [null, null, null] + [1.3, 0.0, -45.369]", "Trying to add Null[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] + [null, null, null]", "Trying to add Float[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] + [1.3, 0.0, -45.369]", "Trying to add String[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] + [\"oui\", \"\", \"123\"]", "Trying to add Float[] with String[]");

        // subtraction
        successInput("return [1, 2, 7] - [8, 5, 7]");
        successInput("return [-1, 3265, -3] - [4, -6985, 0]");

        successInput("return [1.3, 0.0, -45.369] - [4, -6985, 0]");
        successInput("return [4, -6985, 0] - [1.3, 0.0, -45.369]");
        successInput("return [0.0, -2589.36452, 8957.2] - [1.3, 0.0, -45.369]");

        failureInputWith("return [true, false, true] - [8, 5, 7]", "Trying to subtract Bool[] with Int[]");
        failureInputWith("return [-1, 0, 547] - [true, false, true]", "Trying to subtract Int[] with Bool[]");
        failureInputWith("return [null, null, null] - [8, 5, 7]", "Trying to subtract Null[] with Int[]");
        failureInputWith("return [-1, 0, 547] - [null, null, null]", "Trying to subtract Int[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] - [8, 5, 7]", "Trying to subtract String[] with Int[]");
        failureInputWith("return [-1, 0, 547] - [\"oui\", \"\", \"123\"]", "Trying to subtract Int[] with String[]");

        failureInputWith("return [true, false, true] - [1.3, 0.0, -45.369]", "Trying to subtract Bool[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] - [true, false, true]", "Trying to subtract Float[] with Bool[]");
        failureInputWith("return [null, null, null] - [1.3, 0.0, -45.369]", "Trying to subtract Null[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] - [null, null, null]", "Trying to subtract Float[] with Null[]");
        failureInputWith("return [\"oui\", \"\", \"123\"] - [1.3, 0.0, -45.369]", "Trying to subtract String[] with Float[]");
        failureInputWith("return [1.3, 0.0, -45.369] - [\"oui\", \"\", \"123\"]", "Trying to subtract Float[] with String[]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testNumericArrayOperationMultipleDim() {
        // multi dim with int left and float right
        successInput("return [[-1, 1, 0], [15, -36, 789]] + [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]");
        successInput("return [[-1, 1, 0], [15, -36, 789]] - [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]");
        successInput("return [[-1, 1, 0], [15, -36, 789]] * [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]");
        successInput("return [[-1, 1, 0], [15, -36, 789]] / [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]");
        // multi dim with int right and float left
        successInput("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] + [[-1, 1, 0], [15, -36, 789]]");
        successInput("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] - [[-1, 1, 0], [15, -36, 789]]");
        successInput("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] * [[-1, 1, 0], [15, -36, 789]]");
        successInput("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] / [[-1, 1, 0], [15, -36, 789]]");
        // big multi dim
        successInput("return [[[[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]]] / [[[[-1, 1, 0], [15, -36, 789]]]]");

        // failures
        // add
        failureInputWith("return [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]] + [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to add String[] with Float[]");
        failureInputWith("return [1, 2] + [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to add Int[] with Float[][]");
        failureInputWith("return [1.0, 2.0] + [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to add Float[] with Float[][]");
        failureInputWith("return [1.0, 2.6] + [[3, -1, 0], [1, 2, 3]]",
            "Trying to add Float[] with Int[][]");

        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] + [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]]",
            "Trying to add Float[] with String[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] + [1, 2]",
            "Trying to add Array[] with Int[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] + [1.0, 2.0]",
            "Trying to add Array[] with Float[]");
        failureInputWith("return [[3, -1, 0], [1, 2, 3]] + [1.0, 2.6]",
            "Trying to add Array[] with Float[]");

        // subtract
        failureInputWith("return [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]] - [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to subtract String[] with Float[]");
        failureInputWith("return [1, 2] - [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to subtract Int[] with Float[][]");
        failureInputWith("return [1.0, 2.0] - [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to subtract Float[] with Float[][]");
        failureInputWith("return [1.0, 2.6] - [[3, -1, 0], [1, 2, 3]]",
            "Trying to subtract Float[] with Int[][]");

        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] - [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]]",
            "Trying to subtract Float[] with String[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] - [1, 2]",
            "Trying to subtract Array[] with Int[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] - [1.0, 2.0]",
            "Trying to subtract Array[] with Float[]");
        failureInputWith("return [[3, -1, 0], [1, 2, 3]] - [1.0, 2.6]",
            "Trying to subtract Array[] with Float[]");

        // multiply
        failureInputWith("return [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]] * [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to multiply String[] with Float[]");
        failureInputWith("return [1, 2] * [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to multiply Int[] with Float[][]");
        failureInputWith("return [1.0, 2.0] * [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to multiply Float[] with Float[][]");
        failureInputWith("return [1.0, 2.6] * [[3, -1, 0], [1, 2, 3]]",
            "Trying to multiply Float[] with Int[][]");

        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] * [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]]",
            "Trying to multiply Float[] with String[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] * [1, 2]",
            "Trying to multiply Array[] with Int[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] * [1.0, 2.0]",
            "Trying to multiply Array[] with Float[]");
        failureInputWith("return [[3, -1, 0], [1, 2, 3]] * [1.0, 2.6]",
            "Trying to multiply Array[] with Float[]");

        // divide
        failureInputWith("return [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]] / [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to divide String[] with Float[]");
        failureInputWith("return [1, 2] / [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to divide Int[] with Float[][]");
        failureInputWith("return [1.0, 2.0] / [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]]",
            "Trying to divide Float[] with Float[][]");
        failureInputWith("return [1.0, 2.6] / [[3, -1, 0], [1, 2, 3]]",
            "Trying to divide Float[] with Int[][]");

        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] / [[\"oui\", \"oui\", \"oui\"], [\"oui\", \"oui\", \"oui\"]]",
            "Trying to divide Float[] with String[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] / [1, 2]",
            "Trying to divide Array[] with Int[]");
        failureInputWith("return [[3.2, -1.3658, 0.0], [1.0, 2.0, 3.0]] / [1.0, 2.0]",
            "Trying to divide Array[] with Float[]");
        failureInputWith("return [[3, -1, 0], [1, 2, 3]] / [1.0, 2.6]",
            "Trying to divide Array[] with Float[]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testOtherBinary() {
        successInput("return true && false");
        successInput("return false && true");
        successInput("return true && true");
        successInput("return true || false");
        successInput("return false || true");
        successInput("return false || false");

        failureInputWith("return false || 1",
            "Attempting to perform binary logic on non-boolean type: Int");
        failureInputWith("return 2 || true",
            "Attempting to perform binary logic on non-boolean type: Int");

        successInput("return 1 + \"a\"");
        successInput("return \"a\" + 1");
        successInput("return \"a\" + true");

        successInput("return 1 == 1");
        successInput("return 1 == 2");
        successInput("return 1.0 == 1.0");
        successInput("return 1.0 == 2.0");
        successInput("return true == true");
        successInput("return false == false");
        successInput("return true == false");
        successInput("return 1 == 1.0");

        failureInputWith("return true == 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 == false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" == \"hi\"");
        successInput("return [1] == [1]");

        successInput("return 1 != 1");
        successInput("return 1 != 2");
        successInput("return 1.0 != 1.0");
        successInput("return 1.0 != 2.0");
        successInput("return true != true");
        successInput("return false != false");
        successInput("return true != false");
        successInput("return 1 != 1.0");

        failureInputWith("return true != 1", "Trying to compare incomparable types Bool and Int");
        failureInputWith("return 2 != false", "Trying to compare incomparable types Int and Bool");

        successInput("return \"hi\" != \"hi\"");
        successInput("return [1] != [1]");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testVarDecl() {
        successInput("var x: Int = 1; return x");
        successInput("var x: Float = 2.0; return x");

        successInput("var x: Int = 0; return x = 3");
        successInput("var x: String = \"0\"; return x = \"S\"");

        failureInputWith("var x: Int = true", "expected Int but got Bool");
        failureInputWith("return x + 1", "Could not resolve: x");
        failureInputWith("return x + 1; var x: Int = 2", "Variable used before declaration: x");

        // implicit conversions
        successInput("var x: Float = 1 ; x = 2");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testRootAndBlock () {
        successInput("return");
        successInput("return 1");
        successInput("return 1; return 2");

        successInput("print(\"a\")");
        successInput("print(\"a\" + 1)");
        successInput("print(\"a\"); print(\"b\")");

        successInput("{ print(\"a\"); print(\"b\") }");

        successInput(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testCalls() {
        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
            "return add(4, 7)");

        successInput(
            "struct Point { var x: Int; var y: Int }" +
            "return $Point(1, 2)");

        successInput("var str: String = null; return print(str + 1)");

        failureInputWith("return print(1)", "argument 0: expected String but got Int");
    }


    @Test
    public void testLaunch() {
        successInput(
            "fun add (a: Int, b: Int): Int { return a + b } " +
            "launch add(4, 6)"
        );
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess() {
        successInput("return [1][0]");
        successInput("return [1.0][0]");
        successInput("return [1, 2][1]");

        failureInputWith("return [1][true]", "Indexing an array using a non-Int-valued expression");

        // TODO make this legal?
        // successInput("[].length", 0L);

        successInput("return [1].length");
        successInput("return [1, 2].length");

        successInput("var array: Int[] = null; return array[0]");
        successInput("var array: Int[] = null; return array.length");

        successInput("var x: Int[] = [0, 1]; x[0] = 3; return x[0]");
        successInput("var x: Int[] = []; x[0] = 3; return x[0]");
        successInput("var x: Int[] = null; x[0] = 3");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = $P(1, 2);" +
            "p.y = 42;" +
            "return p.y");

        successInput(
            "struct P { var x: Int; var y: Int }" +
            "var p: P = null;" +
            "p.y = 42");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, true)",
            "argument 1: expected Int but got Bool");

        failureInputWith(
            "struct P { var x: Int; var y: Int }" +
            "return $P(1, 2).z",
            "Trying to access missing field z on struct P");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        successInput("if (true) return 1 else return 2");
        successInput("if (false) return 1 else return 2");
        successInput("if (false) return 1 else if (true) return 2 else return 3 ");
        successInput("if (false) return 1 else if (false) return 2 else return 3 ");

        successInput("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ");

        failureInputWith("if 1 return 1",
            "If statement with a non-boolean condition of type: Int");
        failureInputWith("while 1 return 1",
            "While statement with a non-boolean condition of type: Int");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testInference() {
        successInput("var array: Int[] = []");
        successInput("var array: String[] = []");
        successInput("fun use_array (array: Int[]) {} ; use_array([])");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testTypeAsValues() {
        successInput("struct S{} ; return \"\"+ S");
        successInput("struct S{} ; var type: Type = S ; return \"\"+ type");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        successInput("fun f(): Int { if (true) return 1 else return 2 } ; return f()");

        // TODO: would be nice if this pinpointed the if-statement as missing the return,
        //   not the whole function declaration
        failureInputWith("fun f(): Int { if (true) return 1 } ; return f()",
            "Missing return in function");
    }

    // ---------------------------------------------------------------------------------------------
}
