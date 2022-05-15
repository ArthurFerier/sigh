package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class ProtectBlockNode extends StatementNode
{
    public final BlockNode protectedBlock;
    //public final ReentrantLock lock;

    @SuppressWarnings("unchecked")
    public ProtectBlockNode (Span span, Object protectedBlock) { //, ReentrantLock lock
        super(span);
        this.protectedBlock = Util.cast(protectedBlock, BlockNode.class);
        //this.lock = lock;
    }

    @Override public String contents ()
    {
        return "Protect block : "+ protectedBlock.contents();
    }
}
