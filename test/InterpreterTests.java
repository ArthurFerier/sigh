import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

public final class InterpreterTests extends TestFixture {

    // TODO peeling

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", 42L);
        checkExpr("42.0", 42.0d);
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", 42L);
        checkExpr("[1, 2, 3]", new Object[]{1L, 2L, 3L});
        checkExpr("true", true);
        checkExpr("false", false);
        checkExpr("null", Null.INSTANCE);
        checkExpr("!false", true);
        checkExpr("!true", false);
        checkExpr("!!true", true);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", 3L);
        checkExpr("2 - 1", 1L);
        checkExpr("2 * 3", 6L);
        checkExpr("2 / 3", 0L);
        checkExpr("3 / 2", 1L);
        checkExpr("2 % 3", 2L);
        checkExpr("3 % 2", 1L);

        checkExpr("1.0 + 2.0", 3.0d);
        checkExpr("2.0 - 1.0", 1.0d);
        checkExpr("2.0 * 3.0", 6.0d);
        checkExpr("2.0 / 3.0", 2d / 3d);
        checkExpr("3.0 / 2.0", 3d / 2d);
        checkExpr("2.0 % 3.0", 2.0d);
        checkExpr("3.0 % 2.0", 1.0d);

        checkExpr("1 + 2.0", 3.0d);
        checkExpr("2 - 1.0", 1.0d);
        checkExpr("2 * 3.0", 6.0d);
        checkExpr("2 / 3.0", 2d / 3d);
        checkExpr("3 / 2.0", 3d / 2d);
        checkExpr("2 % 3.0", 2.0d);
        checkExpr("3 % 2.0", 1.0d);

        checkExpr("1.0 + 2", 3.0d);
        checkExpr("2.0 - 1", 1.0d);
        checkExpr("2.0 * 3", 6.0d);
        checkExpr("2.0 / 3", 2d / 3d);
        checkExpr("3.0 / 2", 3d / 2d);
        checkExpr("2.0 % 3", 2.0d);
        checkExpr("3.0 % 2", 1.0d);

