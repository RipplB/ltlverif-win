package hu.mondokm.ltlverif;

import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.analysis.expr.refinement.ExprTraceStatus;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.JoiningPrecRefiner;
import hu.bme.mit.theta.analysis.pred.ExprSplitters;
import hu.bme.mit.theta.analysis.pred.ItpRefToPredPrec;
import hu.bme.mit.theta.analysis.pred.PredPrec;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;
import hu.mondokm.ltlverif.abstractor.InfTrace;
import hu.mondokm.ltlverif.abstractor.LtlAbstractor;
import hu.mondokm.ltlverif.abstractor.NDFSAbstractor;
import hu.mondokm.ltlverif.abstractor.SUT;
import hu.mondokm.ltlverif.buchi.BuchiAutomaton;
import hu.mondokm.ltlverif.refiner.TraceChecker;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public class CegarVerifier {

    private static TraceChecker checker=TraceChecker.create(True(),True(), Z3SolverFactory.getInstance().createItpSolver());
    private static JoiningPrecRefiner<ExprState, ExprAction,PredPrec, ItpRefutation> refiner=JoiningPrecRefiner.create(new ItpRefToPredPrec(ExprSplitters.atoms()));


    public static boolean verifySUT(SUT sut, BuchiAutomaton automaton, PredPrec startingPrec){
        LtlAbstractor abstractor = NDFSAbstractor.create(sut,automaton);
        PredPrec precision=startingPrec;
        boolean cexFound=true;
        boolean cexFeasible=false;
        while(cexFound && !cexFeasible){
            System.out.println("Precision: " + precision);
            InfTrace trace=abstractor.check(precision);
            if(trace==null) cexFound=false;
            else {
                ExprTraceStatus<ItpRefutation> status=checker.check(trace);
                if(status.isInfeasible()){
                    PredPrec prec=refiner.refine(precision,trace.getTrace(),status.asInfeasible().getRefutation());
                    precision=precision.join(prec);
                }
                if(status.isFeasible()){
                    System.out.println("Status is feasible, valuations: " + status.asFeasible().getValuations());
                    cexFeasible=true;
                }
            }
        }
        return !cexFound || !cexFeasible;
    }

}
