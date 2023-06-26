package hu.mondokm.ltlverif.refiner;

import hu.bme.mit.theta.core.decl.Decl;
import hu.bme.mit.theta.core.decl.IndexedConstDecl;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.anytype.RefExpr;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;

import static com.google.common.base.Preconditions.checkArgument;
import static hu.bme.mit.theta.core.type.anytype.Exprs.Prime;

public class ItpFolder {

    private final VarIndexing indexing;
    private Valuation valuation;

    public ItpFolder(final VarIndexing indexing, Valuation valuation) {

        this.indexing = indexing;
        this.valuation=valuation;
    }

    public <T extends Type> Expr<T> foldin(final Expr<T> expr) {
        if (expr instanceof RefExpr) {
            final RefExpr<T> ref = (RefExpr<T>) expr;
            final Decl<T> decl = ref.getDecl();
            if (decl instanceof IndexedConstDecl) {
                final IndexedConstDecl<T> constDecl = (IndexedConstDecl<T>) decl;
                final VarDecl<T> varDecl = constDecl.getVarDecl();
                final int index = constDecl.getIndex();
                final int nPrimes = index - indexing.get(varDecl);
                final Expr<T> varRef = varDecl.getRef();
                if (nPrimes == 0) {
                    return varRef;
                } else if(nPrimes>0){
                    return Prime(varRef, nPrimes);
                } else {
                    return expr.eval(valuation);
                }
            }
        }

        return expr.map(this::foldin);
    }
}
