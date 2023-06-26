package hu.mondokm.ltlverif.refiner;

import com.google.common.collect.ImmutableList;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.refinement.*;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.utils.ExprUtils;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;
import hu.bme.mit.theta.core.utils.indexings.VarIndexingFactory;
import hu.bme.mit.theta.solver.Interpolant;
import hu.bme.mit.theta.solver.ItpMarker;
import hu.bme.mit.theta.solver.ItpPattern;
import hu.bme.mit.theta.solver.ItpSolver;
import hu.mondokm.ltlverif.abstractor.InfTrace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;
import static hu.bme.mit.theta.core.type.inttype.IntExprs.Eq;

public class TraceChecker{

    private final ItpSolver solver;
    private final Expr<BoolType> init;
    private final Expr<BoolType> target;

    private TraceChecker(final Expr<BoolType> init, final Expr<BoolType> target, final ItpSolver solver) {
        this.solver = checkNotNull(solver);
        this.init = checkNotNull(init);
        this.target = checkNotNull(target);
    }

    public static TraceChecker create(final Expr<BoolType> init, final Expr<BoolType> target,
                                                  final ItpSolver solver) {
        return new TraceChecker(init, target, solver);
    }

    public ExprTraceStatus<ItpRefutation> check(final InfTrace infTrace) {
        checkNotNull(infTrace);
        final Trace<ExprState,ExprAction> trace=infTrace.getTrace();
        final int cycleStart=infTrace.getCycleStart();
        if(cycleStart<0) return ExprTraceFwBinItpChecker.create(True(),True(),solver).check(trace);
        final int stateCount = trace.getStates().size();

        final List<VarIndexing> indexings = new ArrayList<>(stateCount);
        indexings.add(VarIndexingFactory.indexing(0));

        solver.push();

        final ItpMarker A = solver.createMarker();
        final ItpMarker B = solver.createMarker();
        final ItpPattern pattern = solver.createBinPattern(A, B);

        int nPush = 1;
        solver.add(A, PathUtils.unfold(init, indexings.get(0)));
        solver.add(A, PathUtils.unfold(trace.getState(0).toExpr(), indexings.get(0)));
        assert solver.check().isSat() : "Initial state of the trace is not feasible";
        int satPrefix = 0;

        HashSet<VarDecl> vars=new HashSet<VarDecl>();

        // itt keressuk meg meddig SAT az utvonal
        for (int i = 1; i < stateCount; ++i) {
            solver.push();
            ++nPush;
            indexings.add(indexings.get(i - 1).add(trace.getAction(i - 1).nextIndexing()));
            solver.add(A, PathUtils.unfold(trace.getState(i).toExpr(), indexings.get(i)));
            solver.add(A, PathUtils.unfold(trace.getAction(i - 1).toExpr(), indexings.get(i - 1)));

            vars.addAll(ExprUtils.getVars(trace.getState(i).toExpr()));
            vars.addAll(ExprUtils.getVars(trace.getAction(i - 1).toExpr()));
            if (solver.check().isSat()) {
                satPrefix = i;
            } else {
                solver.pop();
                --nPush;
                break;
            }
        }


        //
        final boolean concretizable;
        boolean cycle=true;
        if (satPrefix == stateCount - 1) {
            solver.push();
            ++nPush;
            solver.add(B, PathUtils.unfold(target, indexings.get(stateCount - 1)));
            concretizable = solver.check().isSat();
        } else {
            // #infeasibleAsLoopIsUnreachable
            solver.add(B, PathUtils.unfold(trace.getState(satPrefix + 1).toExpr(), indexings.get(satPrefix + 1)));
            solver.add(B, PathUtils.unfold(trace.getAction(satPrefix).toExpr(), indexings.get(satPrefix)));
            solver.check();
            assert solver.getStatus().isUnsat() : "Trying to interpolate a feasible formula";
            concretizable = false; // azaz már maga az út eltörik, ciklus nélkül
        }

        ExprTraceStatus<ItpRefutation> status = null;
        if(concretizable){
            // az út bejárható, itt kérdés, hogy a loop a konkrét modellben is létezik e
            final Valuation model = solver.getModel();
            solver.pop();
            --nPush;
            solver.add(A, PathUtils.unfold(target, indexings.get(stateCount - 1)));
            for(VarDecl var:vars){
                solver.add(B,Eq(PathUtils.unfold(var.getRef(),indexings.get(cycleStart)),PathUtils.unfold(var.getRef(),indexings.get(stateCount-1))));
                solver.check();
                if(solver.getStatus().isUnsat()){
                    final Interpolant interpolant = solver.getInterpolant(pattern);
//                    System.out.println(interpolant.eval(A));
                    ItpFolder folder=new ItpFolder(indexings.get(stateCount-1),model);
//                    System.out.println(folder.foldin(interpolant.eval(A)));
//                    status = ExprTraceStatus.infeasible(ItpRefutation.binary(Eq(var.getRef(),PathUtils.unfold(var.getRef(),indexings.get(cycleStart)).eval(model)),satPrefix,stateCount));
                    status = ExprTraceStatus.infeasible(ItpRefutation.binary(folder.foldin(interpolant.eval(A)),satPrefix,stateCount));
                    cycle=false;
                    break;
                }
            }

        }

        if (concretizable && cycle) {
            final Valuation model = solver.getModel();
            final ImmutableList.Builder<Valuation> builder = ImmutableList.builder();
            for (final VarIndexing indexing : indexings) {
                builder.add(PathUtils.extractValuation(model, indexing));
            }
            status = ExprTraceStatus.feasible(Trace.of(builder.build(), trace.getActions()));
        } else if(cycle) {
            // #infeasibleThroughInterpolant
            final Interpolant interpolant = solver.getInterpolant(pattern);
            final Expr<BoolType> itpFolded = PathUtils.foldin(interpolant.eval(A), indexings.get(satPrefix));
            status = ExprTraceStatus.infeasible(ItpRefutation.binary(itpFolded, satPrefix, stateCount));
        }
        assert status != null;
        solver.pop(nPush);

        return status;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
