package norswap.sigh.interpreter;

import norswap.sigh.ast.*;
import norswap.sigh.scopes.DeclarationContext;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Reactor;
import norswap.utils.Util;
import norswap.utils.exceptions.Exceptions;
import norswap.utils.exceptions.NoStackException;
import norswap.utils.visitors.ValuedVisitor;
//import org.graalvm.compiler.graph.spi.Canonicalizable.Binary;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.coIterate;
import static norswap.utils.Vanilla.map;

/**
 * Implements a simple but inefficient interpreter for Sigh.
 *
 * <h2>Limitations</h2>
 * <ul>
 *     <li>The compiled code currently doesn't support closures (using variables in functions that
 *     are declared in some surroudning scopes outside the function). The top scope is supported.
 *     </li>
 * </ul>
 *
 * <p>Runtime value representation:
 * <ul>
 *     <li>{@code Int}, {@code Float}, {@code Bool}: {@link Long}, {@link Double}, {@link Boolean}</li>
 *     <li>{@code String}: {@link String}</li>
 *     <li>{@code null}: {@link Null#INSTANCE}</li>
 *     <li>Arrays: {@code Object[]}</li>
 *     <li>Structs: {@code HashMap<String, Object>}</li>
 *     <li>Functions: the corresponding {@link DeclarationNode} ({@link FunDeclarationNode} or
 *     {@link SyntheticDeclarationNode}), excepted structure constructors, which are
 *     represented by {@link Constructor}</li>
 *     <li>Types: the corresponding {@link StructDeclarationNode}</li>
 * </ul>
 */
public final class Interpreter
{
    // ---------------------------------------------------------------------------------------------

    private final ValuedVisitor<SighNode, Object> visitor = new ValuedVisitor<>();
    private final Reactor reactor;
    private ScopeStorage storage = null;
    private RootScope rootScope;
    private ScopeStorage rootStorage;

    private ExecutorService executorService;
    private ReentrantLock l;

    // ---------------------------------------------------------------------------------------------

    public Interpreter (Reactor reactor) {
        this.reactor = reactor;

        // expressions
        visitor.register(IntLiteralNode.class,           this::intLiteral);
        visitor.register(FloatLiteralNode.class,         this::floatLiteral);
        visitor.register(StringLiteralNode.class,        this::stringLiteral);
        visitor.register(ReferenceNode.class,            this::reference);
        visitor.register(ConstructorNode.class,          this::constructor);
        visitor.register(ArrayLiteralNode.class,         this::arrayLiteral);
        visitor.register(ParenthesizedNode.class,        this::parenthesized);
        visitor.register(FieldAccessNode.class,          this::fieldAccess);
        visitor.register(ArrayAccessNode.class,          this::arrayAccess);
        visitor.register(FunCallNode.class,              this::funCall);
        visitor.register(LaunchNode.class,               this::launchCall);
        visitor.register(LaunchStateNode.class,          this::launchStateCall);
        visitor.register(UnaryExpressionNode.class,      this::unaryExpression);
        visitor.register(BinaryExpressionNode.class,     this::binaryExpression);
        visitor.register(ProtectBlockNode.class,         this::protectedBlock);
        visitor.register(AssignmentNode.class,           this::assignment);

        // statement groups & declarations
        visitor.register(RootNode.class,                 this::root);
        visitor.register(BlockNode.class,                this::block);
        visitor.register(VarDeclarationNode.class,       this::varDecl);
        // no need to visitor other declarations! (use fallback)

        // statements
        visitor.register(ExpressionStatementNode.class,  this::expressionStmt);
        visitor.register(IfNode.class,                   this::ifStmt);
        visitor.register(WhileNode.class,                this::whileStmt);
        visitor.register(ReturnNode.class,               this::returnStmt);

        visitor.registerFallback(node -> null);
    }

    // ---------------------------------------------------------------------------------------------

