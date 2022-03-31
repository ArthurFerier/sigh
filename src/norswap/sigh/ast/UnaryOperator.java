package norswap.sigh.ast;

public enum UnaryOperator
{
    NOT("!"),
    LAUNCH("launch");

    public final String string;

    UnaryOperator (String string) {
        this.string = string;
    }
}