        checkExpr("2 * (4-1) * 4.0 / 6 % (2+1)", 1.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericArrayOp () {
        // multiplication
        checkExpr("[1, 2, 7] * [8, 5, 7]", new Object[]{8L, 10L, 49L});
        checkExpr("[-1, 1, 0] * [3.2, -1.3658, 0.0]", new Object[]{-3.2, -1.3658, 0.0});
        checkExpr("[3.2, -1.3658, 0.0] * [-1, 1, 0]", new Object[]{-3.2, -1.3658, 0.0});
        checkExpr("[3.2, -1.3658, 0.0] * [0.0, 1.753, -1.654]", new Object[]{0.0, -2.3942474, -0.0});

        // division
        checkExpr("[1, 2, -12] / [8, 5, 7]", new Object[]{0L, 0L, -1L});
        checkExpr("[-1, 1, 0] / [3.2, -1.3658, 0.001]", new Object[]{-0.3125, -0.732171621027969, 0.0});
        checkExpr("[3.2, -1.3658, 21.3] / [-1, 1, 8963]", new Object[]{-3.2, -1.3658, 0.0023764364610063594});
        checkExpr("[3.2, -1.3658, 0.0] / [0.01, 1.753, -1.654]", new Object[]{320.0, -0.7791215059897318, -0.0});

        // addition
        checkExpr("[1, 2, -12] + [8, 5, 7]", new Object[]{9L, 7L, -5L});
        checkExpr("[-1, 1, 0] + [3.2, -1.3658, 0.001]", new Object[]{2.2, -0.3657999999999999, 0.001});
        checkExpr("[3.2, -1.3658, 21.3] + [-1, 1, 8963]", new Object[]{2.2, -0.3657999999999999, 8984.3});
        checkExpr("[3.2, -1.3658, 0.0] + [0.0, 1.753, -1.654]", new Object[]{3.2, 0.3872, -1.654});

        // subtraction
        checkExpr("[1, 2, -12] - [8, 5, 7]", new Object[]{-7L, -3L, -19L});
        checkExpr("[-1, 1, 0] - [3.2, -1.3658, 0.001]", new Object[]{-4.2, 2.3658, -0.001});
        checkExpr("[3.2, -1.3658, 21.3] - [-1, 1, 8963]", new Object[]{4.2, -2.3658, -8941.7});
        checkExpr("[3.2, -1.3658, 0.0] - [0.0, 1.753, -1.654]", new Object[]{3.2, -3.1188, 1.654});
    }

    @Test
    public void matricialProduct () {
        // Matricial product
        checkThrows("[[4.0, 5.0, 6.0], [7.0, 8.0, 9.0]] @ [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0], [7.0, 8.0, 9.0]]",
            AssertionError.class);
        checkExpr("[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0], [7.0, 8.0, 9.0]] @ [[1.0, 2.0, 3.0], [4.0, 5.0, 6.0], [7.0, 8.0, 9.0]]",
            new Object[][] {{30.0, 36.0, 42.0}, {66.0, 81.0, 96.0}, {102.0, 126.0, 150.0}});
        checkExpr("[[1.0, 2.0, 3.0]] @ [[4], [5], [6]]",
            new Object[][] {{32.0}});
    }

    @Test
    public void multiDimArrayOperations () {
        // Multi-dimensional arrays
        checkExpr("[[[15.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]] + " +
                "[[[14.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]]",
            new Object[][][]{{{29.0, 10.0, 14.0}, {28.0, 10.0, 14.0}}, {{10.0, 34.0, 46.0} , {-8.0, -20.0, -34.0}}});
        checkExpr("[[[15.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]] * " +
                "[[[14.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]]",
            new Object[][][]{{{210.0, 25.0, 49.0}, {196.0, 25.0, 49.0}}, {{25.0, 289.0, 529.0} , {16.0, 100.0, 289.0}}});
        checkExpr("[[[15.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]] - " +
                "[[[18.0, 7.0, 1.0], [3.0, 2.0, 7.0]], [[5.0, 17.0, 15.0] , [4.0, -12.0, -18.0]]]",
            new Object[][][]{{{-3.0, -2.0, 6.0}, {11.0, 3.0, 0.0}}, {{0.0, 0.0, 8.0} , {-8.0, 2.0, 1.0}}});
        checkExpr("[[[15.0, 5.0, 7.0], [14.0, 5.0, 7.0]], [[5.0, 17.0, 23.0] , [-4.0, -10.0, -17.0]]] / " +
                "[[[14.0, 5.0, 7.0], [14.0, 8.0, 7.0]], [[5.0, 17.0, 9.0] , [-6.0, 10.0, -23.0]]]",
            new Object[][][]{{{1.0714285714285714, 1.0, 1.0}, {1.0, 0.625, 1.0}}, {{1.0, 1.0, 2.5555555555555554}, {0.6666666666666666, -1.0, 0.7391304347826086}}});

        // Corner cases

        // Empty arrays
        checkThrows("[[]] + [[]]", AssertionError.class);
        checkThrows("[[[]]] - [[[]]]", AssertionError.class);
        checkThrows("[[]] / [[]]", AssertionError.class);
        checkThrows("[[[]]] * [[[]]]", AssertionError.class);

        // Division by 0
        checkExpr("[[-1.0, 1.0, 1.0], [3.0, 2.0, 8.0]] / [[3.0, -1.0, 0.0], [0.0, 1.0, 8.0]]",
            new Object[][]{{-0.3333333333333333, -1.0, Double.POSITIVE_INFINITY}, {Double.POSITIVE_INFINITY, 2.0, 1.0}});

    }

    @Test
    public void cornerCasesArrayOperation () {
        // division by 0
        checkThrows("[-1, 1, 1] / [3, -1, 0]", AssertionError.class);
        checkExpr("[-1, 1, 1] / [3.0, -1.0, 0.0]", new Object[]{-0.3333333333333333, -1.0, Double.POSITIVE_INFINITY});
        checkExpr("[-1.0, 1.0, 1.0] / [3.0, -1.0, 0.0]", new Object[]{-0.3333333333333333, -1.0, Double.POSITIVE_INFINITY});

        // operation on two arrays of different length and null length
        checkThrows("[-1, 1, 1] + [3.0, -1.0, 0.0, 0.0]", AssertionError.class);
        checkThrows("[-1, 1, 1, 13] + [3.0, -1.0, 0.0]", AssertionError.class);
        checkThrows("[] + []", AssertionError.class);
        checkThrows("[-1, 1, 1] - [3.0, -1.0, 0.0, 0.0]", AssertionError.class);
        checkThrows("[-1, 1, 1, 13] - [3.0, -1.0, 0.0]", AssertionError.class);
        checkThrows("[] - []", AssertionError.class);
        checkThrows("[-1, 1, 1] * [3.0, -1.0, 0.0, 0.0]", AssertionError.class);
        checkThrows("[-1, 1, 1, 13] * [3.0, -1.0, 0.0]", AssertionError.class);
        checkThrows("[] * []", AssertionError.class);
        checkThrows("[-1, 1, 1] / [3.0, -1.0, 0.0, 0.0]", AssertionError.class);
        checkThrows("[-1, 1, 1, 13] / [3.0, -1.0, 0.0]", AssertionError.class);
        checkThrows("[] / []", AssertionError.class);
    }


    // ---------------------------------------------------------------------------------------------

    @Test
    public void testOtherBinary () {
        checkExpr("true  && true",  true);
        checkExpr("true  || true",  true);
        checkExpr("true  || false", true);
        checkExpr("false || true",  true);
        checkExpr("false && true",  false);
        checkExpr("true  && false", false);
        checkExpr("false && false", false);
        checkExpr("false || false", false);

        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");
        checkExpr("\"a\" + true", "atrue");

        checkExpr("1 == 1", true);
        checkExpr("1 == 2", false);
        checkExpr("1.0 == 1.0", true);
        checkExpr("1.0 == 2.0", false);
        checkExpr("true == true", true);
        checkExpr("false == false", true);
        checkExpr("true == false", false);
        checkExpr("1 == 1.0", true);
        checkExpr("[1] == [1]", false);

        checkExpr("1 != 1", false);
        checkExpr("1 != 2", true);
        checkExpr("1.0 != 1.0", false);
        checkExpr("1.0 != 2.0", true);
        checkExpr("true != true", false);
        checkExpr("false != false", false);
        checkExpr("true != false", true);
        checkExpr("1 != 1.0", false);

        checkExpr("\"hi\" != \"hi2\"", true);
        checkExpr("[1] != [1]", true);

         // test short circuit
        checkExpr("true || print(\"x\") == \"y\"", true, "");
        checkExpr("false && print(\"x\") == \"y\"", false, "");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; return x", 1L);
        check("var x: Float = 2.0; return x", 2d);

        check("var x: Int = 0; return x = 3", 3L);
        check("var x: String = \"0\"; return x = \"S\"", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testRootAndBlock () {
        rule = grammar.root;
        check("return", null);
        check("return 1", 1L);
        check("return 1; return 2", 1L);

        check("print(\"a\")", null, "a\n");
        check("print(\"a\" + 1)", null, "a1\n");
        check("print(\"a\"); print(\"b\")", null, "a\nb\n");

        check("{ print(\"a\"); print(\"b\") }", null, "a\nb\n");

        check(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)",
            null, "1\n2\n1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {
        check(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)",
            11L);

        HashMap<String, Object> point = new HashMap<>();
        point.put("x", 1L);
        point.put("y", 2L);

        check(
            "struct Point { var x: Int; var y: Int }" +
                "return $Point(1, 2)",
            point);

        check("var str: String = null; return print(str + 1)", "null1", "null1\n");
    }

    // ---------------------------------------------------------------------------------------------

    /*@Test
    public void testLaunchSpeed () {
        rule = grammar.root;

        // this tests verify that the execution really is concurrent,
        // if not, the two programs should have approximatively the same execution time
        long start = System.currentTimeMillis();
        check(
            "fun addUpTo1000000 (a: Int): Int { while a < 1000000 { a = a + 1 } return a } " +
                "launch addUpTo1000000(1)" +
                "addUpTo1000000(1)",
            null);
        long end = System.currentTimeMillis();
        long timeElapsedWithLaunch = end - start; // in milliseconds

        start = System.currentTimeMillis();
        check(
            "fun addUpTo1000000 (a: Int): Int { while a < 1000000 { a = a + 1 } return a } " +
                "addUpTo1000000(1)" +
                "addUpTo1000000(1)",
            null);
        end = System.currentTimeMillis();
        long timeElapsedNoLaunch = end - start; // in milliseconds
        assertTrue(timeElapsedWithLaunch * 1.5 <= timeElapsedNoLaunch);
    }*/

    @Test
    public void testLaunchGlobalVariables () {
        rule = grammar.root;

        // checks that the thread correctly can set
        // a global variable that exists outside the scope of the thread
        check(
            "var threadedVar: Int = 0" +
                "fun add1000() {" +
                "    var i : Int = 0" +
                "    while  i < 1000 {" +
                "        protect : {" +
                "            threadedVar = threadedVar + 1" +
                "        }" +
                "        i = i + 1" +
                "    }" +
                "}" +
                "launch add1000()" +
                "var i : Int = 0" +
                "while  i < 100000 {" +
                "    i = i + 1" +
                "}" +
                "return print(\"\" + threadedVar)",
                "1000"
            );

        // checks that the thread don't update the global variable if redifined in the thread scope
        check(
            "var threadedVar: Int = 0" +
                "var i : Int = 0" +
                "fun add1000() {" +
                "    var i : Int = 0" +
                "   while  i < 1000 {" +
                "        protect : {" +
                "            threadedVar = threadedVar + 1" +
                "        }" +
                "        i = i + 1" +
                "    }" +
                "}" +
                "launch add1000()" +
                "var a : Int = 0" +
                "while  a < 100000 {" +
                "    a = a + 1" +
                "}" +
                "return print(\"\" + i)",
            "0"
        );

        // verify that a thread can call a funcion defined outside the scope of the threaded function
        check(
            "var threadedVar: Int = 0" +
                "var i : Int = 0" +
                "fun functionCore() {" +
                "   var i : Int = 0" +
                "   while  i < 1000 {" +
                "        protect : {" +
                "            threadedVar = threadedVar + 1" +
                "        }" +
                "        i = i + 1" +
                "    }" +
                "}" +
                "fun add1000() {" +
                "   functionCore()" +
                "}" +
                "launch add1000()" +
                "var a : Int = 0" +
                "while  a < 100000 {" +
                "    a = a + 1" +
                "}" +
                "return print(\"\" + threadedVar)",
            "1000"
        );
    }

    @Test
    public void testLaunchConcurrent () {

        rule = grammar.root;

        // test that can finish only if the program is concurrent (use of the threads)
        check(
            "var check : Bool = false " +
                "var globalVar : Int = 0" +
                "fun waitingVarCheckToUpdate() : Int {" +
                "    while globalVar != 1000 { }" +
                "    check = true" +
                "    return 1" +
                "}" +
                "fun updateGlobalVar() : Int {" +
                "    var i : Int = 0" +
                "    while  i < 1000 {" +
                "        globalVar = globalVar + 1" +
                "        i = i + 1" +
                "    }" +
                "    return 1" +
                "}" +
                "launch var firstThread : Int = waitingVarCheckToUpdate()" +
                "launch var secondThread : Int = updateGlobalVar()" +
                "wait(firstThread)" +
                "return print(\"\" + check)",
            "true"
        );
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testWait () {
        rule = grammar.root;

        // checking that the wait function correctly wait for
        // the variable to be initialized before continuing the main execution
        check(
            "fun addUpTo100000 (a: Int): Int { while a < 100000 { a = a + 1 } return a } " +
                "launch var b : Int = addUpTo100000(1)" +
                "return b",
            null);
        check(
            "fun addUpTo1000 (a: Int): Int { while a < 10000 { a = a + 1 } return a } " +
                "launch var b : Int = addUpTo1000(1)" +
                "wait(b)" +
                "return b",
            10000L);


        // test with multiple waits, and in disorder
        check(
            "var threadedVar: Int = 0" +
                "fun add1000(): Int {" +
                "        while  threadedVar < 1000 {" +
                "            if (threadedVar < 1000) {" +
                "                threadedVar = threadedVar + 1" +
                "            }" +
                "        }" +
                "        return 1" +
                "}" +
                "launch var protect1: Int = add1000()" +
                "launch var protect2 : Int = add1000()" +
                "launch var protect3 : Int = add1000()" +
                "launch var protect4 : Int = add1000()" +
                "wait(protect3)" +
                "wait(protect2)" +
                "wait(protect4)" +
                "wait(protect1)" +
                "return print(\"\" + protect1 + protect2 + protect3 + protect4)",
                "1111"
        );
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        checkExpr("[1][0]", 1L);
        checkExpr("[1.0][0]", 1d);
        checkExpr("[1, 2][1]", 2L);

        // TODO check that this fails (& maybe improve so that it generates a better message?)
        // or change to make it legal (introduce a top type, and make it a top type array if thre
        // is no inference context available)
        // checkExpr("[].length", 0L);
        checkExpr("[1].length", 1L);
        checkExpr("[1, 2].length", 2L);

        checkThrows("var array: Int[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Int[] = null; return array.length", NullPointerException.class);

        check("var x: Int[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Int[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Int[] = null; x[0] = 3",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "return $P(1, 2).y",
            2L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "return p.y",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = $P(1, 2);" +
                "p.y = 42;" +
                "return p.y",
            42L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "p.y = 42",
            NullPointerException.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        check("if (true) return 1 else return 2", 1L);
        check("if (false) return 1 else return 2", 2L);
        check("if (false) return 1 else if (true) return 2 else return 3 ", 2L);
        check("if (false) return 1 else if (false) return 2 else return 3 ", 3L);

        check("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ", null, "0\n1\n2\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testInference () {
        check("var array: Int[] = []", null);
        check("var array: String[] = []", null);
        check("fun use_array (array: Int[]) {} ; use_array([])", null);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testTypeAsValues () {
        check("struct S{} ; return \"\"+ S", "S");
        check("struct S{} ; var type: Type = S ; return \"\"+ type", "S");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        check("fun f(): Int { if (true) return 1 else return 2 } ; return f()", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testProtect() {
        rule = grammar.root;

        check("var threadedVar: Int = 0" +
                "fun add1000(): Int {" +
                "        while  threadedVar < 1000 {" +
                "            protect : {" +
                "                if (threadedVar < 1000) {" +
                "                    threadedVar = threadedVar + 1" +
                "                }" +
                "            }" +
                "        }" +
                "        return 1" +
                "}" +
                "launch var returned : Int = add1000()" +
                "launch var returned2 : Int = add1000()" +
                "launch var returned3 : Int = add1000()" +
                "launch var returned4 : Int = add1000()" +
                "wait(returned)" +
                "wait(returned2)" +
                "wait(returned3)" +
                "wait(returned4)" +
                "print(\"ThreadedVar : \" + threadedVar)",
            null, "ThreadedVar : 1000\n");


        check("var threadedVar: Int = 0" +
                "fun add1000() {" +
                "        var i: Int = 0" +
                "        while i < 1000 {" +
                "            threadedVar = threadedVar+1" +
                "            i = i +1" +
                "        }" +
                "}" +
                "launch add1000()" +
                "launch add1000()" +
                "launch add1000()" +
                "launch add1000()" +
                "var a: Int = 0" +
                "while a < 100000 {" +
                "    a = a +1" +
                "}" +
                "var boolean : Bool = threadedVar < 4000" +
                "print(\"\" + boolean)",
            null, "true\n");
    }


    // ---------------------------------------------------------------------------------------------

    // NOTE(norswap): Not incredibly complete, but should cover the basics.
}