    public Object interpret (SighNode root) {
        try {
            l = new ReentrantLock();
            return run(root);
        } catch (PassthroughException e) {
            throw Exceptions.runtime(e.getCause());
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object run (SighNode node) {
        try {
            return visitor.apply(node);
        } catch (InterpreterException | Return | PassthroughException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterpreterException("exception while executing " + node, e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to implement the control flow of the return statement.
     */
    private static class Return extends NoStackException {
        final Object value;
        private Return (Object value) {
            this.value = value;
        }
    }

    // ---------------------------------------------------------------------------------------------

    private <T> T get(SighNode node) {
        return cast(run(node));
    }

    // ---------------------------------------------------------------------------------------------

    private Long intLiteral (IntLiteralNode node) {
        return node.value;
    }

    private Double floatLiteral (FloatLiteralNode node) {
        return node.value;
    }

    private String stringLiteral (StringLiteralNode node) {
        return node.value;
    }

    // ---------------------------------------------------------------------------------------------

    private Object parenthesized (ParenthesizedNode node) {
        return get(node.expression);
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] arrayLiteral (ArrayLiteralNode node) {
        return map(node.components, new Object[0], visitor);
    }

    // ---------------------------------------------------------------------------------------------

    private Object binaryExpression (BinaryExpressionNode node)
    {
        Type leftType  = reactor.get(node.left, "type");
        Type rightType = reactor.get(node.right, "type");

        // Cases where both operands should not be evaluated.
        switch (node.operator) {
            case OR:  return booleanOp(node, false);
            case AND: return booleanOp(node, true);
            case MAT_PRODUCT: return matricialProduct(node); // We treat this case apart
        }

        Object left  = get(node.left);
        Object right = get(node.right);

        if (node.operator == BinaryOperator.ADD
            && (leftType instanceof StringType || rightType instanceof StringType))
            return convertToString(left) + convertToString(right);

        boolean floating = leftType instanceof FloatType || rightType instanceof FloatType;
        boolean numeric  = floating || leftType instanceof IntType;
        boolean array = leftType instanceof ArrayType && rightType instanceof ArrayType;


        if (numeric)
            return numericOp(node, floating, (Number) left, (Number) right);
        if (array) {

            // diving into the multi dimension to find the type of the array
            while ((((ArrayType) leftType).componentType instanceof ArrayType)) {
                leftType = ((ArrayType) leftType).componentType;
            }
            while ((((ArrayType) rightType).componentType instanceof ArrayType)) {
                rightType = ((ArrayType) rightType).componentType;
            }
            boolean arrayOfFloatLeft = (((ArrayType) leftType).componentType == FloatType.INSTANCE);
            boolean arrayOfFloatRight = (((ArrayType) rightType).componentType == FloatType.INSTANCE);

            Object[] nodeLeft = getNonNullArray(node.left);
            Object[] noderight =  getNonNullArray(node.right);

            if (node.operator == BinaryOperator.MULTIPLY || node.operator == BinaryOperator.DIVIDE ||
                node.operator == BinaryOperator.ADD || node.operator == BinaryOperator.SUBTRACT)
                return arrayOp(node);
        }

        switch (node.operator) {
            case EQUALITY:
                return  leftType.isPrimitive() ? left.equals(right) : left == right;
            case NOT_EQUALS:
                return  leftType.isPrimitive() ? !left.equals(right) : left != right;
        }

        throw new Error("should not reach here");
    }


    // ---------------------------------------------------------------------------------------------

    private boolean booleanOp (BinaryExpressionNode node, boolean isAnd)
    {
        boolean left = get(node.left);
        return isAnd
            ? left && (boolean) get(node.right)
            : left || (boolean) get(node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private Object numericOp
        (BinaryExpressionNode node, boolean floating, Number left, Number right)
    {
        long ileft, iright;
        double fleft, fright;

        if (floating) {
            fleft  = left.doubleValue();
            fright = right.doubleValue();
            ileft = iright = 0;
        } else {
            ileft  = left.longValue(); // todo : left is null
            iright = right.longValue();
            fleft = fright = 0;
        }

        Object result;
        if (floating)
            switch (node.operator) {
                case MULTIPLY:      return fleft *  fright;
                case DIVIDE:        return fleft /  fright;
                case REMAINDER:     return fleft %  fright;
                case ADD:           return fleft +  fright;
                case SUBTRACT:      return fleft -  fright;
                case GREATER:       return fleft >  fright;
                case LOWER:         return fleft <  fright;
                case GREATER_EQUAL: return fleft >= fright;
                case LOWER_EQUAL:   return fleft <= fright;
                case EQUALITY:      return fleft == fright;
                case NOT_EQUALS:    return fleft != fright;
                default:
                    throw new Error("should not reach here");
            }
        else
            switch (node.operator) {
                case MULTIPLY:      return ileft *  iright;
                case DIVIDE:        return ileft /  iright;
                case REMAINDER:     return ileft %  iright;
                case ADD:           return ileft +  iright;
                case SUBTRACT:      return ileft -  iright;
                case GREATER:       return ileft >  iright;
                case LOWER:         return ileft <  iright;
                case GREATER_EQUAL: return ileft >= iright;
                case LOWER_EQUAL:   return ileft <= iright;
                case EQUALITY:      return ileft == iright;
                case NOT_EQUALS:    return ileft != iright;
                default:
                    throw new Error("should not reach here");
            }
    }

    // ---------------------------------------------------------------------------------------------

    private Object matricialProduct (BinaryExpressionNode node) {
        Object[] ar1 = getNonNullArray(node.left);
        Object[] ar2 = getNonNullArray(node.right);
        Object[] first1 = (Object[]) ar1[0];
        Object[] first2 = (Object[]) ar2[0];
        if (ar1.length != first2.length)
            throw new Error("Number of lines of array1 : " + ar1.length + " is different of number of columns of array2 : " + first2.length);

        Object[][] ar2DA = new Object[ar1.length][first1.length];
        Object[][] ar2DB = new Object[ar2.length][first2.length];
        for (int i = 0; i < ar1.length; i++) {
            ar2DA[i] = (Object[]) ar1[i];
        }
        for (int i = 0; i < ar2.length; i++) {
            ar2DB[i] = (Object[]) ar2[i];
        }
        return multiplyMatrices(ar2DA, ar2DB);
    }

    private Object[][] multiplyMatrices(Object[][] firstMatrix, Object[][] secondMatrix) {
        Object[][] result = new Double[firstMatrix.length][secondMatrix[0].length];
        Object o = firstMatrix[0][0];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(firstMatrix, secondMatrix, row, col);
            }
        }

        return result;
    }

    private Object multiplyMatricesCell(Object[][] firstMatrix, Object[][] secondMatrix, int row, int col) {
        double cell = 0.0;
        for (int i = 0; i < secondMatrix.length; i++) {
            if (firstMatrix[row][i] instanceof Double) {
                if (secondMatrix[i][col] instanceof Double)
                    cell += (Double) firstMatrix[row][i] * (Double) secondMatrix[i][col];
                else if (secondMatrix[i][col] instanceof Long)
                    cell += (Double) firstMatrix[row][i] * ((Long) secondMatrix[i][col]).doubleValue();
            }
            else if (firstMatrix[row][i] instanceof Long) {
                if (secondMatrix[i][col] instanceof Double)
                    cell += ((Long) firstMatrix[row][i]).doubleValue() * (Double) secondMatrix[i][col];
                else if (secondMatrix[i][col] instanceof Long)
                    cell += ((Long) firstMatrix[row][i]).doubleValue() * ((Long) secondMatrix[i][col]).doubleValue();
            }
        }
        return cell;
    }

    public Object arrayOp (BinaryExpressionNode node) {
        Object[] ar1 = getNonNullArray(node.left);
        Object[] ar2 = getNonNullArray(node.right);
        return computeArrayExpression(ar1, ar2, node.operator);
    }

    private Object computeArrayExpression(Object[] ar1, Object[] ar2, BinaryOperator operator) {
        // verifying that the 2 arrays have the same length
        if (ar1.length != ar2.length) {
            throw new Error("The two arrays must have the same length: array1.length is " + ar1.length + " and array2.length is " + ar2.length);
        }

        /*if (ar1.length == 0)
            return new Object[0];*/
        if (ar1.length == 0) {
            throw new Error("No operations allowed on empty arrays"); // TODO, changer
        }

        Object[] res = new Object[ar1.length];
        if (ar1[0].getClass().isArray() && ar2[0].getClass().isArray()) {
            for (int i = 0; i < ar1.length; i++) {
                // Recursive call to deal with multi-dimensional array
                res[i] = computeArrayExpression((Object[]) ar1[i], (Object[]) ar2[i], operator);
            }
            return res;
        }

        boolean floatLeft = ar1[0] instanceof Double;
        boolean floatRight = ar2[0] instanceof Double;
        switch (operator) {
            case ADD:
                if (floatLeft || floatRight) {
                    Double[] resultD = new Double[ar1.length];
                    if (floatLeft && floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] + (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else if (floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar2[i] + ((Number) ar1[i]).doubleValue();
                        }
                        return resultD;
                    }
                    else {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] + ((Number) ar2[i]).doubleValue();
                        }
                        return resultD;
                    }
                }
                else if (ar1[0] instanceof Long && ar2[0] instanceof Long) {
                    Long[] resultL = new Long[ar1.length];
                    for (int i = 0; i < ar1.length; i++) {
                        resultL[i] = (Long) ar1[i] + (Long) ar2[i];
                    }
                    return resultL;
                }
            case SUBTRACT:
                if (floatLeft || floatRight) {
                    Double[] resultD = new Double[ar1.length];
                    if (floatLeft && floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] - (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else if (floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = ((Number) ar1[i]).doubleValue() - (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] - ((Number) ar2[i]).doubleValue();
                        }
                        return resultD;
                    }
                }
                else if (ar1[0] instanceof Long && ar2[0] instanceof Long) {
                    Long[] resultL = new Long[ar1.length];
                    for (int i = 0; i < ar1.length; i++) {
                        resultL[i] = (Long) ar1[i] - (Long) ar2[i];
                    }
                    return resultL;
                }
            case MULTIPLY:
                if (floatLeft || floatRight) {
                    Double[] resultD = new Double[ar1.length];
                    if (floatLeft && floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] * (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else if (floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar2[i] * ((Number) ar1[i]).doubleValue();
                        }
                        return resultD;
                    }
                    else {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] * ((Number) ar2[i]).doubleValue();
                        }
                        return resultD;
                    }
                }
                else if (ar1[0] instanceof Long && ar2[0] instanceof Long) {
                    Long[] resultL = new Long[ar1.length];
                    for (int i = 0; i < ar1.length; i++) {
                        resultL[i] = (Long) ar1[i] * (Long) ar2[i];
                    }
                    return resultL;
                }
            case DIVIDE:
                if (floatLeft || floatRight) {
                    Double[] resultD = new Double[ar1.length];
                    if (floatLeft && floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] / (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else if (floatRight) {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = ((Number) ar1[i]).doubleValue() / (Double) ar2[i];
                        }
                        return resultD;
                    }
                    else {
                        for (int i = 0; i < ar1.length; i++) {
                            resultD[i] = (Double) ar1[i] / ((Number) ar2[i]).doubleValue();
                        }
                        return resultD;
                    }
                }
                else if (ar1[0] instanceof Long && ar2[0] instanceof Long) {
                    Long[] resultL = new Long[ar1.length];
                    for (int i = 0; i < ar1.length; i++) {
                        resultL[i] = (Long) ar1[i] / (Long) ar2[i];
                    }
                    return resultL;
                }
            default: throw new Error("Operand not supported, use among : + ; - ; * ; / or types of array1 and array2 are different from :" +
                "Float - Integer / Integer - Float / Float - Float / Integer - Integer");
        }
    }

    // ---------------------------------------------------------------------------------------------

    public Object assignment (AssignmentNode node)
    {
        if (node.left instanceof ReferenceNode) {
            Scope scope = reactor.get(node.left, "scope");
            String name = ((ReferenceNode) node.left).name;
            Object rvalue = get(node.right);
            assign(scope, name, rvalue, reactor.get(node, "type"));
            return rvalue;
        }

        if (node.left instanceof ArrayAccessNode) {
            ArrayAccessNode arrayAccess = (ArrayAccessNode) node.left;
            Object[] array = getNonNullArray(arrayAccess.array);
            int index = getIndex(arrayAccess.index);
            try {
                return array[index] = get(node.right);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PassthroughException(e);
            }
        }

        if (node.left instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) node.left;
            Object object = get(fieldAccess.stem);
            if (object == Null.INSTANCE)
                throw new PassthroughException(
                    new NullPointerException("accessing field of null object"));
            Map<String, Object> struct = cast(object);
            Object right = get(node.right);
            struct.put(fieldAccess.fieldName, right);
            return right;
        }

        throw new Error("should not reach here");
    }

