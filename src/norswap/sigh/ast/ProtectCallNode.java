package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class ProtectCallNode extends ExpressionNode
{
    public final ReferenceNode protectedVar;

    @SuppressWarnings("unchecked")
    public ProtectCallNode (Span span, Object protectedVar) {
        super(span);
        this.protectedVar = Util.cast(protectedVar, ReferenceNode.class);
    }

    @Override public String contents ()
    {
        return "Protect var " + this.protectedVar.name;
    }
}
