package hu.mondokm.ltlverif.cfa;

import hu.bme.mit.theta.analysis.pred.PredAbstractors;
import hu.bme.mit.theta.analysis.pred.PredPrec;
import hu.bme.mit.theta.analysis.pred.PredState;
import hu.bme.mit.theta.analysis.pred.PredTransFunc;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.CfaAction;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;
import hu.mondokm.ltlverif.abstractor.ProductState;
import hu.mondokm.ltlverif.buchi.BuchiAction;

import java.util.Collection;
import java.util.HashSet;

public class CfaBuchiTransFunc {

    private static PredTransFunc predTransFunc=PredTransFunc.create(PredAbstractors.booleanSplitAbstractor(Z3SolverFactory.getInstance().createSolver()));

    public static HashSet<ProductState> nextStates(CfaProductState curr, PredPrec precision){
        HashSet <ProductState> states=new HashSet<ProductState>();
        for(CFA.Edge edge: curr.getLoc().getOutEdges()) {
            Collection<PredState> collection = predTransFunc.getSuccStates(curr.getPredState(), CfaAction.create(edge), precision);
            for(PredState state : collection){
                if(!state.isBottom())for(BuchiAction action: curr.getBuchiState().getActions()){
                    Collection<PredState> innerCollection = predTransFunc.getSuccStates(state,action,precision);
                    for(PredState innerState : innerCollection){
                        if(!innerState.isBottom())states.add(new CfaProductState(edge, innerState, action.getTarget(),action));
                    }
                }
            }

        }
        return states;
    }

}
