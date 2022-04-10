package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;

public final class RelaxCallNode extends ExpressionNode
{
    public final ReferenceNode protectedVar;

    @SuppressWarnings("unchecked")
    public RelaxCallNode (Span span, Object protectedVar) {
        super(span);
        this.protectedVar = Util.cast(protectedVar, ReferenceNode.class);
    }

    @Override public String contents ()
    {
        return "Relax var " + this.protectedVar.name;
    }
}
