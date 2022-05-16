import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.BinaryOperator.*;

public class GrammarTests extends AutumnTestFixture {
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    // ---------------------------------------------------------------------------------------------

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        rule = grammar.expression;

        successExpect("42", intlit(42));
        successExpect("42.0", floatlit(42d));
        successExpect("\"hello\"", new StringLiteralNode(null, "hello"));
        successExpect("(42)", new ParenthesizedNode(null, intlit(42)));
        successExpect("[1, 2, 3]", new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))));
        successExpect("true", new ReferenceNode(null, "true"));
        successExpect("false", new ReferenceNode(null, "false"));
        successExpect("null", new ReferenceNode(null, "null"));
        successExpect("!false", new UnaryExpressionNode(null, UnaryOperator.NOT, new ReferenceNode(null, "false")));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        successExpect("1 + 2", new BinaryExpressionNode(null, intlit(1), ADD, intlit(2)));
        successExpect("2 - 1", new BinaryExpressionNode(null, intlit(2), SUBTRACT,  intlit(1)));
        successExpect("2 * 3", new BinaryExpressionNode(null, intlit(2), MULTIPLY, intlit(3)));
        successExpect("2 / 3", new BinaryExpressionNode(null, intlit(2), DIVIDE, intlit(3)));
        successExpect("2 % 3", new BinaryExpressionNode(null, intlit(2), REMAINDER, intlit(3)));

        successExpect("1.0 + 2.0", new BinaryExpressionNode(null, floatlit(1), ADD, floatlit(2)));
        successExpect("2.0 - 1.0", new BinaryExpressionNode(null, floatlit(2), SUBTRACT, floatlit(1)));
        successExpect("2.0 * 3.0", new BinaryExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3)));
        successExpect("2.0 / 3.0", new BinaryExpressionNode(null, floatlit(2), DIVIDE, floatlit(3)));
        successExpect("2.0 % 3.0", new BinaryExpressionNode(null, floatlit(2), REMAINDER, floatlit(3)));

        successExpect("2 * (4-1) * 4.0 / 6 % (2+1)", new BinaryExpressionNode(null,
            new BinaryExpressionNode(null,
                new BinaryExpressionNode(null,
                    new BinaryExpressionNode(null,
                        intlit(2),
                        MULTIPLY,
                        new ParenthesizedNode(null, new BinaryExpressionNode(null,
                            intlit(4),
                            SUBTRACT,
                            intlit(1)))),
                    MULTIPLY,
                    floatlit(4d)),
                DIVIDE,
                intlit(6)),
            REMAINDER,
            new ParenthesizedNode(null, new BinaryExpressionNode(null,
                intlit(2),
                ADD,
                intlit(1)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess () {
        rule = grammar.expression;
        successExpect("[1][0]", new ArrayAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), intlit(0)));
        successExpect("[1].length", new FieldAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), "length"));
        successExpect("p.x", new FieldAccessNode(null, new ReferenceNode(null, "p"), "x"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclarations() {
        rule = grammar.statement;

        successExpect("var x: Int = 1", new VarDeclarationNode(null,
            "x", new SimpleTypeNode(null, "Int"), intlit(1)));

        successExpect("struct P {}", new StructDeclarationNode(null, "P", asList()));

        successExpect("struct P { var x: Int; var y: Int }",
            new StructDeclarationNode(null, "P", asList(
                new FieldDeclarationNode(null, "x", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "y", new SimpleTypeNode(null, "Int")))));

        successExpect("fun f (x: Int): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testStatements() {
        rule = grammar.statement;

        successExpect("return", new ReturnNode(null, null));
        successExpect("return 1", new ReturnNode(null, intlit(1)));
        successExpect("print(1)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1)))));
        successExpect("{ return }", new BlockNode(null, asList(new ReturnNode(null, null))));


        successExpect("if true return 1 else return 2", new IfNode(null, new ReferenceNode(null, "true"),
            new ReturnNode(null, intlit(1)),
            new ReturnNode(null, intlit(2))));

        successExpect("if false return 1 else if true return 2 else return 3 ",
            new IfNode(null, new ReferenceNode(null, "false"),
                new ReturnNode(null, intlit(1)),
                new IfNode(null, new ReferenceNode(null, "true"),
                    new ReturnNode(null, intlit(2)),
                    new ReturnNode(null, intlit(3)))));

        successExpect("while 1 < 2 { return } ", new WhileNode(null,
            new BinaryExpressionNode(null, intlit(1), LOWER, intlit(2)),
            new BlockNode(null, asList(new ReturnNode(null, null)))));
    }

    @Test
    public void testWait() {
        rule = grammar.statement;
        ReferenceNode ref = new ReferenceNode(null, "a");
        ArrayList<ReferenceNode> a = new ArrayList<>();
        a.add(ref);
        successExpect("wait(a)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "wait"), a)));
    }

    @Test public void testLaunch() {
        rule = grammar.statement;

        // returnArg is supposed to be a function that return the argument given
        // it is supposed to return an int when an integer is passed, a String when String ......

        successExpect("return launch returnArg(1)",
             new ReturnNode(null,
                new LaunchNode(null,
                    new FunCallNode(null, new ReferenceNode(null, "returnArg"), asList(intlit(1)))
                )
            )
        );


        successExpect("launch var x: Int = returnArg(1)",
            new LaunchStateNode(null,
                new VarDeclarationNode(null, "x", new SimpleTypeNode(null, "Int"),
                    new FunCallNode(null, new ReferenceNode(null, "returnArg"), asList(intlit(1)))
                )
            )
        );
        successExpect("launch var x: String = returnArg(\"hello world\")",
            new LaunchStateNode(null,
                new VarDeclarationNode(null, "x", new SimpleTypeNode(null, "String"),
                    new FunCallNode(null,
                        new ReferenceNode(null, "returnArg"),
                        asList(new StringLiteralNode(null, "hello world"))
                    )
                )
            )
        );
        successExpect("launch var x: Bool = returnArg(true)",
            new LaunchStateNode(null,
                new VarDeclarationNode(null, "x", new SimpleTypeNode(null, "Bool"),
                    new FunCallNode(null,
                        new ReferenceNode(null, "returnArg"),
                        asList(new ReferenceNode(null, "true"))
                    )
                )
            )
        );
        successExpect("launch var x: Int[] = returnArg([1, 2, 3])",
            new LaunchStateNode(null,
                new VarDeclarationNode(null, "x",
                    new ArrayTypeNode(null, new SimpleTypeNode(null, "Int")),
                    new FunCallNode(null,
                        new ReferenceNode(null, "returnArg"),
                        asList(new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))))
                    )
                )
            )
        );


        successExpect("launch nonReturningFun(1)",
            new ExpressionStatementNode(null,
                new LaunchNode(null,
                    new FunCallNode(null,
                        new ReferenceNode(null, "nonReturningFun"), asList(intlit(1))
                    )
                )
            )
        );

        // The LaunchNode can only be followed directly by a FuncallNode
        failure("launch !returnTrue()");
        // the LaunchStateNode can only be followed directly by a VarDeclarationNode
        failure("launch {var x: String = '3'}");
        failure("launch struct Pair {" +
            "    var a: Int" +
            "    var b: Int" +
            "}");
    }

    // ---------------------------------------------------------------------------------------------
    
    @Test public void testProtect() {
        rule = grammar.statement;

        successExpect("protect: {" +
            "print(1)}",
            new ProtectBlockNode(null,
                new BlockNode(null, asList(
                    new ExpressionStatementNode(null,
                        new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1))))
                )), null //new ReentrantLock()
            ));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testMatricialProduct() {
        rule = grammar.expression;

        successExpect("[[1.0, 2.0, 3.0]] @ [[1.0, 2.0, 3.0]]",
            new BinaryExpressionNode(null,
                new ArrayLiteralNode(null, asList(new ArrayLiteralNode(null,
                    asList(floatlit(1.0), floatlit(2.0), floatlit(3.0))))),
                MAT_PRODUCT,
                new ArrayLiteralNode(null, asList(new ArrayLiteralNode(null,
                    asList(floatlit(1.0), floatlit(2.0), floatlit(3.0))))))
            );

    }
}