    // ---------------------------------------------------------------------------------------------

    private int getIndex (ExpressionNode node)
    {
        long index = get(node);
        if (index < 0)
            throw new ArrayIndexOutOfBoundsException("Negative index: " + index);
        if (index >= Integer.MAX_VALUE - 1)
            throw new ArrayIndexOutOfBoundsException("Index exceeds max array index (2ˆ31 - 2): " + index);
        return (int) index;
    }

    // ---------------------------------------------------------------------------------------------

    private Object[] getNonNullArray (ExpressionNode node)
    {
        Object object = get(node);
        if (object == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("indexing null array"));
        return (Object[]) object;
    }

    // ---------------------------------------------------------------------------------------------

    private Object unaryExpression (UnaryExpressionNode node)
    {
        // there is only NOT
        assert node.operator == UnaryOperator.NOT;
        return ! (boolean) get(node.operand);
    }

    // ---------------------------------------------------------------------------------------------

    private Object arrayAccess (ArrayAccessNode node)
    {
        Object[] array = getNonNullArray(node.array);
        try {
            return array[getIndex(node.index)];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new PassthroughException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Object root (RootNode node)
    {
        assert storage == null;
        rootScope = reactor.get(node, "scope");
        storage = rootStorage = new ScopeStorage(rootScope, null);
        storage.initRoot(rootScope);

        int cores = Runtime.getRuntime().availableProcessors();

        executorService = Executors.newFixedThreadPool(cores*2);


        try {
            node.statements.forEach(this::run);
            executorService.shutdown();
            try {
                boolean terminated = false;
                while (!executorService.isTerminated() && !terminated) {
                    terminated = executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (Return r) {
            return r.value;
            // allow returning from the main script
        } finally {
            storage = null;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void block (BlockNode node) {
        Scope scope = reactor.get(node, "scope");
        storage = new ScopeStorage(scope, storage);
        node.statements.forEach(this::run);
        storage = storage.parent;
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Constructor constructor (ConstructorNode node) {
        // guaranteed safe by semantic analysis
        return new Constructor(get(node.ref));
    }

    // ---------------------------------------------------------------------------------------------

    private Object expressionStmt (ExpressionStatementNode node) {
        get(node.expression);
        return null;  // discard value
    }

    // ---------------------------------------------------------------------------------------------

    private Object fieldAccess (FieldAccessNode node)
    {
        Object stem = get(node.stem);
        if (stem == Null.INSTANCE)
            throw new PassthroughException(
                new NullPointerException("accessing field of null object"));
        return stem instanceof Map
            ? Util.<Map<String, Object>>cast(stem).get(node.fieldName)
            : (long) ((Object[]) stem).length; // only field on arrays
    }

    // ---------------------------------------------------------------------------------------------

    private Object protectedBlock(ProtectBlockNode node) {
        if (node.lock == null) {
            l.lock();
            if (node.lock == null) {
                node.lock = new ReentrantLock();
            }
            l.unlock();
        }
        try {
            node.lock.lock();
            get(node.protectedBlock);
            node.lock.unlock();
        }
        catch (Return r) {
            return r.value;
        }
        return 1;
    }

    // ---------------------------------------------------------------------------------------------

    private Object funCall (FunCallNode node)
    {
        Object decl = get(node.function);
        node.arguments.forEach(this::run);
        Object[] args = map(node.arguments, new Object[0], visitor);

        if (decl == Null.INSTANCE)
            throw new PassthroughException(new NullPointerException("calling a null function"));

        if (decl instanceof SyntheticDeclarationNode) {
            List<Object> objects = new ArrayList<>();
            objects.add(args[0]);
            if (node.arguments.get(0) instanceof ReferenceNode) {
                ReferenceNode referenceNode = (ReferenceNode) node.arguments.get(0);
                objects.add(referenceNode.name);
            }
            return builtin(((SyntheticDeclarationNode) decl).name(), objects.toArray());
        }

        if (decl instanceof Constructor)
            return buildStruct(((Constructor) decl).declaration, args);

        ScopeStorage oldStorage = storage;
        Scope scope = reactor.get(decl, "scope");
        storage = new ScopeStorage(scope, storage);

        FunDeclarationNode funDecl = (FunDeclarationNode) decl;
        coIterate(args, funDecl.parameters,
            (arg, param) -> storage.set(scope, param.name, arg));

        try {
            get(funDecl.block);
        } catch (Return r) {
            return r.value;
        } finally {
            storage = oldStorage;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------


    private class LaunchInterpreter implements Runnable {

        FunCallNode funcall;
        VarDeclarationNode varDecl;

        public LaunchInterpreter (FunCallNode funcall, VarDeclarationNode varDecl) {
            this.funcall = funcall;
            this.varDecl = varDecl;
        }

        @Override
        public void run () {
            Interpreter interpreter2 = new Interpreter(reactor);
            interpreter2.rootStorage = rootStorage;
            interpreter2.storage = storage;
            if (funcall == null) {
                interpreter2.interpret(varDecl);
            } else {
                interpreter2.interpret(funcall);
            }
        }
    }

    private Object launchCall(LaunchNode node) {
        LaunchInterpreter launchInterpreter = new LaunchInterpreter(node.funCall, null);
        try {
            executorService.execute(launchInterpreter);
        } catch (Exception e) {
            System.out.println("thread didn't run correctly");
        }
        return VoidType.INSTANCE;
    }

    private Object launchStateCall(LaunchStateNode node) {
        LaunchInterpreter launchInterpreter = new LaunchInterpreter(null, node.varDeclaration);
        try {
            executorService.execute(launchInterpreter);
        } catch (Exception e) {
            System.out.println("thread didn't run correctly");
        }
        return VoidType.INSTANCE;
    }

    // ---------------------------------------------------------------------------------------------

    private Object builtin (String name, Object[] args)
    {
        if (Objects.equals(name, "print")) {
            String out = convertToString(args[0]);
            System.out.println(out);
            return out;
        } else if (Objects.equals(name, "wait")) {
            while (true) {
                // waiting 20 milliseconds to not overuse the main thread
                try {
                    TimeUnit.MILLISECONDS.sleep(20);
                } catch (Exception e) {
                    System.out.println("problem with waiting");
                }
                try {
                    String variable = args[1].toString();
                    Object valueVar = rootStorage.get(rootScope, variable);
                    if (valueVar != null) {
                        // the variable is assigned, we can continue the execution of the main thread
                        return null;
                    }
                } catch (Exception e) {
                    System.out.println("problem while trying to retrieve the value of the var in the rootStorage");
                }
            }
        } else {
            throw new Error("This is not a builtin function : " + name);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private String convertToString (Object arg)
    {
        if (arg == Null.INSTANCE)
            return "null";
        else if (arg instanceof Object[])
            return Arrays.deepToString((Object[]) arg);
        else if (arg instanceof FunDeclarationNode)
            return ((FunDeclarationNode) arg).name;
        else if (arg instanceof StructDeclarationNode)
            return ((StructDeclarationNode) arg).name;
        else if (arg instanceof Constructor)
            return "$" + ((Constructor) arg).declaration.name;
        else
            return arg.toString();
    }

    // ---------------------------------------------------------------------------------------------

    private HashMap<String, Object> buildStruct (StructDeclarationNode node, Object[] args)
    {
        HashMap<String, Object> struct = new HashMap<>();
        for (int i = 0; i < node.fields.size(); ++i)
            struct.put(node.fields.get(i).name, args[i]);
        return struct;
    }

    // ---------------------------------------------------------------------------------------------

    private Void ifStmt (IfNode node)
    {
        if (get(node.condition))
            get(node.trueStatement);
        else if (node.falseStatement != null)
            get(node.falseStatement);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Void whileStmt (WhileNode node)
    {
        while (get(node.condition))
            get(node.body);
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private Object reference (ReferenceNode node)
    {
        Scope scope = reactor.get(node, "scope");
        DeclarationNode decl = reactor.get(node, "decl");

        if (decl instanceof VarDeclarationNode
            || decl instanceof ParameterNode
            || decl instanceof SyntheticDeclarationNode
            && ((SyntheticDeclarationNode) decl).kind() == DeclarationKind.VARIABLE)

            if (scope == rootScope) {
                return rootStorage.get(scope, node.name);
            } else {
                return storage.get(scope, node.name);
            }

        return decl; // structure or function
    }

    // ---------------------------------------------------------------------------------------------

    private Void returnStmt (ReturnNode node) {
        throw new Return(node.expression == null ? null : get(node.expression));
    }

    // ---------------------------------------------------------------------------------------------

    private Void varDecl (VarDeclarationNode node)
    {
        Scope scope = reactor.get(node, "scope");
        assign(scope, node.name, get(node.initializer), reactor.get(node, "type"));
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private void assign (Scope scope, String name, Object value, Type targetType)
    {
        if (value instanceof Long && targetType instanceof FloatType)
            value = ((Long) value).doubleValue();
        storage.set(scope, name, value);
    }

    // ---------------------------------------------------------------------------------------------
}
